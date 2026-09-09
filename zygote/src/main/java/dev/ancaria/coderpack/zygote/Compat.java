package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether a mod's descriptor allows the loader it has landed in.
 *
 * <p>Two questions, and they are not the same one. {@code api} is the contract
 * the code was compiled against, and it decides whether the mod can run at all:
 * a method that is no longer there is not a smaller problem on a newer release.
 * {@code loader} is the launcher's own version, which a mod names when it wants
 * a fix from a particular release. Most mods have no opinion about it and
 * should not have to write one down. Both are ranges, in the notation
 * {@link Ranges} documents.
 *
 * <p>A missing {@code api} is a mismatch, not a pass. The Gradle plugin writes
 * the field, so every mod built with the toolchain has it. A descriptor
 * without one was written by hand or by a plugin older than the field, and
 * neither says anything about which API the code inside calls. Assuming it is current is the
 * one guess that produces the failure this check exists to stop: a mod that
 * loads, registers its listeners, and throws {@code NoSuchMethodError} in the
 * middle of a dispatch. Refusing is loud, it names the jar, and the fix is a
 * rebuild.
 *
 * <p>A missing {@code loader} is a pass, and so is a loader version this process
 * could not find out. The launcher writes {@code <game>/launcher/VERSION} and is
 * also the thing that checks the range before a mod is ever ticked. A host
 * started by hand out of a checkout has no such file, and refusing every mod
 * over its absence would take the loader away from the one person who is
 * definitely debugging it.
 */
final class Compat {

    private Compat() {
    }

    /** Where the launcher stamps the release it installed. */
    private static final String STAMP = "launcher/VERSION";

    /**
     * The loader release in this game folder, or empty when there is none to
     * read, such as a checkout or a folder somebody assembled by hand.
     */
    static String version(Path gameDir) {
        try {
            return new String(Files.readAllBytes(gameDir.resolve(STAMP)),
                    StandardCharsets.UTF_8).strip();
        } catch (Exception noStamp) {
            return "";
        }
    }

    /** Null when the mod may load, otherwise the line to log about why it may not. */
    static String refuse(Meta meta, String loaderVersion) {
        if (meta.api().isBlank()) {
            return meta.id() + ": no API version is declared. This loader uses API "
                    + Api.VERSION + ", so the mod wasn’t loaded. Rebuild it with the current API.";
        }
        Ranges.Range wanted;
        try {
            wanted = Ranges.range(meta.api());
        } catch (IllegalArgumentException bad) {
            return meta.id() + ": API version range is invalid: " + bad.getMessage()
                    + ". The mod wasn’t loaded.";
        }
        if (!wanted.has(Ranges.version(String.valueOf(Api.VERSION)))) {
            return meta.id() + ": built for API " + wanted + ", but this loader uses API "
                    + Api.VERSION + ". The mod wasn’t loaded. Rebuild it with the current API.";
        }

        Ranges.Range release;
        try {
            release = Ranges.range(meta.loader());
        } catch (IllegalArgumentException bad) {
            return meta.id() + ": loader version range is invalid: " + bad.getMessage()
                    + ". The mod wasn’t loaded.";
        }
        if (release.any() || loaderVersion.isEmpty()) {
            return null;
        }
        if (!release.has(Ranges.version(loaderVersion))) {
            return meta.id() + ": requires Sacred Mod Loader " + release + ", but this is "
                    + loaderVersion + ". The mod wasn’t loaded.";
        }
        return null;
    }
}
