package dev.ancaria.coderpack.api.event;

import javax.annotation.Nonnull;

/**
 * What a listener returns when it decides something. Each decidable event
 * declares its own {@code Mutation} as a nested type, so the compiler pins a
 * listener's return to the event it subscribed to, and an event with nothing to
 * decide has no such type to return.
 *
 * <p>Four kinds, and they are not variations on one another:
 *
 * <ul>
 * <li>{@code NONE} adds nothing. The fold stands as it is.</li>
 * <li>{@code RESET} discards every earlier listener's work, including a veto,
 *     and puts the game's own number back. The event still happens.</li>
 * <li>{@code VETO} stops the write. The event does not happen. Only a later
 *     {@code RESET} lifts it; a later value does not.</li>
 * <li>{@code CHANGE} carries a value.</li>
 * </ul>
 *
 * <p>{@link #last()} is orthogonal to all four: it marks a mutation as the end
 * of the chain, so no further deciding listener sees this occurrence of the
 * event. Monitors still run. It hands the win to whoever runs earlier, which
 * priority decides and then mod load order does, so reach for
 * {@link dev.ancaria.coderpack.api.Priority} first.
 */
public abstract sealed class EventMutation
        permits Amount.Change, Console.Mutation, Pickup.Mutation {

    /** Which of the four a mutation is. The loader folds on this. */
    public enum Kind {
        NONE, RESET, VETO, CHANGE
    }

    private final Kind kind;

    // Read by Fold, which is in this package. The public reading is isLast(),
    // which leaves last() free to be the modifier.
    final boolean last;

    // Package-private, and every subclass is nested in this package's events.
    // A mod cannot add a fifth kind.
    EventMutation(Kind kind, boolean last) {
        this.kind = kind;
        this.last = last;
    }

    @Nonnull
    public final Kind getKind() {
        return kind;
    }

    /** True when this mutation ends the chain. See {@link #last()}. */
    public final boolean isLast() {
        return last;
    }

    /**
     * The same mutation, ending the chain: no further deciding listener sees
     * this occurrence of the event, though the monitors still run.
     *
     * <p>Named for what it is rather than for what it does, because the two
     * words that say what it does are both keywords. {@code final} and
     * {@code finally} cannot be method names in Java or in Kotlin.
     */
    @Nonnull
    public abstract EventMutation last();
}
