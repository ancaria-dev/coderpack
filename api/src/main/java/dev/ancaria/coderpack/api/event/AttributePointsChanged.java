package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The number of unspent attribute points changed: spent on an attribute, or
 * granted by a level-up. Report only, like {@link SkillPointsChanged}.
 */
public final class AttributePointsChanged extends Event {

    public AttributePointsChanged(Map<String, String> fields) {
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
