package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/** An attribute holds a new value, after {@link Attribute} was decided. */
public final class AttributeChanged extends Event {

    public AttributeChanged(Map<String, String> fields) {
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

    public long previous() {
        return num("prev");
    }

    public long value() {
        return num("next");
    }
}
