package dev.ancaria.coderpack.api.event;

/**
 * Implemented by an event a listener may decide, naming the mutation type that
 * listener returns.
 *
 * <p>It exists so the decision is a matter of types rather than of discipline.
 * {@code Events.decide} takes an {@code E extends Decides<M>} and a function
 * returning {@code M}, so an event with nothing to decide cannot be passed to
 * it at all, and one that can be gets its own {@code Mutation} pinned as the
 * return.
 *
 * @param <M> that event's nested {@code Mutation} type
 */
public interface Decides<M extends EventMutation> {
}
