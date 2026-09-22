package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The hero's maximum HP was recomputed to a different number, after gear, a
 * level or an attribute changed. Noticed when the game commits derived stats,
 * so it arrives once per recalculation rather than once per cause.
 */
public final class MaxHealthChanged extends Event {

    public MaxHealthChanged(Map<String, String> fields) {
        super(fields);
    }

    /** 0 on the first report of a session: there was nothing to compare with. */
    public long previous() {
        return num("prev");
    }

    public long maxHp() {
        return num("next");
    }
}
