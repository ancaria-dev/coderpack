package dev.ancaria.coderpack.api;

import dev.ancaria.coderpack.api.entity.Player;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Acting on the running game. Every call here is a round-trip to the game
 * thread, so it is cheap but not free -- prefer the values an event already
 * carries over asking again.
 */
public interface Game {

    /** Null until a world is loaded. */
    @Nullable
    Player player();

    /** Localized UI text for a dictionary key, or null if the key is unknown. */
    @Nullable
    String uiString(String key);

    /** Internal type name for an object or creature id, or null when the game
     * does not know the id. */
    @Nullable
    String typeName(int typeId);

    /**
     * The other direction: the id behind an internal name, or 0 when the game
     * does not know it.
     *
     * <p>Use this rather than writing an id into a mod. Ids are build-specific
     * numbers with no meaning of their own -- "TYPE_OBJECT_POTION_LARGE_RED"
     * says what it is and survives a different build; 5171 says nothing and
     * does not.
     */
    int typeId(String name);

    /**
     * Every type whose internal name starts with {@code prefix}, name to id.
     * One round-trip, so a mod can build its whole table at startup.
     */
    @Nonnull
    Map<String, Integer> types(String prefix);

    /**
     * Turns one item into another, in the world, permanently.
     *
     * <p>Careful: the type is the item's <em>label</em>, not its identity.
     * Confirmed in game -- this renames the item and redraws it, and changes
     * nothing else. A rune retyped into another rune still upgrades the combat
     * art it always did, and a potion keeps its old price. What an item
     * <em>does</em> is {@link dev.ancaria.coderpack.api.entity.Item#modifiers()}.
     *
     * @return false when the reference resolves to nothing
     */
    boolean retype(int ref, int typeId);
}
