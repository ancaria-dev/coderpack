package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/** A potion was drunk, by the hero or by another creature. */
public final class Drink extends Event {

    public Drink(Map<String, String> fields) {
        super(fields);
    }

    public int getRef() {
        return (int) getNum("ref");
    }

    public int getTypeId() {
        return (int) getNum("type");
    }

    /** Internal name, e.g. TYPE_OBJECT_POTION_LARGE_RED. */
    @Nullable
    public String getTypeName() {
        return getText("name");
    }

    public boolean isPlayer() {
        return getNum("player") == 1;
    }
}
