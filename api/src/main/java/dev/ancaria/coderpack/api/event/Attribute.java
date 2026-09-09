package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * An attribute point was just spent. Vetoable, but applied differently from the
 * others. There is no register to swap here, so Coderpack writes the field
 * after the game's own grant returns. The result is the same, because nothing
 * else has observed the value yet.
 */
public final class Attribute extends Veto {

    public Attribute(Map<String, String> fields) {
        super(fields);
    }

    /** 0 Strength, 1 Endurance, 2 Dexterity, 3 PhysReg, 4 MentalReg, 5 Charisma. */
    public int index() {
        return (int) num("attr");
    }

    @Nullable
    public String name() {
        return text("name");
    }

    public long value() {
        return num("prev");
    }

    public long next() {
        return num("next");
    }

    public void next(long value) {
        rewrite("next", value);
    }
}
