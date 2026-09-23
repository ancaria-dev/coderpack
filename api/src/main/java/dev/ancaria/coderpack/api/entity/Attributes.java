package dev.ancaria.coderpack.api.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The hero's six attributes as they were when asked, and the points left to
 * spend. A snapshot: ask {@link Player#getAttributes()} again for fresh numbers.
 *
 * <pre>{@code
 * int strength = player.getAttributes().get(Attributes.Kind.STRENGTH);
 * player.getAttributes().forEach(a -> context.log(a.getKind() + " " + a.getValue()));
 * }</pre>
 */
public final class Attributes implements Iterable<Attributes.Entry> {

    /** In the game's own order, which is also the index events report. */
    public enum Kind {
        STRENGTH, ENDURANCE, DEXTERITY, PHYSICAL_REGENERATION, MENTAL_REGENERATION, CHARISMA;

        /** 0 Strength .. 5 Charisma, as {@code Attribute.getIndex()} reports it. */
        public int getIndex() {
            return ordinal();
        }

        /** @throws ArrayIndexOutOfBoundsException for anything outside 0..5 */
        @Nonnull
        public static Kind of(int index) {
            return values()[index];
        }
    }

    /** One attribute and its value. */
    public static final class Entry {

        private final Kind kind;
        private final int value;

        public Entry(Kind kind, int value) {
            this.kind = kind;
            this.value = value;
        }

        @Nonnull
        public Kind getKind() {
            return kind;
        }

        public int getValue() {
            return value;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Entry that && kind == that.kind && value == that.value;
        }

        @Override
        public int hashCode() {
            return 31 * kind.hashCode() + value;
        }

        @Override
        @Nonnull
        public String toString() {
            return kind + "=" + value;
        }
    }

    private final List<Entry> entries;
    private final int points;

    public Attributes(Map<String, String> fields) {
        String[] values = split(fields.get("values"));
        List<Entry> out = new ArrayList<>(Kind.values().length);
        for (Kind kind : Kind.values()) {
            int value = kind.ordinal() < values.length ? parse(values[kind.ordinal()]) : 0;
            out.add(new Entry(kind, value));
        }
        this.entries = Collections.unmodifiableList(out);
        this.points = parse(fields.get("points"));
    }

    public int get(Kind kind) {
        return entries.get(kind.ordinal()).getValue();
    }

    /** Unspent attribute points. */
    public int getPoints() {
        return points;
    }

    @Override
    @Nonnull
    public Iterator<Entry> iterator() {
        return entries.iterator();
    }

    @Nonnull
    static String[] split(@Nullable String packed) {
        return packed == null || packed.isEmpty() ? new String[0] : packed.split(",");
    }

    static int parse(@Nullable String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
