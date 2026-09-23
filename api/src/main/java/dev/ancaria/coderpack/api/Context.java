package dev.ancaria.coderpack.api;

import java.time.Duration;

import javax.annotation.Nonnull;

/**
 * Everything a mod is given, reached through {@link SacredMod#getContext()}.
 * Nothing else in Coderpack is meant to be reached directly.
 */
public interface Context {

    /**
     * Appends a line to {@code <Sacred Gold>/logs/mods.log}, the one file every
     * mod shares, as {@code [2026-09-23 14:05:31.042] [my-mod]: message}.
     *
     * <p>It never waits for the disk. The line is queued and a loader thread
     * writes it moments later, so this is safe on the dispatch thread with the
     * game stopped behind it and from any thread the mod started. When mods
     * log faster than the disk takes it, lines are dropped and the file says
     * how many; a failing disk loses lines rather than throwing here.
     */
    void log(String message);

    /**
     * Prints a line to the loader's console with the same
     * {@code [time] [mod-id]: } prefix {@link #log} writes. For what a person
     * watching the host should see now; {@link #log} is for what somebody
     * reads afterwards.
     */
    void print(String message);

    /** Acting on the game rather than reacting to it. */
    @Nonnull
    Game getGame();

    /** How long ago the loader started creating this mod. */
    @Nonnull
    Duration getUptime();

    /** This mod's {@code declaration.toml}, and the jar it came in. */
    @Nonnull
    SacredModDescriptor getDescriptor();

    /** The loaded mods and the listeners on the bus. */
    @Nonnull
    Registry getRegistry();
}
