package dev.ancaria.coderpack.api.entity;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A creature as it was when the loader looked: a monster, an animal, an NPC or
 * the hero. A snapshot, so its numbers do not follow the creature around. Ask
 * again through {@code Realm} for fresh ones.
 *
 * <p>{@link #getRef()} is what every action on a creature takes. It is stable for
 * the session and means nothing across launches.
 */
public final class Creature {

    private final Map<String, String> fields;

    public Creature(Map<String, String> fields) {
        this.fields = fields;
    }

    public int getRef() {
        return number("ref");
    }

    public int getTypeId() {
        return number("type");
    }

    /** Internal name, e.g. TYPE_NPC_GHUL01. Stable and English, so match on it. */
    @Nullable
    public String getTypeName() {
        return fields.get("name");
    }

    public int getLevel() {
        return number("level");
    }

    public long getHp() {
        return number("hp");
    }

    public long getMaxHp() {
        return number("maxHp");
    }

    /** World coordinates, like {@link Player#getX()}. */
    public int getX() {
        return number("x");
    }

    public int getY() {
        return number("y");
    }

    /** True for the hero. */
    public boolean isPlayer() {
        return number("player") == 1;
    }

    public boolean isAlive() {
        return getHp() > 0;
    }

    @Override
    @Nonnull
    public String toString() {
        return (getTypeName() == null ? "creature" : getTypeName()) + "#" + getRef();
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
