package dev.ancaria.coderpack.zygote;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * {@code <game>/logs/mods.log}, the file every mod's {@code context.log} lands
 * in. One writer for all of them.
 *
 * <p>A log call never waits for the disk. It puts the line on a queue and
 * returns, whichever thread it came from: the dispatch thread with the game
 * stopped behind it, or a thread the mod started itself. One daemon thread
 * drains that queue and writes what it found as one batch, flushed per batch,
 * and wakes on a short timer rather than per line, so a mod logging in a loop
 * costs one flush every {@value #IDLE_MS} ms and not one per call.
 *
 * <p>The queue is bounded. A mod that logs faster than the disk takes it loses
 * lines, and the file says how many, rather than holding the dispatch thread
 * or growing without limit. A disk that refuses the file loses lines too, with
 * one warning on stderr; nothing here ever throws into a mod.
 *
 * <p>The tracer mod's {@code Sink} is the same shape, one daemon thread and a
 * bounded buffer; this is its loader-side twin.
 */
final class ModLog {

    /** {@code [2026-09-23 14:05:31.042] [my-mod]: message}. */
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final int CAPACITY = 65_536;
    private static final long IDLE_MS = 50;
    private static final long RETRY_MS = 1_000;

    private final Path file;
    private final int capacity;
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
    // ConcurrentLinkedQueue.size() walks the whole queue, so the bound is kept
    // beside it rather than asked of it.
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    // The part of dropped the file has not been told about yet.
    private final AtomicLong unreported = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();

    // Written by the writer thread only; read by flush() on another.
    private volatile long settled;
    private volatile boolean running = true;
    private volatile Thread thread;

    private BufferedWriter out;
    private long retryAt;
    private boolean warned;

    ModLog(Path file, int capacity) {
        this.file = file;
        this.capacity = capacity;
    }

    /** The shared log under {@code gameDir}, already writing. */
    static ModLog open(Path gameDir) {
        ModLog log = new ModLog(gameDir.resolve("logs").resolve("mods.log"), CAPACITY);
        log.start();
        // Main ends the JVM with System.exit, and a hook is what still runs then.
        Runtime.getRuntime().addShutdownHook(new Thread(log::close, "sal-log-close"));
        return log;
    }

    /** One formatted line, as {@link #write} stores it. */
    static String line(String mod, String message) {
        return "[" + TIME.format(LocalDateTime.now()) + "] [" + mod + "]: " + message;
    }

    Path file() {
        return file;
    }

    void start() {
        Thread writer = new Thread(this::run, "sal-log");
        writer.setDaemon(true);
        thread = writer;
        writer.start();
    }

    /** Queues one line. Never blocks and never throws. */
    void write(String line) {
        if (!running) {
            dropped.incrementAndGet();
            return;
        }
        if (queued.incrementAndGet() > capacity) {
            queued.decrementAndGet();
            dropped.incrementAndGet();
            unreported.incrementAndGet();
            return;
        }
        accepted.incrementAndGet();
        queue.add(line);
    }

    /** Lines lost so far to a full queue or a closed log. */
    long dropped() {
        return dropped.get();
    }

    /**
     * Waits until every line queued before this call has been written, or
     * given up on, for at most {@code millis}. For shutdown and for tests.
     *
     * @return true when everything made it out in time
     */
    boolean flush(long millis) {
        long target = accepted.get();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (settled < target) {
            Thread writer = thread;
            if (writer == null || !writer.isAlive() || System.nanoTime() > deadline) {
                return false;
            }
            LockSupport.unpark(writer);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
        }
        return true;
    }

    /** Writes what is queued, then stops the writer. */
    void close() {
        flush(2_000);
        running = false;
        Thread writer = thread;
        if (writer != null) {
            LockSupport.unpark(writer);
            try {
                writer.join(500);
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        try {
            while (running) {
                if (!drain()) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(IDLE_MS));
                }
            }
            drain();
        } finally {
            closeFile();
        }
    }

    /** One batch. False when there was nothing to write. */
    private boolean drain() {
        List<String> batch = new ArrayList<>();
        String line;
        while ((line = queue.poll()) != null) {
            batch.add(line);
        }
        long lost = unreported.getAndSet(0);
        if (batch.isEmpty() && lost == 0) {
            return false;
        }
        queued.addAndGet(-batch.size());
        try {
            BufferedWriter writer = writer();
            if (writer == null) {
                dropped.addAndGet(batch.size());
                return true;
            }
            if (lost > 0) {
                writer.write(line("coderpack", "Dropped " + lost
                        + (lost == 1 ? " log line" : " log lines")
                        + " because mods logged faster than the disk took them."));
                writer.newLine();
            }
            for (String each : batch) {
                writer.write(each);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException failure) {
            fail(failure);
            dropped.addAndGet(batch.size());
        } finally {
            settled += batch.size();
        }
        return true;
    }

    private BufferedWriter writer() {
        if (out != null) {
            return out;
        }
        if (System.currentTimeMillis() < retryAt) {
            return null;
        }
        try {
            Files.createDirectories(file.getParent());
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return out;
        } catch (IOException failure) {
            fail(failure);
            return null;
        }
    }

    /** Says so once, drops the handle, and tries the file again a second later. */
    private void fail(IOException failure) {
        if (!warned) {
            warned = true;
            Log.warn("Couldn’t write " + file + ", so mod log lines are being lost: " + failure);
        }
        closeFile();
        retryAt = System.currentTimeMillis() + RETRY_MS;
    }

    private void closeFile() {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException ignored) {
            // Already failing; the warning has been given.
        }
        out = null;
    }
}
