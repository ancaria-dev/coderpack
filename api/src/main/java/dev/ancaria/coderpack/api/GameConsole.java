package dev.ancaria.coderpack.api;

/**
 * The game's own console, the one a player opens in game. Reached through
 * {@link Game#getConsole()}.
 *
 * <p>The other direction, a line the player typed, is the
 * {@link dev.ancaria.coderpack.api.event.Console} event. Together they are
 * enough for a mod to own a console command: veto the line, then answer it
 * here.
 */
public interface GameConsole {

    /**
     * Prints one line in the in-game console, through the game's own output
     * function. One round-trip.
     */
    void print(String text);
}
