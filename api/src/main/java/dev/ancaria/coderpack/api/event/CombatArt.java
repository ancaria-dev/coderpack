package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * A rune is being invested in a combat art. The decision is the art's new base
 * level, the number the game is about to store. The gear bonus the tooltip adds
 * on top is not part of it.
 *
 * <p>An art is identified by {@link #getArtId()} and {@link #getAspect()} together.
 * Ids are per class, so the same name has a different id on another class, and
 * the plain weapon moves several classes share by name are told apart by the
 * aspect. {@link #getIndex()} is the art's place in the character's own list,
 * which is not the order the combat-art screen shows.
 *
 * <p>A veto keeps the old level, and the rune is still spent: taking it out of
 * the inventory happens on a path this hook does not see.
 */
public final class CombatArt extends Amount implements Decides<CombatArt.Mutation> {

    public CombatArt(Map<String, String> fields) {
        super(fields, "next");
    }

    public int getIndex() {
        return (int) getNum("index");
    }

    public int getArtId() {
        return (int) getNum("id");
    }

    public int getAspect() {
        return (int) getNum("aspect");
    }

    /** The base level before this rune. */
    public long getPrevious() {
        return getNum("prev");
    }

    /** How much the rune adds on its own. */
    public long getStep() {
        return getNum("step");
    }

    /** What a {@code CombatArt} listener returns. */
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

        /** Keep the old level. Only a later {@link #reset()} lifts it. */
        @Nonnull
        public static Mutation veto() {
            return VETO;
        }

        /** The base level to store instead. Stored as a byte, so clamped to 0..255. */
        @Nonnull
        public static Mutation change(long level) {
            return new Mutation(Kind.CHANGE, false, level);
        }

        @Override
        @Nonnull
        public Mutation last() {
            return new Mutation(getKind(), true, getValue());
        }
    }
}
