package dev.ancaria.coderpack.api;

import java.nio.file.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Acting on the running game. Most calls here are a round-trip to the game
 * thread, so they are cheap but not free. Prefer the values an event already
 * carries over asking again.
 */
public interface Game {

    /**
     * The Sacred Gold install directory, the folder that holds mods/ and the
     * game executable. Anything a mod writes (logs, config) belongs under here,
     * not next to whatever the working directory happens to be.
     */
    @Nonnull
    Path getDirectory();

    /** Localized UI text for a dictionary key, or null if the key is unknown. */
    @Nullable
    String getUiString(String key);

    /**
     * The world around the hero: where the hero is, and what lives there.
     * Named {@code Realm} because {@code event.World} is the phase event.
     */
    @Nonnull
    Realm getWorld();

    /** The game's internal type names and ids, and changing an item's type. */
    @Nonnull
    TypeRegistry getTypeRegistry();

    /**
     * The game's own console. Named {@code GameConsole} because
     * {@code event.Console} is a line typed into it.
     */
    @Nonnull
    GameConsole getConsole();
}
