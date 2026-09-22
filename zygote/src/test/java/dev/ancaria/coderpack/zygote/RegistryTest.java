package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.GoldChanged;
import dev.ancaria.coderpack.api.event.HealthChanged;
import dev.ancaria.coderpack.api.event.SkillPointsChanged;
import dev.ancaria.coderpack.api.event.Unknown;
import dev.ancaria.coderpack.api.event.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wire names to event types, including the ones that used to arrive as Unknown. */
class RegistryTest {

    private static Event build(String name, Map<String, String> fields) {
        return Registry.build(new Frame("EVT", 0, name, fields));
    }

    @Test
    void everyEventTheAgentSendsHasAType() {
        // Every evt() name in agent/src. A name missing here is a mod seeing
        // Unknown for something the loader already knows how to describe.
        List<String> sent = List.of(
                "agent.ready", "session.world_loading", "session.world_loaded",
                "session.hero_loaded", "session.hero_terminated", "hero.captured",
                "health.damage", "health.changed", "health.max_changed",
                "health.death", "health.near_death", "entity.damage", "entity.death",
                "gold.delta", "gold.changed", "exp.gain", "exp.changed",
                "skill.change", "skill.changed", "skillpoints.changed",
                "attr.spend", "attr.changed", "attrpoints.changed",
                "level.changed", "pos.changed", "item.pickup", "item.stored",
                "item.equip", "item.moved",
                "world.region_enter",
                "world.region_exit",
                "world.sector_enter",
                "entity.spawn",
                "entity.despawn",
                "journal.kill",
                "journal.resurrection",
                "journal.discovery");
        for (String name : sent) {
            assertFalse(build(name, Map.of()) instanceof Unknown, name);
        }
    }

    @Test
    void anUnmappedNameStillArrives() {
        Event event = build("weather.rain_start", Map.of("intensity", "3"));
        assertEquals("weather.rain_start", assertInstanceOf(Unknown.class, event).name());
    }

    @Test
    void theAgentAnnouncingItselfIsTheAttachedPhase() {
        Event event = build("agent.ready", Map.of("base", "0x400000"));
        assertEquals(World.Phase.ATTACHED, assertInstanceOf(World.class, event).phase());
    }

    @Test
    void healthCarriesTheBlow() {
        HealthChanged event = assertInstanceOf(HealthChanged.class, build("health.changed",
                Map.of("kind", "damage", "damage", "30", "prev", "100", "next", "70", "max", "120")));
        assertEquals(30, event.damage());
        assertEquals(100, event.previous());
        assertEquals(70, event.hp());
        assertEquals(120, event.maxHp());
    }

    @Test
    void goldReportsTheTotalAndTheStep() {
        GoldChanged event = assertInstanceOf(GoldChanged.class,
                build("gold.changed", Map.of("next", "150", "delta", "100")));
        assertEquals(150, event.gold());
        assertEquals(100, event.delta());
    }

    @Test
    void pointsKnowAGrantFromASpend() {
        SkillPointsChanged spent = assertInstanceOf(SkillPointsChanged.class,
                build("skillpoints.changed", Map.of("prev", "3", "next", "2")));
        SkillPointsChanged granted = assertInstanceOf(SkillPointsChanged.class,
                build("skillpoints.changed", Map.of("prev", "2", "next", "4", "reason", "7")));
        assertFalse(spent.granted());
        assertTrue(granted.granted());
    }
}
