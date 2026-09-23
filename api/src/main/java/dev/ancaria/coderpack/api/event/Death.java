package dev.ancaria.coderpack.api.event;

import java.util.Map;

/** The player's HP reached zero. Not vetoable, because it already happened. */
public final class Death extends Event {

    public Death(Map<String, String> fields) {
        super(fields);
    }

    public long getHpBefore() {
        return getNum("prev");
    }

    public long getMaxHp() {
        return getNum("max");
    }

    /** The damage of the killing blow. */
    public long getBlow() {
        return getNum("blow");
    }
}
