package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * A skill value is about to change. Slots are reported by index, never by name.
 * The skill set differs per class and per character, so any fixed
 * index-to-name table would be wrong for most saves.
 *
 * <p>{@link #getInitial()} is not trustworthy at the ends of the range. The hook
 * sits one instruction after the game's write, and the game's own clamp-to-0
 * and clamp-to-255 branches store the byte and jump straight to it, so the
 * previous value is reconstructed by subtraction and is wrong whenever a clamp
 * ran. {@code RESET} inherits that. See the {@code skillWrite} row in
 * {@code mappings}.
 */
public final class Skill extends Amount implements Decides<Skill.Mutation> {

    public Skill(Map<String, String> fields) {
        super(fields, "next");
    }

    public int getSlot() {
        return (int) getNum("slot");
    }

    public long getDelta() {
        return getNum("delta");
    }

    /** The value before this change, reconstructed rather than observed. */
    public long getPrevious() {
        return getNum("prev");
    }

    /** What a {@code Skill} listener returns. */
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


        /** The value to store instead. Stored as a byte, so clamped to 0..255. */
        @Nonnull
        public static Mutation change(long value) {
            return new Mutation(Kind.CHANGE, false, value);
        }

        @Override
        @Nonnull
        public Mutation last() {
            return new Mutation(getKind(), true, getValue());
        }
    }
}
