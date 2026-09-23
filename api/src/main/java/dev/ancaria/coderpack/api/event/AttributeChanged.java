package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/** An attribute holds a new value, after {@link Attribute} was decided. */
public final class AttributeChanged extends Event {

    public AttributeChanged(Map<String, String> fields) {
        super(fields);
    }

    /** 0 Strength, 1 Endurance, 2 Dexterity, 3 PhysReg, 4 MentalReg, 5 Charisma. */
    public int getIndex() {
        return (int) getNum("attr");
    }

    @Nullable
    public String getName() {
        return getText("name");
    }

    public long getPrevious() {
        return getNum("prev");
    }

    public long getValue() {
        return getNum("next");
    }
}
