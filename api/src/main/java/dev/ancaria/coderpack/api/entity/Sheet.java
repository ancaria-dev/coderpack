package dev.ancaria.coderpack.api.entity;

import java.util.Map;

/**
 * Derived numbers from the character screen, as they were when asked. A
 * snapshot. Armour and attack speed come from the game's own getters, which
 * matched the screen exactly, so these are the screen's numbers rather than a
 * reimplementation of its formulas.
 */
public final class Sheet {

    /** The four resistances, in the order the screen lists them. */
    public enum Element { PHYSICAL, FIRE, MAGIC, POISON }

    private final int armor;
    private final int attackSpeed;
    private final int movementSpeed;
    private final int[] resistances;

    public Sheet(Map<String, String> fields) {
        this.armor = Attributes.parse(fields.get("armor"));
        this.attackSpeed = Attributes.parse(fields.get("attackSpeed"));
        this.movementSpeed = Attributes.parse(fields.get("move"));
        String[] packed = Attributes.split(fields.get("resist"));
        this.resistances = new int[Element.values().length];
        for (int i = 0; i < resistances.length && i < packed.length; i++) {
            resistances[i] = Attributes.parse(packed[i]);
        }
    }

    /** The armour bonus in percent. */
    public int getArmorPercent() {
        return armor;
    }

    public int getAttackSpeed() {
        return attackSpeed;
    }

    public int getMovementSpeed() {
        return movementSpeed;
    }

    public int getResistance(Element element) {
        return resistances[element.ordinal()];
    }
}
