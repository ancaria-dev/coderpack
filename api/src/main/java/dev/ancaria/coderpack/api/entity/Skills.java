package dev.ancaria.coderpack.api.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The hero's skill slots as they were when asked, and the points left to
 * spend. A snapshot, like {@link Attributes}.
 *
 * <p>Slots, not skills. The set of skills differs per class and per character,
 * and which skill sits in which slot is not something the game stores next to
 * the level, so a slot is an index in the order the character screen lists
 * them. An empty slot reads 0.
 */
public final class Skills implements Iterable<Skills.Slot> {

    /** One slot and the level in it. */
    public static final class Slot {

        private final int index;
        private final int level;

        public Slot(int index, int level) {
            this.index = index;
            this.level = level;
        }

        public int getIndex() {
            return index;
        }

        public int getLevel() {
            return level;
        }

        public boolean isEmpty() {
            return level == 0;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Slot that && index == that.index && level == that.level;
        }

        @Override
        public int hashCode() {
            return 31 * index + level;
        }

        @Override
        @Nonnull
        public String toString() {
            return "Slot[index=" + index + ", level=" + level + "]";
        }
    }

    private final List<Slot> slots;
    private final int points;

    public Skills(Map<String, String> fields) {
        String[] levels = Attributes.split(fields.get("levels"));
        List<Slot> out = new ArrayList<>(levels.length);
        for (int i = 0; i < levels.length; i++) {
            out.add(new Slot(i, Attributes.parse(levels[i])));
        }
        this.slots = Collections.unmodifiableList(out);
        this.points = Attributes.parse(fields.get("points"));
    }

    /** The level in a slot, or 0 for an empty or unknown one. */
    public int get(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot).getLevel() : 0;
    }

    public int size() {
        return slots.size();
    }

    /** Unspent skill points. */
    public int getPoints() {
        return points;
    }

    @Override
    @Nonnull
    public Iterator<Slot> iterator() {
        return slots.iterator();
    }
}
