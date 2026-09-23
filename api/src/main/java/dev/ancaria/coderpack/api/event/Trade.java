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
    public boolean isBought() {
        return bought;
    }

    /** A sale by shift-click from the inventory rather than onto the merchant. */
    public boolean isQuick() {
        return getNum("quick") == 1;
    }

    /** What the game charged or paid, before any {@link Gold} listener. */
    public long getPrice() {
        return getNum("price");
    }

    public int getRef() {
        return (int) getNum("ref");
    }

    public int getTypeId() {
        return (int) getNum("type");
    }

    @Nullable
    public String getTypeName() {
        return getText("name");
    }
}
