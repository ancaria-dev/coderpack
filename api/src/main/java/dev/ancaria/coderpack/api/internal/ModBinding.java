package dev.ancaria.coderpack.api.internal;

import dev.ancaria.coderpack.api.Context;
import dev.ancaria.coderpack.api.SacredMod;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * For the loader only. A mod has no reason to call anything here, and nothing
 * here is part of the contract {@code Api.VERSION} numbers.
 *
 * <p>It is how a {@link SacredMod} gets its {@link Context} without a
 * constructor parameter. The loader binds the context to the current thread,
 * calls the mod's no-argument constructor, and clears the binding again
 * whatever happens. {@code SacredMod}'s own constructor, which runs before any
 * field initialiser of the subclass, takes the binding, so the context is
 * there from the subclass's first line on.
 *
 * <p>The binding is taken exactly once. A second {@code SacredMod} constructed
 * while the first is being built, a helper instance say, finds nothing and
 * has no context, rather than sharing the entry point's.
 */
public final class ModBinding {

    private static final class Binding {

        final Context context;
        final Consumer<? super SacredMod> claimed;

        Binding(Context context, Consumer<? super SacredMod> claimed) {
            this.context = context;
            this.claimed = claimed;
        }
    }

    private static final ThreadLocal<Binding> PENDING = new ThreadLocal<>();

    private ModBinding() {
    }

    /**
     * Creates {@code type} through its no-argument constructor with
     * {@code context} bound to it.
     *
     * @param claimed told which instance took the context, before the
     *                subclass's own initialisers run, so the loader can
     *                answer questions about the mod from inside its constructor
     */
    @Nonnull
    public static <T extends SacredMod> T create(Class<T> type, Context context,
                                                 Consumer<? super SacredMod> claimed)
            throws ReflectiveOperationException {
        Binding previous = PENDING.get();
        PENDING.set(new Binding(context, claimed));
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } finally {
            if (previous == null) {
                PENDING.remove();
            } else {
                PENDING.set(previous);
            }
        }
    }

    /**
     * Takes the context bound to this thread, or null when there is none.
     * Called by {@link SacredMod}'s constructor and by nothing else.
     */
    @Nullable
    public static Context pending(SacredMod claimant) {
        Binding binding = PENDING.get();
        if (binding == null) {
            return null;
        }
        PENDING.remove();
        binding.claimed.accept(claimant);
        return binding.context;
    }
}
