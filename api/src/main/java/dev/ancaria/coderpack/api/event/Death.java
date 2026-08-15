package dev.ancaria.coderpack.api.event;

import java.util.Map;

/** The player's HP reached zero. Not vetoable -- it already happened. */
public final class Death extends Event {

    public Death(Map<String, String> fields) {
        super(fields);
    }

    public long hpBefore() {
        return num("prev");
    }

    public long maxHp() {
        return num("max");
    }

    /** The damage of the killing blow. */
    public long blow() {
        return num("blow");
    }
}
