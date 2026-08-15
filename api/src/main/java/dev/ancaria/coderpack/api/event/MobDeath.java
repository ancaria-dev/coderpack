package dev.ancaria.coderpack.api.event;

import java.util.Map;

/** A creature other than the player died. */
public final class MobDeath extends MobHit {

    public MobDeath(Map<String, String> fields) {
        super(fields);
    }
}
