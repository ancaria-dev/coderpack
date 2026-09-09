package dev.ancaria.coderpack.zygote;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The half of the stdout arrangement that a refactor could quietly undo.
 *
 * <p>{@code Main.claimStdout} sends {@code System.out} to stderr so no mod can
 * splice itself into a frame. That only works while frames go out on the file
 * descriptor instead. A {@code Pipe} simplified to {@code System.out} would
 * compile, print every frame to stderr, and answer the host nothing.
 */
class PipeTest {

    @Test
    void framesGoOutPastSystemOut() {
        PrintStream real = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            new Pipe().write(new Frame("END", 7, "", Map.of("ok", "1")));
        } finally {
            System.setOut(real);
        }
        assertEquals("", captured.toString(StandardCharsets.UTF_8));
    }
}
