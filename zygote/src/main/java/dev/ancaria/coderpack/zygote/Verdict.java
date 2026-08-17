package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.event.Veto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the listeners did, as the one line the game is waiting for.
 *
 * <p>Three rules, in this order. A cancel wins over every rewrite, because a
 * write that never happens has no value to set. Otherwise the rewrites go out
 * as {@code set.<field>}, one per field, whoever wrote them last -- that is
 * priority order doing its job, not a race. With neither, the game keeps its
 * own value.
 *
 * <p>Nothing a MONITOR listener did is in here: the bus watched it and dropped
 * the write before this ever saw the event.
 */
final class Verdict {

    private Verdict() {
    }

    /** Nothing was up for decision: the event was not vetoable. */
    static Frame ok(long seq) {
        return new Frame("END", seq, "", Map.of("ok", "1"));
    }

    static Frame of(long seq, Veto veto) {
        if (veto.canceled()) {
            return new Frame("END", seq, "", Map.of("cancel", "1"));
        }
        if (veto.rewrites().isEmpty()) {
            return ok(seq);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        veto.rewrites().forEach((key, value) -> fields.put("set." + key, value));
        return new Frame("END", seq, "", fields);
    }
}
