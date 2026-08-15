package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * Gold is about to change. Vetoable, and the delta is what may be rewritten --
 * never the total: the game keeps XOR-encoded mirrors of gold and re-checks
 * them periodically, so a rewritten total is detected and reset to 1. Boosting
 * the delta lets the game compute the total itself and refresh its own mirrors.
 */
public final class Gold extends Veto {

    public Gold(Map<String, String> fields) {
        super(fields);
    }

    /** Negative for a purchase, positive for loot. */
    public long delta() {
        return num("delta");
    }

    public long current() {
        return num("current");
    }

    public boolean spending() {
        return "spend".equals(text("dir"));
    }

    public void delta(long value) {
        rewrite("delta", value);
    }
}
