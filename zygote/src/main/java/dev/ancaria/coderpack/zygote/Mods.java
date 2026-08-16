package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Context;
import dev.ancaria.coderpack.api.Events;
import dev.ancaria.coderpack.api.Game;
import dev.ancaria.coderpack.api.SacredMod;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Finds and starts the mods in {@code <Sacred Gold>/mods}.
 *
 * <p>Each jar gets its own class loader, and listeners are registered by the mod
 * itself rather than by scanning the classpath: explicit, fast, and the order is
 * the mod's own.
 */
final class Mods {

    private static final String DESCRIPTOR = "META-INF/declaration.toml";

    private Mods() {
    }

    /**
     * @param enabled mod ids the launcher asked for; null means every jar in
     *                the directory, which is what a bare host run does
     */
    static void loadAll(Path directory, Path gameDir, Set<String> enabled,
                        Bus bus, Game game) {
        if (!Files.isDirectory(directory)) {
            Log.warn("Mods directory not found: " + directory.toAbsolutePath());
            return;
        }
        List<Path> jars;
        try (var stream = Files.list(directory)) {
            jars = stream.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        } catch (Exception failure) {
            Log.error("Couldn’t list " + directory, failure);
            return;
        }
        // Read once rather than per jar: it is the same file for all of them,
        // and a folder with no launcher in it is the normal shape of a checkout.
        String loaderVersion = Compat.version(gameDir);
        for (Path jar : jars) {
            load(jar, gameDir, loaderVersion, enabled, bus, game);
        }
        Log.info(jars.size() + (jars.size() == 1 ? " mod jar in " : " mod jars in ")
                + directory.toAbsolutePath());
    }

    private static void load(Path jar, Path gameDir, String loaderVersion,
                             Set<String> enabled, Bus bus, Game game) {
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = file.getJarEntry(DESCRIPTOR);
            if (entry == null) {
                Log.warn(jar.getFileName() + " has no " + DESCRIPTOR + " and was skipped.");
                return;
            }
            Meta meta;
            try (InputStream in = file.getInputStream(entry)) {
                meta = Meta.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            if (meta == null) {
                Log.warn(jar.getFileName() + ": the descriptor requires id and entrypoint.");
                return;
            }

            // Before the enabled check on purpose: a player whose saved list
            // still ticks this mod gets told why nothing happened.
            String refusal = Compat.refuse(meta, loaderVersion);
            if (refusal != null) {
                Log.warn(refusal);
                return;
            }

            if (enabled != null && !enabled.contains(meta.id())) {
                Log.info("Skipping " + meta.id() + " because it isn’t enabled.");
                return;
            }

            URL[] urls = { jar.toUri().toURL() };
            ClassLoader loader = new URLClassLoader(meta.id(), urls, Mods.class.getClassLoader());
            Object instance = loader.loadClass(meta.entrypoint())
                                    .getDeclaredConstructor().newInstance();
            if (!(instance instanceof SacredMod mod)) {
                Log.warn(meta.id() + ": " + meta.entrypoint() + " isn’t a SacredMod.");
                return;
            }
            mod.onLoad(context(meta, gameDir, bus, game));
            Log.info("Loaded " + meta.credit() + ".");
        } catch (Throwable failure) {
            Log.error("Failed to load " + jar.getFileName(), failure);
        }
    }

    private static Context context(Meta meta, Path gameDir, Bus bus, Game game) {
        Events events = new ModEvents(meta.id(), bus);
        return new Context() {
            @Override
            public String id() {
                return meta.id();
            }

            @Override
            public Events events() {
                return events;
            }

            @Override
            public Game game() {
                return game;
            }

            @Override
            public Path gameDir() {
                return gameDir;
            }

            @Override
            public void log(String message) {
                Log.info(meta.id() + ": " + message);
            }
        };
    }
}
