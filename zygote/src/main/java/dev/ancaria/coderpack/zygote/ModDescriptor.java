package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.SacredMod;
import dev.ancaria.coderpack.api.SacredModDescriptor;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link Meta} as a mod sees it: the same descriptor, with the entry point
 * class the loader resolved and the jar it came from.
 */
final class ModDescriptor implements SacredModDescriptor {

    private final Meta meta;
    private final Class<? extends SacredMod> entryPoint;
    private final Path path;
    private final List<String> authors;
    private final URL website;
    private final URL repository;

    ModDescriptor(Meta meta, Class<? extends SacredMod> entryPoint, Path path) {
        this.meta = meta;
        this.entryPoint = entryPoint;
        this.path = path;
        this.authors = List.copyOf(meta.authors());
        this.website = url(meta.website());
        this.repository = url(meta.repository());
    }

    /** Null for an absent or malformed address, which a descriptor is allowed to have. */
    static URL url(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new URI(raw.strip()).toURL();
        } catch (Exception malformed) {
            return null;
        }
    }

    @Override
    public String getId() {
        return meta.id();
    }

    @Override
    public String getDisplayName() {
        return meta.name();
    }

    @Override
    public String getVersion() {
        return meta.version();
    }

    @Override
    public String getDescription() {
        return meta.description();
    }

    @Override
    public String getEntryPoint() {
        return meta.entrypoint();
    }

    @Override
    public Class<? extends SacredMod> getEntryPointClass() {
        return entryPoint;
    }

    @Override
    public List<String> getAuthors() {
        return authors;
    }

    @Override
    public URL getWebsite() {
        return website;
    }

    @Override
    public URL getRepository() {
        return repository;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public String toString() {
        return meta.credit();
    }
}
