package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * A line was typed into the game's console. Decidable in one way only: a mod
 * that recognises the line as its own command vetoes it, the game never sees
 * it, and it prints no "Try HELP" error. Anything not vetoed reaches the game's
 * own commands as typed.
 *
 * <pre>{@code
 * events.decide(Console.class, line -> line.getText().startsWith("/heal")
 *         ? Console.Mutation.veto() : Console.Mutation.none());
 * }</pre>
 */
public final class Console extends Decision implements Decides<Console.Mutation> {

    public Console(Map<String, String> fields) {
        super(fields);
    }

    /** The whole line, as typed. */
    @Nonnull
    public String getText() {
        String text = getText("text");
        return text == null ? "" : text;
    }

    @Override
    void reset() {
        // Nothing is folded but the veto, which the bus keeps itself.
    }

    @Override
    void change(EventMutation mutation) {
        // Console.Mutation has no CHANGE.
    }

    @Override
    Map<String, String> verdict() {
        return Map.of();
    }

    /** What a {@code Console} listener returns. */
    public static final class Mutation extends EventMutation {

        private static final Mutation NONE = new Mutation(Kind.NONE, false);
        private static final Mutation RESET = new Mutation(Kind.RESET, false);
        private static final Mutation VETO = new Mutation(Kind.VETO, false);

        private Mutation(Kind kind, boolean last) {
            super(kind, last);
        }

        /** Not mine. The game handles the line as usual. */
        @Nonnull
        public static Mutation none() {
            return NONE;
        }

        /** Undo an earlier listener's veto, so the game sees the line after all. */
        @Nonnull
        public static Mutation reset() {
            return RESET;
        }

        /** Mine. The game does not see the line and shows no error. */
        @Nonnull
        public static Mutation veto() {
            return VETO;
        }

        @Override
        @Nonnull
        public Mutation last() {
            return new Mutation(getKind(), true);
        }
    }
}
