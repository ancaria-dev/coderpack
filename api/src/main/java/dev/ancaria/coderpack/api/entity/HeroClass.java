package dev.ancaria.coderpack.api.entity;

import javax.annotation.Nonnull;

/**
 * The nine playable classes. The id is {@code full+0x10}, the same field that
 * holds a creature type id for everything else, which is what lets a Player
 * and a monster share one Entity shape.
 *
 * <p>The game calls id 9 "Daemon" in the UI although its internal name is
 * Succubus. The UI name is the one used here.
 */
public enum HeroClass {

    UNKNOWN(0),
    SERAPHIM(1),
    GLADIATOR(2),
    BATTLEMAGE(3),
    DARK_ELF(4),
    WOOD_ELF(5),
    VAMPIRESS(6),
    VAMPIRESS_FORM(7),
    DWARF(8),
    DAEMON(9);

    private final int id;

    HeroClass(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    @Nonnull
    public static HeroClass of(int id) {
        for (HeroClass value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
