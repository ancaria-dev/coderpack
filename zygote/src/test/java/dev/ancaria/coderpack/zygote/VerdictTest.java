package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.event.Gold;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What the merged decision looks like on the wire. */
class VerdictTest {

    private static Gold gold() {
        return new Gold(Map.of("delta", "100", "current", "50", "dir", "gain"));
    }

    @Test
    void untouchedEventKeepsTheGameValue() {
        assertEquals("END 7 ok=1", Verdict.of(7, gold()).encode());
    }

    @Test
    void rewritesGoOutAsSetFields() {
        Gold event = gold();
        event.delta(200);
        assertEquals("END 7 set.delta=200", Verdict.of(7, event).encode());
    }

    @Test
    void cancelWinsOverARewrite() {
        Gold event = gold();
        event.delta(200);
        event.cancel();
        assertEquals("END 7 cancel=1", Verdict.of(7, event).encode());
    }
}
