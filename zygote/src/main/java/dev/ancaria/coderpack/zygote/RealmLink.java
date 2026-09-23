package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Realm;
import dev.ancaria.coderpack.api.entity.Creature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The world side of the command wire. Nothing is cached here: creatures come
 * and go between two calls, and a stale list is worse than a round-trip.
 */
final class RealmLink implements Realm {

    private final GameLink game;

    RealmLink(GameLink game) {
        this.game = game;
    }

    @Override
    public List<Creature> creatures() {
        return unpack(game.call("world.creatures", Map.of()));
    }

    @Override
    public List<Creature> creaturesNear(int x, int y, int radius) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("x", Integer.toString(x));
        fields.put("y", Integer.toString(y));
        fields.put("radius", Integer.toString(radius));
        return unpack(game.call("world.creatures", fields));
    }

    @Override
    public Creature creature(int ref) {
        Map<String, String> answer = game.call("world.creature", "ref", ref);
        return answer.get("ok") == null ? null : new Creature(answer);
    }

    @Override
    public boolean kill(int ref) {
        return game.call("world.kill", "ref", ref).get("ok") != null;
    }

    @Override
    public boolean hp(int ref, long value) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ref", Integer.toString(ref));
        fields.put("value", Long.toString(value));
        return game.call("world.hp", fields).get("ok") != null;
    }

    @Override
    public int region() {
        return number(game.call("world.state", Map.of()).get("region"), 0);
    }

    @Override
    public int sectorX() {
        return number(game.call("world.state", Map.of()).get("sx"), -1);
    }

    @Override
    public int sectorY() {
        return number(game.call("world.state", Map.of()).get("sy"), -1);
    }

    // ref:type:level:hp:maxHp:x:y:player joined by ';', and each type's name
    // once, type=NAME joined by ','. See world.creatures in 47-entities.js.
    static List<Creature> unpack(Map<String, String> answer) {
        String packed = answer.get("creatures");
        if (packed == null || packed.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = new HashMap<>();
        String named = answer.get("names");
        if (named != null && !named.isEmpty()) {
            for (String pair : named.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    names.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
        String[] keys = {"ref", "type", "level", "hp", "maxHp", "x", "y", "player"};
        List<Creature> out = new ArrayList<>();
        for (String record : packed.split(";")) {
            String[] part = record.split(":");
            if (part.length != keys.length) {
                Log.warn("world.creatures sent something unparseable: " + record);
                continue;
            }
            Map<String, String> fields = new HashMap<>();
            for (int i = 0; i < keys.length; i++) {
                fields.put(keys[i], part[i]);
            }
            String name = names.get(part[1]);
            if (name != null) {
                fields.put("name", name);
            }
            out.add(new Creature(fields));
        }
        return Collections.unmodifiableList(out);
    }

    private static int number(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
