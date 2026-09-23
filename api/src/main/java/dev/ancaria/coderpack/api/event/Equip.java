package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.Item;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * An equipment slot changed. One game function does both directions, so
 * unequipping arrives here too, with {@link #isOff()} set and no item.
 *
 * <p>Slots seen so far: 5 arms, 10 and 11 rings, 12 and 13 weapon hands.
 */
public final class Equip extends Event {

    private final Item item;

    public Equip(Map<String, String> fields) {
        super(fields);
        this.item = new Item(fields);
    }

    public int getSlot() {
        return (int) getNum("slot");
    }

    /** Null when the slot is being cleared. */
    @Nullable
    public Item getItem() {
        return isOff() ? null : item;
    }

    public boolean isOff() {
        return getNum("off") == 1;
    }

    public boolean isPlayer() {
        return getNum("player") == 1;
    }
}
