package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Handle;
import dev.ancaria.coderpack.api.Priority;
import dev.ancaria.coderpack.api.SacredMod;
import dev.ancaria.coderpack.api.Subscribe;
import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.Decides;
import dev.ancaria.coderpack.api.event.Decision;
import dev.ancaria.coderpack.api.event.EventMutation;
import dev.ancaria.coderpack.api.event.Fold;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Listener registry and dispatch.
 *
 * <p>Order is {@link Priority} order (FIRST, NORMAL, LAST, MONITOR) and
 * within one step the order the mod registered its listeners, whether it did
 * that with {@link Subscribe} or with {@code events.on(...)}. What keeps the
 * last mod to run from simply overwriting what the others decided is the fold:
 * each answer is applied before the next listener is called, so a listener
 * reading {@code getValue()} reads everyone before it and composes with them
 * instead of starting over. A veto by itself stops nothing; later listeners
 * still see the event unless they asked for {@code ignoreVetoed}.
 *
 * <p>A listener that throws is not allowed to take the game with it: the
 * exception is logged and, after a few of them, that listener is dropped. One
 * broken mod degrades to a silent mod, never to a crashed game.
 *
 * <p>Events arrive one per frame, so dispatch is the one loop here that has to
 * be cheap. It costs one map lookup and no allocation: listeners are bucketed
 * by the type they declared, and the buckets an event class needs, its own
 * and every supertype up to {@link Event}, are merged into priority order
 * once and kept. Registering or dropping a listener throws that away and the
 * next event of each type rebuilds it.
 *
 * <h2>Threads</h2>
 *
 * <p>{@link #dispatch} runs on one thread and only one: {@code sal-dispatch},
 * started by {@code Main}. Registration does not. {@code events.on(...)} and
 * {@link Handle#unregister()} are public API, and a mod may call either from
 * any thread, at any time. So the registry has to be safe while the frame stays
 * free.
 *
 * <p>Both, by keeping the two sides apart. {@link #declared}, the mutable side,
 * is plain collections behind {@link #lock}, which is fine because nothing
 * takes that lock per frame. {@link #resolved}, the side dispatch reads, is an immutable
 * map of immutable lists published through one volatile field and replaced
 * whole rather than edited.
 *
 * <p>Copy-on-write rather than copy-on-read, and that is the whole reason for
 * the shape. The merged list for an event class is built once and then reused
 * for every frame of that class, so making it immutable costs nothing, while
 * copying it per dispatch (what a synchronized list or a defensive copy would
 * amount to) is exactly the per-frame allocation this design exists to avoid.
 * A dispatch that is running keeps iterating whichever snapshot it started
 * with. A registration from another thread cannot be seen half applied,
 * because there is no half.
 */
final class Bus {

    private static final int FAILURES_ALLOWED = 3;

    /**
     * The one shape every listener is called through. A {@link MethodHandle} is
     * signature-polymorphic, so {@code invokeExact} demands the static types at
     * the call site and the handle's own type to match to the letter. Hence
     * one type, asType'd onto every handle at registration, and one call site.
     *
     * <p>The return is {@code Object} rather than {@code void} because a
     * listener that decides returns a mutation and one that observes returns
     * nothing. {@code asType} adapts both onto this: it drops a value when the
     * target is void and introduces a null when the source is. So an observer
     * answers null, which is also what "nothing to fold" means, and the two
     * kinds of listener stay one loop and one call site.
     */
    private static final MethodType CALL = MethodType.methodType(Object.class, Event.class);

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /** {@code Consumer.accept}, the entry point of the observing lambda path. */
    private static final MethodHandle ACCEPT = entry(
            Consumer.class, "accept", void.class);

    /** {@code Function.apply}, the same for the deciding one. */
    private static final MethodHandle APPLY = entry(
            Function.class, "apply", Object.class);

    private static MethodHandle entry(Class<?> owner, String name, Class<?> returns) {
        try {
            return MethodHandles.publicLookup().findVirtual(
                    owner, name, MethodType.methodType(returns, Object.class));
        } catch (ReflectiveOperationException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    /**
     * One registration, and the {@link Handle} a mod is given for it. The
     * same object on both sides, so a handle from {@code getEvents()} and the
     * one {@code on(...)} returned are one listener, not two views of it.
     */
    private final class Listener implements Handle {

        final LoadedMod owner;
        final MethodHandle call;
        final Class<? extends Event> type;
        final Priority priority;
        final boolean ignoreVetoed;
        final String name;
        final int seq;
        // The @Subscribe object and method; null for a lambda.
        final Object target;
        final String method;

        // Written and read only inside dispatch(), which is one thread. Plain
        // fields on purpose: volatile here would put a barrier on the per-frame
        // path to publish two counters nobody outside that thread reads.
        int failures;
        boolean warned;

        // The one that genuinely crosses threads. unregister() sets it from
        // whatever thread the mod called it on, and dispatch reads it just
        // before every call.
        volatile boolean dead;

        Listener(LoadedMod owner, MethodHandle call, Class<? extends Event> type,
                 Priority priority, boolean ignoreVetoed, String name,
                 Object target, String method) {
            this.owner = owner;
            this.call = call;
            this.type = type;
            this.priority = priority;
            this.ignoreVetoed = ignoreVetoed;
            this.name = name;
            this.seq = registrations.getAndIncrement();
            this.target = target;
            this.method = method;
        }

        @Override
        public void unregister() {
            drop(this);
        }

        @Override
        public boolean isRegistered() {
            return !dead;
        }

        @Override
        public SacredMod getMod() {
            return owner.instance();
        }

        @Override
        public Class<? extends Event> getEventType() {
            return type;
        }

        @Override
        public Priority getPriority() {
            return priority;
        }

        @Override
        public boolean isIgnoreVetoed() {
            return ignoreVetoed;
        }

        @Override
        public Class<?> getListenerClass() {
            return target == null ? null : target.getClass();
        }

        @Override
        public String getMethodName() {
            return method;
        }

        Bus bus() {
            return Bus.this;
        }

        @Override
        public String toString() {
            return name + (dead ? " (unregistered)" : "");
        }
    }

    /** Priority first, then the order of registration across every bucket. */
    private static final Comparator<Listener> ORDER =
            Comparator.<Listener>comparingInt(l -> l.priority.ordinal())
                      .thenComparingInt(l -> l.seq);

    /** Guards {@link #declared} and every replacement of {@link #resolved}. */
    private final Object lock = new Object();

    /** Keyed by the type a listener declared, in registration order. Under {@link #lock}. */
    private final Map<Class<?>, List<Listener>> declared = new HashMap<>();

    /**
     * Keyed by the class of an event that has actually been dispatched. Read on
     * every frame with nothing held, so it is never edited in place: a change
     * publishes a whole new map through this one volatile write.
     */
    private volatile Map<Class<?>, List<Listener>> resolved = Map.of();

    /** Registration order, and registration can come from more than one thread. */
    private final AtomicInteger registrations = new AtomicInteger();

    /** Every {@link Subscribe} method on {@code target}, one handle each. */
    List<Handle> register(LoadedMod owner, Object target) {
        String mod = owner.id();
        List<Handle> added = new ArrayList<>();
        for (Method method : target.getClass().getMethods()) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) {
                continue;
            }
            if (method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                Log.warn(mod + ": " + method.getName()
                         + " uses @Subscribe but doesn’t take exactly one event.");
                continue;
            }
            // void observes, a mutation decides. The linter rejects anything
            // else when the mod is built; this is the loader’s own backstop for
            // a hand-built jar.
            Class<?> answers = method.getReturnType();
            if (answers != void.class && !EventMutation.class.isAssignableFrom(answers)) {
                Log.warn(mod + ": " + method.getName() + " returns "
                         + answers.getSimpleName() + ". A @Subscribe method"
                         + " returns void or that event’s own Mutation.");
                continue;
            }
            if (answers != void.class && annotation.priority() == Priority.MONITOR) {
                Log.warn(mod + ": " + method.getName() + " is MONITOR and returns"
                         + " a mutation. MONITOR watches; its answer is ignored.");
            }
            // A public method on a package-private class is not reachable by
            // reflection from here, and keeping listener classes package-private
            // is the natural way to write a mod, so ask for access explicitly.
            // unreflect then inherits that permission instead of checking again.
            MethodHandle call;
            try {
                method.setAccessible(true);
                MethodHandle raw = LOOKUP.unreflect(method);
                // A static @Subscribe method has no receiver to bind.
                call = (Modifier.isStatic(method.getModifiers())
                        ? raw : raw.bindTo(target)).asType(CALL);
            } catch (IllegalAccessException | RuntimeException denied) {
                Log.warn(mod + ": can’t access " + method.getName()
                         + ". Make the class public (" + denied + ").");
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> type = (Class<? extends Event>) method.getParameterTypes()[0];
            Listener listener = new Listener(owner, call, type, annotation.priority(),
                                             annotation.ignoreVetoed(),
                                             mod + "." + method.getName(),
                                             target, method.getName());
            add(listener);
            added.add(listener);
        }
        int found = added.size();
        Log.info(mod + ": " + found + (found == 1 ? " listener." : " listeners."));
        return List.copyOf(added);
    }

    /**
     * The deciding lambda path. Same bus, same order, same fold: a
     * {@code Function} differs from a {@code Consumer} only in answering with
     * something instead of null, which is exactly the difference between the
     * two kinds of listener.
     */
    <M extends EventMutation, E extends Event & Decides<M>> Handle decide(
            LoadedMod owner, Class<E> type, Priority priority, boolean ignoreVetoed,
            Function<E, M> listener) {
        Listener added = new Listener(owner, APPLY.bindTo(listener).asType(CALL), type,
                                      priority, ignoreVetoed,
                                      owner.id() + ".decide(" + type.getSimpleName() + ")",
                                      null, null);
        add(added);
        return added;
    }

    /** The observing lambda path. */
    <E extends Event> Handle on(LoadedMod owner, Class<E> type, Priority priority,
                                boolean ignoreVetoed, Consumer<E> listener) {
        Listener added = new Listener(owner, ACCEPT.bindTo(listener).asType(CALL), type,
                                      priority, ignoreVetoed,
                                      owner.id() + ".on(" + type.getSimpleName() + ")",
                                      null, null);
        add(added);
        return added;
    }

    private void add(Listener listener) {
        synchronized (lock) {
            declared.computeIfAbsent(listener.type, key -> new ArrayList<>()).add(listener);
            resolved = Map.of();
        }
    }

    /**
     * Runs every listener that accepts this event, in priority order, folding
     * each answer in before the next one is called. That fold is what makes
     * {@code getValue()} mean "with everyone before me in it", and it is why a
     * second mod doubling the same number composes with the first instead of
     * overwriting it.
     */
    void dispatch(Event event) {
        List<Listener> list = listenersFor(event.getClass());
        Decision decision = event instanceof Decision decided ? decided : null;
        boolean died = false;
        // By index and over a list nobody can mutate: it is immutable, and a
        // registration on another thread publishes a different one instead of
        // touching this. A listener that fails out is marked here and swept
        // afterwards, so the frame pays for no copy of anything.
        for (int i = 0; i < list.size(); i++) {
            Listener listener = list.get(i);
            if (listener.dead) {
                continue;
            }
            boolean monitor = listener.priority == Priority.MONITOR;
            if (decision != null && !monitor) {
                // A mod may end the deciding early. It may not blind the
                // trackers, so this skip never reaches the monitor step.
                if (Fold.stopped(decision)) {
                    continue;
                }
                if (listener.ignoreVetoed && decision.isVetoed()) {
                    continue;
                }
            }
            Object answer;
            try {
                answer = listener.call.invokeExact(event);
            } catch (Throwable failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                Log.error(listener.name, cause);
                if (++listener.failures >= FAILURES_ALLOWED) {
                    Log.warn(listener.name + " failed " + FAILURES_ALLOWED
                             + " times and was disabled.");
                    listener.dead = true;
                    died = true;
                }
                continue;
            }
            if (answer == null || decision == null) {
                continue;
            }
            if (monitor) {
                refuse(listener);
                continue;
            }
            fold(listener, decision, (EventMutation) answer);
        }
        if (died) {
            sweep();
        }
    }

    /**
     * Folds one answer in and says so when it threw another mod's work away.
     * Every such case is logged every time, not once per listener: the fold
     * moved in here so that these are visible, and a quiet reset would be the
     * silent overwrite this design exists to remove.
     */
    private static void fold(Listener listener, Decision decision, EventMutation answer) {
        boolean wasOpen = !Fold.stopped(decision);
        switch (answer.getKind()) {
            case RESET -> Log.warn(listener.name + " reset "
                                   + decision.getClass().getSimpleName()
                                   + ", discarding every earlier listener's work.");
            case VETO -> Log.info(listener.name + " vetoed "
                                  + decision.getClass().getSimpleName() + ".");
            default -> {
            }
        }
        if (Fold.apply(decision, answer) && wasOpen) {
            Log.warn(listener.name + " ended the chain on "
                     + decision.getClass().getSimpleName()
                     + "; later deciding listeners were skipped.");
        }
    }

    /**
     * The listeners for one event class: its own bucket and every supertype's,
     * merged into dispatch order. One lookup on the frame and nothing else once
     * the class has been seen.
     */
    private List<Listener> listenersFor(Class<?> eventClass) {
        List<Listener> known = resolved.get(eventClass);
        return known != null ? known : resolve(eventClass);
    }

    /**
     * The miss: the first event of a class, and the first of each class after
     * the registry changes. Never a frame that hits. Under the lock, so it
     * cannot publish a merge of a registry that is halfway through an edit.
     */
    private List<Listener> resolve(Class<?> eventClass) {
        synchronized (lock) {
            List<Listener> known = resolved.get(eventClass);
            if (known != null) {
                return known;
            }
            List<Listener> list = new ArrayList<>();
            // Every declared type is an Event subclass, so the chain ends at
            // Event and never walks into Object.
            for (Class<?> type = eventClass;
                 type != null && Event.class.isAssignableFrom(type);
                 type = type.getSuperclass()) {
                List<Listener> bucket = declared.get(type);
                if (bucket != null) {
                    list.addAll(bucket);
                }
            }
            list.sort(ORDER);
            List<Listener> merged = List.copyOf(list);
            Map<Class<?>, List<Listener>> next = new HashMap<>(resolved);
            next.put(eventClass, merged);
            resolved = next;
            return merged;
        }
    }

    /**
     * Takes a listener off. Idempotent, safe from any thread, and safe from
     * inside a dispatch. The loop reads {@code dead} again before each call,
     * so a listener it has not reached yet is not reached.
     *
     * @return false when it was already off
     */
    private boolean drop(Listener listener) {
        synchronized (lock) {
            if (listener.dead) {
                return false;
            }
            listener.dead = true;
            sweep();
            return true;
        }
    }

    /** {@link Handle#unregister()} with an answer, for a handle of this bus. */
    boolean drop(Handle handle) {
        if (handle instanceof Listener listener && listener.bus() == this) {
            return drop(listener);
        }
        boolean was = handle.isRegistered();
        handle.unregister();
        return was;
    }

    /**
     * Takes off every listener {@link #register(LoadedMod, Object)} made for
     * {@code target}, or only those declared for exactly {@code type} when it
     * is not null.
     */
    int dropTarget(Object target, Class<?> type) {
        return dropWhere(l -> l.target == target && (type == null || l.type == type));
    }

    /** Takes off everything a mod registered, annotation and lambda alike. */
    int dropMod(LoadedMod owner) {
        return dropWhere(l -> l.owner == owner);
    }

    private int dropWhere(Predicate<Listener> which) {
        synchronized (lock) {
            int dropped = 0;
            for (List<Listener> bucket : declared.values()) {
                for (Listener listener : bucket) {
                    if (!listener.dead && which.test(listener)) {
                        listener.dead = true;
                        dropped++;
                    }
                }
            }
            if (dropped > 0) {
                sweep();
            }
            return dropped;
        }
    }

    /** Every live listener of every mod, in registration order. */
    List<Handle> handles() {
        synchronized (lock) {
            List<Listener> all = new ArrayList<>();
            for (List<Listener> bucket : declared.values()) {
                for (Listener listener : bucket) {
                    if (!listener.dead) {
                        all.add(listener);
                    }
                }
            }
            all.sort(Comparator.comparingInt(l -> l.seq));
            return List.copyOf(all);
        }
    }

    /** Reentrant: {@link #drop} calls it holding the lock, dispatch without. */
    private void sweep() {
        synchronized (lock) {
            declared.values().removeIf(bucket -> {
                bucket.removeIf(listener -> listener.dead);
                return bucket.isEmpty();
            });
            resolved = Map.of();
        }
    }

    /**
     * How many listeners are on the bus. For the tests: a sweep that failed to
     * sweep is invisible from dispatch, which skips the dead ones anyway.
     */
    int size() {
        synchronized (lock) {
            int total = 0;
            for (List<Listener> bucket : declared.values()) {
                total += bucket.size();
            }
            return total;
        }
    }

    /** Says once, per listener, that its answer is being thrown away. */
    private static void refuse(Listener listener) {
        if (listener.warned) {
            return;
        }
        listener.warned = true;
        Log.warn(listener.name + " is MONITOR and returned a mutation. It was "
                 + "ignored; this warning won’t be repeated. The mod linter "
                 + "refuses this when the mod is built.");
    }
}
