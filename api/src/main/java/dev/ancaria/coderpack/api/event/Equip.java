package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.Item;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * An equipment slot changed. One game function does both directions, so
 * unequipping arrives here too, with {@link #off()} set and no item.
 *
 * <p>Slots seen so far: 5 arms, 10 and 11 rings, 12 and 13 weapon hands.
 */
public final class Equip extends Event {

    private final Item item;

    public Equip(Map<String, String> fields) {
        super(fields);
        this.item = new Item(fields);
    }

    public int slot() {
        return (int) num("slot");
    }

    /** Null when the slot is being cleared. */
    @Nullable
    public Item item() {
        return off() ? null : item;
    }

    public boolean off() {
        return num("off") == 1;
    }

    public boolean player() {
        return num("player") == 1;
    }
}
