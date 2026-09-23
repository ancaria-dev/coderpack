package dev.ancaria.coderpack.api;

import javax.annotation.Nonnull;

/**
 * What is registered with the loader: the mods, and the listeners they put on
 * the bus. Reached through {@link Context#getRegistry()}.
 */
public interface Registry {

    /** The loaded mods, and loading or unloading one while the game runs. */
    @Nonnull
    ModRegistry getModRegistry();

    /** Listener registration, for this mod, and a view of every mod's listeners. */
    @Nonnull
    EventRegistry getEventRegistry();
}
