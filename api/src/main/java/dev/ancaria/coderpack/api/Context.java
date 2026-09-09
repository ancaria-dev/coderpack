package dev.ancaria.coderpack.api;

import java.nio.file.Path;

import javax.annotation.Nonnull;

/** Everything a mod is given. Nothing else in Coderpack is meant to be reached directly. */
public interface Context {

    /** This mod's id, from its descriptor. */
    @Nonnull
    String id();

    /** Where listeners are registered. */
    @Nonnull
    Events events();

    /** Acting on the game rather than reacting to it. */
    @Nonnull
    Game game();

    /**
     * The Sacred Gold install directory, the folder that holds mods/ and the
     * game executable. Anything a mod writes (logs, config) belongs under here,
     * not next to whatever the working directory happens to be.
     */
    @Nonnull
    Path gameDir();

    /** Goes to the host console, prefixed with the mod id. */
    void log(String message);
}
