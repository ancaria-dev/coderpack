package dev.ancaria.coderpack.api.entity;

import java.time.Duration;
import java.util.Map;

/**
 * The Statistics page of the journal, as it was when asked. A snapshot.
 *
 * <p>These counters belong to the game, not to the loader. They survive saves
 * and loads, and a mod that counts kills itself will disagree with them about
 * everything that happened before it was installed.
 */
public final class Stats {

    private final Map<String, String> fields;

    public Stats(Map<String, String> fields) {
        this.fields = fields;
    }

    /** "Opponents Defeated". */
    public long getKills() {
        return number("kills");
    }

    /** How many times the hero died and came back. */
    public long getResurrections() {
        return number("resurrections");
    }

    /** Areas discovered. The page shows this as a share of all of them. */
    public long getDiscoveredAreas() {
        return number("areas");
    }

    /** The number under the Game History graph. */
    public long getGraphLevel() {
        return number("graph");
    }

    public Duration getPlayTime() {
        return Duration.ofMillis(number("playMillis"));
    }

    /** Time since the last death, the clock the survival bonus runs on. */
    public Duration getSinceDeath() {
        return Duration.ofMillis(number("sinceDeath"));
    }

    /** The survival bonus in percent, 0 to 50, from the game's own curve. */
    public double getSurvivalBonus() {
        String raw = fields.get("survival");
        if (raw == null) {
            return 0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long number(String key) {
        String raw = fields.get(key);
        if (raw == null) {
            return 0;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
