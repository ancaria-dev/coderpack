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
    public String getKind() {
        return getText("kind");
    }

    public long getDamage() {
        return getNum("damage");
    }

    /** HP before the blow. */
    public long getHp() {
        return getNum("prev");
    }

    public long getMaxHp() {
        return getNum("max");
    }

    /** What a {@code Damage} listener returns. */
    public static final class Mutation extends Amount.Change {

        private static final Mutation NONE = new Mutation(Kind.NONE, false, 0);
        private static final Mutation RESET = new Mutation(Kind.RESET, false, 0);
        private static final Mutation VETO = new Mutation(Kind.VETO, false, 0);

        private Mutation(Kind kind, boolean last, long value) {
            super(kind, last, value);
        }

        /** Add nothing. The fold stands as it is. */
        @Nonnull
        public static Mutation none() {
            return NONE;
        }

        /** Discard every earlier listener's work and put the game's own back. */
        @Nonnull
        public static Mutation reset() {
            return RESET;
        }

        /** Stop the write. Only a later {@link #reset()} lifts it. */
        @Nonnull
        public static Mutation veto() {
            return VETO;
        }


        /** The HP to store instead. Clamped to {@code [0, maxHp]}. */
        @Nonnull
        public static Mutation change(long hp) {
            return new Mutation(Kind.CHANGE, false, hp);
        }

        @Override
        @Nonnull
        public Mutation last() {
            return new Mutation(getKind(), true, getValue());
        }
    }
}
