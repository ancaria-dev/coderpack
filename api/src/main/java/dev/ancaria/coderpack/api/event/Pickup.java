package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.Item;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Something is about to be picked up off the ground. Vetoable, and the item
 * itself may be swapped for another one.
 *
 * <p>Both powers come from the game's own code rather than from a trick: the
 * pickup function looks the item up by reference and, when the lookup fails,
 * jumps to its epilogue having done nothing. Cancelling hands it a reference
 * that resolves to nothing; {@link #replace(int)} hands it a different one.
 *
 * <p>Only the hero's pickups are asked about. A creature picking something up
 * arrives as an ordinary event with {@link #player()} false, and cancelling it
 * does nothing.
 */
public final class Pickup extends Veto {

    private final Item item;

    public Pickup(Map<String, String> fields) {
        super(fields);
        this.item = new Item(fields);
    }

    @Nonnull
    public Item item() {
        return item;
    }

    /** True when the hero is picking it up rather than a creature. */
    public boolean player() {
        return num("player") == 1;
    }

    /**
     * Pick up a different object instead. The reference has to be one that
     * already exists -- Coderpack cannot conjure an item, so this swaps between
     * things the world already holds. An unknown reference picks up nothing,
     * which is the same as cancelling.
     */
    public void replace(int ref) {
        rewrite("ref", ref);
    }

    /**
     * Turn the item into another type before it is picked up. Unlike
     * {@link #replace(int)}, which only redirects this one pickup, this edits
     * the object itself and the change outlives the event.
     *
     * <p>It changes what the item is called and how it is drawn, and nothing
     * else -- see {@link dev.ancaria.coderpack.api.Game#retype(int, int)}.
     */
    public void type(int typeId) {
        rewrite("type", typeId);
    }

    /** Base value; the tooltip price is derived from it. */
    public void price(int value) {
        rewrite("price", value);
    }

    /**
     * Make this item a copy of one that already exists.
     *
     * <p>The reliable way to turn an item into another: rather than assembling
     * a plausible one out of a type id and hoping the rest follows, take
     * everything that makes an item what it is from a real one that was seen in
     * this session. That is the difference between a rune that is renamed and a
     * rune that works -- what a rune upgrades is in its modifiers, not its
     * name.
     */
    public void copy(Item template) {
        rewrite("type", template.typeId());
        rewrite("price", template.price());
        rewrite("level", template.level());
        rewrite("min", template.minLevel());
        rewrite("mods", template.packedModifiers());
    }
}
