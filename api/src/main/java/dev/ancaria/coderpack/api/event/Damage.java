package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * The player is about to take damage (or be healed). Vetoable: the hook sits on
 * the instruction that commits HP, with the new value still in a register.
 */
public final class Damage extends Veto {

    public Damage(Map<String, String> fields) {
        super(fields);
    }

    /** "damage", "heal" or "clamp". */
    @Nullable
    public String kind() {
        return text("kind");
    }

    public long damage() {
        return num("damage");
    }

    public long hp() {
        return num("prev");
    }

    public long next() {
        return num("next");
    }

    public long maxHp() {
        return num("max");
    }

    /** Rewrite the HP the game is about to store. Clamped to [0, maxHp]. */
    public void next(long value) {
        rewrite("next", value);
    }
}
