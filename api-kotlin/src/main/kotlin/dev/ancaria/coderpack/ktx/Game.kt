package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Game
import dev.ancaria.coderpack.api.Realm
import dev.ancaria.coderpack.api.entity.Attributes
import dev.ancaria.coderpack.api.entity.CombatArts
import dev.ancaria.coderpack.api.entity.Creature
import dev.ancaria.coderpack.api.entity.HeroClass
import dev.ancaria.coderpack.api.entity.Player
import dev.ancaria.coderpack.api.entity.Sheet
import dev.ancaria.coderpack.api.entity.Skills
import dev.ancaria.coderpack.api.entity.Stats

/**
 * A point in the world.
 *
 * The game's coordinates travel as two loose ints through [Player.teleport] and
 * every read that produces them, which is two chances to swap them. This is the
 * pair with names on it. The HUD shows each component divided by 53.66563 and
 * truncated; these are the world numbers.
 */
public data class Pos(val x: Int, val y: Int)

/** Null until a world is loaded. */
public inline val Game.player: Player? get() = player()

/**
 * The id behind an internal name, or null when the game does not know it.
 *
 * [Game.typeId] answers 0 for an unknown name, and 0 is also what an
 * uninitialised field holds, so a mod that forgets to check it looks up
 * nothing and reports nothing. This makes the unknown case a type the compiler
 * asks about.
 */
public fun Game.typeIdOrNull(name: String): Int? = typeId(name).takeIf { it != 0 }

public inline val Player.heroClass: HeroClass get() = heroClass()

public inline val Player.level: Int get() = level()

/** Reads the last state the loader saw. Assigning goes to the game thread. */
public inline var Player.hp: Long
    get() = hp()
    set(value) = hp(value)

public inline val Player.maxHp: Long get() = maxHp()

public inline var Player.gold: Long
    get() = gold()
    set(value) = gold(value)

public inline val Player.exp: Long get() = exp()

/** Where the hero is, in world coordinates. */
public inline val Player.position: Pos get() = Pos(x(), y())

/** [Player.teleport], with the pair kept together. */
public fun Player.teleport(to: Pos): Unit = teleport(to.x, to.y)

// Everything below asks the game on every read: one round-trip, a fresh
// snapshot back. Read a property once and keep the value for as long as the
// moment it describes, rather than reading it again in the same expression.
// Attributes, Skills and CombatArts are Iterable with a Java `get`, so
// `attributes[Attributes.Kind.STRENGTH]` and `for (slot in skills)` work as is.

/** The creatures around the hero and where the hero is. */
public inline val Game.world: Realm get() = world()

public inline val Player.attributes: Attributes get() = attributes()

public inline val Player.skills: Skills get() = skills()

public inline val Player.combatArts: CombatArts get() = combatArts()

/** The journal's Statistics page. */
public inline val Player.stats: Stats get() = stats()

/** Derived numbers from the character screen. */
public inline val Player.sheet: Sheet get() = sheet()

/** [Realm.creaturesNear], around a point kept as one value. */
public fun Realm.creaturesNear(at: Pos, radius: Int): List<Creature> =
    creaturesNear(at.x, at.y, radius)

/** The last sector the hero entered, or null until one was crossed. */
public val Realm.sector: Pos? get() = sectorX().takeIf { it >= 0 }?.let { Pos(it, sectorY()) }

public inline val Creature.ref: Int get() = ref()

public inline val Creature.typeId: Int get() = typeId()

/** Internal name, e.g. TYPE_NPC_GHUL01. Stable and English, so match on it. */
public inline val Creature.typeName: String? get() = typeName()

public inline val Creature.level: Int get() = level()

public inline val Creature.hp: Long get() = hp()

public inline val Creature.maxHp: Long get() = maxHp()

public inline val Creature.alive: Boolean get() = alive()

public inline val Creature.position: Pos get() = Pos(x(), y())
