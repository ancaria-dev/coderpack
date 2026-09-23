package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.TypeRegistry;
import dev.ancaria.coderpack.api.entity.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Type names and ids over the command wire, and the two item edits. */
final class TypeLink implements TypeRegistry {

    private final GameLink game;

    TypeLink(GameLink game) {
        this.game = game;
    }

    @Override
    public Map<String, Integer> types(String prefix) {
        return unpack(game.call("type.list", "prefix", prefix));
    }

    // "NAME:id,NAME:id" in one field: the caller is building a table at
    // startup and a round-trip per name would be a hundred of them. Insertion
    // order is the game's, so the map is wrapped rather than Map.copyOf'd.
    static Map<String, Integer> unpack(Map<String, String> answer) {
        String packed = answer.get("types");
        if (packed == null || packed.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String pair : packed.split(",")) {
            int split = pair.lastIndexOf(':');
            if (split <= 0) {
                continue;
            }
            try {
                out.put(pair.substring(0, split),
                        Integer.parseInt(pair.substring(split + 1)));
            } catch (NumberFormatException skip) {
                Log.warn("type.list sent something unparseable: " + pair);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    @Override
    public int getTypeId(String name) {
        String id = game.call("type.find", "name", name).get("id");
        if (id == null) {
            return 0;
        }
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException bad) {
            return 0;
        }
    }

    @Override
    public String getTypeName(int typeId) {
        return game.call("type.name", "id", typeId).get("name");
    }

    @Override
    public boolean retype(int ref, int typeId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ref", Integer.toString(ref));
        fields.put("type", Integer.toString(typeId));
        // A positive signal, not the absence of "err": a command that timed
        // out comes back as an empty map and would otherwise read as success.
        return game.call("item.reshape", fields).get("ok") != null;
    }

    @Override
    public boolean reshape(int ref, Item template) {
        // The agent names these fields exactly as they arrive on an item event,
        // so what was read off a Pickup is what is written back here.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ref", Integer.toString(ref));
        fields.put("type", Integer.toString(template.getTypeId()));
        fields.put("price", Integer.toString(template.getPrice()));
        fields.put("level", Integer.toString(template.getLevel()));
        fields.put("min", Integer.toString(template.getMinLevel()));
        fields.put("mods", template.getPackedModifiers());
        return game.call("item.reshape", fields).get("ok") != null;
    }
}
