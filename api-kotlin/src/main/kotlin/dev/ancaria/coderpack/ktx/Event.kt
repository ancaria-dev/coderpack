package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.event.Amount
import dev.ancaria.coderpack.api.event.Decision
import dev.ancaria.coderpack.api.event.Event

/**
 * The field as it arrived, or null when the agent did not send it:
 * `event["delta"]`.
 *
 * The named getters cover what an event is about. This is the way at a field
 * the agent has started sending and no SDK release has named yet, which is the
 * reason the raw map is on [Event] at all.
 */
public operator fun Event.get(key: String): String? = text(key)

/** The field as a number, 0 when it is missing or not one. Parsed once and remembered. */
public fun Event.long(key: String): Long = num(key)

/** [long], narrowed. The wire carries 32-bit game values in most of these fields. */
public fun Event.int(key: String): Int = num(key).toInt()

/** Every field as it arrived. */
public inline val Event.fields: Map<String, String> get() = fields()

/**
 * Whether a listener before this one vetoed the write.
 *
 * A veto does not stop the dispatch, so this can be true while the event is
 * still going round, and a later listener can lift it with a reset. A listener
 * with nothing to say about a vetoed event asks to be skipped instead, with
 * `ignoreVetoed = true`.
 */
public inline val Decision.vetoed: Boolean get() = vetoed()

/**
 * The number under decision, with every earlier listener folded in. This is the
 * one to read: two mods doubling it compose into four times.
 */
public inline val Amount.value: Long get() = value()

/** The number as the game sent it, before any mod touched it. */
public inline val Amount.initial: Long get() = initial()
