package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.event.Attribute
import dev.ancaria.coderpack.api.event.Experience
import dev.ancaria.coderpack.api.event.Gold
import dev.ancaria.coderpack.api.event.Skill

// The four decidable events about a character getting richer or better.
//
// Every property here is a `val`, and that is the whole point of the shape the
// API settled on. These used to be `var`s whose assignment did not change what
// the getter read: `gold.delta = x` added a rewrite the loader collected
// separately, and a read still gave the game's own number, to this listener and
// to every later one. That asymmetry is gone because the event is read-only. A
// listener returns a mutation instead, and what it decided is visible to the
// next listener through `value`.
//
// The number under decision is `value`, with `initial` beside it, both in
// Event.kt for all five numeric events at once. What differs per event is what
// that number means, and the named properties below say which.

/** The total before this change. The game keeps XOR mirrors of it, so it is never decided. */
public inline val Gold.current: Long get() = current()

public inline val Gold.spending: Boolean get() = spending()

public inline val Experience.gain: Long get() = gain()

/** The total before this grant. */
public inline val Experience.exp: Long get() = exp()

/** Slots are reported by index. The skill set differs per class and per character. */
public inline val Skill.slot: Int get() = slot()

public inline val Skill.delta: Long get() = delta()

/** Reconstructed rather than observed, and wrong at either end of 0..255. */
public inline val Skill.previous: Long get() = previous()

/** 0 Strength, 1 Endurance, 2 Dexterity, 3 PhysReg, 4 MentalReg, 5 Charisma. */
public inline val Attribute.index: Int get() = index()

public inline val Attribute.name: String? get() = name()

/** The value before the point was spent. */
public inline val Attribute.previous: Long get() = previous()
