package dev.ancaria.coderpack.api.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The combat arts the hero owns, as they were when asked. A snapshot.
 *
 * <p>Only owned arts are here: the combat-art screen also lists ones the
 * character has not learned, and those have no record. The order is the
 * game's storage order, not the screen's.
 *
 * <p>An art is {@code (artId, aspect)}. Ids are per class, so the same name has
 * a different id on another class, and the plain weapon moves several classes
 * share by name are told apart by the aspect. Names are not in the game's
 * record; match on the pair.
 */
public final class CombatArts implements Iterable<CombatArts.Art> {

    /**
     * One art. {@code level} is the base level runes raise, {@code bonus} what
     * gear adds on top, and the tooltip shows their sum.
     */
    public static final class Art {

        private final int index;
        private final int artId;
        private final int aspect;
        private final int level;
        private final int bonus;

        public Art(int index, int artId, int aspect, int level, int bonus) {
            this.index = index;
            this.artId = artId;
            this.aspect = aspect;
            this.level = level;
            this.bonus = bonus;
        }

        /** The storage index, the one {@code CombatArt.getIndex()} reports. */
        public int getIndex() {
            return index;
        }

        /** The art's id within its class. */
        public int getArtId() {
            return artId;
        }

        public int getAspect() {
            return aspect;
        }

        /** The base level, the number runes raise. */
        public int getLevel() {
            return level;
        }

        /** What gear adds on top of the base level. */
        public int getBonus() {
            return bonus;
        }

        /** The number the tooltip shows. */
        public int getTotal() {
            return level + bonus;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Art that && index == that.index && artId == that.artId
                    && aspect == that.aspect && level == that.level && bonus == that.bonus;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(index, artId, aspect, level, bonus);
        }

        @Override
        @Nonnull
        public String toString() {
            return "Art[index=" + index + ", artId=" + artId + ", aspect=" + aspect
                    + ", level=" + level + ", bonus=" + bonus + "]";
        }
    }

    private final List<Art> arts;

    public CombatArts(Map<String, String> fields) {
        String packed = fields.get("arts");
        List<Art> out = new ArrayList<>();
        if (packed != null && !packed.isEmpty()) {
            for (String record : packed.split(";")) {
                String[] part = record.split(":");
                if (part.length != 5) {
                    continue;
                }
                out.add(new Art(Attributes.parse(part[0]), Attributes.parse(part[1]),
                        Attributes.parse(part[2]), Attributes.parse(part[3]),
                        Attributes.parse(part[4])));
            }
        }
        this.arts = Collections.unmodifiableList(out);
    }

    /** By storage index, the same index {@code CombatArt.getIndex()} reports. */
    @Nullable
    public Art get(int index) {
        return index >= 0 && index < arts.size() ? arts.get(index) : null;
    }

    @Nullable
    public Art find(int artId, int aspect) {
        for (Art art : arts) {
            if (art.getArtId() == artId && art.getAspect() == aspect) {
                return art;
            }
        }
        return null;
    }

    public int size() {
        return arts.size();
    }

    @Override
    @Nonnull
    public Iterator<Art> iterator() {
        return arts.iterator();
    }
}
