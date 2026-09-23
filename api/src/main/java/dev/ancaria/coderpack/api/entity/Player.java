package dev.ancaria.coderpack.api.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
    HeroClass getHeroClass();

    int getLevel();

    long getHp();

    long getMaxHp();

    long getGold();

    long getExp();

    /** World coordinates, not the HUD numbers (those are world / 53.66563). */
    int getX();

    int getY();

    void teleport(int x, int y);

    void setHp(long value);

    void setGold(long value);

    /** Adds experience through the game's own addExperience, which clamps. */
    void addExp(long amount);

    // Everything below asks the game each time, one round-trip per call, and
    // returns a snapshot. The getters above are free because events keep them
    // current; these are not covered by any event, or not completely.

    @Nonnull
    Attributes getAttributes();

    /**
     * Sets an attribute, then runs the game's own derived-stat recalculation,
     * so max HP and the rest follow at once. Clamped to 0..65535.
     */
    void setAttribute(Attributes.Kind kind, int value);

    @Nonnull
    Skills getSkills();

    /** Sets the level in a skill slot and recalculates. Clamped to 0..255. */
    void setSkill(int slot, int level);

    @Nonnull
    CombatArts getCombatArts();

    /**
     * One combat art by its storage index, or null when the hero owns none at
     * that index. The same round-trip as {@link #getCombatArts()}, which is
     * the one to use when more than one art is wanted.
     */
    @Nullable
    CombatArts.Art getCombatArt(int index);

    /**
     * Sets a combat art's base level, the number runes raise, by its storage
     * index. Clamped to 0..255.
     */
    void setCombatArt(int index, int level);

    /** The journal's Statistics page. */
    @Nonnull
    Stats getStats();

    /** Derived numbers from the character screen. */
    @Nonnull
    Sheet getSheet();

    /** Sets HP to 0, the call the game's own sudden-death action makes. */
    void kill();
}
