package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * The hero's HP was written. Fires after {@link Damage} has been decided, with
 * what the game actually stored, and also at the three writes nobody can
 * decide: the lethal blow, the survive-with-1 effect and the overheal clamp.
 * Regeneration is not reported. It writes the same field about ten times a
 * second and is deliberately not hooked.
 */
public final class HealthChanged extends Event {

    public HealthChanged(Map<String, String> fields) {
        super(fields);
    }

    /** "damage", "heal", "lethal", "survive" or "clamp". */
    @Nullable
    public String kind() {
        return text("kind");
    }

    /** HP before the write. */
    public long previous() {
        return num("prev");
    }

    /** HP as stored. */
    public long hp() {
        return num("next");
    }

    public long maxHp() {
        return num("max");
    }

    /** The blow the game computed, before any mod changed the outcome. */
    public long damage() {
        return num("damage");
    }
}
