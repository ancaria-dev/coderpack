package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Context
import dev.ancaria.coderpack.api.EventRegistry
import dev.ancaria.coderpack.api.Game
import dev.ancaria.coderpack.api.GameConsole
import dev.ancaria.coderpack.api.Handle
import dev.ancaria.coderpack.api.ModRegistry
import dev.ancaria.coderpack.api.Priority
import dev.ancaria.coderpack.api.Realm
import dev.ancaria.coderpack.api.Registry
import dev.ancaria.coderpack.api.SacredMod
import dev.ancaria.coderpack.api.SacredModDescriptor
import dev.ancaria.coderpack.api.TypeRegistry
import dev.ancaria.coderpack.api.entity.Item
import dev.ancaria.coderpack.api.event.Decides
import dev.ancaria.coderpack.api.event.Event
import dev.ancaria.coderpack.api.event.EventMutation

import java.nio.file.Path
import java.time.Duration
import java.util.function.Consumer
import java.util.function.Function

// Stand-ins for the loader. Everything in this module forwards to the API and
// nothing reaches the game, so what a test has to watch is what was forwarded:
// which class went to the bus, with which priority, and how often a listener
// was called afterwards.

/** The mod a fake handle says it belongs to. Made with `new`, so it has no context. */
object Nobody : SacredMod()

/** A handle that runs [off] once, however often it is asked to. */
class FakeHandle(
    private val type: Class<out Event>,
    private val priority: Priority = Priority.NORMAL,
    private val ignoreVetoed: Boolean = false,
    private val off: () -> Unit,
) : Handle {

    @Volatile
    private var registered = true

    @Synchronized
    override fun unregister() {
        if (registered) {
            registered = false
            off()
        }
    }

    override fun isRegistered(): Boolean = registered

    override fun getMod(): SacredMod = Nobody

    override fun getEventType(): Class<out Event> = type

    override fun getPriority(): Priority = priority

    override fun isIgnoreVetoed(): Boolean = ignoreVetoed

    override fun getListenerClass(): Class<*>? = null

    override fun getMethodName(): String? = null
}

/** The parts of [EventRegistry] no test here reaches. */
abstract class UnusedRegistry : EventRegistry {

    override fun register(listener: Any): List<Handle> = emptyList()

    override fun getEvents(): List<Handle> = emptyList()

    override fun unregister(handle: Handle): Boolean {
        val was = handle.isRegistered
        handle.unregister()
        return was
    }

    override fun unregister(listener: Any): Int = 0

    override fun unregister(listener: Any, type: Class<out Event>): Int = 0
}

/** A bus that keeps what it was handed and fires on demand. */
class Bus : UnusedRegistry() {

    var type: Class<*>? = null
        private set

    var priority: Priority? = null
        private set

    var ignoreVetoed: Boolean = false
        private set

    /** Listeners still on the bus. Registering adds one, unregistering removes it. */
    val live: MutableList<Consumer<Event>> = mutableListOf()

    /** What the deciding listeners answered the last time [fire] ran. */
    val answers: MutableList<EventMutation?> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    override fun <E : Event> on(
        type: Class<E>,
        priority: Priority,
        ignoreVetoed: Boolean,
        listener: Consumer<E>,
    ): Handle {
        this.type = type
        this.priority = priority
        this.ignoreVetoed = ignoreVetoed
        val added = listener as Consumer<Event>
        live.add(added)
        return FakeHandle(type, priority, ignoreVetoed) { live.remove(added) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <M : EventMutation, E> decide(
        type: Class<E>,
        priority: Priority,
        ignoreVetoed: Boolean,
        listener: Function<E, M>,
    ): Handle where E : Event, E : Decides<M> {
        this.type = type
        this.priority = priority
        this.ignoreVetoed = ignoreVetoed
        // One list for both kinds, because "still on the bus" is one idea.
        // A decider is wrapped so firing it records what it answered.
        val raw = listener as Function<Event, out EventMutation>
        val added = Consumer<Event> { event -> answers.add(raw.apply(event)) }
        live.add(added)
        return FakeHandle(type, priority, ignoreVetoed) { live.remove(added) }
    }

    /** What dispatch does: every listener still on the bus, in order. */
    fun fire(event: Event) {
        answers.clear()
        for (listener in live.toList()) {
            listener.accept(event)
        }
    }
}

/**
 * A bus that dispatches before it hands the handle back.
 *
 * The race `once` is written around, made deterministic: the event arrives
 * inside `on`, so a listener that expects to have been given a handle by then
 * has not been.
 */
class EagerBus(private val event: Event) : UnusedRegistry() {

    var live: Int = 0
        private set

    @Suppress("UNCHECKED_CAST")
    override fun <E : Event> on(
        type: Class<E>,
        priority: Priority,
        ignoreVetoed: Boolean,
        listener: Consumer<E>,
    ): Handle {
        live++
        (listener as Consumer<Event>).accept(event)
        return FakeHandle(type) { live-- }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <M : EventMutation, E> decide(
        type: Class<E>,
        priority: Priority,
        ignoreVetoed: Boolean,
        listener: Function<E, M>,
    ): Handle where E : Event, E : Decides<M> {
        live++
        (listener as Function<Event, M>).apply(event)
        return FakeHandle(type) { live-- }
    }
}

class FakeContext(private val bus: EventRegistry = Bus()) : Context {

    val logged: MutableList<String> = mutableListOf()

    override fun log(message: String) {
        logged.add(message)
    }

    override fun print(message: String) {
        logged.add(message)
    }

    override fun getGame(): Game = FakeGame()

    override fun getUptime(): Duration = Duration.ZERO

    override fun getDescriptor(): SacredModDescriptor =
        throw UnsupportedOperationException("no descriptor in a test")

    override fun getRegistry(): Registry = object : Registry {

        override fun getModRegistry(): ModRegistry =
            throw UnsupportedOperationException("no mod registry in a test")

        override fun getEventRegistry(): EventRegistry = bus
    }
}

/** A game whose only live part is the type table. */
class FakeGame(private val types: TypeRegistry = FakeTypes()) : Game {

    override fun getDirectory(): Path = Path.of(".")

    override fun getUiString(key: String): String? = null

    override fun getWorld(): Realm = throw UnsupportedOperationException("no world in a test")

    override fun getTypeRegistry(): TypeRegistry = types

    override fun getConsole(): GameConsole = GameConsole { }
}

/** Answers the one question the extensions here ask it. */
class FakeTypes(private val known: Map<String, Int> = mapOf("TYPE_OBJECT_RING" to 5171)) : TypeRegistry {

    override fun types(prefix: String): Map<String, Int> =
        known.filterKeys { it.startsWith(prefix) }

    override fun getTypeId(name: String): Int = known[name] ?: 0

    override fun getTypeName(typeId: Int): String? = null

    override fun retype(ref: Int, typeId: Int): Boolean = false

    override fun reshape(ref: Int, template: Item): Boolean = false
}
