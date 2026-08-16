package dev.ancaria.coderpack.zygote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * stdin carries frames from the host, stdout carries verdicts and commands back.
 * Nothing else may write to stdout -- a stray println would be parsed as a
 * frame. Mod logging goes to stderr, which the host prints as-is.
 */
public final class Pipe {

    private final BufferedReader in =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final PrintStream out =
            new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),
                            true, StandardCharsets.UTF_8);

    /** Next frame, or null at end of stream. Unparseable lines are skipped. */
    public Frame read() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            Frame frame = Frame.parse(line);
            if (frame != null) {
                return frame;
            }
            Log.warn("ignoring unparseable line: " + line);
        }
        return null;
    }

    public synchronized void write(Frame frame) {
        out.println(frame.encode());
    }
}
