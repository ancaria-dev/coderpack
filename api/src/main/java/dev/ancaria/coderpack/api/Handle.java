package dev.ancaria.coderpack.api;

/**
 * What {@link Events#on} hands back: the listener it just registered, with a
 * way to take it off again.
 *
 * <p>A listener registered while the game runs usually has to come off again
 * while the game runs -- a mod that only watches during a boss fight, a debug
 * handler behind a toggle. The annotation path has no equivalent, because a
 * {@link Subscribe} method is registered once at load and stays.
 *
 * <p>Unregistering twice is allowed and does nothing the second time.
 * Unregistering from inside a listener is allowed too: the dispatch already
 * running finishes without calling it again.
 *
 * <p>It may be called from any thread -- see {@link Events} for what that
 * promises and when it takes effect.
 */
@FunctionalInterface
public interface Handle {

    void unregister();
}
