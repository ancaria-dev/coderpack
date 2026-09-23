package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * The hero bought from or sold to an NPC merchant. Reported as the gold changes
 * hands, so {@link Gold} for the same payment arrives right after, and that is
 * where the amount can be decided.
 */
public final class Trade extends Event {

    private final boolean bought;

    public Trade(boolean bought, Map<String, String> fields) {
        super(fields);
        this.bought = bought;
    }

    /** True for a purchase, false for a sale. */
    public boolean bought() {
        return bought;
    }

    /** A sale by shift-click from the inventory rather than onto the merchant. */
    public boolean quick() {
        return num("quick") == 1;
    }

    /** What the game charged or paid, before any {@link Gold} listener. */
    public long price() {
        return num("price");
    }

    public int ref() {
        return (int) num("ref");
    }

    public int typeId() {
        return (int) num("type");
    }

    @Nullable
    public String typeName() {
        return text("name");
    }
}
