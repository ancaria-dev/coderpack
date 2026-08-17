package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.entity.HeroClass;
import dev.ancaria.coderpack.api.entity.Player;
import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.Hero;
import dev.ancaria.coderpack.api.event.Position;

/**
 * Player state, kept current from the events that already carry it. Reads are
 * free; only writes cost a round-trip. That matters because a mod reacting to
 * damage would otherwise ask the game a question while the game thread is
 * stopped waiting for that same mod's verdict.
 */
final class PlayerLink implements Player {

    private final GameLink game;

    private volatile boolean present;
    private volatile HeroClass heroClass = HeroClass.UNKNOWN;
    private volatile int level;
    private volatile long hp;
    private volatile long maxHp;
    private volatile long gold;
    private volatile long exp;
    private volatile int x;
    private volatile int y;

    PlayerLink(GameLink game) {
        this.game = game;
    }

    /** Folds whatever an event knows into the cache. */
    void observe(Event event) {
        if (event instanceof Hero hero) {
            present = true;
            heroClass = hero.heroClass();
            level = hero.level();
            hp = hero.hp();
            maxHp = hero.maxHp();
            gold = hero.gold();
            exp = hero.exp();
        } else if (event instanceof Position position) {
            x = position.x();
            y = position.y();
        }
    }

    void observeFields(String name, java.util.Map<String, String> fields) {
        switch (name) {
            case "health.changed" -> {
                hp = number(fields.get("next"), hp);
                maxHp = number(fields.get("max"), maxHp);
            }
            case "health.max_changed" -> maxHp = number(fields.get("next"), maxHp);
            case "gold.changed" -> gold = number(fields.get("next"), gold);
            case "exp.changed" -> exp = number(fields.get("next"), exp);
            case "level.changed" -> level = (int) number(fields.get("next"), level);
            default -> {
            }
        }
    }

    void clear() {
        present = false;
    }

    boolean present() {
        return present;
    }

    private static long number(String raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public HeroClass heroClass() {
        return heroClass;
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public long hp() {
        return hp;
    }

    @Override
    public long maxHp() {
        return maxHp;
    }

    @Override
    public long gold() {
        return gold;
    }

    @Override
    public long exp() {
        return exp;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public void teleport(int toX, int toY) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("x", Integer.toString(toX));
        fields.put("y", Integer.toString(toY));
        game.call("player.teleport", fields);
    }

    @Override
    public void hp(long value) {
        hp = number(game.call("player.hp", "value", value).get("hp"), value);
    }

    @Override
    public void gold(long value) {
        gold = number(game.call("player.gold", "value", value).get("gold"), value);
    }

    @Override
    public void addExp(long amount) {
        exp = number(game.call("player.exp", "amount", amount).get("exp"), exp);
    }
}
