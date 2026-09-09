package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * Experience is about to be granted. Vetoable via the new total, which the game
 * clamps to 2,586,931,599 on its own. Coderpack also caps at int32 so a large
 * multiplier cannot overflow on the way there.
 */
public final class Experience extends Veto {

    public Experience(Map<String, String> fields) {
        super(fields);
    }

    public long gain() {
        return num("gain");
    }

    public long exp() {
        return num("prev");
    }

    public long next() {
        return num("next");
    }

    public void next(long value) {
        rewrite("next", value);
    }
}
