package dev.ancaria.coderpack.api;

import dev.ancaria.coderpack.api.internal.ModBinding;

import javax.annotation.Nonnull;

/**
 * A mod. Coderpack instantiates the class named by {@code declaration.toml}
 * through its public no-argument constructor, then calls {@link #onLoad()}
 * once.
 *
 * <pre>{@code
 * public final class ExampleMod extends SacredMod {
 *
 *     @Override
 *     public void onLoad() {
 *         getContext().getRegistry().getEventRegistry().register(new Listeners());
 *         getContext().log("ready");
 *     }
 * }
 * }</pre>
 *
 * <p>The {@link Context} is there from the first line of the subclass: the
 * loader hands it over while it constructs the instance, so field initialisers
 * and the constructor can already use {@link #getContext()}. An instance
 * created any other way, with {@code new} in a test for instance, has none.
 */
public abstract class SacredMod {

    private final Context context;

    /**
     * Picks up the context the loader is creating this instance for, or none
     * when the loader is not the caller.
     */
    protected SacredMod() {
        this.context = ModBinding.pending(this);
    }

    /**
     * Everything this mod is given.
     *
     * @throws IllegalStateException when this instance was not created by the loader
     */
    @Nonnull
    public final Context getContext() {
        if (context == null) {
            throw new IllegalStateException(getClass().getName()
                    + " was not created by the loader, so it has no context. Only the"
                    + " instance Coderpack creates from declaration.toml gets one.");
        }
        return context;
    }

    /**
     * Register listeners here. The world may not exist yet, so wait for a
     * {@code World} or {@code Hero} event before touching the player.
     */
    public void onLoad() {
    }

    /**
     * The last call this instance gets: the mod is being unregistered, or the
     * loader is shutting down. Its listeners are already off the bus. Close
     * what the mod opened, stop the threads it started, and return quickly.
     * An exception here is logged and goes no further.
     *
     * <p>At shutdown the game may already be gone, so a command sent from here
     * can go unanswered, and the loader waits only a few seconds for all mods
     * together before it exits anyway.
     */
    public void onUnload() {
    }
}
