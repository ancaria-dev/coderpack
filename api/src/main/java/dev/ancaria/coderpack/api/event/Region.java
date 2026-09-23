package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The hero crossed into or out of a region.
 *
 * <p>A region here is the game's own map cell, not a named area on the map
 * screen. One area spans many of them, and walking through it changes the id
 * every few steps. No key from an id to a localized name is known, so there is
 * none to offer. Match on ids a mod has seen, or use {@link Sector}.
 */
public final class Region extends Event {

    private final boolean entered;

    public Region(boolean entered, Map<String, String> fields) {
        super(fields);
        this.entered = entered;
    }

    public int getId() {
        return (int) getNum("id");
    }

    /** True on the way in, false on the way out. */
    public boolean isEntered() {
        return entered;
    }

    /** On entry, the region the hero came from, or 0 when unknown. 0 on exit. */
    public int getFrom() {
        return (int) getNum("from");
    }
}
