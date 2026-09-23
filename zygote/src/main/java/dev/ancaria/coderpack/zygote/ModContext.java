package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Context;
import dev.ancaria.coderpack.api.EventRegistry;
import dev.ancaria.coderpack.api.Game;
import dev.ancaria.coderpack.api.ModRegistry;
import dev.ancaria.coderpack.api.SacredMod;
import dev.ancaria.coderpack.api.SacredModDescriptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * What one mod is given. Everything here either forwards to something the
 * loader shares between mods ({@link Mods}, the {@link Bus}, the game, the
 * log) or carries the one thing that is this mod's own: which mod it is.
 *
 * <p>The api's {@code Registry} is named in full throughout, because this
 * package has a {@code Registry} of its own, the wire-name table.
 */
final class ModContext implements Context {

    private final LoadedMod mod;
    private final Game game;
    private final ModLog log;
    private final EventRegistry events;
    private final ModRegistry mods;
    private final dev.ancaria.coderpack.api.Registry registry;

    ModContext(LoadedMod mod, Mods loader, Bus bus, Game game, ModLog log) {
        this.mod = mod;
        this.game = game;
        this.log = log;
        this.events = new ModEvents(mod, bus);
        this.mods = new Loaded(mod, loader);
        this.registry = new dev.ancaria.coderpack.api.Registry() {
            @Override
            public ModRegistry getModRegistry() {
                return mods;
            }

            @Override
            public EventRegistry getEventRegistry() {
                return events;
            }
        };
    }

    @Override
    public void log(String message) {
        log.write(ModLog.line(mod.id(), String.valueOf(message)));
    }

    @Override
    public void print(String message) {
        // Main points System.out at stderr before any mod loads, so this
        // cannot land on a stdio wire.
        System.out.println(ModLog.line(mod.id(), String.valueOf(message)));
    }

    @Override
    public Game getGame() {
        return game;
    }

    @Override
    public Duration getUptime() {
        return mod.uptime();
    }

    @Override
    public SacredModDescriptor getDescriptor() {
        return mod.descriptor();
    }

    @Override
    public dev.ancaria.coderpack.api.Registry getRegistry() {
        return registry;
    }

    /** The mod list as one mod sees it: the shared list, plus "me". */
    private static final class Loaded implements ModRegistry {

        private final LoadedMod mod;
        private final Mods loader;

        Loaded(LoadedMod mod, Mods loader) {
            this.mod = mod;
            this.loader = loader;
        }

        @Override
        public List<SacredMod> getMods() {
            return loader.instances();
        }

        @Override
        public SacredMod getMod(String id) {
            return loader.instance(Objects.requireNonNull(id, "id"));
        }

        @Override
        public SacredMod getCurrentMod() {
            return mod.instance();
        }

        @Override
        public SacredMod register(Path jar) {
            return loader.register(Objects.requireNonNull(jar, "jar"));
        }

        @Override
        public boolean unregister(String modId) {
            return loader.unregister(Objects.requireNonNull(modId, "modId"));
        }

        @Override
        public ClassLoader getClassLoader() {
            return mod.loader();
        }
    }
}
