package dev.ancaria.coderpack.zygote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A mod's descriptor, {@code META-INF/declaration.toml} inside the jar.
 *
 * <pre>
 * id = "example"
 * name = "Example Mod"
 * version = "0.1.0"
 * description = "What a player sees in the launcher's mod list."
 * entrypoint = "com.example.ExampleMod"
 * api = "[2,3)"
 * loader = "[0.1.20,)"
 * authors = ["Somebody", "Somebody Else"]
 * website = "https://ancaria.dev"
 * repository = "https://github.com/ancaria-dev/mods"
 * </pre>
 *
 * <p>{@code id} is the handle, lowercase and hyphenated, and what the
 * launcher writes into its enabled list. {@code name} and {@code description} are the two
 * things a player reads, so they are written like prose and not like keys.
 * {@code api} is the range of API contracts the mod was built for and
 * {@code loader} the range of launcher releases it wants. The Gradle plugin
 * writes both and {@link Compat} decides what to do with them.
 *
 * <p>An unreadable {@code api} is still a parsed descriptor. A mod refused for
 * its API has to keep its name and its description, because the launcher lists
 * it and tells the player why it is off. Dropping it here would make it
 * disappear instead.
 *
 * Only {@code key = "value"} and a flat array of strings are understood. That
 * is enough for a descriptor, and it saves pulling a TOML parser into the
 * loader.
 */
record Meta(String id, String name, String version, String description,
            String entrypoint, String api, String loader, List<String> authors,
            String website, String repository) {

    /** "Example Mod 0.1.0 by Somebody", for the line printed when it loads. */
    String credit() {
        String line = name + " " + version;
        return authors.isEmpty() ? line : line + " by " + String.join(", ", authors);
    }

    static Meta parse(String text) {
        Map<String, String> values = new HashMap<>();
        for (String line : text.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int split = trimmed.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = trimmed.substring(0, split).strip();
            String value = trimmed.substring(split + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        String id = values.get("id");
        String entrypoint = values.get("entrypoint");
        if (id == null || entrypoint == null) {
            return null;
        }
        return new Meta(id, values.getOrDefault("name", id),
                        values.getOrDefault("version", "0"),
                        values.getOrDefault("description", ""), entrypoint,
                        values.getOrDefault("api", ""),
                        values.getOrDefault("loader", ""),
                        list(values.get("authors")),
                        values.getOrDefault("website", ""),
                        values.getOrDefault("repository", ""));
    }

    /** {@code ["a", "b"]} to a list. Anything else is one entry, or none. */
    private static List<String> list(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String body = raw.startsWith("[") && raw.endsWith("]")
                ? raw.substring(1, raw.length() - 1)
                : raw;
        List<String> found = new ArrayList<>();
        for (String part : body.split(",")) {
            String value = part.strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isBlank()) {
                found.add(value);
            }
        }
        return found;
    }
}
