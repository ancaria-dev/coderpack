package dev.ancaria.coderpack.api.event;

import java.util.Map;

/** A combat art holds a new base level, after {@link CombatArt} was decided. */
public final class CombatArtChanged extends Event {

    public CombatArtChanged(Map<String, String> fields) {
        super(fields);
    }

    public int index() {
        return (int) num("index");
    }

    public int artId() {
        return (int) num("id");
    }

    public int aspect() {
        return (int) num("aspect");
    }

    public int previous() {
        return (int) num("prev");
    }

    public int level() {
        return (int) num("next");
    }
}
