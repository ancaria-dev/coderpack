package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * A quest started or ended, as the game's quest scripts said so. A quest is its
 * number, the one Sacred's quest files use. There is no title here: titles are
 * localized text the quest files point at, and that link is not mapped.
 */
public final class Quest extends Event {

    private final boolean started;

    public Quest(boolean started, Map<String, String> fields) {
        super(fields);
        this.started = started;
    }

    /** The quest number, or -1 when the script named it through a variable. */
    public int number() {
        return (int) num("number");
    }

    /** True when it starts, false when it ends. */
    public boolean started() {
        return started;
    }

    /**
     * On an end, the flag the game passed: 1 from ExitQuest, 0 from LoseQuest.
     * Which of the two means solved is not confirmed yet, so this is the raw
     * value and not a boolean with a name it may not deserve.
     */
    public int endFlag() {
        return (int) num("flag");
    }
}
