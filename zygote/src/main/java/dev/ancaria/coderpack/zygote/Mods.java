package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Game;
import dev.ancaria.coderpack.api.ModLoadException;
import dev.ancaria.coderpack.api.SacredMod;
import dev.ancaria.coderpack.api.internal.ModBinding;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The mods the loader runs: the ones found in {@code <Sacred Gold>/mods} at
 * startup, and any a mod registers or unregisters while the game runs.
 *
 * <p>Each jar gets its own class loader, and listeners are registered by the mod
 * itself rather than by scanning the classpath: explicit, fast, and the order is
 * the mod's own. Every listener remembers its mod, which is what lets
 * {@link #unregister} take a mod out whole.
 *
 * <p>Loading and unloading are serialised on one lock, and a mod may load or
 * unload another from inside its own {@code onLoad}; the lock is reentrant.
 * Reading the list takes no lock at all.
 */
final class Mods {

    private static final String DESCRIPTOR = "META-INF/declaration.toml";

    /** How long shutdown waits for every onUnload together. */
    private static final long UNLOAD_MILLIS = 3_000;

    private final Path gameDir;
    private final Bus bus;
    private final Game game;
    private final ModLog log;
    // Read once rather than per jar: it is the same file for all of them,
    // and a folder with no launcher in it is the normal shape of a checkout.
    private final String loaderVersion;

    private final Object lock = new Object();
    /** Load order. Copy-on-write, so a snapshot is one read. */
    private final List<LoadedMod> loaded = new CopyOnWriteArrayList<>();

    Mods(Path gameDir, Bus bus, Game game, ModLog log) {
        this.gameDir = gameDir;
        this.bus = bus;
        this.game = game;
        this.log = log;
        this.loaderVersion = Compat.version(gameDir);
    }

    /**
     * Every jar in {@code directory}, in file-name order. A jar that fails is
     * logged and skipped; the rest still load.
     *
     * @param enabled mod ids the launcher asked for. Null means every jar in
     *                the directory, which is what a bare host run does
     */
    void loadAll(Path directory, Set<String> enabled) {
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
        for (Path jar : jars) {
            try {
                load(jar, enabled);
            } catch (ModLoadException refused) {
                if (refused.getCause() == null) {
                    Log.warn(refused.getMessage());
                } else {
                    Log.error(refused.getMessage(), refused.getCause());
                }
            }
        }
        Log.info(jars.size() + (jars.size() == 1 ? " mod jar in " : " mod jars in ")
                + directory.toAbsolutePath());
    }

    /** {@code ModRegistry.register}: one jar, from anywhere, enabled by asking. */
    SacredMod register(Path jar) {
        return load(jar, null).instance();
    }

    /**
     * {@code ModRegistry.unregister}: listeners off first, so nothing of the
     * mod's runs while it is being told to stop, then {@code onUnload}, then
     * the class loader.
     */
    boolean unregister(String id) {
        synchronized (lock) {
            LoadedMod mod = find(id);
            if (mod == null) {
                return false;
            }
            loaded.remove(mod);
            int dropped = bus.dropMod(mod);
            unload(mod);
            // Whatever onUnload registered on its way out goes too.
            dropped += bus.dropMod(mod);
            close(mod);
            Log.info("Unloaded " + id + " and " + dropped
                    + (dropped == 1 ? " listener." : " listeners."));
            return true;
        }
    }

    /**
     * On BYE or a closed pipe: every mod's {@code onUnload}, newest first,
     * then the log. Class loaders stay open, because a mod's own shutdown hook
     * may still need a class from its jar while the JVM exits.
     *
     * <p>All of it on one thread with a deadline: a mod that hangs in
     * {@code onUnload} does not keep the loader alive.
     */
    void shutdown() {
        List<LoadedMod> order = new ArrayList<>(loaded);
        java.util.Collections.reverse(order);
        Thread unloader = new Thread(() -> {
            for (LoadedMod mod : order) {
                bus.dropMod(mod);
                unload(mod);
            }
        }, "sal-unload");
        unloader.setDaemon(true);
        unloader.start();
        try {
            unloader.join(UNLOAD_MILLIS);
        } catch (InterruptedException stop) {
            Thread.currentThread().interrupt();
        }
        if (unloader.isAlive()) {
            Log.warn("A mod’s onUnload is still running after " + UNLOAD_MILLIS
                    + " ms; shutting down without it.");
        }
        loaded.clear();
        log.close();
    }

    /** Every loaded mod's instance, in load order. */
    List<SacredMod> instances() {
        List<SacredMod> out = new ArrayList<>(loaded.size());
        for (LoadedMod mod : loaded) {
            out.add(mod.instance());
        }
        return List.copyOf(out);
    }

    SacredMod instance(String id) {
        LoadedMod mod = find(id);
        return mod == null ? null : mod.instance();
    }

    private LoadedMod find(String id) {
        for (LoadedMod mod : loaded) {
            if (mod.id().equals(id)) {
                return mod;
            }
        }
        return null;
    }

    /**
     * Reads, checks, creates and starts one mod.
     *
     * @return null when the launcher did not enable it
     * @throws ModLoadException for everything else that stops it
     */
    private LoadedMod load(Path jar, Set<String> enabled) {
        Meta meta = descriptor(jar);

        // Before the enabled check on purpose: a player whose saved list
        // still ticks this mod gets told why nothing happened.
        String refusal = Compat.refuse(meta, loaderVersion);
        if (refusal != null) {
            throw new ModLoadException(refusal);
        }
        if (enabled != null && !enabled.contains(meta.id())) {
            Log.info("Skipping " + meta.id() + " because it isn’t enabled.");
            return null;
        }

        synchronized (lock) {
            if (find(meta.id()) != null) {
                throw new ModLoadException(meta.id() + " is already loaded; "
                        + jar.getFileName() + " wasn’t loaded.");
            }
            URLClassLoader loader;
            try {
                loader = new URLClassLoader(meta.id(), new URL[] { jar.toUri().toURL() },
                                            Mods.class.getClassLoader());
            } catch (Exception failure) {
                throw new ModLoadException("Failed to load " + jar.getFileName(), failure);
            }
            LoadedMod mod = null;
            try {
                Class<?> entry = loader.loadClass(meta.entrypoint());
                if (!SacredMod.class.isAssignableFrom(entry)) {
                    throw new ModLoadException(meta.id() + ": " + meta.entrypoint()
                            + " isn’t a SacredMod.");
                }
                Class<? extends SacredMod> type = entry.asSubclass(SacredMod.class);
                mod = new LoadedMod(meta.id(), new ModDescriptor(meta, type, jar), loader);
                ModContext context = new ModContext(mod, this, bus, game, log);
                mod.claim(ModBinding.create(type, context, mod::claim));
                // In the list before onLoad, so the mod finds itself there.
                loaded.add(mod);
                mod.instance().onLoad();
                Log.info("Loaded " + meta.credit() + ".");
                return mod;
            } catch (Throwable failure) {
                // Not half loaded: whatever it registered before failing goes.
                if (mod != null) {
                    loaded.remove(mod);
                    bus.dropMod(mod);
                }
                close(loader, meta.id());
                if (failure instanceof ModLoadException refused) {
                    throw refused;
                }
                Throwable cause = failure instanceof InvocationTargetException wrapped
                        && wrapped.getCause() != null ? wrapped.getCause() : failure;
                throw new ModLoadException("Failed to load " + jar.getFileName(), cause);
            }
        }
    }

    private static Meta descriptor(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = file.getJarEntry(DESCRIPTOR);
            if (entry == null) {
                throw new ModLoadException(jar.getFileName() + " has no " + DESCRIPTOR
                        + " and was skipped.");
            }
            Meta meta;
            try (InputStream in = file.getInputStream(entry)) {
                meta = Meta.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            if (meta == null) {
                throw new ModLoadException(jar.getFileName()
                        + ": the descriptor requires id and entrypoint.");
            }
            return meta;
        } catch (ModLoadException refused) {
            throw refused;
        } catch (Exception failure) {
            throw new ModLoadException("Couldn’t read " + jar.getFileName(), failure);
        }
    }

    /** onUnload, whose failure is the mod's problem and goes no further. */
    private static void unload(LoadedMod mod) {
        try {
            mod.instance().onUnload();
        } catch (Throwable failure) {
            Log.error(mod.id() + ".onUnload", failure);
        }
    }

    private static void close(LoadedMod mod) {
        close(mod.loader(), mod.id());
    }

    private static void close(ClassLoader loader, String id) {
        if (loader instanceof URLClassLoader closeable) {
            try {
                closeable.close();
            } catch (Exception failure) {
                Log.warn("Couldn’t close the class loader of " + id + ": " + failure);
            }
        }
    }
}
