package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.Creature;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * A creature entered the world: a monster spawned, an NPC streamed in with its
 * sector. Items, effects and scenery go through the same game function and are
 * filtered out before they reach the wire.
 *
 * <p>Busy in a crowded fight, tens a second at worst. Nothing fires while a
 * world loads, because the hook is off for that burst on purpose, so the
 * creatures already there when {@link World.Phase#LOADED} arrives were never
 * announced. Ask {@code Realm} for them.
 *
 * <p>HP and level are read the moment the object exists, and a freshly built
 * creature can still show 0 for either.
 */
public final class Spawn extends Event {

    private Creature creature;

    public Spawn(Map<String, String> fields) {
        super(fields);
    }

    @Nonnull
    public Creature getCreature() {
        if (creature == null) {
            creature = new Creature(getFields());
        }
        return creature;
    }
}
