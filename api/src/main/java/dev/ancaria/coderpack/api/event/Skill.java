package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * A skill value is about to change. Slots are reported by index, never by
 * name. The skill set differs per class and per character, so any fixed
 * index-to-name table would be wrong for most saves.
 */
public final class Skill extends Veto {

    public Skill(Map<String, String> fields) {
        super(fields);
    }

    public int slot() {
        return (int) num("slot");
    }

    public long delta() {
        return num("delta");
    }

    public long value() {
        return num("prev");
    }

    public long next() {
        return num("next");
    }

    /** Stored as a byte, so the value is clamped to 0..255. */
    public void next(long value) {
        rewrite("next", value);
    }
}
