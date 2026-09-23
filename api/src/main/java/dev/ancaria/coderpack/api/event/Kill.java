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
    public long getTotal() {
        return getNum("total");
    }

    /**
     * The victim's type, as the kill recorder was handed it. Expected to be the
     * same id {@link MobDeath#getTypeId()} reports, and not yet confirmed in game.
     */
    public int getTypeId() {
        return (int) getNum("type");
    }

    /** The internal name for {@link #getTypeId()}. */
    @Nullable
    public String getTypeName() {
        return getText("name");
    }
}
