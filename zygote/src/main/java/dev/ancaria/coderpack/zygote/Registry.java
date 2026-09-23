package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.event.Attribute;
import dev.ancaria.coderpack.api.event.AttributeChanged;
import dev.ancaria.coderpack.api.event.AttributePointsChanged;
import dev.ancaria.coderpack.api.event.CombatArt;
import dev.ancaria.coderpack.api.event.CombatArtChanged;
import dev.ancaria.coderpack.api.event.Console;
import dev.ancaria.coderpack.api.event.Damage;
import dev.ancaria.coderpack.api.event.Death;
import dev.ancaria.coderpack.api.event.Despawn;
import dev.ancaria.coderpack.api.event.Discovery;
import dev.ancaria.coderpack.api.event.Drink;
import dev.ancaria.coderpack.api.event.Equip;
import dev.ancaria.coderpack.api.event.Event;
import dev.ancaria.coderpack.api.event.Experience;
import dev.ancaria.coderpack.api.event.ExperienceChanged;
import dev.ancaria.coderpack.api.event.Gold;
import dev.ancaria.coderpack.api.event.GoldChanged;
import dev.ancaria.coderpack.api.event.HealthChanged;
import dev.ancaria.coderpack.api.event.Hero;
import dev.ancaria.coderpack.api.event.Kill;
import dev.ancaria.coderpack.api.event.LevelUp;
import dev.ancaria.coderpack.api.event.Load;
import dev.ancaria.coderpack.api.event.Loot;
import dev.ancaria.coderpack.api.event.MaxHealthChanged;
import dev.ancaria.coderpack.api.event.MobDeath;
import dev.ancaria.coderpack.api.event.MobHit;
import dev.ancaria.coderpack.api.event.Moved;
import dev.ancaria.coderpack.api.event.NearDeath;
import dev.ancaria.coderpack.api.event.Pickup;
import dev.ancaria.coderpack.api.event.Position;
import dev.ancaria.coderpack.api.event.Quest;
import dev.ancaria.coderpack.api.event.Region;
import dev.ancaria.coderpack.api.event.Resurrection;
import dev.ancaria.coderpack.api.event.Save;
import dev.ancaria.coderpack.api.event.Sector;
import dev.ancaria.coderpack.api.event.Skill;
import dev.ancaria.coderpack.api.event.SkillChanged;
import dev.ancaria.coderpack.api.event.SkillPointsChanged;
import dev.ancaria.coderpack.api.event.Spawn;
import dev.ancaria.coderpack.api.event.Stored;
import dev.ancaria.coderpack.api.event.Trade;
import dev.ancaria.coderpack.api.event.Unknown;
import dev.ancaria.coderpack.api.event.World;

import java.util.Map;
import java.util.function.Function;

/**
 * Wire name to event object. This is the only place that knows both, which is
 * what lets the agent add a field without an SDK change and lets an event be
 * renamed on one side at a time.
 */
final class Registry {

    private static final Map<String, Function<Map<String, String>, Event>> TYPES = Map.ofEntries(
            Map.entry("health.damage", Damage::new),
            Map.entry("health.changed", HealthChanged::new),
            Map.entry("health.max_changed", MaxHealthChanged::new),
            Map.entry("health.death", Death::new),
            Map.entry("health.near_death", NearDeath::new),
            Map.entry("entity.damage", MobHit::new),
            Map.entry("entity.death", MobDeath::new),
            Map.entry("gold.delta", Gold::new),
            Map.entry("gold.changed", GoldChanged::new),
            Map.entry("exp.gain", Experience::new),
            Map.entry("exp.changed", ExperienceChanged::new),
            Map.entry("skill.change", Skill::new),
            Map.entry("skill.changed", SkillChanged::new),
            Map.entry("skillpoints.changed", SkillPointsChanged::new),
            Map.entry("attr.spend", Attribute::new),
            Map.entry("attr.changed", AttributeChanged::new),
            Map.entry("attrpoints.changed", AttributePointsChanged::new),
            Map.entry("item.pickup", Pickup::new),
            Map.entry("item.stored", Stored::new),
            Map.entry("item.equip", Equip::new),
            Map.entry("item.moved", Moved::new),
            Map.entry("pos.changed", Position::new),
            Map.entry("level.changed", LevelUp::new),
            Map.entry("hero.captured", Hero::new),
            Map.entry("agent.ready", f -> new World(World.Phase.ATTACHED, f)),
            Map.entry("session.world_loading", f -> new World(World.Phase.LOADING, f)),
            Map.entry("session.world_loaded", f -> new World(World.Phase.LOADED, f)),
            Map.entry("session.hero_loaded", f -> new World(World.Phase.HERO_LOADED, f)),
            Map.entry("session.hero_terminated", f -> new World(World.Phase.HERO_TERMINATED, f)),
            Map.entry("world.region_enter", f -> new Region(true, f)),
            Map.entry("world.region_exit", f -> new Region(false, f)),
            Map.entry("world.sector_enter", Sector::new),
            Map.entry("entity.spawn", Spawn::new),
            Map.entry("entity.despawn", Despawn::new),
            Map.entry("journal.kill", Kill::new),
            Map.entry("journal.resurrection", Resurrection::new),
            Map.entry("journal.discovery", Discovery::new),
            Map.entry("art.raise", CombatArt::new),
            Map.entry("art.changed", CombatArtChanged::new),
            Map.entry("session.saved", Save::new),
            Map.entry("session.load_start", f -> new Load(false, f)),
            Map.entry("session.load_done", f -> new Load(true, f)),
            Map.entry("console.line", Console::new),
            Map.entry("quest.start", f -> new Quest(true, f)),
            Map.entry("quest.end", f -> new Quest(false, f)),
            Map.entry("loot.drop", Loot::new),
            Map.entry("item.drink", Drink::new),
            Map.entry("trade.buy", f -> new Trade(true, f)),
            Map.entry("trade.sell", f -> new Trade(false, f)),
            Map.entry("session.detached", f -> new World(World.Phase.DETACHED, f)));

    private Registry() {
    }

    /**
     * Never null. An event with no SDK type becomes an {@link Unknown} carrying
     * the wire name. The agent is allowed to run ahead of the API, and a mod
     * that subscribes to {@code Event}, a tracer for instance, has to see those
     * too.
     */
    static Event build(Frame frame) {
        Function<Map<String, String>, Event> factory = TYPES.get(frame.name());
        return factory == null
                ? new Unknown(frame.name(), frame.fields())
                : factory.apply(frame.fields());
    }
}
