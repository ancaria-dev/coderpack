package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * Experience was stored. {@link Experience} decided the total. This is the
 * total the game actually kept, after its own clamp.
 */
public final class ExperienceChanged extends Event {

    public ExperienceChanged(Map<String, String> fields) {
        super(fields);
    }

    public long getPrevious() {
        return getNum("prev");
    }

    public long getExp() {
        return getNum("next");
    }
}
