package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * The game finished writing a save. The moment for a mod to write whatever it
 * keeps per save next to it, under {@code gameDir()}, keyed by {@link #slot()}.
 */
public final class Save extends Event {

    public Save(Map<String, String> fields) {
        super(fields);
    }

    /** The save slot. 0 is the quicksave. */
    public int slot() {
        return (int) num("slot");
    }

    /** The name the save dialog shows for it. */
    @Nullable
    public String name() {
        return text("name");
    }

    /** The file, relative to the game directory, e.g. SAVE/GAME03.PAK. */
    @Nullable
    public String path() {
        return text("path");
    }

    /** False when the game reported the save as failed. */
    public boolean ok() {
        return num("ok") == 1;
    }
}
