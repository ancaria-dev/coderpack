package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.entity.Attributes;
import dev.ancaria.coderpack.api.entity.CombatArts;
import dev.ancaria.coderpack.api.entity.Creature;
import dev.ancaria.coderpack.api.entity.Sheet;
import dev.ancaria.coderpack.api.entity.Skills;
import dev.ancaria.coderpack.api.entity.Stats;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The packed answers the agent sends for direct queries, read back. */
class SnapshotTest {

    @Test
    void creaturesArriveWithTheirNames() {
        List<Creature> found = RealmLink.unpack(Map.of(
                "n", "2",
                "creatures", "812:50:5:10:40:222850:137608:0;17:9:31:900:1200:222900:137600:1",
                "names", "50=TYPE_NPC_GHUL01,9=TYPE_NPC_DAEMONIN"));
        assertEquals(2, found.size());
        Creature ghoul = found.get(0);
        assertEquals(812, ghoul.getRef());
        assertEquals("TYPE_NPC_GHUL01", ghoul.getTypeName());
        assertEquals(40, ghoul.getMaxHp());
        assertEquals(137608, ghoul.getY());
        assertTrue(found.get(1).isPlayer());
    }

    @Test
    void noAnswerIsNoCreatures() {
        assertTrue(RealmLink.unpack(Map.of()).isEmpty());
    }

    @Test
    void attributesAreReadByKindAndInOrder() {
        Attributes attributes = new Attributes(Map.of("values", "120,80,95,10,12,30", "points", "4"));
        assertEquals(95, attributes.get(Attributes.Kind.DEXTERITY));
        assertEquals(4, attributes.getPoints());
        List<Attributes.Kind> order = new ArrayList<>();
        attributes.forEach(entry -> order.add(entry.getKind()));
        assertEquals(List.of(Attributes.Kind.values()), order);
    }

    @Test
    void anEmptyAnswerIsAllZeroes() {
        Attributes attributes = new Attributes(Map.of());
        assertEquals(0, attributes.get(Attributes.Kind.CHARISMA));
        assertEquals(0, new Skills(Map.of()).size());
    }

    @Test
    void skillsAreSlots() {
        Skills skills = new Skills(Map.of("levels", "12,7,0,0,0,0,0,0", "points", "2"));
        assertEquals(8, skills.size());
        assertEquals(7, skills.get(1));
        assertEquals(0, skills.get(40));
        assertTrue(skills.iterator().next().getLevel() == 12);
    }

    @Test
    void combatArtsAreFoundByIdAndAspect() {
        CombatArts arts = new CombatArts(Map.of("n", "2", "arts", "0:67:0:13:2;1:35:4:5:0"));
        assertEquals(15, arts.get(0).getTotal());
        assertEquals(1, arts.find(35, 4).getIndex());
        assertNull(arts.find(35, 0));
        assertNull(arts.get(9));
    }

    @Test
    void theStatisticsPage() {
        Stats stats = new Stats(Map.of("kills", "1502", "playMillis", "3723000",
                "sinceDeath", "600000", "survival", "3.3057851"));
        assertEquals(1502, stats.getKills());
        assertEquals(Duration.ofSeconds(3723), stats.getPlayTime());
        assertEquals(Duration.ofMinutes(10), stats.getSinceDeath());
        assertEquals(3.3057851, stats.getSurvivalBonus(), 1e-9);
    }

    @Test
    void resistancesAreReadPerElement() {
        Sheet sheet = new Sheet(Map.of("armor", "430", "attackSpeed", "220", "move", "164",
                "resist", "55,70,31,12"));
        assertEquals(430, sheet.getArmorPercent());
        assertEquals(31, sheet.getResistance(Sheet.Element.MAGIC));
    }
}
