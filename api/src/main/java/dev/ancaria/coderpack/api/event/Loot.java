package dev.ancaria.coderpack.api.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Loot appeared: a creature dropped it, or a chest was opened. Each item is
 * already an object in the world, addressed by its ref, so a mod that wants to
 * change one can {@code reshape} or {@code retype} it from here.
 *
 * <p>Creature drops are the objects the game creates while its loot function
 * runs, minus effects; nothing is reported while a world loads.
 */
public final class Loot extends Event {

    /** One item of the drop. */
    public static final class Drop {

        private final int ref;
        private final int typeId;
        private final String typeName;

        public Drop(int ref, int typeId, String typeName) {
            this.ref = ref;
            this.typeId = typeId;
            this.typeName = typeName;
        }

        /** The object-manager reference of the dropped item. */
        public int getRef() {
            return ref;
        }

        public int getTypeId() {
            return typeId;
        }

        /** Internal name, e.g. TYPE_OBJECT_POTION_LARGE_RED. */
        @Nonnull
        public String getTypeName() {
            return typeName;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Drop that && ref == that.ref && typeId == that.typeId
                    && typeName.equals(that.typeName);
        }

        @Override
        public int hashCode() {
            return (31 * ref + typeId) * 31 + typeName.hashCode();
        }

        @Override
        @Nonnull
        public String toString() {
            return typeName + "#" + ref;
        }
    }

    private List<Drop> items;

    public Loot(Map<String, String> fields) {
        super(fields);
    }

    /** True for a chest, false for a creature. */
    public boolean isChest() {
        return getNum("chest") == 1;
    }

    /** The ref of the creature or chest the loot came from, 0 when unknown. */
    public int getSourceRef() {
        return (int) getNum("source");
    }

    public int getSourceTypeId() {
        return (int) getNum("type");
    }

    @Nullable
    public String getSourceTypeName() {
        String name = getText("name");
        return name == null || name.isEmpty() ? null : name;
    }

    @Nonnull
    public List<Drop> getItems() {
        if (items == null) {
            List<Drop> out = new ArrayList<>();
            String packed = getText("items");
            if (packed != null && !packed.isEmpty()) {
                for (String record : packed.split(";")) {
                    String[] part = record.split(":", 3);
                    if (part.length != 3) {
                        continue;
                    }
                    try {
                        out.add(new Drop(Integer.parseInt(part[0]), Integer.parseInt(part[1]), part[2]));
                    } catch (NumberFormatException skip) {
                        // One unreadable item is one lost item, not a lost drop.
                    }
                }
            }
            items = Collections.unmodifiableList(out);
        }
        return items;
    }
}
