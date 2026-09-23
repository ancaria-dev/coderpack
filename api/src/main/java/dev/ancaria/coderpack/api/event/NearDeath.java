package dev.ancaria.coderpack.api.event;

import java.util.Map;

/** The player dropped to 15% of max HP or below, having been above it. */
public final class NearDeath extends Event {

    public NearDeath(Map<String, String> fields) {
        super(fields);
    }

    public long getHp() {
        return getNum("next");
    }

    public long getMaxHp() {
        return getNum("max");
    }

    public int getPercent() {
        return (int) getNum("percent");
    }
}
