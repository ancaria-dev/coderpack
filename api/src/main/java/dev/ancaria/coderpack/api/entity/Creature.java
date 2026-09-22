package dev.ancaria.coderpack.api.entity;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A creature as it was when the loader looked: a monster, an animal, an NPC or
 * the hero. A snapshot, so its numbers do not follow the creature around. Ask
 * again through {@code Realm} for fresh ones.
 *
 * <p>{@link #ref()} is what every action on a creature takes. It is stable for
 * the session and means nothing across launches.
 */
public final class Creature {

    private final Map<String, String> fields;

    public Creature(Map<String, String> fields) {
        this.fields = fields;
    }

    public int ref() {
        return number("ref");
    }

    public int typeId() {
        return number("type");
    }

    /** Internal name, e.g. TYPE_NPC_GHUL01. Stable and English, so match on it. */
    @Nullable
    public String typeName() {
        return fields.get("name");
    }

    public int level() {
        return number("level");
    }

    public long hp() {
        return number("hp");
    }

    public long maxHp() {
        return number("maxHp");
    }

    /** World coordinates, like {@link Player#x()}. */
    public int x() {
        return number("x");
    }

    public int y() {
        return number("y");
    }

    /** True for the hero. */
    public boolean player() {
        return number("player") == 1;
    }

    public boolean alive() {
        return hp() > 0;
    }

    @Override
    @Nonnull
    public String toString() {
        return (typeName() == null ? "creature" : typeName()) + "#" + ref();
    }

    private int number(String key) {
        String raw = fields.get(key);
        if (raw == null) {
            return 0;
        }
        try {
            return (int) Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
