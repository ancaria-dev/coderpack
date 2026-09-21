package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Gold is about to change. The decision is the delta, never the total. The game
 * keeps XOR-encoded mirrors of gold and re-checks them periodically, so a
 * rewritten total is detected and reset to 1. Changing the delta lets the game
 * compute the total itself and refresh its own mirrors.
 *
 * <p>This is the one event whose {@link #value()} is a delta rather than the
 * number about to be stored. The game forced the shape, and it is the shape the
 * rest of the API would have wanted anyway.
 */
public final class Gold extends Amount implements Decides<Gold.Mutation> {

    public Gold(Map<String, String> fields) {
        super(fields, "delta");
    }

    /** The total before this change. Not up for decision. */
    public long current() {
        return num("current");
    }

    public boolean spending() {
        return "spend".equals(text("dir"));
    }

    /** What a {@code Gold} listener returns. */
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


        /** The delta to apply instead. Negative for a purchase. */
        @Nonnull
        public static Mutation change(long delta) {
            return new Mutation(Kind.CHANGE, false, delta);
        }

        @Override
        @Nonnull
        public Mutation last() {
            return new Mutation(kind(), true, value());
        }
    }
}
