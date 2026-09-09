package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.event.Attribute
import dev.ancaria.coderpack.api.event.Experience
import dev.ancaria.coderpack.api.event.Gold
import dev.ancaria.coderpack.api.event.Skill

// The four vetoable events about a character getting richer or better, and the
// reason this whole module is worth having. Each one has a field the game is
// about to write and a method that rewrites it, which in Kotlin is a `var`:
//
//     gold.delta = gold.delta * 3 / 2
//
// rather than `gold.delta(gold.delta() * 3 / 2)`, where the two halves of the
// same field are spelled the same way and mean opposite things.
//
// Which field is writable is not a matter of taste. `Gold.delta` is writable
// and `Gold.current` is not, because the game keeps XOR-encoded mirrors of the
// total and resets a total it did not compute itself to 1. The `val`s below are
// the fields the API refuses to rewrite, and they stay `val` here.

/** Negative for a purchase, positive for loot. Rewrite this, never the total. */
public inline var Gold.delta: Long
    get() = delta()
    set(value) = delta(value)

public inline val Gold.current: Long get() = current()

public inline val Gold.spending: Boolean get() = spending()

public inline val Experience.gain: Long get() = gain()

public inline val Experience.exp: Long get() = exp()

/** The new total the game is about to store. Clamped by the game, and capped at int32 on the way. */
public inline var Experience.next: Long
    get() = next()
    set(value) = next(value)

/** Slots are reported by index. The skill set differs per class and per character. */
public inline val Skill.slot: Int get() = slot()

public inline val Skill.delta: Long get() = delta()

public inline val Skill.value: Long get() = value()

/** Stored as a byte, so what the game receives is clamped to 0..255. */
public inline var Skill.next: Long
    get() = next()
    set(value) = next(value)

/** 0 Strength, 1 Endurance, 2 Dexterity, 3 PhysReg, 4 MentalReg, 5 Charisma. */
public inline val Attribute.index: Int get() = index()

public inline val Attribute.name: String? get() = name()

public inline val Attribute.value: Long get() = value()

public inline var Attribute.next: Long
    get() = next()
    set(value) = next(value)
