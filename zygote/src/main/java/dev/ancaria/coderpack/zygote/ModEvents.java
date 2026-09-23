package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.EventRegistry;
import dev.ancaria.coderpack.api.Handle;
import dev.ancaria.coderpack.api.Priority;
import dev.ancaria.coderpack.api.event.Decides;
import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.EventMutation;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One mod's view of the {@link Bus}. It exists to carry the mod, which the bus
 * needs to name a listener in a log line and to take every listener off when
 * the mod goes, and which the mod has no reason to repeat at every
 * registration.
 */
final class ModEvents implements EventRegistry {

    private final LoadedMod mod;
    private final Bus bus;

    ModEvents(LoadedMod mod, Bus bus) {
        this.mod = mod;
        this.bus = bus;
    }

    @Override
    public List<Handle> register(Object listener) {
        return bus.register(mod, Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public <E extends Event> Handle on(Class<E> type, Priority priority,
                                       boolean ignoreVetoed, Consumer<E> listener) {
        return bus.on(mod, Objects.requireNonNull(type, "type"),
                      Objects.requireNonNull(priority, "priority"), ignoreVetoed,
                      Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public <M extends EventMutation, E extends Event & Decides<M>> Handle decide(
            Class<E> type, Priority priority, boolean ignoreVetoed,
            Function<E, M> listener) {
        return bus.decide(mod, Objects.requireNonNull(type, "type"),
                          Objects.requireNonNull(priority, "priority"), ignoreVetoed,
                          Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public List<Handle> getEvents() {
        return bus.handles();
    }

    @Override
    public boolean unregister(Handle handle) {
        return bus.drop(Objects.requireNonNull(handle, "handle"));
    }

    @Override
    public int unregister(Object listener) {
        return bus.dropTarget(Objects.requireNonNull(listener, "listener"), null);
    }

    @Override
    public int unregister(Object listener, Class<? extends Event> type) {
        return bus.dropTarget(Objects.requireNonNull(listener, "listener"),
                              Objects.requireNonNull(type, "type"));
    }
}
