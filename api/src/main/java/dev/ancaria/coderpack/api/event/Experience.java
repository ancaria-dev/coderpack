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

        public static final Mutation NONE = new Mutation(Kind.NONE, false, 0);
        public static final Mutation RESET = new Mutation(Kind.RESET, false, 0);
        public static final Mutation VETO = new Mutation(Kind.VETO, false, 0);

        private Mutation(Kind kind, boolean last, long value) {
            super(kind, last, value);
        }

        /** The new total to store instead. */
        @Nonnull
        public static Mutation of(long total) {
            return new Mutation(Kind.CHANGE, false, total);
        }

        @Override
        @Nonnull
        public Mutation asLast() {
            return new Mutation(kind(), true, value());
        }
    }
}
