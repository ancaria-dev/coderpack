package dev.ancaria.coderpack.api;

/**
 * When a listener runs. Dispatch walks this list top to bottom, and listeners
 * that share a step run in the order their mod registered them.
 *
 * <p>Pick by what the listener is for rather than by how much it wants to
 * win. A listener that decides something is {@code FIRST} or {@code LAST}.
 * One that only wants to know what was decided is {@code MONITOR}.
 */
public enum Priority {

    /** Runs before everyone else. For a listener whose decision others react to. */
    FIRST,

    /** The default. Where a listener belongs unless it has a reason not to. */
    NORMAL,

    /** Runs after the deciding is done, but may still change the outcome. */
    LAST,

    /**
     * Watch only. A {@code MONITOR} listener sees the event with every other
     * listener's work already in it, and whatever it cancels or rewrites is
     * thrown away instead of reaching the game. The loader logs the attempt
     * once and carries on. This is where a tracer, a counter or a statistics
     * mod belongs.
     */
    MONITOR
}
