package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.SacredMod;
import dev.ancaria.coderpack.api.SacredModDescriptor;

import java.time.Duration;

/**
 * One mod as the loader keeps it: its id, its descriptor, the class loader
 * its jar was opened with, and the instance, once there is one.
 *
 * <p>The instance arrives from inside the mod's own constructor, through
 * {@code ModBinding}, so a listener the mod registers in a field initialiser
 * already has an owner to name.
 */
final class LoadedMod {

    private final String id;
    private final SacredModDescriptor descriptor;
    private final ClassLoader loader;
    private final long started = System.nanoTime();

    private volatile SacredMod instance;

    /**
     * @param descriptor null only in tests, which build a bus without a jar
     * @param loader     null only in tests, for the same reason
     */
    LoadedMod(String id, SacredModDescriptor descriptor, ClassLoader loader) {
        this.id = id;
        this.descriptor = descriptor;
        this.loader = loader;
    }

    String id() {
        return id;
    }

    SacredModDescriptor descriptor() {
        return descriptor;
    }

    ClassLoader loader() {
        return loader;
    }

    SacredMod instance() {
        return instance;
    }

    /** Called once, from the mod's constructor, before its initialisers run. */
    void claim(SacredMod mod) {
        this.instance = mod;
    }

    Duration uptime() {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    @Override
    public String toString() {
        return id;
    }
}
