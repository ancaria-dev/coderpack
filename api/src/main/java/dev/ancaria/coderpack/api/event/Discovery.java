package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The hero discovered a new area, and "World Discovered" on the Statistics
 * page moved. Which area is not known: the game only counts them.
 */
public final class Discovery extends Event {

    public Discovery(Map<String, String> fields) {
        super(fields);
    }

    /** Areas discovered so far, this one included. */
    public long areas() {
        return num("areas");
    }
}
