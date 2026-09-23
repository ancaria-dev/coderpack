package dev.ancaria.coderpack.api;

import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The mods the loader is running. Every method may be called from any thread.
 *
 * <p>Each mod has its own class loader, so two mods may carry different
 * versions of one library. A mod reaching into another mod's classes gets
 * that mod's copies, loaded by {@link #getClassLoader()} of that mod's
 * context, not its own.
 */
public interface ModRegistry {

    /** Every loaded mod, in load order. An unmodifiable snapshot. */
    @Nonnull
    List<SacredMod> getMods();

    /** The loaded mod with this id, or null when there is none. */
    @Nullable
    SacredMod getMod(String id);

    /** The mod this context belongs to. */
    @Nonnull
    SacredMod getCurrentMod();

    /**
     * Loads one more mod jar while the game runs: reads its descriptor,
     * checks it the way a mod in {@code mods/} is checked, creates its entry
     * point and calls {@link SacredMod#onLoad()}. The jar can be anywhere.
     * The launcher's enabled list does not apply: asking is enabling.
     *
     * @return the new mod's instance
     * @throws ModLoadException when the jar is unreadable, its descriptor
     *         incomplete or incompatible, a mod with its id is already
     *         loaded, or its entry point fails to start. A mod whose
     *         {@code onLoad} threw is not left half loaded.
     */
    @Nonnull
    SacredMod register(Path jar);

    /**
     * Takes a mod out: every listener it registered comes off the bus, its
     * {@link SacredMod#onUnload()} is called, and its class loader is closed.
     * An exception from {@code onUnload} is logged and goes no further.
     *
     * <p>A mod may unregister itself, from a listener or from its own thread.
     * The code already running keeps running; nothing of the mod's is called
     * again.
     *
     * @return false when no mod with that id was loaded
     */
    boolean unregister(String modId);

    /** The class loader of the mod this context belongs to. */
    @Nonnull
    ClassLoader getClassLoader();
}
