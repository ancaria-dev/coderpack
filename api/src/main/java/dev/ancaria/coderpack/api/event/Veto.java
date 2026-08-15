package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.Priority;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * An event fired before the game commits the value, where a listener may
 * suppress it or rewrite it.
 *
 * <p>Which events are vetoable is decided by the game's code, not by taste: it
 * works only where the hook sits before the write and Coderpack owns the register (or
 * can rewrite the field before anything else observes it). The game thread is
 * stopped while listeners run, so keep them short -- the host cuts the veto off
 * after its deadline and lets the original value through.
 *
 * <p>A cancel does not stop the dispatch. Later listeners still see the event,
 * and a listener that has nothing to say about a cancelled one asks to be
 * skipped with {@code @Subscribe(ignoreCancelled = true)}. Listeners at
 * {@link Priority#MONITOR} are watching rather than deciding, so {@link
 * #cancel()} and every rewrite they make is dropped.
 */
public abstract class Veto extends Event {

    private final Map<String, String> rewrites = new LinkedHashMap<>();
    private boolean canceled;

    // Set by the loader around a MONITOR listener; see Guard, which is the only
    // thing that touches either of these.
    boolean watching;
    boolean refused;

    protected Veto(Map<String, String> fields) {
        super(fields);
    }

    /** Suppress the write entirely. Ignored from a MONITOR listener. */
    public void cancel() {
        if (watching) {
            refused = true;
            return;
        }
        canceled = true;
    }

    public boolean canceled() {
        return canceled;
    }

    protected void rewrite(String key, long value) {
        rewrite(key, Long.toString(value));
    }

    /** For the few fields that are not numbers, like a packed list. */
    protected void rewrite(String key, String value) {
        if (watching) {
            refused = true;
            return;
        }
        rewrites.put(key, value);
    }

    /** Read by Coderpack when it answers the host. */
    @Nonnull
    public Map<String, String> rewrites() {
        return rewrites;
    }
}
