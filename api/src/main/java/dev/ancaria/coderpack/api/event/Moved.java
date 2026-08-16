package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * An item was dragged from one inventory slot to another. The game's move
 * function carries grid indices and nothing else, so there is no item identity
 * on this path -- it is the signal that a bag was rearranged, not what by.
 */
public final class Moved extends Event {

    public Moved(Map<String, String> fields) {
        super(fields);
    }

    public int from() {
        return (int) num("from");
    }

    public int to() {
        return (int) num("to");
    }
}
