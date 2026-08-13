package dev.ancaria.coderpack.api;

/** A mod. Coderpack instantiates the class named by {@code declaration.toml} and calls this once. */
public interface SacredMod {

    /**
     * Register listeners here. The world may not exist yet -- wait for a
     * {@code World} or {@code Hero} event before touching the player.
     */
    void onLoad(Context context);
}
