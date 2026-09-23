package dev.ancaria.coderpack.api.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

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

    public record Slot(int index, int level) {

        public boolean empty() {
            return level == 0;
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
        return slot >= 0 && slot < slots.size() ? slots.get(slot).level() : 0;
    }

    public int size() {
        return slots.size();
    }

    /** Unspent skill points. */
    public int points() {
        return points;
    }

    @Override
    @Nonnull
    public Iterator<Slot> iterator() {
        return slots.iterator();
    }
}
