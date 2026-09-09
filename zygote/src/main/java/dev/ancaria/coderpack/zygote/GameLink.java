package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Game;
import dev.ancaria.coderpack.api.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The command side: Coderpack asking the game to do something.
 *
 * <p>A call blocks the calling thread until the agent answers. That is safe
 * only because commands are issued from the dispatch thread while the reader
 * thread keeps consuming replies. If one thread did both, a command inside an
 * event handler would deadlock.
 */
final class GameLink implements Game {

    private static final long TIMEOUT_SECONDS = 2;

    private final Pipe pipe;
    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, CompletableFuture<Map<String, String>>> pending =
            new ConcurrentHashMap<>();
    private final PlayerLink player = new PlayerLink(this);

    GameLink(Pipe pipe) {
        this.pipe = pipe;
    }

    Map<String, String> call(String name, Map<String, String> fields) {
        long id = sequence.getAndIncrement();
        CompletableFuture<Map<String, String>> answer = new CompletableFuture<>();
        pending.put(id, answer);
        pipe.write(new Frame("CMD", id, name, fields));
        try {
            return answer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            pending.remove(id);
            Log.warn("command " + name + " got no answer: " + failure);
            return Map.of();
        }
    }

    Map<String, String> call(String name, String key, Object value) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(key, String.valueOf(value));
        return call(name, fields);
    }

    /** Called from the reader thread when a RES frame arrives. */
    void complete(Frame frame) {
        CompletableFuture<Map<String, String>> answer = pending.remove(frame.seq());
        if (answer != null) {
            answer.complete(frame.fields());
        }
    }

    PlayerLink playerLink() {
        return player;
    }

    @Override
    public Player player() {
        return player.present() ? player : null;
    }

    @Override
    public String uiString(String key) {
        return call("ui.string", "key", key).get("text");
    }

    @Override
    public String typeName(int typeId) {
        return call("type.name", "id", typeId).get("name");
    }

    @Override
    public int typeId(String name) {
        String id = call("type.find", "name", name).get("id");
        return id == null ? 0 : Integer.parseInt(id);
    }

    @Override
    public Map<String, Integer> types(String prefix) {
        // "NAME:id,NAME:id" in one field: the caller is building a table at
        // startup and a round-trip per name would be a hundred of them.
        String packed = call("type.list", "prefix", prefix).get("types");
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
        return out;
    }

    @Override
    public boolean retype(int ref, int typeId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ref", Integer.toString(ref));
        fields.put("type", Integer.toString(typeId));
        // A positive signal, not the absence of "err": a command that timed
        // out comes back as an empty map and would otherwise read as success.
        return call("item.reshape", fields).get("ok") != null;
    }
}
