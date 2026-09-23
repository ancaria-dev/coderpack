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
