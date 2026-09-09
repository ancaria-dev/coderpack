package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.event.Damage
import dev.ancaria.coderpack.api.event.Death
import dev.ancaria.coderpack.api.event.MobHit
import dev.ancaria.coderpack.api.event.NearDeath

// Damage is the one vetoable event here: its hook sits on the instruction that
// commits HP, with the new value still in a register. Everything else in this
// file reports something that has already happened, and the properties say so
// by being `val`.
//
// MobHit's extensions reach MobDeath as well, which is a MobHit that finished
// the job.

/** "damage", "heal" or "clamp". */
public inline val Damage.kind: String? get() = kind()

public inline val Damage.damage: Long get() = damage()

/** HP before the blow. */
public inline val Damage.hp: Long get() = hp()

/** The HP the game is about to store. Assigning to it clamps to 0..maxHp. */
public inline var Damage.next: Long
    get() = next()
    set(value) = next(value)

public inline val Damage.maxHp: Long get() = maxHp()

public inline val Death.hpBefore: Long get() = hpBefore()

public inline val Death.maxHp: Long get() = maxHp()

/** The damage of the killing blow. */
public inline val Death.blow: Long get() = blow()

public inline val NearDeath.hp: Long get() = hp()

public inline val NearDeath.maxHp: Long get() = maxHp()

public inline val NearDeath.percent: Int get() = percent()

public inline val MobHit.typeId: Int get() = typeId()

/** Internal name, e.g. TYPE_NPC_GHUL01. Stable and English, so match on it. */
public inline val MobHit.typeName: String? get() = typeName()

public inline val MobHit.level: Int get() = level()

public inline val MobHit.hp: Long get() = hp()

public inline val MobHit.next: Long get() = next()

public inline val MobHit.maxHp: Long get() = maxHp()

public inline val MobHit.damage: Long get() = damage()
