package dev.ancaria.coderpack.zygote;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What the two ends of a frame have to look like, on either transport. */
class PipeTest {

    private static Frame frame() {
        return new Frame("END", 7, "", Map.of("ok", "1"));
    }

    /**
     * The half of the stdio transport that a refactor could quietly undo.
     *
     * <p>{@code Main.claimStdout} sends {@code System.out} to stderr so no mod
     * can splice itself into a frame. That only works while frames go out on
     * the file descriptor instead. A {@code Pipe} simplified to
     * {@code System.out} would compile, print every frame to stderr, and answer
     * the host nothing.
     */
    @Test
    void framesGoOutPastSystemOut() {
        PrintStream real = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            Pipe.overStdio().write(frame());
        } finally {
            System.setOut(real);
        }
        assertEquals("", captured.toString(StandardCharsets.UTF_8));
    }

    /**
     * One frame, one line, one {@code \n}. The host splits on that byte and
     * this machine's idea of a line separator does not come into it, which a
     * {@code println} would have decided differently on Windows.
     */
    @Test
    void framesEndWithOneNewline() {
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        new Pipe(InputStream.nullInputStream(), written, null).write(frame());
        assertEquals("END 7 ok=1\n", written.toString(StandardCharsets.UTF_8));
    }
}
