package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Experience is about to be granted. The decision is the new total, which the
 * game clamps to 2,586,931,599 on its own. Coderpack also caps at int32 so a
 * large multiplier cannot overflow on the way there.
 */
public final class Experience extends Amount implements Decides<Experience.Mutation> {

    public Experience(Map<String, String> fields) {
        super(fields, "next");
    }

    public long gain() {
        return num("gain");
    }

    /** The total before this grant. */
    public long exp() {
        return num("prev");
    }

    /** What an {@code Experience} listener returns. */
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


        /** The new total to store instead. */
        @Nonnull
        public static Mutation change(long total) {
            return new Mutation(Kind.CHANGE, false, total);
        }

        @Override
        @Nonnull
        public Mutation last() {
            return new Mutation(kind(), true, value());
        }
    }
}
