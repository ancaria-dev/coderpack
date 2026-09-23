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

    public int getPrevious() {
        return (int) getNum("prev");
    }

    public int getPoints() {
        return (int) getNum("next");
    }

    /** More than before: a grant rather than a spend. */
    public boolean isGranted() {
        return getPoints() > getPrevious();
    }
}
