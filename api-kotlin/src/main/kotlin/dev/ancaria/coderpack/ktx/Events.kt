package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Context
import dev.ancaria.coderpack.api.Events
import dev.ancaria.coderpack.api.Handle
import dev.ancaria.coderpack.api.Priority
import dev.ancaria.coderpack.api.event.Event

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * Registers a listener for [E], the type argument taking the place of the
 * `Class` the Java call needs:
 *
 * ```
 * events.on<Damage> { it.next = it.maxHp }
 * events.on<Gold>(Priority.LAST, ignoreCancelled = true) { ... }
 * ```
 *
 * Same bus, same order, same MONITOR rule. This is [Events.on] with the class
 * literal derived and the two flags given the defaults `@Subscribe` has.
 */
public inline fun <reified E : Event> Events.on(
    priority: Priority = Priority.NORMAL,
    ignoreCancelled: Boolean = false,
    crossinline listener: (E) -> Unit,
): Handle = on(E::class.java, priority, ignoreCancelled, Consumer { listener(it) })

/** [on], straight off the context, for a mod that only registers a listener or two. */
public inline fun <reified E : Event> Context.on(
    priority: Priority = Priority.NORMAL,
    ignoreCancelled: Boolean = false,
    crossinline listener: (E) -> Unit,
): Handle = events().on(E::class.java, priority, ignoreCancelled, Consumer { listener(it) })

/**
 * The registration block:
 *
 * ```
 * context.events {
 *     on<Hero> { ... }
 *     on<Damage>(Priority.FIRST) { ... }
 * }
 * ```
 *
 * [Events] is the receiver inside, so a mod that registers more than one
 * listener says `events` once instead of on every line.
 */
public inline fun Context.events(block: Events.() -> Unit) {
    events().block()
}

/**
 * A listener that takes itself off the bus the first time it fires.
 *
 * For the events a mod waits for rather than watches: the first
 * [dev.ancaria.coderpack.api.event.Hero] of a session, the pickup that finishes
 * a quest. Written by hand this is a handle a listener has to see before
 * anything can hand it one, and the gap between those two fires twice.
 */
public inline fun <reified E : Event> Events.once(
    priority: Priority = Priority.NORMAL,
    ignoreCancelled: Boolean = false,
    noinline listener: (E) -> Unit,
): Handle = once(E::class.java, priority, ignoreCancelled, listener)

/**
 * The part of [once] that is a race rather than sugar, kept out of the inline
 * body so there is one copy of it and it can be read.
 *
 * Registration returns a handle, dispatch runs on the loader's own thread, and
 * nothing orders the two: the event this listener waits for can arrive before
 * `on` has returned the handle that would take it off again. So neither side
 * waits for the other. The [AtomicBoolean] settles which call reaches the mod,
 * exactly one, and whichever side loses the race does the unregistering —
 * both, if they tie, which [Handle.unregister] allows.
 *
 * The handle travels through an [AtomicReference] rather than a captured
 * variable for the ordering, not the atomicity: a plain capture leaves the
 * dispatch thread free to read a stale null, and then nothing ever comes off
 * the bus.
 */
@PublishedApi
internal fun <E : Event> Events.once(
    type: Class<E>,
    priority: Priority,
    ignoreCancelled: Boolean,
    listener: (E) -> Unit,
): Handle {
    val fired = AtomicBoolean()
    val registered = AtomicReference<Handle>()
    val handle = on(type, priority, ignoreCancelled, Consumer { event ->
        if (fired.compareAndSet(false, true)) {
            // Null only when the event beat `on` back to the line below, which
            // then reads the flag and unregisters instead.
            registered.get()?.unregister()
            listener(event)
        }
    })
    registered.set(handle)
    if (fired.get()) {
        handle.unregister()
    }
    return handle
}

/**
 * One handle for two, so a pair registered together comes off together:
 * `(on<Hero> { } + on<Death> { }).unregister()`.
 */
public operator fun Handle.plus(other: Handle): Handle {
    val first = this
    return Handle {
        first.unregister()
        other.unregister()
    }
}

/** Takes every listener in the collection off the bus. */
public fun Iterable<Handle>.unregister(): Unit = forEach(Handle::unregister)
