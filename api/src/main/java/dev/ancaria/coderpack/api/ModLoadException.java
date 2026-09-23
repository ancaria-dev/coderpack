package dev.ancaria.coderpack.api;

import javax.annotation.Nullable;

/**
 * A mod jar that {@link ModRegistry#register} could not load. The message
 * says why in the words the loader would log.
 */
public class ModLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModLoadException(String message) {
        super(message);
    }

    public ModLoadException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
