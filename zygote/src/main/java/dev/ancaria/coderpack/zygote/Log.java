package dev.ancaria.coderpack.zygote;

/** Everything Coderpack says goes to stderr; stdout belongs to the protocol. */
public final class Log {

    private Log() {
    }

    public static void info(String message) {
        System.err.println("[coderpack] " + message);
    }

    public static void warn(String message) {
        System.err.println("[coderpack] " + message);
    }

    public static void error(String message, Throwable cause) {
        System.err.println("[coderpack] " + message + ": " + cause);
        cause.printStackTrace(System.err);
    }
}
