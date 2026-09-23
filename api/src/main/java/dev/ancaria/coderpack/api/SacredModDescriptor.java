package dev.ancaria.coderpack.api;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A mod's {@code META-INF/declaration.toml}, as the loader read it, and where
 * the jar is. Immutable: it describes the jar, not the running mod.
 *
 * <pre>
 * id = "example"
 * name = "Example Mod"
 * version = "0.1.0"
 * description = "What a player sees in the launcher's mod list."
 * entrypoint = "com.example.ExampleMod"
 * authors = ["Somebody", "Somebody Else"]
 * website = "https://ancaria.dev"
 * repository = "https://github.com/ancaria-dev/mods"
 * </pre>
 */
public interface SacredModDescriptor {

    /** The handle, lowercase and hyphenated, unique among loaded mods. */
    @Nonnull
    String getId();

    /** The {@code name} field, what a player reads. The id when it is absent. */
    @Nonnull
    String getDisplayName();

    /** "0" when the descriptor names none. */
    @Nonnull
    String getVersion();

    /** Empty when the descriptor has none. */
    @Nonnull
    String getDescription();

    /** The {@code entrypoint} class name, as written. */
    @Nonnull
    String getEntryPoint();

    /** The class {@link #getEntryPoint()} names, loaded from the mod's jar. */
    @Nonnull
    Class<? extends SacredMod> getEntryPointClass();

    /** Unmodifiable, in the order the descriptor lists them. */
    @Nonnull
    List<String> getAuthors();

    /** Null when absent or not a URL. */
    @Nullable
    URL getWebsite();

    /** Null when absent or not a URL. */
    @Nullable
    URL getRepository();

    /** The mod's jar file. */
    @Nonnull
    Path getPath();
}
