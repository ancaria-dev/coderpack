package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.event.Event
import dev.ancaria.coderpack.api.event.Veto

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
 * Whether a listener before this one suppressed the write.
 *
 * A cancel does not stop the dispatch, so this can be true while the event is
 * still going round. A listener with nothing to say about a cancelled event
 * asks to be skipped instead, with `ignoreCancelled = true`.
 */
public inline val Veto.canceled: Boolean get() = canceled()
