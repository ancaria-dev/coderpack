package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * An event the game is waiting on. The game thread is stopped while listeners
 * run, so keep them short. The host cuts the wait off after its deadline and
 * lets the game's own value through.
 *
 * <p>Nothing here is writable by a mod. A listener says what it wants by
 * returning a mutation, and the loader folds that in before calling the next
 * listener. Which events are decidable is decided by the game's code, not by
 * taste: it works only where the hook sits before the write and Coderpack owns
 * the register, or can rewrite the field before anything else observes it.
 */
public abstract sealed class Decision extends Event permits Amount, Console, Pickup {

    // Written by the fold, through Fold, and by nothing else.
    private boolean vetoed;
    private boolean stopped;

    Decision(Map<String, String> fields) {
        super(fields);
    }

    /** True when an earlier listener vetoed and no later one reset it. */
    public final boolean vetoed() {
        return vetoed;
    }

    final void vetoed(boolean value) {
        this.vetoed = value;
    }

    final boolean stopped() {
        return stopped;
    }

    final void stopped(boolean value) {
        this.stopped = value;
    }

    /** Puts every folded value back to what the game sent. */
    abstract void reset();

    /** Folds one CHANGE in. Never called for the other three kinds. */
    abstract void change(EventMutation mutation);

    /** The fields the host is answered with, empty when nothing moved. */
    abstract Map<String, String> verdict();
}
