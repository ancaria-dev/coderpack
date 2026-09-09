package dev.ancaria.coderpack.zygote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corpus here is the specification, and it is deliberately the same set of
 * cases the other two implementations check: {@code pin} in the launcher and
 * {@code Ranges} in the build repository's linter. There is no shared source any
 * of the three could read, so a case added in one belongs in all of them.
 */
class RangesTest {

    private static void order(String left, String right, int want) {
        assertEquals(want, Integer.signum(Ranges.version(left).compareTo(Ranges.version(right))),
                left + " vs " + right);
        assertEquals(-want, Integer.signum(Ranges.version(right).compareTo(Ranges.version(left))),
                right + " vs " + left);
    }

    @Test
    void versionsCompare() {
        order("1", "1", 0);
        order("1", "1.0.0", 0);
        order("1.0", "1.0.0.0", 0);
        order("1.2.3", "1.2.4", -1);
        order("1.10", "1.9", 1);
        order("2", "10", -1);
        order("0.1.20", "0.1.9", 1);
        order("25.0.4.1", "25.0.4", 1);
        // A qualifier is a release that has not happened yet.
        order("1.0.0-rc1", "1.0.0", -1);
        order("1.0.0-rc1", "1.0.0-rc2", -1);
        order("1.0.0+1", "1.0.0", -1);
    }

    @Test
    void unknownVersionsStayUnknown() {
        for (String text : new String[] {"", "  ", "x", "1.x", "1..2", "-1", ".", "1.-2", "v1"}) {
            assertFalse(Ranges.version(text).known(), text);
        }
        for (String text : new String[] {"1", "0.1.20", "1.0.0-rc1", "25.0.4.1+1", "\"1\"", " 2.0 "}) {
            assertTrue(Ranges.version(text).known(), text);
        }
    }

    private static void allows(String range, String version, boolean want) {
        assertEquals(want, Ranges.range(range).has(Ranges.version(version)),
                range + " / " + version);
    }

    @Test
    void rangesDecide() {
        // The usual one: a whole major.
        allows("[1,2)", "1", true);
        allows("[1,2)", "1.4.0", true);
        allows("[1,2)", "2", false);
        allows("[1,2)", "0.9", false);
        // No upper end.
        allows("[0.1.20,)", "0.1.20", true);
        allows("[0.1.20,)", "0.2.0", true);
        allows("[0.1.20,)", "0.1.19", false);
        // No lower end.
        allows("(,1.5]", "1.5", true);
        allows("(,1.5]", "1.5.1", false);
        allows("(,1.5)", "1.5", false);
        // Exactly one, both ways of writing it.
        allows("[1.2]", "1.2", true);
        allows("[1.2]", "1.2.0", true);
        allows("[1.2]", "1.2.1", false);
        allows("1", "1", true);
        allows("1", "1.0", true);
        allows("1", "2", false);
        // A union, and the hole in the middle of it.
        allows("[1,2),[3,4)", "1.5", true);
        allows("[1,2),[3,4)", "3.0", true);
        allows("[1,2),[3,4)", "2.5", false);
        allows("[1,2), [3,4)", "3.9", true);
        // Nothing said allows everything. An unreadable version allows nothing.
        allows("", "1", true);
        allows("[1,2)", "", false);
        allows("[1,2)", "x", false);
    }

    @Test
    void badRangesThrowRatherThanMatchingNothing() {
        for (String text : new String[] {
                "[1,2",        // never closed
                "1,2",         // a comma outside brackets
                "[2,1)",       // backwards
                "(,)",         // everything, written at length
                "[1,2)[3,4)",  // no comma between them
                "[1,2),",      // a comma with nothing after it
                "[x,2)",       // not a version
                "(1.2)",       // one version needs square brackets
                "[]",          // nothing at all
                "[1,2}",       // not a bracket this notation knows
        }) {
            assertThrows(IllegalArgumentException.class, () -> Ranges.range(text), text);
        }
    }

    /**
     * An empty range is not the same as a range that matched, and the two have
     * to stay distinguishable: one of them is a line somebody left out.
     */
    @Test
    void anyIsNotTheSameAsMatching() {
        Ranges.Range empty = Ranges.range("");
        assertTrue(empty.any());
        assertTrue(empty.has(Ranges.version("1")));
        assertTrue(empty.has(Ranges.version("nonsense")));
        assertFalse(Ranges.range("[1,2)").any());
    }

    /** Every message built out of one of these quotes what somebody wrote. */
    @Test
    void textIsKept() {
        assertEquals("[1,2)", Ranges.range(" [1,2) ").toString());
        assertEquals("1.0.0-rc1", Ranges.version(" 1.0.0-rc1 ").toString());
    }
}
