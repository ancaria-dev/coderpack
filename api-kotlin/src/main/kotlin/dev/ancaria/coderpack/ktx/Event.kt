package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.event.Event

// Every named reader on an event is a Java getter now, `getValue()` and
// `isVetoed()`, so Kotlin already sees `it.value` and `it.isVetoed` without
// help from this module. What is left here is the raw map, whose readers take
// a key and therefore stay methods.

/**
 * The field as it arrived, or null when the agent did not send it:
 * `event["delta"]`.
 *
 * The named getters cover what an event is about. This is the way at a field
 * the agent has started sending and no SDK release has named yet, which is the
 * reason the raw map is on [Event] at all.
 */
public operator fun Event.get(key: String): String? = getText(key)

/** The field as a number, 0 when it is missing or not one. Parsed once and remembered. */
public fun Event.long(key: String): Long = getNum(key)

/** [long], narrowed. The wire carries 32-bit game values in most of these fields. */
public fun Event.int(key: String): Int = getNum(key).toInt()
