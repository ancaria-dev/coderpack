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

    public int typeId() {
        return (int) num("type");
    }

    /** Internal name, e.g. TYPE_NPC_GHUL01. Stable and English -- match on this. */
    @Nullable
    public String typeName() {
        return text("name");
    }

    public int level() {
        return (int) num("level");
    }

    public long hp() {
        return num("prev");
    }

    public long next() {
        return num("next");
    }

    public long maxHp() {
        return num("max");
    }

    public long damage() {
        return num("damage");
    }
}
