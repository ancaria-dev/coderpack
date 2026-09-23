package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.entity.Item

// Items. Everything an Item says about itself is a getter, so `item.typeName`
// and `item.modifiers` need nothing from here. The one addition is reading a
// single modifier by its id.

/**
 * One modifier's value, or null when the item does not carry it:
 * `if (item[601] != null) ...` for an item with Weapon Lore on it.
 *
 * Null rather than 0 on purpose. A modifier the item does not have and one it
 * has at zero are different facts, and the map already tells them apart.
 */
public operator fun Item.get(modifier: Int): Int? = modifiers[modifier]
