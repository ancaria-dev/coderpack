package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.event.Decision;
import dev.ancaria.coderpack.api.event.Fold;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the listeners decided, as the one line the game is waiting for.
 *
 * <p>Three rules, in this order. A veto wins over every value, because a write
 * that never happens has no value to set. Otherwise whatever the fold arrived
 * at goes out as {@code set.<field>}, one per field. With neither, the game
 * keeps its own value.
 *
 * <p>The fold is the whole difference from the old shape. This used to read a
 * map of rewrites the listeners had written into the event themselves, so the
 * last one to touch a field won without knowing anyone else had. Now the bus
 * has already folded every answer in order, and this only says what came out.
 *
 * <p>Nothing a MONITOR listener answered is in here: the bus dropped it before
 * this ever saw the event.
 */
final class Verdict {

    private Verdict() {
    }

    /** Nothing was up for decision: the event was not decidable. */
    static Frame ok(long seq) {
        return new Frame("END", seq, "", Map.of("ok", "1"));
    }

    static Frame of(long seq, Decision decision) {
        if (decision.vetoed()) {
            return new Frame("END", seq, "", Map.of("cancel", "1"));
        }
        Map<String, String> decided = Fold.verdict(decision);
        if (decided.isEmpty()) {
            return ok(seq);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        decided.forEach((key, value) -> fields.put("set." + key, value));
        return new Frame("END", seq, "", fields);
    }
}
