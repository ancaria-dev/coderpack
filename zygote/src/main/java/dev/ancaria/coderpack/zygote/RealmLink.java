package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.EntityRegistry;
import dev.ancaria.coderpack.api.Realm;
import dev.ancaria.coderpack.api.entity.Creature;

import java.util.ArrayList;
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
    private final EntityLink entities;

    RealmLink(GameLink game) {
        this.game = game;
        this.entities = new EntityLink(game);
    }

    @Override
    public int getRegion() {
        return number(game.call("world.state", Map.of()).get("region"), 0);
    }

    @Override
    public int getSectorX() {
        return number(game.call("world.state", Map.of()).get("sx"), -1);
    }

    @Override
    public int getSectorY() {
        return number(game.call("world.state", Map.of()).get("sy"), -1);
    }

    @Override
    public boolean kill(int ref) {
        return game.call("world.kill", "ref", ref).get("ok") != null;
    }

    @Override
    public boolean setHp(int ref, long hp) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ref", Integer.toString(ref));
        fields.put("value", Long.toString(hp));
        return game.call("world.hp", fields).get("ok") != null;
    }

    @Override
    public EntityRegistry getEntityRegistry() {
        return entities;
    }

    /** The hero from the event cache, the creatures from the wire. */
    static final class EntityLink implements EntityRegistry {

        private final GameLink game;

        EntityLink(GameLink game) {
            this.game = game;
        }

        @Override
        public PlayerLink getPlayer() {
            PlayerLink player = game.playerLink();
            return player.present() ? player : null;
        }

        @Override
        public List<Creature> getCreatures() {
            return unpack(game.call("world.creatures", Map.of()));
        }

        @Override
        public List<Creature> getCreaturesNear(int x, int y, int radius) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("x", Integer.toString(x));
            fields.put("y", Integer.toString(y));
            fields.put("radius", Integer.toString(radius));
            return unpack(game.call("world.creatures", fields));
        }

        @Override
        public Creature getCreature(int ref) {
            Map<String, String> answer = game.call("world.creature", "ref", ref);
            return answer.get("ok") == null ? null : new Creature(answer);
        }
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
        return List.copyOf(out);
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
