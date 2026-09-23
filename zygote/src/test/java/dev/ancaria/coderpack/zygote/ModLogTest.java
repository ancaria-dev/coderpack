package dev.ancaria.coderpack.zygote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared mods.log writer: its line format, its bound, and many threads at once. */
class ModLogTest {

    @TempDir
    Path game;

    @Test
    void aLineIsTimeThenModThenMessage() {
        String line = ModLog.line("my-mod", "hello: world");
        assertTrue(line.matches(
                "\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}] \\[my-mod]: hello: world"),
                line);
    }

    @Test
    void theFileAndItsFolderAreCreated() throws Exception {
        ModLog log = new ModLog(game.resolve("logs").resolve("mods.log"), 1024);
        log.start();
        log.write(ModLog.line("a", "one"));
        assertTrue(log.flush(10_000));
        log.close();
        List<String> lines = Files.readAllLines(game.resolve("logs/mods.log"), StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).endsWith("[a]: one"), lines.get(0));
    }

    @Test
    void manyThreadsLoseNothingAndThrowNothing() throws Exception {
        int threads = 16;
        int each = 5_000;
        ModLog log = new ModLog(game.resolve("logs").resolve("mods.log"), threads * each);
        log.start();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> all = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            String mod = "mod-" + t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException stop) {
                    return;
                }
                for (int i = 0; i < each; i++) {
                    log.write(ModLog.line(mod, "line " + i));
                }
            }, mod);
            thread.setUncaughtExceptionHandler((who, failure) -> errors.add(failure));
            all.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : all) {
            thread.join();
        }
        assertTrue(log.flush(30_000), "the writer did not catch up");
        log.close();

        assertEquals(List.of(), errors);
        assertEquals(0, log.dropped());
        List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
        assertEquals(threads * each, lines.size());
        Set<String> distinct = new HashSet<>();
        for (String line : lines) {
            distinct.add(line.substring(line.indexOf("] [") + 2));
        }
        assertEquals(threads * each, distinct.size(), "a line was duplicated or mangled");
        // Per thread the order is the order it logged in.
        for (int t = 0; t < threads; t++) {
            String prefix = "[mod-" + t + "]: line ";
            int expected = 0;
            for (String line : lines) {
                int at = line.indexOf(prefix);
                if (at >= 0) {
                    assertEquals(expected++, Integer.parseInt(line.substring(at + prefix.length())));
                }
            }
            assertEquals(each, expected);
        }
    }

    @Test
    void aFullQueueDropsAndCountsInsteadOfBlocking() throws Exception {
        ModLog log = new ModLog(game.resolve("logs").resolve("mods.log"), 10);
        // Not started yet, so nothing drains and the bound is what decides.
        for (int i = 0; i < 15; i++) {
            log.write(ModLog.line("flood", "line " + i));
        }
        assertEquals(5, log.dropped());
        log.start();
        assertTrue(log.flush(10_000));
        log.close();
        List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
        assertEquals(11, lines.size(), String.join("\n", lines));
        assertTrue(lines.get(0).contains("[coderpack]: Dropped 5 log lines"), lines.get(0));
    }

    @Test
    void anUnwritableFileNeverThrowsIntoTheCaller() throws Exception {
        // A directory where the file should be: every open fails.
        Path file = game.resolve("logs").resolve("mods.log");
        Files.createDirectories(file);
        ModLog log = new ModLog(file, 1024);
        log.start();
        log.write(ModLog.line("a", "lost"));
        assertTrue(log.flush(10_000));
        log.close();
        assertEquals(1, log.dropped());
    }
}
