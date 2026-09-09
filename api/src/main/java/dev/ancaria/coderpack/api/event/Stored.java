package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.Item;

import java.util.Map;

import javax.annotation.Nonnull;

/** An item went into a creature's inventory. Read-only. */
@Delivery.Unstable("Only one store path is hooked, so an item can reach"
        + " a bag without this firing.")
public final class Stored extends Event {

    private final Item item;

    public Stored(Map<String, String> fields) {
        super(fields);
        this.item = new Item(fields);
    }

    @Nonnull
    public Item item() {
        return item;
    }

    public boolean player() {
        return num("player") == 1;
    }
}
