package dev.ancaria.coderpack.api;

import dev.ancaria.coderpack.api.event.Event;

import java.util.function.Consumer;

import javax.annotation.Nonnull;

/**
 * Listener registration. Two ways in, and they are the same bus: order,
 * {@link Priority} and the MONITOR rule do not care which one a listener came
 * from.
 *
 * <p>{@link #register(Object)} is for a mod's own handlers -- a class of
 * methods, each named after what it does. {@link #on} is for a one-liner and
 * for anything registered after load, since it hands back a {@link Handle} that
 * takes the listener off again.
 *
 * <h2>Threads</h2>
 *
 * <p>Every method here, and {@link Handle#unregister()}, may be called from any
 * thread at any time -- at load, or later from a thread the mod started itself.
 * The registry is the loader's problem, not the mod's.
 *
 * <p>The other direction is the guarantee that matters more: listeners are only
 * ever called on the loader's one dispatch thread, one event at a time and one
 * listener at a time. A listener body never has to guard against another
 * listener or against another event -- only against the mod's own threads, if
 * it has any.
 *
 * <p>Registering while an event is being dispatched takes effect from the next
 * event; the dispatch already running does not pick the new listener up.
 * Unregistering from inside a listener takes effect at once -- the dispatch
 * already running will not call it again. Unregistering from another thread
 * takes effect from the next event at the latest, since a dispatch that has
 * already passed the listener cannot un-call it.
 */
public interface Events {

    /**
     * Registers every {@link Subscribe} method on {@code listener}. Each such
     * method takes exactly one event parameter, and that parameter's type is
     * what it subscribes to -- there is no event name to keep in sync.
     *
     * <p>The annotation carries the rest: {@link Subscribe#priority()} for when
     * the method runs and {@link Subscribe#ignoreCancelled()} for whether an
     * already cancelled event still reaches it.
     */
    void register(Object listener);

    /**
     * Registers one listener for one event type, at {@link Priority#NORMAL} and
     * hearing about cancelled events -- the defaults {@link Subscribe} has.
     *
     * <pre>{@code events.on(Damage.class, e -> e.next(e.maxHp()));}</pre>
     *
     * <p>The type is the same thing the annotation's parameter type is, so a
     * listener on {@link Event} still sees every event and one on
     * {@link dev.ancaria.coderpack.api.event.Veto} sees every vetoable one.
     */
    @Nonnull
    default <E extends Event> Handle on(Class<E> type, Consumer<E> listener) {
        return on(type, Priority.NORMAL, false, listener);
    }

    /** As {@link #on(Class, Consumer)}, choosing when it runs. */
    @Nonnull
    default <E extends Event> Handle on(Class<E> type, Priority priority,
                                        Consumer<E> listener) {
        return on(type, priority, false, listener);
    }

    /**
     * As {@link #on(Class, Consumer)}, choosing when it runs and whether an
     * already cancelled event still reaches it.
     */
    @Nonnull
    <E extends Event> Handle on(Class<E> type, Priority priority,
                                boolean ignoreCancelled, Consumer<E> listener);
}
