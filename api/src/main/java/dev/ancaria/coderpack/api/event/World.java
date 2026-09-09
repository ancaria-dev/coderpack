package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Session lifecycle. {@link Phase#LOADED} is the first moment the world
 * exists. {@link Phase#HERO_TERMINATED} is when the hero pointer stops being
 * valid, so anything holding player state should drop it there.
 */
@Delivery.Unstable("Not every path into a phase is hooked, so a phase"
        + " can be missed entirely.")
public final class World extends Event {

    public enum Phase { LOADING, LOADED, HERO_LOADED, HERO_TERMINATED, DETACHED }

    private final Phase phase;

    public World(Phase phase, Map<String, String> fields) {
        super(fields);
        this.phase = phase;
    }

    @Nonnull
    public Phase phase() {
        return phase;
    }
}
