package dev.ancaria.coderpack.api.event;

import java.util.Map;

/** The player dropped to 15% of max HP or below, having been above it. */
public final class NearDeath extends Event {

    public NearDeath(Map<String, String> fields) {
        super(fields);
    }

    public long hp() {
        return num("next");
    }

    public long maxHp() {
        return num("max");
    }

    public int percent() {
        return (int) num("percent");
    }
}
