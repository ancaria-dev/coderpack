package dev.ancaria.coderpack.api;

import javax.annotation.Nonnull;

/**
 * The world around the hero: where the hero is in the game's own grid, and
 * acting on the creatures in it. Reached through {@link Game#getWorld()}.
 *
 * <p>Every call is one round-trip to the game. Creatures are addressed by
 * {@link dev.ancaria.coderpack.api.entity.Creature#getRef()}, which is stable
 * for the session and means nothing across launches.
 */
public interface Realm {

    /**
     * The map cell the hero last entered, as {@link dev.ancaria.coderpack.api.event.Region}
     * reports it, or 0 until the hero has crossed one since the loader attached.
     */
    int getRegion();

    /** The sector the hero last entered, or -1 until one was crossed. */
    int getSectorX();

    /** The sector the hero last entered, or -1 until one was crossed. */
    int getSectorY();

    /**
     * Sets a creature's HP to 0, the same single call the game's own sudden-death
     * action makes.
     *
     * @return false when the ref is not a creature
     */
    boolean kill(int ref);

    /**
     * Sets a creature's current HP through the game's own setter.
     *
     * @return false when the ref is not a creature
     */
    boolean setHp(int ref, long hp);

    /** The hero and the creatures around it. */
    @Nonnull
    EntityRegistry getEntityRegistry();
}
