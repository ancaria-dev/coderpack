package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.entity.Attributes;
import dev.ancaria.coderpack.api.entity.CombatArts;
import dev.ancaria.coderpack.api.entity.HeroClass;
import dev.ancaria.coderpack.api.entity.Player;
import dev.ancaria.coderpack.api.entity.Sheet;
import dev.ancaria.coderpack.api.entity.Skills;
import dev.ancaria.coderpack.api.entity.Stats;
import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.Hero;
import dev.ancaria.coderpack.api.event.Position;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Player state, kept current from the events that already carry it. Reads are
 * free, and only writes cost a round-trip. That matters because a mod
 * reacting to damage would otherwise ask the game a question while the game
 * thread is stopped waiting for that same mod's verdict.
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
            heroClass = hero.getHeroClass();
            level = hero.getLevel();
            hp = hero.getHp();
            maxHp = hero.getMaxHp();
            gold = hero.getGold();
            exp = hero.getExp();
        } else if (event instanceof Position position) {
            x = position.getX();
            y = position.getY();
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
    public HeroClass getHeroClass() {
        return heroClass;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public long getHp() {
        return hp;
    }

    @Override
    public long getMaxHp() {
        return maxHp;
    }

    @Override
    public long getGold() {
        return gold;
    }

    @Override
    public long getExp() {
        return exp;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
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
    public void setHp(long value) {
        hp = number(game.call("player.hp", "value", value).get("hp"), value);
    }

    @Override
    public void setGold(long value) {
        gold = number(game.call("player.gold", "value", value).get("gold"), value);
    }

    @Override
    public void addExp(long amount) {
        exp = number(game.call("player.exp", "amount", amount).get("exp"), exp);
    }

    // Not cached: no event covers these completely, so a cached copy would be
    // wrong in ways a mod could not see. Each answer is a fresh snapshot, and a
    // failed call is an empty one rather than an exception.

    @Override
    public Attributes getAttributes() {
        return new Attributes(game.call("player.attributes", Map.of()));
    }

    @Override
    public void setAttribute(Attributes.Kind kind, int value) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("index", Integer.toString(kind.getIndex()));
        fields.put("value", Integer.toString(value));
        game.call("player.attribute", fields);
    }

    @Override
    public Skills getSkills() {
        return new Skills(game.call("player.skills", Map.of()));
    }

    @Override
    public void setSkill(int slot, int level) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("slot", Integer.toString(slot));
        fields.put("value", Integer.toString(level));
        game.call("player.skill", fields);
    }

    @Override
    public CombatArts getCombatArts() {
        return new CombatArts(game.call("player.arts", Map.of()));
    }

    @Override
    public CombatArts.Art getCombatArt(int index) {
        return getCombatArts().get(index);
    }

    @Override
    public void setCombatArt(int index, int level) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("index", Integer.toString(index));
        fields.put("level", Integer.toString(level));
        game.call("player.art", fields);
    }

    @Override
    public Stats getStats() {
        return new Stats(game.call("player.stats", Map.of()));
    }

    @Override
    public Sheet getSheet() {
        return new Sheet(game.call("player.sheet", Map.of()));
    }

    @Override
    public void kill() {
        hp = number(game.call("player.kill", Map.of()).get("hp"), hp);
    }
}
