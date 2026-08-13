package dev.ancaria.coderpack.api;

/**
 * The version of this API contract, written into every mod's descriptor as
 * {@code api = "1"} and checked by the loader before a mod is started.
 *
 * <p>This is not the artifact version. {@code dev.ancaria.coderpack:api} is at
 * 0.1.0 and that number moves with every release; this one moves only when a
 * mod compiled against the old API would break against the new one -- a method
 * removed, a signature changed, an event class renamed. A mod that loads and
 * then dies on a {@code NoSuchMethodError} halfway through a dispatch is worse
 * than a mod that never loads, so the loader refuses the mismatch instead.
 *
 * <p>Raising this is one edit here and one in each of the two places that
 * cannot see this class: {@code Descriptor.API} in the Gradle plugin, which
 * writes the field, and {@code mods.API} in the launcher, which reads it
 * without a JVM.
 */
public final class Api {

    /** The major this loader implements. */
    public static final int VERSION = 1;

    private Api() {
    }
}
