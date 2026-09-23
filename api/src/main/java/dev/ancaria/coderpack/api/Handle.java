package dev.ancaria.coderpack.api;

import dev.ancaria.coderpack.api.event.Event;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One listener on the bus: what it listens to, whose it is, and a way to take
 * it off again. {@link EventRegistry#on}, {@link EventRegistry#decide} and
 * {@link EventRegistry#register(Object)} hand these out, and
 * {@link EventRegistry#getEvents()} lists them all.
 *
 * <p>A listener registered while the game runs usually has to come off again
 * while the game runs. A mod that only watches during a boss fight, say, or a
 * debug handler behind a toggle.
 *
 * <p>Unregistering twice is allowed and does nothing the second time.
 * Unregistering from inside a listener is allowed too: the dispatch already
 * running finishes without calling it again.
 *
 * <p>It may be called from any thread. See {@link EventRegistry} for what that
 * promises and when it takes effect.
 */
public interface Handle {

    /** Takes the listener off the bus. Idempotent, from any thread. */
    void unregister();

    /**
     * False once it was unregistered, its mod was, or the loader dropped it
     * after it kept throwing.
     */
    boolean isRegistered();

    /** The mod that registered it. */
    @Nonnull
    SacredMod getMod();

    /** The event type it was registered for. Subtypes reach it too. */
    @Nonnull
    Class<? extends Event> getEventType();

    @Nonnull
    Priority getPriority();

    /** True when it skips events an earlier listener vetoed. */
    boolean isIgnoreVetoed();

    /** The class holding the {@link Subscribe} method, or null for a lambda. */
    @Nullable
    Class<?> getListenerClass();

    /** The {@link Subscribe} method's name, or null for a lambda. */
    @Nullable
    String getMethodName();
}
