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
 * <p>{@link #asLast()} is orthogonal to all four: it marks a mutation as the end
 * of the chain, so no further deciding listener sees this occurrence of the
 * event. Monitors still run. It hands the win to whoever runs earlier, which
 * priority decides and then mod load order does, so reach for
 * {@link dev.ancaria.coderpack.api.Priority} first.
 */
public abstract sealed class EventMutation permits Amount.Change, Pickup.Mutation {

    /** Which of the four a mutation is. The loader folds on this. */
    public enum Kind {
        NONE, RESET, VETO, CHANGE
    }

    private final Kind kind;
    private final boolean last;

    // Package-private, and every subclass is nested in this package's events.
    // A mod cannot add a fifth kind.
    EventMutation(Kind kind, boolean last) {
        this.kind = kind;
        this.last = last;
    }

    @Nonnull
    public final Kind kind() {
        return kind;
    }

    /** True when no further deciding listener should see this event. */
    public final boolean last() {
        return last;
    }

    /**
     * The same mutation, ending the chain. Spelled this way because
     * {@code final} is a keyword.
     */
    @Nonnull
    public abstract EventMutation asLast();
}
