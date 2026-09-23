package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * A decision about one number.
 *
 * <p>Two readings of it, and which one a listener wants is not a matter of
 * taste:
 *
 * <ul>
 * <li>{@link #getValue()} is the number with every earlier listener's work folded
 *     in. This is the one to read. A mod that doubles something writes
 *     {@code getValue() * 2}, and two such mods compose into four times without
 *     either knowing the other exists.</li>
 * <li>{@link #getInitial()} is what the game sent, before any mod touched it. For
 *     a listener that reports or reasons about what the game itself
 *     intended.</li>
 * </ul>
 *
 * <p>The unit is whatever that event decides, and it differs: {@link Gold}
 * decides a delta, the rest decide the number about to be stored. Each event's
 * own named getters say which.
 */
public abstract sealed class Amount extends Decision
        permits Attribute, CombatArt, Damage, Experience, Gold, Skill {

    private final String key;
    private long value;

    Amount(Map<String, String> fields, String key) {
        super(fields);
        this.key = key;
        this.value = getNum(key);
    }

    /** The number as the game sent it. */
    public final long getInitial() {
        return getNum(key);
    }

    /** The number with every earlier listener's work folded in. */
    public final long getValue() {
        return value;
    }

    @Override
    final void reset() {
        this.value = getInitial();
    }

    @Override
    final void change(EventMutation mutation) {
        this.value = ((Change) mutation).getValue();
    }

    @Override
    @Nonnull
    final Map<String, String> verdict() {
        return value == getInitial() ? Map.of() : Map.of(key, Long.toString(value));
    }

    /**
     * The shape the six numeric mutations share. Each event still declares its
     * own subclass, so the compiler keeps a {@link Gold} answer out of a
     * {@link Damage} listener.
     */
    public abstract static sealed class Change extends EventMutation
            permits Attribute.Mutation, CombatArt.Mutation, Damage.Mutation,
                    Experience.Mutation, Gold.Mutation, Skill.Mutation {

        private final long value;

        Change(Kind kind, boolean last, long value) {
            super(kind, last);
            this.value = value;
        }

        /** Meaningless unless {@link Kind#CHANGE}. */
        public final long getValue() {
            return value;
        }
    }
}
