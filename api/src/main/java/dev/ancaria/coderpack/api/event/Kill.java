package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * The journal counted a defeated opponent: the "Opponents Defeated" line on
 * the Statistics page went up by one. This is the game's own notion of a kill
 * that counts, which {@link MobDeath} is not: every creature whose HP reaches
 * zero is a MobDeath, whoever dealt the blow.
 */
public final class Kill extends Event {

    public Kill(Map<String, String> fields) {
        super(fields);
    }

    /** Opponents defeated so far, this one included. */
    public long total() {
        return num("total");
    }

    /**
     * The victim's type, as the kill recorder was handed it. Expected to be the
     * same id {@link MobDeath#typeId()} reports, and not yet confirmed in game.
     */
    public int typeId() {
        return (int) num("type");
    }

    /** The internal name for {@link #typeId()}. */
    @Nullable
    public String typeName() {
        return text("name");
    }
}
