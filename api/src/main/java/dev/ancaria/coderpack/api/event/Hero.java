package dev.ancaria.coderpack.api.event;

import dev.ancaria.coderpack.api.entity.HeroClass;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The hero object was found. Fired once per world load, and again if the
 * player switches character. Everything a mod needs to decide "whose game is
 * this" arrives here.
 */
public final class Hero extends Event {

    public Hero(Map<String, String> fields) {
        super(fields);
    }

    @Nonnull
    public HeroClass getHeroClass() {
        return HeroClass.of((int) getNum("cls"));
    }

    /** The game's own name for the class, already localized where it matters. */
    @Nullable
    public String getClassName() {
        return getText("clsName");
    }

    public int getLevel() {
        return (int) getNum("level");
    }

    public long getHp() {
        return getNum("hp");
    }

    public long getMaxHp() {
        return getNum("maxHp");
    }

    public long getGold() {
        return getNum("gold");
    }

    public long getExp() {
        return getNum("exp");
    }
}
