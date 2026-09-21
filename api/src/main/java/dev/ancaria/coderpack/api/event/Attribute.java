package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An attribute point was just spent. Decidable, but applied differently from
 * the others. There is no register to swap here, so Coderpack writes the field
 * after the game's own grant returns. The result is the same, because nothing
 * else has observed the value yet.
 */
public final class Attribute extends Amount implements Decides<Attribute.Mutation> {

    public Attribute(Map<String, String> fields) {
        super(fields, "next");
    }

    /** 0 Strength, 1 Endurance, 2 Dexterity, 3 PhysReg, 4 MentalReg, 5 Charisma. */
    public int index() {
        return (int) num("attr");
    }

    @Nullable
    public String name() {
        return text("name");
    }

    /** The value before the point was spent. */
    public long previous() {
        return num("prev");
    }

    /** What an {@code Attribute} listener returns. */
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


        /** The value to store instead. Clamped to 0..65535. */
        @Nonnull
        public static Mutation of(long value) {
            return new Mutation(Kind.CHANGE, false, value);
        }

        @Override
        @Nonnull
        public Mutation asLast() {
            return new Mutation(kind(), true, value());
        }
    }
}
