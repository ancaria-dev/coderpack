package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The number of unspent skill points changed: spent on a skill, or granted by
 * a level-up. Report only. Multiplying this budget once wrapped a live save's
 * counter to 65535.
 */
public final class SkillPointsChanged extends Event {

    public SkillPointsChanged(Map<String, String> fields) {
        super(fields);
    }

    public int previous() {
        return (int) num("prev");
    }

    public int points() {
        return (int) num("next");
    }

    /** More than before: a grant rather than a spend. */
    public boolean granted() {
        return points() > previous();
    }
}
