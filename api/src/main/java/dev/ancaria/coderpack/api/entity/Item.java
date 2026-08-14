package dev.ancaria.coderpack.api.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An item, as the fields that came with the event.
 *
 * <p>There is no display name here on purpose. Sacred composes item names from
 * affixes -- "Damaged" + base + "of Oblivion" -- so no single string exists to
 * read, and {@link #typeName()} is the stable identifier. It is internal and
 * English ({@code TYPE_OBJECT_RING_FIRE01}), which is exactly what mod logic
 * should match on; anything shown to a player has to be localized instead.
 */
public final class Item {

    private final Map<String, String> fields;

    // "601:3,802:12" has to be split and parsed to be useful, and a handler
    // that asks twice used to pay twice. On a pickup this is the one field
    // that is genuinely expensive.
    private Map<Integer, Integer> modifiers;

    public Item(Map<String, String> fields) {
        this.fields = fields;
    }

    /** The object-manager reference. Stable for the session, not across launches. */
    public int ref() {
        return number("ref");
    }

    public int typeId() {
        return number("type");
    }

    /** Null when the reference did not resolve; see {@link #known()}. */
    @Nullable
    public String typeName() {
        return fields.get("name");
    }

    public int level() {
        return number("level");
    }

    /** Level the character needs to use it. */
    public int minLevel() {
        return number("min");
    }

    public int attack() {
        return number("atk");
    }

    /** The tooltip's bracketed number: the game shows the two parts added up. */
    public int protection() {
        return number("prot");
    }

    public int percent() {
        return number("pct");
    }

    /**
     * Base value. The price on the tooltip is derived from it -- it moves with
     * charisma -- so this is the number that actually belongs to the item.
     * A small red potion is 400 and a large one 1200.
     */
    public int price() {
        return number("price");
    }

    /** The modifier list as it travels on the wire, for copying it verbatim. */
    @Nonnull
    public String packedModifiers() {
        String packed = fields.get("mods");
        return packed == null ? "" : packed;
    }

    /**
     * The modifier list: what the item actually DOES, as {@code id -> value}.
     *
     * <p>Not the same thing as {@link #typeId()}, and the difference matters.
     * The type is what an item is called and drawn as; the modifiers are its
     * effect. A rune retyped into another rune is renamed and still upgrades
     * the combat art its modifiers name -- which is why swapping the type alone
     * leaves a foreign rune foreign.
     *
     * <p>Ids seen so far: 601 Weapon Lore, 802 fire resist, 805 physical
     * resist, 809 attack %, 811 attack speed, 812 run speed, 817 endurance,
     * 819 mental regen, 841 life leech, 858 spell resist.
     */
    @Nonnull
    public Map<Integer, Integer> modifiers() {
        if (modifiers == null) {
            modifiers = unpack(fields.get("mods"));
        }
        return modifiers;
    }

    @Nonnull
    private static Map<Integer, Integer> unpack(@Nullable String packed) {
        if (packed == null || packed.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (String pair : packed.split(",")) {
            String[] half = pair.split(":");
            if (half.length != 2) {
                continue;
            }
            try {
                out.put(Integer.parseInt(half[0]), Integer.parseInt(half[1]));
            } catch (NumberFormatException skip) {
                // A malformed pair is one lost modifier, not a broken item.
            }
        }
        // Handed out more than once now, so it cannot be a map somebody edits.
        return Collections.unmodifiableMap(out);
    }

    /** False when the ref did not resolve -- the item was gone by then. */
    public boolean known() {
        return fields.get("name") != null;
    }

    @Override
    public String toString() {
        return known() ? typeName() + " lvl " + level() : "item#" + ref();
    }

    private int number(String key) {
        String raw = fields.get(key);
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
