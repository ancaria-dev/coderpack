package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.Veto;
import dev.ancaria.coderpack.api.event.World;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Coderpack's entry point.
 *
 * <p>Two threads on purpose. The reader only parses and routes, so it is always
 * free to complete a command's reply. The dispatcher runs mod code, which may
 * block on a command. Doing both on one thread deadlocks the moment a mod
 * calls the game from inside an event handler, which is the normal thing to
 * do.
 */
public final class Main {

    /** Bounded so a slow mod costs events, not memory. Verdicts are never dropped. */
    private static final int QUEUE = 4096;

    private static final Bus BUS = new Bus();
    private static final Pipe PIPE = new Pipe();
    private static final GameLink GAME = new GameLink(PIPE);
    private static final BlockingQueue<Frame> QUEUED = new ArrayBlockingQueue<>(QUEUE);

    private static long dropped;
    private static volatile boolean working;

    public static void main(String[] args) throws Exception {
        Path mods = Path.of(argument(args, "--mods", "mods")).toAbsolutePath().normalize();
        // Mods live in <Sacred Gold>/mods, so the game folder is one level up.
        // A mod writing a file needs that, not the working directory, which
        // belongs to whoever started the host.
        Path game = mods.getParent() == null ? mods : mods.getParent();
        // The launcher passes the ticked boxes. Without it, everything loads.
        String only = argument(args, "--enable", null);
        Set<String> enabled = only == null
                ? null
                : Set.of(only.isEmpty() ? new String[0] : only.split(","));
        Mods.loadAll(mods, game, enabled, BUS, GAME);

        Thread dispatcher = new Thread(Main::dispatchLoop, "sal-dispatch");
        dispatcher.setDaemon(true);
        dispatcher.start();

        Frame frame;
        while ((frame = PIPE.read()) != null) {
            switch (frame.verb()) {
                case "RES" -> GAME.complete(frame);
                case "BYE" -> {
                    Log.info("Host said goodbye.");
                    drain();
                    quit();
                }
                // A verdict the game is waiting for must never be dropped.
                case "ASK" -> QUEUED.put(frame);
                default -> offer(frame);
            }
        }
        drain();
        Log.info("Host closed the pipe.");
        quit();
    }

    /**
     * Ends the JVM rather than returning from main.
     *
     * <p>A mod is allowed to start threads, and one that starts a non-daemon
     * thread (a window, a timer, a server) keeps this process alive forever
     * once main returns. That was not theoretical: the first mod to open a
     * JavaFX window left a JVM behind after every run, holding its own jar open
     * so the next build could not replace it. The host outlives nothing here,
     * and shutdown hooks still run, so a tracer still flushes what it buffered.
     */
    private static void quit() {
        System.exit(0);
    }

    private static void offer(Frame frame) {
        if (!QUEUED.offer(frame) && ++dropped % 100 == 1) {
            Log.warn("Dropped " + dropped + (dropped == 1 ? " event" : " events")
                    + " because dispatch fell behind.");
        }
    }

    /** Finishes what has already arrived before shutting down. Events in flight
     *  are events a mod was promised, and a verdict still owed is worse. */
    private static void drain() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && (!QUEUED.isEmpty() || working)) {
            Thread.sleep(5);
        }
    }

    private static void dispatchLoop() {
        while (true) {
            try {
                Frame frame = QUEUED.take();
                working = true;
                handle(frame);
                working = false;
            } catch (InterruptedException stop) {
                return;
            } catch (Throwable failure) {
                Log.error("dispatch", failure);
            } finally {
                working = false;
            }
        }
    }

    private static void handle(Frame frame) {
        boolean asked = "ASK".equals(frame.verb());
        GAME.playerLink().observeFields(frame.name(), frame.fields());

        // Never null: an unmapped wire event still arrives, as an Unknown.
        Event event = Registry.build(frame);
        GAME.playerLink().observe(event);
        if (event instanceof World world && world.phase() != World.Phase.LOADED) {
            if (world.phase() == World.Phase.HERO_TERMINATED
                    || world.phase() == World.Phase.DETACHED) {
                GAME.playerLink().clear();
            }
        }

        BUS.dispatch(event);

        if (!asked) {
            return;
        }
        PIPE.write(event instanceof Veto veto
                ? Verdict.of(frame.seq(), veto)
                : Verdict.ok(frame.seq()));
    }

    private static String argument(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
