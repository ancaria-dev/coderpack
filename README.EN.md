# Coderpack

<div align="center">

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.7.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-agent-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Version](https://img.shields.io/badge/version-0.200.0-4B5563?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-4B5563?style=for-the-badge)
![Sacred](https://img.shields.io/badge/Sacred-Community-8B1A1A?style=for-the-badge&labelColor=1C1410)

</div>

[Русский](README.md) · [Deutsch](README.DE.md)

The API you write Sacred Gold mods against, and the loader that runs them.

Coderpack watches the running game and turns what happens in it into events:
gold picked up, damage dealt, a level gained. Your mod subscribes to the events
it cares about. Some of them arrive before the game stores a value, so your mod
can change or cancel it.

The game files stay untouched. Every hook lives in memory and disappears when
the game closes.

## Getting started

A mod is a jar in `<Sacred Gold>/mods`. The Gradle plugin from
[build](https://github.com/ancaria-dev/build) builds it. The quickest way to a
ready project is the [IntelliJ IDEA plugin](https://github.com/ancaria-dev/idea)
or `coderpack new my-mod`. By hand, the build script looks like this:

```kotlin
plugins {
    id("dev.ancaria.coderpack") version "0.200.0"
}

version = "1.0.0"

sacred {
    id = "double-gold"
    displayName = "Double Gold"
    description = "Twice the loot, same purse"
    entrypoint = "com.example.DoubleGold"
    apiVersion = "0.200.0"
    author("you")
}
```

`apiVersion` adds `dev.ancaria.coderpack:api` as a `compileOnly` dependency.
The loader supplies the API at run time, so it must not end up in your jar.

Now the mod itself:

```java
package com.example;

import dev.ancaria.coderpack.api.SacredMod;
import dev.ancaria.coderpack.api.Subscribe;
import dev.ancaria.coderpack.api.event.Gold;
import dev.ancaria.coderpack.api.event.LevelUp;

public final class DoubleGold extends SacredMod {

    @Override
    public void onLoad() {
        getContext().getRegistry().getEventRegistry().register(this);
    }

    @Subscribe
    public void onLevelUp(LevelUp event) {
        getContext().log("level " + event.getLevel());
    }

    @Subscribe
    public Gold.Mutation onGold(Gold event) {
        if (event.isSpending()) {
            return Gold.Mutation.none();
        }
        return Gold.Mutation.change(event.getValue() * 2);   // decided before the game stores it
    }
}
```

`gradlew assembleSacredMod` writes the finished jar to `build/sacred-mod`. The
plugin generates `META-INF/declaration.toml` from the `sacred` block, packs
your runtime dependencies into the jar, and rejects a jar that contains loader
API classes.

## Mod lifecycle

Your entry point extends `SacredMod` and keeps a public no-argument
constructor. The loader creates the instance and hands it a `Context` in the
same step, so `getContext()` already works in a field initialiser.

`onLoad()` is where you register listeners. `onUnload()` is the last call your
mod gets, when it's unregistered or the loader shuts down. By then its
listeners are already off.

## Listeners

`register(this)` turns every public method annotated with `@Subscribe` that
takes exactly one event into a listener. The parameter type selects the event,
so there's no event name to keep in sync.

The return type says what the method may do:

- `void` only observes.
- The event's own `Mutation` decides: `none()`, `reset()`, `veto()`, or a new
  value such as `change(value)`.

Events are read-only. Returning a mutation is the only way to change one.

You can also register without annotations. On
`getContext().getRegistry().getEventRegistry()`, `on(...)` adds an observer
and `decide(...)` a decider. Both return a `Handle`. It unregisters the
listener and tells you which mod registered it, for which event, and at which
priority. `getEvents()` lists the handles of every mod.

`getModRegistry()` is the other half of the registry. It lists the loaded mods,
loads one more jar with `register(path)`, and removes a mod with
`unregister(id)`, listeners and all.

### Order and vetoes

`priority` on `@Subscribe` sets the dispatch order: `FIRST`, `NORMAL` (the
default), `LAST`, then `MONITOR`. Within one priority, registration order
wins. The loader folds in each answer before the next listener runs, so
`getValue()` already includes every earlier decision.

A veto doesn't stop dispatch. A listener with `ignoreVetoed = true` is skipped
once an earlier listener has vetoed the event. A `MONITOR` listener sees the
final decision and must return `void`. The mod linter rejects a `MONITOR`
method that returns a mutation, and the loader drops such a mutation with one
warning.

Listeners on a decidable event run while the game thread waits, so keep them
short. The host's deadline is 250 ms, and it checks every 125 ms. Once the
deadline passes, the host lets the original value through.

## Events

Eight events can be decided: `Gold`, `Experience`, `Damage`, `Skill`,
`Attribute`, `CombatArt`, `Pickup`, and `Console`. A veto on `Console` takes
the typed line as your mod's own command.

The rest report something that has already happened:

- The hero and the session: `Hero`, `World`, `Save`, `Load`, `Position`,
  `LevelUp`, `Death`, `NearDeath`.
- The outcome of a decision: `HealthChanged`, `MaxHealthChanged`,
  `GoldChanged`, `ExperienceChanged`, `SkillChanged`, `AttributeChanged`,
  `SkillPointsChanged`, `AttributePointsChanged`, `CombatArtChanged`.
- The world: `Region`, `Sector`, `Spawn`, `Despawn`, `MobHit`, `MobDeath`.
- The journal and quests: `Kill`, `Resurrection`, `Discovery`, `Quest`.
- Items: `Loot`, `Drink`, `Trade`, `Moved`, `Equip`, `Stored`.
- `Unknown`, for a wire event that no type covers yet.

Whether an event is decidable depends on where its hook sits. A veto works
only when the hook runs before the game writes the value.
[docs/EVENTS.md](docs/EVENTS.md) describes the full contract.

## Talking to the game

`getContext().getGame()` gives you direct access.

- `getWorld().getEntityRegistry().getPlayer()` returns the hero. Level, HP,
  gold, experience, and position come from the latest observed state and cost
  nothing to read. The hero also offers `teleport`, `setGold`, `setHp`,
  `addExp`, and `kill`.
- `getAttributes()`, `getSkills()`, `getCombatArts()`, `getStats()` (the
  journal's Statistics page), and `getSheet()` (armour, attack and movement
  speed, resistances) ask the game on every call and return a snapshot.
  `setAttribute`, `setSkill`, and `setCombatArt` write back.
- The same `EntityRegistry` lists the creatures the game holds, near a point
  or all of them, and reads one by its ref. `getWorld()` sets a creature's HP,
  kills it, and reports the region and sector the hero last entered.
- `getTypeRegistry()` offers `getTypeName`, `getTypeId`, `types`, `retype`,
  and `reshape`. `retype` changes an item's type label and look for good, but
  keeps its behaviour and modifiers. `reshape` turns an item into a copy of
  another, modifiers included.
- `getUiString` reads the game's localised text, `getConsole().print(text)`
  writes a line to the in-game console, and `getDirectory()` returns the game
  folder.

Every collection the API returns is unmodifiable. A command waits up to two
seconds for the agent to reply.

## Logging

`getContext().log` appends a line to `<Sacred Gold>/logs/mods.log`, shared by
all mods:

```
[2026-09-23 14:05:31.042] [double-gold]: level 12
```

It never waits for the disk: a loader thread writes the lines in batches. That
makes it safe inside a listener that holds up the game, and from your own
threads. `getContext().print` sends the same line to the host console instead.

Any other logging works too. Log4j2, Logback, slf4j-simple, and
`java.util.logging` need no setup, and the JVM's stdout and stderr reach the
host console. The loader points `System.out` at stderr before the first mod
loads, which is why a plain `println` shows up there. Still, prefer `log` and
`print`: with five mods installed, they're the only ones that say which mod
spoke.

## Kotlin and Groovy

`dev.ancaria.coderpack:api-kotlin` puts Kotlin syntax on top of the same API.
It adds no features: every declaration forwards to a method in `api`. The
loader doesn't provide it, so a mod that uses it packs it next to the Kotlin
standard library. Projects from `coderpack new --language kotlin` already
depend on it.

```kotlin
dependencies {
    implementation("dev.ancaria.coderpack:api-kotlin:0.200.0")
}
```

```kotlin
package com.example

import dev.ancaria.coderpack.api.SacredMod
import dev.ancaria.coderpack.api.event.Gold
import dev.ancaria.coderpack.ktx.mutate
import dev.ancaria.coderpack.ktx.on

class DoubleGold : SacredMod() {

    override fun onLoad() {
        context.on<Gold> { if (!it.isSpending) mutate { Gold.Mutation.change(it.value * 2) } }
    }
}
```

`SacredMod` is the same Java class, and Kotlin reads `getContext()` as
`context`. `on<Gold>` takes the event as a type argument, and
`context.events { }` groups several registrations in one block. `mutate { }`
decides. It exists only on decidable events, so `on<Death> { mutate { ... } }`
doesn't compile. Every reader in the API is a getter, so `it.value` and
`it.isSpending` are properties already.

`@Subscribe` and `context.registry.eventRegistry` work exactly as in Java.
None of the extensions are required.

Groovy works as well: `coderpack new --language groovy` creates the project and
packs the Groovy runtime into the mod jar.

## Compatibility

The descriptor carries `api = "[3,4)"` by default. That's the range of API
contracts the loader checks before starting the mod. The contract is separate
from the artifact version (`0.200.0` today). The artifact version can change
with any release. The contract changes only when mods built against the old
one would break.

- `apiRange` widens or narrows that range, for example `apiRange = "[3,5)"`.
  It must include the contract of your toolchain.
- `loaderRange` limits which Sacred Mod Loader releases may run the mod.

If either range excludes the current version, the launcher lists the mod with
its checkbox disabled and shows why. The JVM loader checks the same ranges
before running mod code and logs one refusal. A missing or invalid `api` field
is refused as well. An invalid `loader` range is refused too, but when
`<Sacred Gold>/launcher/VERSION` is missing, the loader skips the `loader`
check.

## How it works

Sacred Gold is a 32-bit process, so the JVM runs next to the game, not inside
it. [protocol](https://github.com/ancaria-dev/protocol) is the Rust host in
between. It injects the agent, starts the JVM, and carries the line protocol.
On the JVM side, a reader thread routes command replies and queues events, and
the `sal-dispatch` thread calls mod listeners one event at a time.

| Path | Contents |
|---|---|
| `agent/` | Plain JavaScript that Frida injects into the game. It hooks individual x86 instructions and reports what it sees. Addresses come from `gen/addr.js`. |
| `api/` | `dev.ancaria.coderpack:api`, which mods compile against. No run-time dependencies, JSR 305 at compile time only. |
| `api-kotlin/` | `dev.ancaria.coderpack:api-kotlin`, inline Kotlin extensions over `api` in `dev.ancaria.coderpack.ktx`. |
| `zygote/` | `dev.ancaria.coderpack:zygote`. It reads frames from the host, finds mod jars, gives each mod its own class loader, and dispatches events. |
| `tools/`, `tests/`, `docs/` | Address and safety tools, test harnesses, and [RUNNING.md](docs/RUNNING.md) on building, running, and bisecting crashes. |

## Building

You need JDK 21 or newer and Python 3.11. The Gradle wrapper downloads Gradle
9.7.1. `tools/hooksafe.py` also needs `pefile` and `capstone`, and
`tests/buildcheck.js` needs Node.

```
gradlew build                  the three jars, in */build/libs
gradlew publishToMavenLocal    lets a mod build resolve the API from mavenLocal
python tools/addr.py           regenerates agent/src/gen/addr.js
python tools/hooksafe.py       refuses hook sites a trampoline would corrupt
node tests/buildcheck.js       checks the agent against a fake process
python tests/replay.py         checks the Java side with no game running
```

### Addresses

`addr.py` generates the game addresses from the registry in
[mappings](https://github.com/ancaria-dev/mappings). Nobody copies them by
hand. It looks for the registry in this order: a path on the command line,
`$CODERPACK_MAPPINGS`, the sibling `../mappings` checkout, then
`build/mappings/mappings.json`. If none exists, it downloads the file from
GitHub into that cache. The revision comes from `.mappings-ref`, currently
`master`. Put a tag or commit there for a reproducible build.

Every address targets `pureHD.exe` 2.0.2.118. The loader also attaches to
`Sacred.exe` and `Game.exe`, but the agent warns when the bytes at the hook
sites don't match `agent/signatures.json`. It installs the hooks anyway.

### Hook safety

Frida replaces at least five bytes at a hook site. After a short instruction,
the patch spills into the next one. `hooksafe.py` rejects a site when a branch
lands inside the replaced bytes, when two patches overlap, or when relocation
separates a flag-setting instruction from its conditional branch. Other risky
forms produce warnings.

By default it reads the game from
`D:\SteamLibrary\steamapps\common\Sacred Gold`. Pass an executable or its
folder to use another one. When the folder holds no supported executable, the
check prints `skipped`. `--signatures` rewrites `agent/signatures.json` from
the selected binary.

### Replay

`replay.py` feeds the loader a scripted event stream, checks every verdict, and
verifies the trace, an unknown event included. It needs the two built jars in
`*/build/libs` and at least one built mod. It looks in
`../mods/*/build/sacred-mod/` or takes a folder argument. Without a mod it
stops with `no mod jars found` and a nonzero status. It can't prove that the
game commits the value it was asked to.

## Releases

On `master`, CI publishes when the remote has no `v<version>` tag for the
version in `gradle.properties`. It uploads `api`, `api-kotlin`, and `zygote` to
Maven Central as one signed bundle and attaches `api.jar`, `zygote.jar`, and
`agent.zip` to a GitHub release. Then it creates the tag. `agent.zip` carries
the generated address table, so `protocol` can build without a coderpack
checkout next to it.

The Central upload waits in the portal until someone presses Publish. A Central
artifact can never be deleted, so check it first. The order of releases across
repositories is in the root
[CONTRIBUTING](https://github.com/ancaria-dev/.github/blob/master/CONTRIBUTING.EN.md).

## License

MIT, see [LICENSE](LICENSE).
