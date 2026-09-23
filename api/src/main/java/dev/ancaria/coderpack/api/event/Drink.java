package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/** A potion was drunk, by the hero or by another creature. */
public final class Drink extends Event {

    public Drink(Map<String, String> fields) {
        super(fields);
    }

    public int ref() {
        return (int) num("ref");
    }

    public int typeId() {
        return (int) num("type");
    }

    /** Internal name, e.g. TYPE_OBJECT_POTION_LARGE_RED. */
    @Nullable
    public String typeName() {
        return text("name");
    }

    public boolean player() {
        return num("player") == 1;
    }
}
