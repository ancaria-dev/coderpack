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
    public record Drop(int ref, int typeId, String typeName) {
    }

    private List<Drop> items;

    public Loot(Map<String, String> fields) {
        super(fields);
    }

    /** True for a chest, false for a creature. */
    public boolean chest() {
        return num("chest") == 1;
    }

    /** The ref of the creature or chest the loot came from, 0 when unknown. */
    public int sourceRef() {
        return (int) num("source");
    }

    public int sourceTypeId() {
        return (int) num("type");
    }

    @Nullable
    public String sourceTypeName() {
        String name = text("name");
        return name == null || name.isEmpty() ? null : name;
    }

    @Nonnull
    public List<Drop> items() {
        if (items == null) {
            List<Drop> out = new ArrayList<>();
            String packed = text("items");
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
