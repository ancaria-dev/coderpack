package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Game
import dev.ancaria.coderpack.api.entity.HeroClass
import dev.ancaria.coderpack.api.entity.Player

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
