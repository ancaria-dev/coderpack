package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.entity.Creature
import dev.ancaria.coderpack.api.entity.HeroClass
import dev.ancaria.coderpack.api.event.Despawn
import dev.ancaria.coderpack.api.event.Discovery
import dev.ancaria.coderpack.api.event.Hero
import dev.ancaria.coderpack.api.event.LevelUp
import dev.ancaria.coderpack.api.event.Position
import dev.ancaria.coderpack.api.event.Region
import dev.ancaria.coderpack.api.event.Sector
import dev.ancaria.coderpack.api.event.Spawn
import dev.ancaria.coderpack.api.event.Unknown
import dev.ancaria.coderpack.api.event.World

// Whose game this is, where they are, and what the session is doing. Read-only
// events, so read-only properties.

public inline val Hero.heroClass: HeroClass get() = heroClass()

/** The game's own name for the class, already localized where it matters. */
public inline val Hero.className: String? get() = className()

public inline val Hero.level: Int get() = level()

public inline val Hero.hp: Long get() = hp()

public inline val Hero.maxHp: Long get() = maxHp()

public inline val Hero.gold: Long get() = gold()

public inline val Hero.exp: Long get() = exp()

public inline val LevelUp.previous: Int get() = previous()

public inline val LevelUp.level: Int get() = level()

public inline val World.phase: World.Phase get() = phase()

// Position is the one event the loader refuses to memoize, because it fires on
// most frames while the player walks and every field on it is read once. These
// are inline, so reading `pos.x` costs the same parse the method does and
// nothing on top.

/** World coordinate. The HUD shows this divided by 53.66563, truncated. */
public inline val Position.x: Int get() = x()

public inline val Position.y: Int get() = y()

public inline val Position.hudX: Int get() = hudX()

public inline val Position.hudY: Int get() = hudY()

/** A map cell id, not a named area. */
public inline val Region.id: Int get() = id()

public inline val Region.entered: Boolean get() = entered()

/** On entry, the region the hero came from, or 0 when unknown. */
public inline val Region.from: Int get() = from()

public inline val Sector.x: Int get() = x()

public inline val Sector.y: Int get() = y()

public inline val Spawn.creature: Creature get() = creature()

public inline val Despawn.creature: Creature get() = creature()

/** Areas discovered so far, this one included. */
public inline val Discovery.areas: Long get() = areas()

/** The wire name of an event no SDK type covers yet. */
public inline val Unknown.name: String get() = name()
