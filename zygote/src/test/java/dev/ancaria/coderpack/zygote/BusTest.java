package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Events;
import dev.ancaria.coderpack.api.Handle;
import dev.ancaria.coderpack.api.Priority;
import dev.ancaria.coderpack.api.Subscribe;
import dev.ancaria.coderpack.api.event.Decision;
import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.Fold;
import dev.ancaria.coderpack.api.event.Gold;
import dev.ancaria.coderpack.api.event.MobHit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dispatch rules (order, the fold, ignoreVetoed and MONITOR) through both
 *  registration styles, plus the type index that has to leave them alone. */
class BusTest {

    private static Gold gold() {
        return new Gold(Map.of("delta", "100", "current", "50", "dir", "gain"));
    }

    /** The same event with an id in it, so a listener can say which one it saw. */
    private static Gold gold(long id) {
        return new Gold(Map.of("delta", Long.toString(id), "current", "50", "dir", "gain"));
    }

    private static Events events(Bus bus, String mod) {
        return new ModEvents(mod, bus);
    }

    /** Listeners have to be reachable by reflection, hence public. */
    public static final class Order {

        final List<Priority> seen = new ArrayList<>();

        @Subscribe(priority = Priority.LAST)
        public void last(Gold event) {
            seen.add(Priority.LAST);
        }

        @Subscribe(priority = Priority.MONITOR)
        public void monitor(Gold event) {
            seen.add(Priority.MONITOR);
        }

        @Subscribe(priority = Priority.FIRST)
        public void first(Gold event) {
            seen.add(Priority.FIRST);
        }

        @Subscribe
        public void normal(Gold event) {
            seen.add(Priority.NORMAL);
        }
    }

    @Test
    void runsInPriorityOrder() {
        Order listener = new Order();
        Bus bus = new Bus();
        bus.register("order", listener);
        bus.dispatch(gold());
        assertEquals(List.of(Priority.FIRST, Priority.NORMAL, Priority.LAST,
                             Priority.MONITOR), listener.seen);
    }

    public static final class Vetoer {

        @Subscribe(priority = Priority.FIRST)
        public Gold.Mutation veto(Gold event) {
            return Gold.Mutation.veto();
        }
    }

    public static final class Both {

        boolean careful;
        boolean careless;

        @Subscribe(ignoreVetoed = true)
        public void skipMe(Gold event) {
            careful = true;
        }

        @Subscribe
        public void callMe(Gold event) {
            careless = true;
        }
    }

    @Test
    void vetoedEventSkipsOnlyTheListenersThatAskedToBeSkipped() {
        Both listener = new Both();
        Bus bus = new Bus();
        bus.register("vetoer", new Vetoer());
        bus.register("both", listener);
        bus.dispatch(gold());
        assertFalse(listener.careful, "ignoreVetoed listener was called anyway");
        assertTrue(listener.careless, "dispatch stopped at the veto");
    }

    public static final class Meddler {

        long sawValue;

        @Subscribe
        public Gold.Mutation decide(Gold event) {
            return Gold.Mutation.of(200);
        }

        /**
         * A monitor may still declare a return. The bus drops it, which is the
         * loader backstop behind the lint rule, so this asserts the drop rather
         * than relying on a mod never writing it.
         */
        @Subscribe(priority = Priority.MONITOR)
        public Gold.Mutation watch(Gold event) {
            sawValue = event.value();
            return Gold.Mutation.veto();
        }
    }

    @Test
    void monitorSeesEverythingAndChangesNothing() {
        Meddler listener = new Meddler();
        Bus bus = new Bus();
        bus.register("meddler", listener);
        Gold event = gold();
        bus.dispatch(event);
        assertEquals(200, listener.sawValue, "a monitor should see what was decided");
        assertFalse(event.vetoed(), "a monitor vetoed the event");
        assertEquals(Map.of("delta", "200"), Fold.verdict(event));
    }

    // --- the fold -------------------------------------------------------

    public static final class Doubler {

        @Subscribe
        public Gold.Mutation twice(Gold event) {
            return Gold.Mutation.of(event.value() * 2);
        }
    }

    @Test
    void twoListenersDoublingTheSameNumberCompose() {
        Bus bus = new Bus();
        bus.register("one", new Doubler());
        bus.register("two", new Doubler());
        Gold event = gold();
        bus.dispatch(event);
        // 100 -> 200 -> 400. The old shape gave 200, because the second
        // listener read the arrived value and overwrote the first.
        assertEquals(400, event.value());
        assertEquals(100, event.initial());
    }

    @Test
    void resetDiscardsEarlierWorkAndLiftsAVeto() {
        Bus bus = new Bus();
        Events events = events(bus, "fold");
        events.decide(Gold.class, Priority.FIRST, e -> Gold.Mutation.of(200));
        events.decide(Gold.class, Priority.NORMAL, e -> Gold.Mutation.veto());
        events.decide(Gold.class, Priority.LAST, e -> Gold.Mutation.reset());
        Gold event = gold();
        bus.dispatch(event);
        assertFalse(event.vetoed(), "reset should lift a veto");
        assertEquals(100, event.value(), "reset should put the arrived value back");
        assertEquals(Map.of(), Fold.verdict(event));
    }

    @Test
    void aLastMutationEndsTheChainButNotTheMonitors() {
        Bus bus = new Bus();
        Events events = events(bus, "fold");
        List<String> seen = new ArrayList<>();
        events.decide(Gold.class, Priority.FIRST, e -> {
            seen.add("first");
            return Gold.Mutation.of(200).asLast();
        });
        events.decide(Gold.class, Priority.NORMAL, e -> {
            seen.add("normal");
            return Gold.Mutation.of(999);
        });
        events.on(Gold.class, Priority.MONITOR, e -> seen.add("monitor:" + e.value()));
        Gold event = gold();
        bus.dispatch(event);
        assertEquals(List.of("first", "monitor:200"), seen);
        assertEquals(200, event.value());
    }

    // --- the type index -------------------------------------------------

    /** What a tracer looks like: one method for everything, one for the decidable half. */
    public static final class Wide {

        final List<String> seen = new ArrayList<>();

        @Subscribe
        public void everything(Event event) {
            seen.add("event:" + event.getClass().getSimpleName());
        }

        @Subscribe
        public void decidable(Decision event) {
            seen.add("decision:" + event.getClass().getSimpleName());
        }
    }

    @Test
    void aListenerOnASupertypeStillGetsTheSubtype() {
        Wide listener = new Wide();
        Bus bus = new Bus();
        bus.register("wide", listener);
        bus.dispatch(gold());
        bus.dispatch(new MobHit(Map.of("name", "TYPE_NPC_GHUL01")));
        // Gold is a Decision is an Event. MobHit is only an Event.
        //
        // Which of the two Gold lines comes first is deliberately not asserted.
        // Both methods are NORMAL, so their order is their registration order,
        // and register() registers them in the order Class.getMethods() hands
        // them back, which the JDK does not specify and which really does
        // come out differently between runs of the same class file. Order
        // inside one priority is pinned by the test below instead, where the
        // registrations are made one at a time.
        assertEquals(3, listener.seen.size());
        assertEquals(List.of("decision:Gold", "event:Gold"),
                     listener.seen.subList(0, 2).stream().sorted().toList());
        assertEquals("event:MobHit", listener.seen.get(2));
    }

    @Test
    void supertypeAndSubtypeListenersShareOneRegistrationOrder() {
        Bus bus = new Bus();
        Events events = events(bus, "mixed");
        List<String> seen = new ArrayList<>();
        // Registered across three different declared types, at two priorities.
        events.on(Event.class, e -> seen.add("a"));
        events.on(Gold.class, e -> seen.add("b"));
        events.on(Decision.class, Priority.FIRST, e -> seen.add("c"));
        events.on(Event.class, e -> seen.add("d"));
        events.on(Decision.class, e -> seen.add("e"));
        bus.dispatch(gold());
        assertEquals(List.of("c", "a", "b", "d", "e"), seen,
                     "the index reordered listeners inside one priority");
    }

    @Test
    void theResolvedListIsRebuiltWhenSomethingIsRegisteredLater() {
        Bus bus = new Bus();
        Events events = events(bus, "late");
        List<String> seen = new ArrayList<>();
        events.on(Gold.class, e -> seen.add("early"));
        bus.dispatch(gold());
        events.on(Event.class, e -> seen.add("late"));
        bus.dispatch(gold());
        assertEquals(List.of("early", "early", "late"), seen);
    }

    // --- a listener that keeps throwing ---------------------------------

    public static final class Broken {

        int calls;

        @Subscribe(priority = Priority.FIRST)
        public void fail(Gold event) {
            calls++;
            throw new IllegalStateException("no");
        }
    }

    public static final class Survivor {

        int calls;

        @Subscribe
        public void fine(Gold event) {
            calls++;
        }
    }

    @Test
    void aListenerThatKeepsFailingIsDroppedAndTheRestCarryOn() {
        Broken broken = new Broken();
        Survivor survivor = new Survivor();
        Bus bus = new Bus();
        bus.register("broken", broken);
        bus.register("survivor", survivor);
        for (int i = 0; i < 5; i++) {
            bus.dispatch(gold());
        }
        assertEquals(3, broken.calls, "a failing listener should be dropped after three");
        assertEquals(5, survivor.calls,
                     "dropping a listener disturbed the ones after it");
    }

    // --- the lambda path ------------------------------------------------

    @Test
    void aLambdaListenerHonoursPriority() {
        Bus bus = new Bus();
        Events events = events(bus, "lambda");
        List<Priority> seen = new ArrayList<>();
        events.on(Gold.class, Priority.LAST, e -> seen.add(Priority.LAST));
        events.on(Gold.class, e -> seen.add(Priority.NORMAL));
        events.on(Gold.class, Priority.FIRST, e -> seen.add(Priority.FIRST));
        bus.dispatch(gold());
        assertEquals(List.of(Priority.FIRST, Priority.NORMAL, Priority.LAST), seen);
    }

    @Test
    void aLambdaListenerHonoursIgnoreVetoed() {
        Bus bus = new Bus();
        Events events = events(bus, "lambda");
        List<String> seen = new ArrayList<>();
        events.decide(Gold.class, Priority.FIRST, e -> Gold.Mutation.veto());
        events.on(Gold.class, Priority.NORMAL, true, e -> seen.add("careful"));
        events.on(Gold.class, e -> seen.add("careless"));
        bus.dispatch(gold());
        assertEquals(List.of("careless"), seen);
    }

    @Test
    void aLambdaMonitorChangesNothingEither() {
        Bus bus = new Bus();
        Events events = events(bus, "lambda");
        events.decide(Gold.class, e -> Gold.Mutation.of(200));
        events.decide(Gold.class, Priority.MONITOR, e -> Gold.Mutation.veto());
        Gold event = gold();
        bus.dispatch(event);
        assertFalse(event.vetoed(), "a lambda monitor vetoed the event");
        assertEquals(Map.of("delta", "200"), Fold.verdict(event));
    }

    @Test
    void aLambdaCanBeUnregisteredAgain() {
        Bus bus = new Bus();
        Events events = events(bus, "lambda");
        List<String> seen = new ArrayList<>();
        Handle handle = events.on(Gold.class, e -> seen.add("once"));
        events.on(Gold.class, e -> seen.add("always"));
        bus.dispatch(gold());
        handle.unregister();
        handle.unregister();
        bus.dispatch(gold());
        assertEquals(List.of("once", "always", "always"), seen);
    }

    @Test
    void unregisteringFromInsideADispatchDoesNotDisturbIt() {
        Bus bus = new Bus();
        Events events = events(bus, "lambda");
        List<String> seen = new ArrayList<>();
        Handle[] self = new Handle[1];
        self[0] = events.on(Gold.class, e -> {
            seen.add("self");
            self[0].unregister();
        });
        events.on(Gold.class, e -> seen.add("after"));
        bus.dispatch(gold());
        bus.dispatch(gold());
        assertEquals(List.of("self", "after", "after"), seen);
    }

    // --- more than one thread -------------------------------------------
    //
    // Dispatch is one thread, but events.on(...) and Handle.unregister() are
    // public API and a mod may call either from a thread of its own. Nothing
    // below sleeps: the handshakes are latches, and the churn test asserts
    // properties that hold under every interleaving rather than a timing.

    /** Remembers the id of every event it was handed, in the order handed. */
    private static final class Seen {

        // Only the dispatch thread writes this, and the assertions read it
        // after joining that thread.
        final List<Long> ids = new ArrayList<>();

        void record(Gold event) {
            ids.add(event.value());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "the other thread never got there");
        } catch (InterruptedException stop) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stop);
        }
    }

    @Test
    void unregisteringFromAnotherThreadWhileAListenerRunsTakesEffectAtOnce()
            throws InterruptedException {
        Bus bus = new Bus();
        Events events = events(bus, "cross");
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch gone = new CountDownLatch(1);
        // The dispatch parks inside the first listener. The other thread takes
        // the second one off while it is parked there.
        events.on(Gold.class, Priority.FIRST, e -> {
            seen.add("first");
            inside.countDown();
            await(gone);
        });
        Handle later = events.on(Gold.class, e -> seen.add("later"));
        Thread remover = new Thread(() -> {
            await(inside);
            later.unregister();
            gone.countDown();
        }, "remover");
        remover.start();
        bus.dispatch(gold());
        remover.join();
        assertEquals(List.of("first"), seen,
                     "a listener unregistered mid-dispatch was called anyway");
        assertEquals(1, bus.size(), "the dropped listener was not swept");
    }

    @Test
    void churnFromOtherThreadsLosesAndDuplicatesNothing() throws InterruptedException {
        int rounds = 4000;
        int churners = 4;
        int ops = 500;
        // Each churner holds a rolling window of live handles, so there are
        // always listeners being added and taken off around the dispatch
        // rather than a pair that never overlaps it.
        int window = 8;

        Bus bus = new Bus();
        Events events = events(bus, "race");
        Seen always = new Seen();
        events.on(Gold.class, always::record);

        Queue<Seen> transients = new ConcurrentLinkedQueue<>();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);

        Thread dispatcher = new Thread(() -> {
            await(start);
            for (int i = 0; i < rounds; i++) {
                bus.dispatch(gold(i));
            }
        }, "dispatch");

        List<Thread> churn = new ArrayList<>();
        for (int t = 0; t < churners; t++) {
            churn.add(new Thread(() -> {
                await(start);
                Handle[] live = new Handle[window];
                for (int i = 0; i < ops; i++) {
                    Seen seen = new Seen();
                    transients.add(seen);
                    Handle old = live[i % window];
                    if (old != null) {
                        old.unregister();
                        old.unregister();
                    }
                    live[i % window] = events.on(Gold.class, seen::record);
                }
                for (Handle handle : live) {
                    if (handle != null) {
                        handle.unregister();
                    }
                }
            }, "churn-" + t));
        }

        List<Thread> all = new ArrayList<>(churn);
        all.add(dispatcher);
        for (Thread thread : all) {
            thread.setUncaughtExceptionHandler((who, failure) -> errors.add(failure));
            thread.start();
        }
        start.countDown();
        for (Thread thread : all) {
            thread.join();
        }

        assertEquals(List.of(), errors, "a thread died: " + errors);

        // The listener that was there the whole time saw every event exactly
        // once, in order. That is the loss-and-duplication check.
        List<Long> expected = new ArrayList<>(rounds);
        for (long i = 0; i < rounds; i++) {
            expected.add(i);
        }
        assertEquals(expected, always.ids, "the permanent listener lost or repeated events");

        // Every other listener saw a consecutive run: none before it was
        // registered, none after it was dropped, and no gap or repeat in
        // between. A corrupted bucket or a stale merged list shows up here.
        long delivered = 0;
        for (Seen seen : transients) {
            List<Long> ids = seen.ids;
            delivered += ids.size();
            for (int i = 0; i < ids.size(); i++) {
                assertEquals(ids.get(0) + i, ids.get(i),
                             "a listener saw " + ids + ", which is not one unbroken run");
            }
        }
        assertTrue(delivered > 0, "no transient listener saw anything; the test proved nothing");

        assertEquals(1, bus.size(), "every transient listener should have been swept");
        bus.dispatch(gold(rounds));
        assertEquals(rounds + 1, always.ids.size());
        for (Seen seen : transients) {
            assertFalse(seen.ids.contains((long) rounds), "an unregistered listener still ran");
        }
    }
}
