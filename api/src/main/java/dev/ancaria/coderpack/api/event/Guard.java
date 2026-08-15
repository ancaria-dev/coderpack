package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.Priority;

/**
 * The loader's handle on a vetoable event. A mod has no reason to call this.
 *
 * <p>It exists so the bus can put an event into watch-only mode around a
 * {@link Priority#MONITOR} listener and ask afterwards whether that listener
 * tried to write anyway. Enforcing the rule here rather than in the bus means a
 * {@code cancel()} from a monitor never happens at all, instead of happening
 * and being undone.
 */
public final class Guard {

    private Guard() {
    }

    /** Turns every write on this event into a no-op. */
    public static void watch(Veto veto) {
        veto.watching = true;
        veto.refused = false;
    }

    /** Ends watch-only mode. True when a write was refused while it lasted. */
    public static boolean release(Veto veto) {
        veto.watching = false;
        return veto.refused;
    }
}
