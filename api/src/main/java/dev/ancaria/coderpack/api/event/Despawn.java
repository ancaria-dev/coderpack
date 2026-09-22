package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.Creature;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * A creature left the world: a corpse was cleared, or its sector streamed out.
 * Not a death. {@link MobDeath} is the death, and this can come much later or
 * never. The creature is described as it was just before it went, and its ref
 * means nothing afterwards.
 */
public final class Despawn extends Event {

    private Creature creature;

    public Despawn(Map<String, String> fields) {
        super(fields);
    }

    @Nonnull
    public Creature creature() {
        if (creature == null) {
            creature = new Creature(fields());
        }
        return creature;
    }
}
