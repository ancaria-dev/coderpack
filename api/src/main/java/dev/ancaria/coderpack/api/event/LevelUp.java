package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The hero's level changed. Read-only on purpose: the level is one of the
 * anti-cheat mirrored fields and it drives the grant tables, so rewriting it
 * here would desync both.
 */
public final class LevelUp extends Event {

    public LevelUp(Map<String, String> fields) {
        super(fields);
    }

    public int getPrevious() {
        return (int) getNum("prev");
    }

    public int getLevel() {
        return (int) getNum("next");
    }
}
