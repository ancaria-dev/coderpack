package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * A creature other than the player took a hit. Read-only: rewriting another
 * creature's HP goes through a different, riskier path than the player's.
 */
public class MobHit extends Event {

    public MobHit(Map<String, String> fields) {
        super(fields);
    }

    public int getTypeId() {
        return (int) getNum("type");
    }

    /**
     * Internal name, e.g. TYPE_NPC_GHUL01. Stable and English, so match on it.
     */
    @Nullable
    public String getTypeName() {
        return getText("name");
    }

    public int getLevel() {
        return (int) getNum("level");
    }

    public long getHp() {
        return getNum("prev");
    }

    public long getNext() {
        return getNum("next");
    }

    public long getMaxHp() {
        return getNum("max");
    }

    public long getDamage() {
        return getNum("damage");
    }
}
