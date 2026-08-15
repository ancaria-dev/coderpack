package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The player moved. Fires only when the HUD coordinates change, not on every
 * sample -- the game has no cheap "player moved" writer, so the position is
 * read at points it already passes through often.
 */
public final class Position extends Event {

    public Position(Map<String, String> fields) {
        super(fields);
    }

    // The one event that parses instead of memoizing, and the only place the
    // reasoning fits.
    //
    // This is the highest-frequency event there is. It is sampled at three
    // sites the game already runs constantly and fires on every change of the
    // HUD coordinate, which while walking is most frames -- where Damage,
    // Pickup and the rest arrive a handful of times a minute.
    //
    // And it is read once. A handler takes x and y, or hudX and hudY, and does
    // something with them; there is no condition-then-arithmetic shape here
    // that reads the same getter twice, because a coordinate is not a value to
    // reason about, it is a value to use. The loader itself is the proof --
    // PlayerLink calls x() and y() exactly once on every one of these, before
    // any mod is even asked -- so with the memo the allocation would happen on
    // every pos.changed whether a mod subscribed or not.
    //
    // Hot plus read-once is exactly where Event's memo loses. Measured on the
    // same two-field read: 24 bytes a frame this way, 232 with the memo. It
    // would buy nothing, on the hottest path in the loader. Everything else on
    // the bus keeps num(); see the comment there.
    /** World coordinate. The HUD shows this divided by 53.66563, truncated. */
    public int x() {
        return (int) once("x");
    }

    public int y() {
        return (int) once("y");
    }

    public int hudX() {
        return (int) once("uiX");
    }

    public int hudY() {
        return (int) once("uiY");
    }
}
