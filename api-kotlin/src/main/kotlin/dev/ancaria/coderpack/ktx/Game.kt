package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.EntityRegistry
import dev.ancaria.coderpack.api.Realm
import dev.ancaria.coderpack.api.TypeRegistry
import dev.ancaria.coderpack.api.entity.Creature
import dev.ancaria.coderpack.api.entity.Player

// The game side is getters all the way down, `game.world.entityRegistry.player`
// included, so Kotlin reads it as properties with nothing from here. What this
// file adds is the one thing the Java cannot say: a point as a single value.

/**
 * A point in the world.
 *
 * The game's coordinates travel as two loose ints through [Player.teleport] and
 * every read that produces them, which is two chances to swap them. This is the
 * pair with names on it. The HUD shows each component divided by 53.66563 and
 * truncated; these are the world numbers.
 */
public data class Pos(val x: Int, val y: Int)

/** Where the hero is, in world coordinates. */
public inline val Player.position: Pos get() = Pos(x, y)

/** [Player.teleport], with the pair kept together. */
public fun Player.teleport(to: Pos): Unit = teleport(to.x, to.y)

public inline val Creature.position: Pos get() = Pos(x, y)

/**
 * [EntityRegistry.getCreaturesNear], around a point kept as one value. One
 * round-trip to the game, like the call it forwards to.
 */
public fun EntityRegistry.creaturesNear(at: Pos, radius: Int): List<Creature> =
    getCreaturesNear(at.x, at.y, radius)

/** The last sector the hero entered, or null until one was crossed. */
public val Realm.sector: Pos? get() = sectorX.takeIf { it >= 0 }?.let { Pos(it, sectorY) }

/**
 * The id behind an internal name, or null when the game does not know it.
 *
 * [TypeRegistry.getTypeId] answers 0 for an unknown name, and 0 is also what
 * an uninitialised field holds, so a mod that forgets to check it looks up
 * nothing and reports nothing. This makes the unknown case a type the compiler
 * asks about.
 */
public fun TypeRegistry.typeIdOrNull(name: String): Int? = getTypeId(name).takeIf { it != 0 }
