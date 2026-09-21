package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.Decision;
import dev.ancaria.coderpack.api.event.World;

import java.io.IOException;
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
    private static final BlockingQueue<Frame> QUEUED = new ArrayBlockingQueue<>(QUEUE);

    // Assigned once, first thing in main, and never again. Not final only
    // because which transport to open is an argument, and arguments are not
    // available while this class initialises.
    private static Pipe PIPE;
    private static GameLink GAME;

    private static long dropped;
    private static volatile boolean working;

    public static void main(String[] args) throws Exception {
        claimStdout();
        connect(argument(args, "--pipe", null));
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

    /**
     * Opens the channel to the host, before anything can want to use it.
     *
     * <p>{@code --pipe} names a Windows named pipe the host created for this
     * JVM alone, and that is what the launcher always starts. Without it the
     * host is either an older one or a test harness driving Coderpack through
     * stdin and stdout by hand, so that arrangement stays available. Failing
     * to open a pipe the host did name is fatal on purpose: falling back would
     * put frames on stdout again, quietly, on the one path where nothing else
     * is guarding it.
     */
    private static void connect(String name) throws IOException {
        if (name == null) {
            PIPE = Pipe.overStdio();
        } else {
            PIPE = Pipe.over(name);
            Log.info("Connected to the host on " + name);
        }
        GAME = new GameLink(PIPE);
    }

    /**
     * Points {@code System.out} at stderr before a single mod class is loaded.
     *
     * <p>On a named pipe this changes nothing, and it is still done, because
     * the stdio transport is one missing argument away and the damage there is
     * quiet. stdout is the wire in that mode, and a mod calling {@code println}
     * shares neither {@link Pipe}'s stream nor its lock. The loss is not the
     * stray line, which the host reports and skips. It is the frame the stray
     * line splices itself into: a verdict that never arrives leaves the game
     * thread waiting for the host's fallback, and a command that never arrives
     * costs the dispatch thread its full two-second timeout.
     *
     * <p>This also covers a mod that brings a logging framework, which is the
     * likelier way to hit it. Log4j2 and Logback both aim their console
     * appender at {@code System.out} by default, and both read it when they
     * configure themselves, on that mod's first call to a logger. That is
     * necessarily later than this, so both land on stderr with nothing to set.
     */
    private static void claimStdout() {
        System.setOut(System.err);
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
        PIPE.write(event instanceof Decision decision
                ? Verdict.of(frame.seq(), decision)
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
