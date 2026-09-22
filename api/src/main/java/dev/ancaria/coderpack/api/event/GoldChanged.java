package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The hero's gold total moved, for any reason: loot, a purchase, a quest
 * reward, a mod. {@link Gold} is where a delta is decided. This is what the
 * total came to afterwards, read a moment later rather than at the write,
 * because every write site inside AddGold has crashed the game.
 */
public final class GoldChanged extends Event {

    public GoldChanged(Map<String, String> fields) {
        super(fields);
    }

    public long gold() {
        return num("next");
    }

    /** 0 on the first report of a session, which is the starting total. */
    public long delta() {
        return num("delta");
    }
}
