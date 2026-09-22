package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The hero was brought back after dying, and the journal counted it. The
 * surest sign that a death was real: {@link Death} fires when HP reaches zero,
 * this fires when the game agrees the character died. It also resets the time
 * since the last death, which the survival bonus is computed from.
 */
public final class Resurrection extends Event {

    public Resurrection(Map<String, String> fields) {
        super(fields);
    }

    /** Resurrections so far, this one included. */
    public long count() {
        return num("count");
    }
}
