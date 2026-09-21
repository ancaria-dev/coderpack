package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

/**
 * The player is about to take damage (or be healed). The hook sits on the
 * instruction that commits HP, with the new value still in a register, so the
 * decision is that value. It is clamped to {@code [0, maxHp]} on the way to the
 * game.
 */
public final class Damage extends Amount implements Decides<Damage.Mutation> {

    public Damage(Map<String, String> fields) {
        super(fields, "next");
    }

    /** "damage", "heal" or "clamp". */
    @Nullable
    public String kind() {
        return text("kind");
    }

    public long damage() {
        return num("damage");
    }

    /** HP before the blow. */
    public long hp() {
        return num("prev");
    }

    public long maxHp() {
        return num("max");
    }

    /** What a {@code Damage} listener returns. */
    public static final class Mutation extends Amount.Change {

        public static final Mutation NONE = new Mutation(Kind.NONE, false, 0);
        public static final Mutation RESET = new Mutation(Kind.RESET, false, 0);
        public static final Mutation VETO = new Mutation(Kind.VETO, false, 0);

        private Mutation(Kind kind, boolean last, long value) {
            super(kind, last, value);
        }

        /** The HP to store instead. Clamped to {@code [0, maxHp]}. */
        @Nonnull
        public static Mutation of(long hp) {
            return new Mutation(Kind.CHANGE, false, hp);
        }

        @Override
        @Nonnull
        public Mutation asLast() {
            return new Mutation(kind(), true, value());
        }
    }
}
