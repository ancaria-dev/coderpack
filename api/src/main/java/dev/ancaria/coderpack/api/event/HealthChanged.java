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
    public String getKind() {
        return getText("kind");
    }

    /** HP before the write. */
    public long getPrevious() {
        return getNum("prev");
    }

    /** HP as stored. */
    public long getHp() {
        return getNum("next");
    }

    public long getMaxHp() {
        return getNum("max");
    }

    /** The blow the game computed, before any mod changed the outcome. */
    public long getDamage() {
        return getNum("damage");
    }
}
