package dev.ancaria.coderpack.api;

import dev.ancaria.coderpack.api.entity.Creature;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The world around the hero: the creatures in it, and where the hero is in the
 * game's own grid. Reached through {@link Game#world()}.
 *
 * <p>Every call is one round-trip to the game, and what comes back is a
 * snapshot. A creature list does not follow the creatures around, so ask again
 * rather than keeping one. Creatures are addressed by {@link Creature#ref()},
 * which is stable for the session and means nothing across launches.
 */
public interface Realm {

    /**
     * Every creature the game currently holds: monsters, animals, NPCs and the
     * hero. Only what is streamed in, which is the sectors around the hero, not
     * the whole map. Empty on the main menu.
     */
    @Nonnull
    List<Creature> creatures();

    /** Only the creatures within {@code radius} world units of a point. */
    @Nonnull
    List<Creature> creaturesNear(int x, int y, int radius);

    /** A fresh snapshot of one creature, or null when the ref is not one. */
    @Nullable
    Creature creature(int ref);

    /**
     * Sets a creature's HP to 0, the same single call the game's own sudden-death
     * action makes.
     *
     * @return false when the ref is not a creature
     */
    boolean kill(int ref);

    /** Sets a creature's current HP through the game's own setter. */
    boolean hp(int ref, long value);

    /**
     * The map cell the hero last entered, as {@link dev.ancaria.coderpack.api.event.Region}
     * reports it, or 0 until the hero has crossed one since the loader attached.
     */
    int region();

    /** The sector the hero last entered, or -1 until one was crossed. */
    int sectorX();

    int sectorY();
}
