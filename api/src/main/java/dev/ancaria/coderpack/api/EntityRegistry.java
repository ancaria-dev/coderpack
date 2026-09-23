package dev.ancaria.coderpack.api;

import dev.ancaria.coderpack.api.entity.Creature;
import dev.ancaria.coderpack.api.entity.Player;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The hero and the creatures around it. Reached through
 * {@link Realm#getEntityRegistry()}.
 *
 * <p>The player is cached from events and free to read. Everything else is
 * one round-trip to the game and comes back as a snapshot: a creature list
 * does not follow the creatures around, so ask again rather than keeping one.
 */
public interface EntityRegistry {

    /** The hero, or null until a world is loaded. */
    @Nullable
    Player getPlayer();

    /**
     * Every creature the game currently holds: monsters, animals, NPCs and the
     * hero. Only what is streamed in, which is the sectors around the hero, not
     * the whole map. Empty on the main menu. Unmodifiable.
     */
    @Nonnull
    List<Creature> getCreatures();

    /** Only the creatures within {@code radius} world units of a point. Unmodifiable. */
    @Nonnull
    List<Creature> getCreaturesNear(int x, int y, int radius);

    /** A fresh snapshot of one creature, or null when the ref is not one. */
    @Nullable
    Creature getCreature(int ref);
}
