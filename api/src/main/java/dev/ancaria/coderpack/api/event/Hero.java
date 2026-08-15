package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.HeroClass;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The hero object was found -- fired once per world load, and again if the
 * player switches character. Everything a mod needs to decide "whose game is
 * this" arrives here.
 */
public final class Hero extends Event {

    public Hero(Map<String, String> fields) {
        super(fields);
    }

    @Nonnull
    public HeroClass heroClass() {
        return HeroClass.of((int) num("cls"));
    }

    /** The game's own name for the class, already localized where it matters. */
    @Nullable
    public String className() {
        return text("clsName");
    }

    public int level() {
        return (int) num("level");
    }

    public long hp() {
        return num("hp");
    }

    public long maxHp() {
        return num("maxHp");
    }

    public long gold() {
        return num("gold");
    }

    public long exp() {
        return num("exp");
    }
}
