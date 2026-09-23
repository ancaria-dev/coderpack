package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.event.CombatArt;
import dev.ancaria.coderpack.api.event.Console;
import dev.ancaria.coderpack.api.event.Fold;
import dev.ancaria.coderpack.api.event.Gold;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What the folded decision looks like on the wire. */
class VerdictTest {

    private static Gold gold() {
        return new Gold(Map.of("delta", "100", "current", "50", "dir", "gain"));
    }

    private static Gold folded(Gold event, dev.ancaria.coderpack.api.event.EventMutation m) {
        Fold.apply(event, m);
        return event;
    }

    @Test
    void untouchedEventKeepsTheGameValue() {
        assertEquals("END 7 ok=1", Verdict.of(7, gold()).encode());
    }

    @Test
    void aNoneIsStillTheGameValue() {
        Gold event = folded(gold(), Gold.Mutation.none());
        assertEquals("END 7 ok=1", Verdict.of(7, event).encode());
    }

    @Test
    void changesGoOutAsSetFields() {
        Gold event = folded(gold(), Gold.Mutation.change(200));
        assertEquals("END 7 set.delta=200", Verdict.of(7, event).encode());
    }

    @Test
    void vetoWinsOverAChange() {
        Gold event = folded(gold(), Gold.Mutation.change(200));
        folded(event, Gold.Mutation.veto());
        assertEquals("END 7 cancel=1", Verdict.of(7, event).encode());
    }

    @Test
    void resetPutsTheGameValueBack() {
        Gold event = folded(gold(), Gold.Mutation.change(200));
        folded(event, Gold.Mutation.reset());
        assertEquals("END 7 ok=1", Verdict.of(7, event).encode());
    }

    @Test
    void aChangeBackToTheArrivedValueSaysNothing() {
        Gold event = folded(gold(), Gold.Mutation.change(100));
        assertEquals("END 7 ok=1", Verdict.of(7, event).encode());
    }

    @Test
    void aCombatArtAnswersWithTheLevelToStore() {
        CombatArt art = new CombatArt(Map.of("index", "3", "id", "67", "aspect", "0",
                "prev", "13", "next", "14", "step", "1"));
        Fold.apply(art, CombatArt.Mutation.change(art.getValue() + 1));
        assertEquals("END 9 set.next=15", Verdict.of(9, art).encode());
    }

    @Test
    void aClaimedConsoleLineIsACancel() {
        Console line = new Console(Map.of("text", "/heal"));
        assertEquals("END 4 ok=1", Verdict.of(4, line).encode());
        Fold.apply(line, Console.Mutation.veto());
        assertEquals("END 4 cancel=1", Verdict.of(4, line).encode());
    }
}
