package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Context
import dev.ancaria.coderpack.api.Events
import dev.ancaria.coderpack.api.Game
import dev.ancaria.coderpack.api.Handle
import dev.ancaria.coderpack.api.Priority
import dev.ancaria.coderpack.api.entity.Player
import dev.ancaria.coderpack.api.event.Decides
import dev.ancaria.coderpack.api.event.Event
import dev.ancaria.coderpack.api.event.EventMutation

import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Function

// Stand-ins for the loader. Everything in this module forwards to the API and
// nothing reaches the game, so what a test has to watch is what was forwarded:
// which class went to the bus, with which priority, and how often a listener
// was called afterwards.

/** A bus that keeps what it was handed and fires on demand. */
class Bus : Events {

    var registered: Any? = null
        private set

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

    override fun register(listener: Any) {
        registered = listener
    }

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
        return Handle { live.remove(added) }
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
        return Handle { live.remove(added) }
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
class EagerBus(private val event: Event) : Events {

    var live: Int = 0
        private set

    override fun register(listener: Any): Unit = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <E : Event> on(
        type: Class<E>,
        priority: Priority,
        ignoreVetoed: Boolean,
        listener: Consumer<E>,
    ): Handle {
        live++
        (listener as Consumer<Event>).accept(event)
        return Handle { live-- }
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
        return Handle { live-- }
    }
}

class FakeContext(private val bus: Events = Bus()) : Context {

    val logged: MutableList<String> = mutableListOf()

    override fun id(): String = "demo-mod"

    override fun events(): Events = bus

    override fun game(): Game = FakeGame()

    override fun gameDir(): Path = Path.of(".")

    override fun log(message: String) {
        logged.add(message)
    }
}

/** Answers the one question the extensions here ask it. */
class FakeGame(private val known: Map<String, Int> = mapOf("TYPE_OBJECT_RING" to 5171)) : Game {

    override fun player(): Player? = null

    override fun world(): dev.ancaria.coderpack.api.Realm = throw UnsupportedOperationException("no world in a test")

    override fun uiString(key: String): String? = null

    override fun typeName(typeId: Int): String? = null

    override fun typeId(name: String): Int = known[name] ?: 0

    override fun types(prefix: String): Map<String, Int> =
        known.filterKeys { it.startsWith(prefix) }

    override fun retype(ref: Int, typeId: Int): Boolean = false

    override fun reshape(ref: Int, template: dev.ancaria.coderpack.api.entity.Item): Boolean = false
}
