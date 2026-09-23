package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * A save file is being read, or has been. Arrives twice per load: once as it
 * starts and once as it ends, and {@link World} phases happen in between. A
 * new game reads a template file too, and says so through {@link #fresh()}.
 */
public final class Load extends Event {

    private final boolean done;

    public Load(boolean done, Map<String, String> fields) {
        super(fields);
        this.done = done;
    }

    /** False as the load starts, true once it has returned. */
    public boolean done() {
        return done;
    }

    /** The save slot, 0 for the quicksave, or -1 when the file is not a slot. */
    public int slot() {
        return (int) num("slot");
    }

    /** A new game rather than a saved one. */
    public boolean fresh() {
        return num("fresh") == 1;
    }

    @Nullable
    public String path() {
        return text("path");
    }
}
