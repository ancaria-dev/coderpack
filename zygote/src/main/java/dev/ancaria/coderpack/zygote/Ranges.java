package dev.ancaria.coderpack.zygote;

import java.util.ArrayList;
import java.util.List;

/**
 * The version notation a mod's descriptor uses to say which loader it runs on.
 *
 * <p>A descriptor carries two of these, {@code api} and {@code loader}, and both
 * are ranges rather than numbers, which is the whole point: a mod that works on
 * API 1 and on API 2 has no way to say so with a number, and a mod that needs a
 * fix released in 0.2.0 has no way to say that either. The syntax is Maven's,
 * the one NeoForge writes in a {@code mods.toml}:
 *
 * <pre>
 * [1,2)        1 or newer, below 2 -- one major, the usual thing to write
 * [1.0,)       1.0 or newer, no upper end
 * (,1.5]       anything up to and including 1.5
 * [1.2]        that version and nothing else
 * [1,2),[3,4)  either of those, and nothing in between
 * 1            the same as [1]: that version and nothing else
 * </pre>
 *
 * <p>Square brackets include the end, round ones exclude it, and an end left
 * blank is no end at all.
 *
 * <p><b>One deliberate difference from Maven.</b> A bare {@code 1} in Maven is a
 * <em>soft</em> requirement, satisfied by any version at all. That answer is
 * useless here and dangerous: the question this class exists to answer is
 * whether a mod will run, and "any" is the one reply that is never true. A bare
 * version therefore means exactly that version, which is also what every
 * {@code api = "1"} written before ranges existed meant.
 *
 * <p><b>Versions</b> are dotted numbers with an optional qualifier after the
 * first {@code -} or {@code +}. Missing parts count as zero, so {@code 1} and
 * {@code 1.0.0} are the same version. A qualifier sorts <em>before</em> the
 * version without one, because {@code 1.0.0-rc1} comes before {@code 1.0.0};
 * two qualifiers are compared as text, which is not clever and does not pretend
 * to be. Anything that is not that shape is unknown rather than zero, and an
 * unknown version satisfies nothing.
 *
 * <p>The same notation is implemented twice more and there is no shared source
 * any of the three could read: {@code pin} in the launcher, which decides what a
 * player is offered, and {@code Ranges} in the build repository's linter, which
 * refuses a jar before it is published. A change here belongs in both.
 */
final class Ranges {

    private Ranges() {
    }

    /** Reads a version. An unreadable one comes back unknown rather than throwing. */
    static Version version(String text) {
        return new Version(text);
    }

    /**
     * Reads a range.
     *
     * @throws IllegalArgumentException with a message meant for whoever wrote
     *                                  the line, rather than one naming a
     *                                  production in a grammar
     */
    static Range range(String text) {
        return new Range(text);
    }

    /** One release, in a form that can be ordered. */
    static final class Version implements Comparable<Version> {

        private final List<Integer> parts = new ArrayList<>();
        private final String qualifier;
        private final String text;

        Version(String raw) {
            String trimmed = strip(raw);
            this.text = trimmed;
            int at = -1;
            for (int i = 0; i < trimmed.length(); i++) {
                if (trimmed.charAt(i) == '-' || trimmed.charAt(i) == '+') {
                    at = i;
                    break;
                }
            }
            String head = at < 0 ? trimmed : trimmed.substring(0, at);
            this.qualifier = at < 0 ? "" : trimmed.substring(at + 1);
            if (head.isEmpty()) {
                return;
            }
            List<Integer> read = new ArrayList<>();
            for (String field : head.split("\\.", -1)) {
                if (field.isEmpty() || !field.chars().allMatch(Character::isDigit)) {
                    return;
                }
                try {
                    read.add(Integer.valueOf(field));
                } catch (NumberFormatException huge) {
                    return;
                }
            }
            parts.addAll(read);
        }

        /** Whether this is a version at all. */
        boolean known() {
            return !parts.isEmpty();
        }

        @Override
        public int compareTo(Version other) {
            for (int i = 0; i < Math.max(parts.size(), other.parts.size()); i++) {
                int order = Integer.compare(at(i), other.at(i));
                if (order != 0) {
                    return order;
                }
            }
            if (qualifier.equals(other.qualifier)) {
                return 0;
            }
            // 1.0.0-rc1 is before 1.0.0, so having a qualifier is being earlier.
            if (qualifier.isEmpty()) {
                return 1;
            }
            if (other.qualifier.isEmpty()) {
                return -1;
            }
            return qualifier.compareTo(other.qualifier);
        }

        private int at(int index) {
            return index < parts.size() ? parts.get(index) : 0;
        }

        /** The text it was read from, so a message quotes what somebody wrote. */
        @Override
        public String toString() {
            return text;
        }
    }

    /** One bracketed pair. An absent end is an unknown Version. */
    private record Clause(Version low, Version high, boolean lowOpen, boolean highClosed) {

        boolean has(Version version) {
            if (low.known()) {
                int order = version.compareTo(low);
                if (order < 0 || (order == 0 && lowOpen)) {
                    return false;
                }
            }
            if (high.known()) {
                int order = version.compareTo(high);
                if (order > 0 || (order == 0 && !highClosed)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A set of versions: one or more clauses, any of which will do. */
    static final class Range {

        private final List<Clause> clauses = new ArrayList<>();
        private final String text;

        Range(String raw) {
            this.text = strip(raw);
            if (text.isEmpty()) {
                return;
            }
            if (text.charAt(0) != '[' && text.charAt(0) != '(') {
                // A bare version. Exactly that one -- see the note above.
                Version only = new Version(text);
                if (!only.known()) {
                    throw new IllegalArgumentException("“" + text + "” is neither a version"
                            + " nor a range; a range looks like [1,2) and a version like 1.2.3");
                }
                clauses.add(new Clause(only, only, false, true));
                return;
            }

            String rest = text;
            while (!rest.isEmpty()) {
                char open = rest.charAt(0);
                if (open != '[' && open != '(') {
                    throw fault("expected [ or ( where “" + rest + "” starts");
                }
                int end = -1;
                for (int i = 0; i < rest.length(); i++) {
                    if (rest.charAt(i) == ')' || rest.charAt(i) == ']') {
                        end = i;
                        break;
                    }
                }
                if (end < 0) {
                    throw fault("a bracket is opened and never closed");
                }
                clauses.add(bounds(rest.substring(1, end), open == '(', rest.charAt(end) == ']'));

                rest = rest.substring(end + 1).strip();
                if (rest.isEmpty()) {
                    break;
                }
                if (rest.charAt(0) != ',') {
                    throw fault("two ranges are separated by a comma");
                }
                rest = rest.substring(1).strip();
                if (rest.isEmpty()) {
                    throw fault("a comma with nothing after it");
                }
            }
        }

        /** Reads what is between one pair of brackets. */
        private Clause bounds(String body, boolean lowOpen, boolean highClosed) {
            int comma = body.indexOf(',');
            if (comma < 0) {
                // A single version, which only means anything as [1.2]: (1.2) is
                // the empty set written at length, and nobody means that.
                Version only = new Version(body);
                if (!only.known()) {
                    throw fault("“" + body.strip() + "” is not a version");
                }
                if (lowOpen || !highClosed) {
                    throw fault("one version means exactly that version, so it takes"
                            + " square brackets: [" + only + "]");
                }
                return new Clause(only, only, false, true);
            }

            Version low = new Version(body.substring(0, comma));
            Version high = new Version(body.substring(comma + 1));
            if (!body.substring(0, comma).isBlank() && !low.known()) {
                throw fault("“" + body.substring(0, comma).strip() + "” is not a version");
            }
            if (!body.substring(comma + 1).isBlank() && !high.known()) {
                throw fault("“" + body.substring(comma + 1).strip() + "” is not a version");
            }
            if (!low.known() && !high.known()) {
                throw fault("a range with neither end allows everything;"
                        + " leave the line out instead of writing it");
            }
            if (low.known() && high.known() && low.compareTo(high) > 0) {
                throw fault(low + " is above " + high + ", so nothing can be in that range");
            }
            return new Clause(low, high, lowOpen, highClosed);
        }

        private IllegalArgumentException fault(String why) {
            return new IllegalArgumentException("“" + text + "”: " + why);
        }

        /**
         * A range that constrains nothing, which is what an absent line parses
         * to. Kept apart from a range that happens to match: "said nothing" and
         * "said yes" are different things to print.
         */
        boolean any() {
            return clauses.isEmpty();
        }

        /** Whether a version is in the range. An unknown version is in nothing. */
        boolean has(Version version) {
            if (any()) {
                return true;
            }
            if (!version.known()) {
                return false;
            }
            for (Clause clause : clauses) {
                if (clause.has(version)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static String strip(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).strip();
        }
        return trimmed;
    }
}
