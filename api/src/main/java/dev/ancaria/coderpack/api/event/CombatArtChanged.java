package dev.ancaria.coderpack.api.event;

import java.util.Map;

/** A combat art holds a new base level, after {@link CombatArt} was decided. */
public final class CombatArtChanged extends Event {

    public CombatArtChanged(Map<String, String> fields) {
        super(fields);
    }

    public int getIndex() {
        return (int) getNum("index");
    }

    public int getArtId() {
        return (int) getNum("id");
    }

    public int getAspect() {
        return (int) getNum("aspect");
    }

    public int getPrevious() {
        return (int) getNum("prev");
    }

    public int getLevel() {
        return (int) getNum("next");
    }
}
