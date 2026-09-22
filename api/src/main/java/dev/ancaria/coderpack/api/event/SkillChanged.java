package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * A skill slot holds a new value, after {@link Skill} was decided. Slots are
 * reported by index, for the same reason {@link Skill} gives.
 */
public final class SkillChanged extends Event {

    public SkillChanged(Map<String, String> fields) {
        super(fields);
    }

    public int slot() {
        return (int) num("slot");
    }

    public int level() {
        return (int) num("next");
    }
}
