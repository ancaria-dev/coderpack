package dev.ancaria.coderpack.zygote;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * The channel to the host: frames in one direction, verdicts and commands back.
 *
 * <p>Two transports, and which one is in use decides how careful everything
 * else has to be. {@link #over} is what a player runs: a named pipe the host
 * created for this JVM alone, on which nothing but frames can ever appear.
 * {@link #overStdio} is the original arrangement, kept for the test harnesses
 * that drive Coderpack by hand and for a host too old to offer a pipe. There
 * stdout is the wire, so anything else printed on it lands in the host's
 * parser, and {@code Main.claimStdout} exists to keep mods away from it.
 */
public final class Pipe {

    private final BufferedReader in;
    private final PrintStream out;
    /**
     * The object that opened the handle both streams sit on, held for as long
     * as they are. Never read: reachability is the whole point, because a
     * collected owner is a closed handle.
     */
    private final AutoCloseable owner;

    /** Package-private rather than private so a test can supply both ends. */
    Pipe(InputStream in, OutputStream out, AutoCloseable owner) {
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        // Buffered and flushed once per frame rather than autoflush: a frame
        // then costs the host one read, which is what the old unbuffered
        // stream happened to give and this must not give up.
        this.out = new PrintStream(new BufferedOutputStream(out), false,
                                   StandardCharsets.UTF_8);
        this.owner = owner;
    }

    /**
     * The dedicated channel, named by the host on the command line.
     *
     * <p>Windows hands a named pipe out as an ordinary file, so opening one
     * duplex takes no native code. Both streams share that one handle, and the
     * {@link RandomAccessFile} is kept because it owns it.
     */
    public static Pipe over(String name) throws IOException {
        RandomAccessFile handle = new RandomAccessFile(name, "rw");
        FileDescriptor fd = handle.getFD();
        return new Pipe(new FileInputStream(fd), new FileOutputStream(fd), handle);
    }

    /** stdin and stdout, the way the host spoke before it offered a pipe. */
    public static Pipe overStdio() {
        // Past System.out on purpose. Main points that at stderr, and a frame
        // written through it would go to the console instead of the host.
        return new Pipe(System.in, new FileOutputStream(FileDescriptor.out), null);
    }

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
        // A literal newline rather than println: the line terminator is part of
        // the wire format, and it is not this machine's business what that is.
        out.print(frame.encode());
        out.print('\n');
        out.flush();
    }
}
