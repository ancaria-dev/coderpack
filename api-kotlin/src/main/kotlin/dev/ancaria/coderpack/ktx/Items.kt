package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.entity.Item
import dev.ancaria.coderpack.api.event.Equip
import dev.ancaria.coderpack.api.event.Moved
import dev.ancaria.coderpack.api.event.Pickup
import dev.ancaria.coderpack.api.event.Stored

// Items, and the events that carry one.
//
// Pickup decides which object is picked up and what that object becomes,
// neither of which is a quantity, so it has no `value`. A listener answers with
// Pickup.Mutation.replace, .retype or .reshape. The properties here
// stay methods.

public inline val Pickup.item: Item get() = item()

/** True when the hero is picking it up rather than a creature. */
public inline val Pickup.player: Boolean get() = player()

public inline val Equip.slot: Int get() = slot()

/** Null when the slot is being cleared. */
public inline val Equip.item: Item? get() = item()

public inline val Equip.off: Boolean get() = off()

public inline val Equip.player: Boolean get() = player()

public inline val Stored.item: Item get() = item()

public inline val Stored.player: Boolean get() = player()

public inline val Moved.from: Int get() = from()

public inline val Moved.to: Int get() = to()

/** The object-manager reference. Stable for the session, not across launches. */
public inline val Item.ref: Int get() = ref()

public inline val Item.typeId: Int get() = typeId()

/**
 * Internal name, e.g. TYPE_OBJECT_RING_FIRE01, or null when the reference did
 * not resolve. There is no display name: Sacred composes those from affixes,
 * so no single string exists to read.
 */
public inline val Item.typeName: String? get() = typeName()

public inline val Item.level: Int get() = level()

/** Level the character needs to use it. */
public inline val Item.minLevel: Int get() = minLevel()

public inline val Item.attack: Int get() = attack()

/** The tooltip's bracketed number. The game shows the two parts added up. */
public inline val Item.protection: Int get() = protection()

public inline val Item.percent: Int get() = percent()

/** Base value. The tooltip price is derived from it and moves with charisma. */
public inline val Item.price: Int get() = price()

/** The modifier list as it travels on the wire, for copying it verbatim. */
public inline val Item.packedModifiers: String get() = packedModifiers()

/** What the item actually does, as `id -> value`. Not the same thing as its type. */
public inline val Item.modifiers: Map<Int, Int> get() = modifiers()

/** False when the ref did not resolve. The item was gone by then. */
public inline val Item.known: Boolean get() = known()

/**
 * One modifier's value, or null when the item does not carry it:
 * `if (item[601] != null) ...` for an item with Weapon Lore on it.
 *
 * Null rather than 0 on purpose. A modifier the item does not have and one it
 * has at zero are different facts, and the map already tells them apart.
 */
public operator fun Item.get(modifier: Int): Int? = modifiers()[modifier]
