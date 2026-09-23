package dev.ancaria.coderpack.api.entity;

import javax.annotation.Nonnull;

/**
 * The hero. Reads come from the last state Coderpack saw, so they are free.
 * Writes go to the game thread and take effect immediately.
 *
 * <p>Where the game has its own primitive, Coderpack calls it instead of writing the
 * field. The engine then refreshes its own caches and anti-cheat mirrors, and
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

    // Everything below asks the game each time, one round-trip per call, and
    // returns a snapshot. The getters above are free because events keep them
    // current; these are not covered by any event, or not completely.

    @Nonnull
    Attributes attributes();

    /**
     * Sets an attribute, then runs the game's own derived-stat recalculation,
     * so max HP and the rest follow at once. Clamped to 0..65535.
     */
    void attribute(Attributes.Kind kind, int value);

    @Nonnull
    Skills skills();

    /** Sets the level in a skill slot and recalculates. Clamped to 0..255. */
    void skill(int slot, int level);

    @Nonnull
    CombatArts combatArts();

    /**
     * Sets a combat art's base level, the number runes raise, by its storage
     * index. Clamped to 0..255.
     */
    void combatArt(int index, int level);

    /** The journal's Statistics page. */
    @Nonnull
    Stats stats();

    /** Derived numbers from the character screen. */
    @Nonnull
    Sheet sheet();

    /** Sets HP to 0, the call the game's own sudden-death action makes. */
    void kill();
}
