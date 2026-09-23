package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Game;
import dev.ancaria.coderpack.api.GameConsole;
import dev.ancaria.coderpack.api.Realm;
import dev.ancaria.coderpack.api.TypeRegistry;

import java.nio.file.Path;
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
    private final Path directory;
    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, CompletableFuture<Map<String, String>>> pending =
            new ConcurrentHashMap<>();
    private final PlayerLink player = new PlayerLink(this);
    private final RealmLink world = new RealmLink(this);
    private final TypeLink types = new TypeLink(this);
    // One line out through the game's own console. See 95-console.js.
    private final GameConsole console = text -> call("console.print", "text", text);

    GameLink(Pipe pipe, Path directory) {
        this.pipe = pipe;
        this.directory = directory;
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
    public Path getDirectory() {
        return directory;
    }

    @Override
    public String getUiString(String key) {
        return call("ui.string", "key", key).get("text");
    }

    @Override
    public Realm getWorld() {
        return world;
    }

    @Override
    public TypeRegistry getTypeRegistry() {
        return types;
    }

    @Override
    public GameConsole getConsole() {
        return console;
    }
}
