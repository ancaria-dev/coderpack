package dev.ancaria.coderpack.api.entity;

import javax.annotation.Nonnull;

/**
 * The hero. Reads come from the last state Coderpack saw, so they are free; writes go
 * to the game thread and take effect immediately.
 *
 * <p>Where the game has its own primitive, Coderpack calls it instead of writing the
 * field -- the engine then refreshes its own caches and anti-cheat mirrors, and
 * the result matches the UI by construction.
 */
public interface Player {

    @Nonnull
    HeroClass heroClass();

    int level();

    long hp();

    long maxHp();

    long gold();

    long exp();

    /** World coordinates, not the HUD numbers (those are world / 53.66563). */
    int x();

    int y();

    void teleport(int x, int y);

    void hp(long value);

    void gold(long value);

    /** Adds experience through the game's own addExperience, which clamps. */
    void addExp(long amount);
}
