package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * The loader's handle on a decision. A mod has no reason to call this.
 *
 * <p>It exists so a {@link Decision} can be immutable to a mod and still be
 * folded between listeners. The event carries no setter, so there is no write
 * for a mod to make and no rule for the loader to enforce afterwards: the only
 * way in is a returned mutation, and the only thing that applies one is here.
 *
 * <p>This replaces the old {@code Guard}. There is nothing left to guard: what
 * a listener may do is decided by its return type, not by a flag the loader
 * sets around the call.
 */
public final class Fold {

    private Fold() {
    }

    /**
     * Folds one listener's answer in.
     *
     * @return true when the chain should stop, so no further deciding listener
     *         sees this occurrence. Monitors still run.
     */
    public static boolean apply(Decision decision, @Nonnull EventMutation mutation) {
        switch (mutation.kind()) {
            case NONE -> {
            }
            case RESET -> {
                decision.reset();
                decision.vetoed(false);
            }
            case VETO -> decision.vetoed(true);
            case CHANGE -> decision.change(mutation);
        }
        if (mutation.last()) {
            decision.stopped(true);
        }
        return decision.stopped();
    }

    /** True once a mutation ended the chain. */
    public static boolean stopped(Decision decision) {
        return decision.stopped();
    }

    /**
     * What the host is answered with. A veto wins over every value, because a
     * write that never happens has no value to set.
     */
    @Nonnull
    public static Map<String, String> verdict(Decision decision) {
        return decision.vetoed() ? Map.of() : decision.verdict();
    }
}
