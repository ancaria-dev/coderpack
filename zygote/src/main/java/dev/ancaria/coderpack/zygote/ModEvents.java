package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Events;
import dev.ancaria.coderpack.api.Handle;
import dev.ancaria.coderpack.api.Priority;
import dev.ancaria.coderpack.api.event.Event;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One mod's view of the {@link Bus}. It exists to carry the mod id, which the
 * bus needs to name a listener in a log line and the mod has no reason to
 * repeat at every registration.
 */
final class ModEvents implements Events {

    private final String mod;
    private final Bus bus;

    ModEvents(String mod, Bus bus) {
        this.mod = mod;
        this.bus = bus;
    }

    @Override
    public void register(Object listener) {
        bus.register(mod, Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public <E extends Event> Handle on(Class<E> type, Priority priority,
                                       boolean ignoreCancelled, Consumer<E> listener) {
        return bus.on(mod, Objects.requireNonNull(type, "type"), priority,
                      ignoreCancelled, Objects.requireNonNull(listener, "listener"));
    }
}
