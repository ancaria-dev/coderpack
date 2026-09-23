package dev.ancaria.coderpack.api.event;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * An event the agent sends that no SDK type covers yet.
 *
 * <p>The agent is allowed to run ahead of the API, and a mod that wants
 * everything, a tracer most obviously, should still see it. Subscribing to
 * {@link Event} catches these along with the rest. {@link #getName()} is the wire
 * name and {@link #getFields()} is the whole payload.
 */
public final class Unknown extends Event {

    private final String name;

    public Unknown(String name, Map<String, String> fields) {
        super(fields);
        this.name = name;
    }

    @Nonnull
    public String getName() {
        return name;
    }
}
