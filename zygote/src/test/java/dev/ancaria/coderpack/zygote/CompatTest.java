package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Api;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which descriptors the loader will start and what it says about the rest. */
class CompatTest {

    /** The release the launcher would have stamped into the game folder. */
    private static final String HERE = "0.1.20";

    private static Meta meta(String lines) {
        return Meta.parse("""
                id = "demo"
                name = "Demo"
                entrypoint = "demo.DemoMod"
                """ + lines);
    }

    private static String refuse(String lines) {
        return Compat.refuse(meta(lines), HERE);
    }

    @Test
    void ownMajorLoads() {
        assertNull(refuse("api = \"" + Api.VERSION + "\""));
    }

    @Test
    void anotherMajorIsRefusedAndSaysBothNumbers() {
        String refusal = refuse("api = \"99\"");
        assertNotNull(refusal);
        assertTrue(refusal.contains("demo"), refusal);
        assertTrue(refusal.contains("API 99"), refusal);
        assertTrue(refusal.contains("API " + Api.VERSION), refusal);
    }

    /** No field means an unknown API, which is refused like any other mismatch. */
    @Test
    void missingFieldIsRefused() {
        String refusal = refuse("");
        assertNotNull(refusal);
        assertTrue(refusal.contains("no API version is declared"), refusal);
    }

    /** What the plugin writes today, and what a mod spanning two majors writes. */
    @Test
    void aRangeCoveringThisMajorLoads() {
        int next = Api.VERSION + 1;
        assertNull(refuse("api = \"[" + Api.VERSION + "," + next + ")\""));
        assertNull(refuse("api = \"[" + Api.VERSION + "," + (next + 1) + ")\""));
        assertNull(refuse("api = \"[" + Api.VERSION + ",)\""));
    }

    @Test
    void aRangeAboveThisMajorIsRefusedAndQuotesItself() {
        String wanted = "[" + (Api.VERSION + 1) + "," + (Api.VERSION + 2) + ")";
        String refusal = refuse("api = \"" + wanted + "\"");
        assertNotNull(refusal);
        assertTrue(refusal.contains(wanted), refusal);
    }

    @Test
    void anUnreadableRangeIsRefusedRatherThanIgnored() {
        String refusal = refuse("api = \"[1,2\"");
        assertNotNull(refusal);
        assertTrue(refusal.contains("version range is invalid"), refusal);
    }

    // --- the second range: the launcher's own release ---------------------

    @Test
    void aLoaderRangeThisReleaseSatisfiesLoads() {
        assertNull(refuse("api = \"[" + Api.VERSION + ",)\"\nloader = \"[0.1.20,)\""));
        assertNull(refuse("api = \"[" + Api.VERSION + ",)\"\nloader = \"[0.1.0,0.2.0)\""));
    }

    @Test
    void aLoaderRangeAboveThisReleaseIsRefused() {
        String refusal = refuse("api = \"[" + Api.VERSION + ",)\"\nloader = \"[0.2.0,)\"");
        assertNotNull(refusal);
        assertTrue(refusal.contains("[0.2.0,)"), refusal);
        assertTrue(refusal.contains(HERE), refusal);
    }

    /**
     * A host started by hand out of a checkout has no launcher/VERSION beside
     * it. Refusing every mod over that would take the loader away from the one
     * person who is definitely debugging it.
     */
    @Test
    void noVersionStampMeansTheLoaderRangeIsNotChecked() {
        Meta meta = meta("api = \"[" + Api.VERSION + ",)\"\nloader = \"[9.9.9,)\"");
        assertNull(Compat.refuse(meta, ""));
        // The API contract is not excused by it: that one decides whether the
        // code can run at all.
        assertNotNull(Compat.refuse(meta("api = \"99\""), ""));
    }
}
