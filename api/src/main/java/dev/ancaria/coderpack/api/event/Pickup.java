package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.Item;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Something is about to be picked up off the ground. The decision is which
 * object, which is a choice rather than a quantity, so this event carries no
 * {@code value()}: the last listener to name an object wins, and that is the
 * right answer for an identity.
 *
 * <p>Both powers come from the game's own code rather than from a trick: the
 * pickup function looks the item up by reference and, when the lookup fails,
 * jumps to its epilogue having done nothing. A veto hands it a reference that
 * resolves to nothing, and {@link Mutation#replace(int)} hands it another.
 *
 * <p>Only the hero's pickups are asked about. A creature picking something up
 * arrives as an ordinary event with {@link #player()} false, and a veto on it
 * does nothing.
 *
 * <p>Editing the item itself is no longer part of this decision. Changing a
 * type, a price or a set of modifiers edits an object in the world and outlives
 * the event, so it belongs on
 * {@link dev.ancaria.coderpack.api.Game#retype(int, int)} rather than in a
 * verdict the game is waiting on.
 */
public final class Pickup extends Decision implements Decides<Pickup.Mutation> {

    private final Item item;
    private int ref;

    public Pickup(Map<String, String> fields) {
        super(fields);
        this.item = new Item(fields);
        this.ref = initial();
    }

    @Nonnull
    public Item item() {
        return item;
    }

    /** True when the hero is picking it up rather than a creature. */
    public boolean player() {
        return num("player") == 1;
    }

    /** The object the game means to pick up. */
    public int initial() {
        return (int) num("ref");
    }

    /** The object it will pick up, with every earlier listener folded in. */
    public int ref() {
        return ref;
    }

    @Override
    void reset() {
        this.ref = initial();
    }

    @Override
    void change(EventMutation mutation) {
        this.ref = ((Mutation) mutation).ref;
    }

    @Override
    @Nonnull
    Map<String, String> verdict() {
        return ref == initial() ? Map.of() : Map.of("ref", Integer.toString(ref));
    }

    /** What a {@code Pickup} listener returns. */
    public static final class Mutation extends EventMutation {

        private static final Mutation NONE = new Mutation(Kind.NONE, false, 0);
        private static final Mutation RESET = new Mutation(Kind.RESET, false, 0);
        private static final Mutation VETO = new Mutation(Kind.VETO, false, 0);
        private final int ref;

        private Mutation(Kind kind, boolean last, int ref) {
            super(kind, last);
            this.ref = ref;
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


        /**
         * Pick up a different object instead. The reference has to be one that
         * already exists. Coderpack cannot conjure an item, so this swaps
         * between things the world already holds. An unknown reference picks up
         * nothing, which is the same as a veto.
         */
        @Nonnull
        public static Mutation replace(int ref) {
            return new Mutation(Kind.CHANGE, false, ref);
        }

        @Override
        @Nonnull
        public Mutation last() {
            return new Mutation(kind(), true, ref);
        }
    }
}
