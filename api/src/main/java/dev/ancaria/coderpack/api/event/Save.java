package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * The game finished writing a save. The moment for a mod to write whatever it
 * keeps per save next to it, under {@code gameDir()}, keyed by {@link #getSlot()}.
 */
public final class Save extends Event {

    public Save(Map<String, String> fields) {
        super(fields);
    }

    /** The save slot. 0 is the quicksave. */
    public int getSlot() {
        return (int) getNum("slot");
    }

    /** The name the save dialog shows for it. */
    @Nullable
    public String getName() {
        return getText("name");
    }

    /** The file, relative to the game directory, e.g. SAVE/GAME03.PAK. */
    @Nullable
    public String getPath() {
        return getText("path");
    }

    /** False when the game reported the save as failed. */
    public boolean isOk() {
        return getNum("ok") == 1;
    }
}
