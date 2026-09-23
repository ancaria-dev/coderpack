package dev.ancaria.coderpack.api.event;

import java.util.Map;

/**
 * The hero entered a sector, the coarse grid the world is streamed in. Each
 * crossing fires once, and also opens a {@link Region}.
 */
public final class Sector extends Event {

    public Sector(Map<String, String> fields) {
        super(fields);
    }

    public int getX() {
        return (int) getNum("x");
    }

    public int getY() {
        return (int) getNum("y");
    }
}
