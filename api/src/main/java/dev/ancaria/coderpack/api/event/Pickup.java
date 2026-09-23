package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.Item;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Something is about to be picked up off the ground.
 *
 * <p>This decision carries two powers, and that is the game's shape rather
 * than an oversight. {@link Mutation#replace(int)} chooses <em>which</em>
 * object is picked up, and affects this pickup only.
 * {@link Mutation#retype(int)} and {@link Mutation#reshape(Item)} edit the
 * object that ends up being picked up, and outlive the event. They compose in
 * that order: the reference is swapped first, then the object it names is
 * edited.
 *
 * <p>Editing an object does belong to {@link dev.ancaria.coderpack.api.Game}
 * in principle, and it is there too, as
 * {@link dev.ancaria.coderpack.api.Game#retype(int, int)} and
 * {@link dev.ancaria.coderpack.api.Game#reshape(int, Item)}. What keeps it
 * here as well is timing rather than taste: a command is a round trip through
 * the host while the game thread is stopped waiting for this verdict, and the
 * edit has to land before the game picks the item up. In the verdict is the
 * only place it arrives in time.
 *
 * <p>Both vetoing and replacing come from the game's own code rather than from
 * a trick: the pickup function looks the item up by reference and, when the
 * lookup fails, jumps to its epilogue having done nothing. A veto hands it a
 * reference that resolves to nothing, and a replacement hands it another.
 *
 * <p>Only the hero's pickups are asked about. A creature picking something up
 * arrives as an ordinary event with {@link #isPlayer()} false, and a veto on it
 * does nothing.
 */
public final class Pickup extends Decision implements Decides<Pickup.Mutation> {

    private final Item item;

    // Folded between listeners, by Fold and by nothing else.
    private int ref;
    private Integer typeId;
    private Item template;

    public Pickup(Map<String, String> fields) {
        super(fields);
        this.item = new Item(fields);
        this.ref = getInitial();
    }

    @Nonnull
    public Item getItem() {
        return item;
    }

    /** True when the hero is picking it up rather than a creature. */
    public boolean isPlayer() {
        return getNum("player") == 1;
    }

    /** The object the game means to pick up. */
    public int getInitial() {
        return (int) getNum("ref");
    }

    /** The object it will pick up, with every earlier listener folded in. */
    public int getRef() {
        return ref;
    }

    /**
     * True when an earlier listener asked to edit the object itself.
     *
     * <p>A listener that would rather not fight over an item somebody else is
     * already changing asks this first. There is no such question for
     * {@link #getRef()}, which answers it by differing from {@link #getInitial()}.
     */
    public boolean isEdited() {
        return typeId != null || template != null;
    }

    @Override
    void reset() {
        this.ref = getInitial();
        this.typeId = null;
        this.template = null;
    }

    @Override
    void change(EventMutation mutation) {
        Mutation asked = (Mutation) mutation;
        if (asked.ref != null) {
            this.ref = asked.ref;
        }
        // The two edits are one decision about what the object becomes, so the
        // later one replaces the earlier rather than half of each surviving.
        if (asked.typeId != null) {
            this.typeId = asked.typeId;
            this.template = null;
        } else if (asked.template != null) {
            this.template = asked.template;
            this.typeId = null;
        }
    }

    @Override
    @Nonnull
    Map<String, String> verdict() {
        Map<String, String> fields = new LinkedHashMap<>();
        if (ref != getInitial()) {
            fields.put("ref", Integer.toString(ref));
        }
        if (typeId != null) {
            fields.put("type", Integer.toString(typeId));
        } else if (template != null) {
            fields.put("type", Integer.toString(template.getTypeId()));
            fields.put("price", Integer.toString(template.getPrice()));
            fields.put("level", Integer.toString(template.getLevel()));
            fields.put("min", Integer.toString(template.getMinLevel()));
            fields.put("mods", template.getPackedModifiers());
        }
        return fields;
    }

    /** What a {@code Pickup} listener returns. */
    public static final class Mutation extends EventMutation {

        private static final Mutation NONE = new Mutation(Kind.NONE, false, null, null, null);
        private static final Mutation RESET = new Mutation(Kind.RESET, false, null, null, null);
        private static final Mutation VETO = new Mutation(Kind.VETO, false, null, null, null);

        @Nullable
        private final Integer ref;
        @Nullable
        private final Integer typeId;
        @Nullable
        private final Item template;

        private Mutation(Kind kind, boolean last, @Nullable Integer ref,
                         @Nullable Integer typeId, @Nullable Item template) {
            super(kind, last);
            this.ref = ref;
            this.typeId = typeId;
            this.template = template;
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

        /** Pick nothing up. Only a later {@link #reset()} lifts it. */
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
            return new Mutation(Kind.CHANGE, false, ref, null, null);
        }

        /**
         * Turn the item into another type before it is picked up.
         *
         * <p>Careful: the type is the item's <em>label</em>. It changes what
         * the item is called and how it is drawn, and nothing else. A rune
         * retyped into another rune still upgrades the combat art it always
         * did. What an item does is its {@link Item#getModifiers()}, which is what
         * {@link #reshape(Item)} carries.
         */
        @Nonnull
        public static Mutation retype(int typeId) {
            return new Mutation(Kind.CHANGE, false, null, typeId, null);
        }

        /**
         * Make the item a copy of one that already exists.
         *
         * <p>The reliable way to turn an item into another. Rather than
         * assembling a plausible one out of a type id and hoping the rest
         * follows, this takes everything that makes an item what it is from a
         * real one seen in this session: its type, price, level, minimum level
         * and modifiers.
         */
        @Nonnull
        public static Mutation reshape(Item template) {
            return new Mutation(Kind.CHANGE, false, null, null, template);
        }

        /**
         * This choice of object, and then this edit to it:
         * {@code replace(other).and(reshape(template))}.
         */
        @Nonnull
        public Mutation and(Mutation edit) {
            return new Mutation(Kind.CHANGE, last || edit.last,
                                edit.ref != null ? edit.ref : ref,
                                edit.typeId, edit.template);
        }

        @Override
        @Nonnull
        public Mutation last() {
            return new Mutation(getKind(), true, ref, typeId, template);
        }
    }
}
