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

Write Java mods for Sacred Gold, the 2004 action RPG.

Coderpack attaches to the running game and turns selected operations into
events. A mod subscribes to the events it needs. Events fired before a game
write can be changed or vetoed. Coderpack never patches files in the game
directory. Its hooks disappear when the game closes.

## Writing a mod

A mod is a JAR file installed in `<Sacred Gold>/mods`. The Gradle plugin from
[ancaria-dev/build](https://github.com/ancaria-dev/build) builds it:

```kotlin
plugins {
    id("dev.ancaria.coderpack") version "0.101.0"
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

`gradlew assembleSacredMod` writes the verified fat JAR to
`build/sacred-mod`. The plugin generates `META-INF/declaration.toml` from the
`sacred` block and packs runtime dependencies beside the mod’s classes.
`apiVersion` adds `dev.ancaria.coderpack:api` as a **compileOnly** dependency.
The loader supplies the API at runtime, and the verifier rejects mod JARs that
contain loader API classes.

By default, the descriptor contains `api = "[3,4)"`. This is the API contract
range checked before the loader starts the mod. It is separate from the
`dev.ancaria.coderpack:api` artifact version, currently `0.200.0`. The artifact
version can change with each release. The contract changes only when a mod
compiled against the previous API would break.

Set `apiRange` when a mod supports a different range of contracts, for example
`apiRange = "[3,5)"`. The range must include the contract used by the
toolchain. `loaderRange` can restrict the Sacred Mod Loader releases that may
run the mod. If either range excludes the current version, the launcher lists
the mod but disables its checkbox and shows the refusal reason. The JVM loader
checks the same ranges before running mod code and logs one refusal. A missing
or invalid `api` field is also refused. If `<Sacred Gold>/launcher/VERSION` is
missing, the JVM loader skips the `loader` check.
An invalid `loader` range is refused when present.

The mod itself looks like this:

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

The entry point extends `SacredMod` and keeps a public no-argument
constructor. The loader creates it and hands it its `Context` while doing so,
so `getContext()` already works in a field initialiser. `onUnload()` is the
last call a mod gets, when it is unregistered or the loader shuts down, and
comes after its listeners are already off.

A supported listener method is public and takes exactly one event parameter.
The parameter type selects the event, so there is no separate event name to
maintain. The return type says what the method may do. `void` only observes.
A method that decides returns that event’s own `Mutation`: `none()`,
`reset()`, `veto()`, or a new value such as `change(value)`. Events are read-only, so returning a
mutation is the only way to change one. For dynamic registration,
`getContext().getRegistry().getEventRegistry().on(...)` registers an observer
and `decide(...)` a decider. Both return a `Handle` that can unregister the
listener and says which mod registered it, for which event and at which
priority; `getEvents()` lists every mod's handles. `getModRegistry()`, the
other half of the registry, lists the loaded mods, loads one more jar with
`register(path)` and takes a mod out with `unregister(id)`, listeners and
all.

The `priority` value on `@Subscribe` controls dispatch order: `FIRST`, `NORMAL` by default,
`LAST`, then `MONITOR`. Registration order decides the order within one
priority. The loader folds each answer in before the next listener runs, so
`getValue()` includes every earlier decision. A veto does not stop dispatch. With
`ignoreVetoed = true`, a listener is skipped if an earlier listener vetoed the
event. A `MONITOR` listener sees the final decision and must return `void`. The
mod linter rejects a `MONITOR` method that returns a mutation, and the loader
drops such a mutation with one warning.

Eight events can be decided: `Gold`, `Experience`, `Damage`, `Skill`,
`Attribute`, `CombatArt`, `Pickup`, and `Console`, whose veto takes a typed
console line as a mod's own command. Read-only events report something that
has already happened:

- the hero and the session: `Hero`, `World`, `Save`, `Load`, `Position`,
  `LevelUp`, `Death`, `NearDeath`;
- what a decision came to: `HealthChanged`, `MaxHealthChanged`,
  `GoldChanged`, `ExperienceChanged`, `SkillChanged`, `AttributeChanged`,
  `SkillPointsChanged`, `AttributePointsChanged`, `CombatArtChanged`;
- the world: `Region`, `Sector`, `Spawn`, `Despawn`, `MobHit`, `MobDeath`;
- the journal and quests: `Kill`, `Resurrection`, `Discovery`, `Quest`;
- items: `Loot`, `Drink`, `Trade`, `Moved`, `Equip`, `Stored`;
- and `Unknown` for a wire event no type covers yet.

The hook position determines which events are decidable. A veto works only when
the hook runs before the game writes the value. [docs/EVENTS.md](docs/EVENTS.md)
describes the contract in full.

Use `getContext().getGame()` for direct operations.
`getWorld().getEntityRegistry().getPlayer()` returns the hero handle. Its
level, HP, gold, experience and position come from the latest state Coderpack
observed and cost nothing to read, and it provides `teleport`, `setGold`,
`setHp`, `addExp` and `kill`. `getAttributes()`, `getSkills()`,
`getCombatArts()`, `getStats()` (the journal's Statistics page) and
`getSheet()` (armour, attack and movement speed, resistances) ask the game on
every call and return a snapshot; the first three are collections with `get`
and `forEach`, and `setAttribute`, `setSkill` and `setCombatArt` write back.
The same `EntityRegistry` lists the creatures the game holds, near a point or
all of them, and reads one by its ref. `getWorld()` itself sets a creature's
HP or kills it, and says which region and sector the hero last entered.
`getTypeRegistry()` provides `getTypeName`, `getTypeId`, `types`, `retype`,
and `reshape`. `retype` permanently changes an item’s type label and
appearance, but keeps its original behavior and modifiers. `reshape` makes an
item a copy of another, modifiers included. `getUiString` reads the game's
localized text, `getConsole().print(text)` writes a line into the in-game
console, and `getDirectory()` is the game folder. Every collection the API
returns is unmodifiable. Commands wait up to two seconds for an agent reply.

Listeners on a decidable event run while the game thread is stopped, so keep
them short. The host’s deadline is 250 ms, checked every 125 ms. When it
passes, the host lets the original value through.

### Logging

`getContext().log` appends a line to `<Sacred Gold>/logs/mods.log`, one file
for every mod: `[2026-09-23 14:05:31.042] [double-gold]: level 12`. It never
waits for the disk. A loader thread writes the lines in batches, so it is safe
on a listener with the game stopped behind it and from a mod's own threads.
`getContext().print` puts the same line on the host's console instead.

Printing any other way is fine as well:
protocol frames travel on named pipes of their own, and the JVM’s own stdout
and stderr belong to the mods and reach the host’s console. Log4j2, Logback,
slf4j-simple and `java.util.logging` all work with nothing configured.

The loader still points `System.out` at stderr before the first mod loads,
which is why a plain `println` turns up there. That guards the case where the
host offers no pipe, and it changes nothing for a mod.

`log` and `print` are the better habit even so: with five mods installed,
they are the only ones that say which mod spoke.

### The same mod in Kotlin

`dev.ancaria.coderpack:api-kotlin` is the same API with Kotlin syntax on top. It
adds no capability: every declaration forwards to a method on `api`, and the
registration helpers are inline. The loader does not hand it to a mod, so a mod that
wants it packs it, next to the standard library it already carries.

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

Three things are doing the work there. `SacredMod` is the same Java class a
Java mod extends, and Kotlin reads its `getContext()` as `context`, in `onLoad`
and anywhere else in the class. `on<Gold>` takes the event as a type argument
instead of a class literal, and `context.events { }` groups several of those
into one block. And `mutate { }` is how a body decides. It exists only for an
event that can be decided, so `on<Death> { mutate { ... } }` does not compile.
Every reader in the API is a getter, so Kotlin already sees `it.value` and
`it.isSpending` as properties and the module adds none of its own.

`@Subscribe` works exactly as it does from Java, and so does calling
`context.registry.eventRegistry` directly. None of this is required.

## Building Coderpack

You need JDK 21 or newer and Python 3.11. The Gradle wrapper downloads Gradle
9.7.1. `tools/hooksafe.py` also needs `pefile` and `capstone`.
`tests/buildcheck.js` needs Node.

```
gradlew build                  the three jars, in */build/libs
gradlew publishToMavenLocal    so a mod build can resolve the API from mavenLocal
python tools/addr.py           regenerates agent/src/gen/addr.js
python tools/hooksafe.py       refuses hook sites a trampoline would corrupt
node tests/buildcheck.js       checks the agent against a fake process
python tests/replay.py         checks the Java side with no game running
```

`addr.py` reads the address registry from the
[mappings](https://github.com/ancaria-dev/mappings) repository. It checks, in
order, a path passed on the command line, `$CODERPACK_MAPPINGS`, the sibling
`../mappings` checkout, and `build/mappings/mappings.json`. If none exists, it
downloads the file from GitHub into that cache. The revision comes from
`.mappings-ref`, which currently contains `master`. Use a tag or commit there
for a reproducible build.

Game addresses are generated rather than copied by hand. The current table has
52 RVAs, four global addresses, and eight-byte signatures for 38 hook sites.
Every address belongs to `pureHD.exe` 2.0.2.118. The loader can also attach to
`Sacred.exe` and `Game.exe`, but the agent warns when the instructions at the
38 hook sites do not match `agent/signatures.json`. It still installs the hooks.

`hooksafe.py` reads a game binary from
`D:\SteamLibrary\steamapps\common\Sacred Gold` by default. You can pass either
an executable or its directory. If no supported executable is found in the
default directory, the check prints `skipped`. Frida replaces at least five
bytes at a hook site, so a short instruction can make the patch spill into the
next instruction. `hooksafe.py` rejects sites where a branch lands inside the
replaced span, two spans overlap, or relocation separates a flag-setting
instruction from its conditional branch. Other risky forms produce warnings.
Run it with `--signatures` to rewrite `agent/signatures.json` from the selected
binary.

`replay.py` needs the two built JARs in `*/build/libs` and at least one built mod
JAR. It looks under `../mods/*/build/sacred-mod/` or accepts a directory
argument. With no mod JARs, it stops with `no mod jars found` and a nonzero
status. The replay feeds the loader a scripted event stream, checks every
verdict, and verifies the trace, including an unknown event. It cannot prove
that the game commits the requested value.

On `master`, CI publishes only when the remote has no `v<version>` tag for the
version in `gradle.properties`. It uploads the API, its Kotlin
extensions and the zygote to Maven Central as one signed bundle over the Portal
API, attaches `api.jar`,
`zygote.jar`, and `agent.zip` to a GitHub release, then creates the tag. The
generated address table is already inside `agent.zip`, which is what the host
builds itself around when it has no coderpack checkout beside it.

The Central upload does not publish by itself: it waits in the portal for
somebody to press Publish. A Central artifact can never be deleted, so the
first releases are worth looking at.

## Repository layout

| | |
|---|---|
| `agent/` | Plain JavaScript injected by Frida. It hooks individual x86 instructions and reports what it sees. Addresses come from `gen/addr.js`. |
| `api/` | `dev.ancaria.coderpack:api`, which mods compile against. It has no runtime dependencies. JSR 305 is compile-only. |
| `api-kotlin/` | `dev.ancaria.coderpack:api-kotlin`, the same API in Kotlin. Inline extensions over `api`, in package `dev.ancaria.coderpack.ktx`. The loader does not provide it; a mod that uses it packs it. |
| `zygote/` | `dev.ancaria.coderpack:zygote`. It reads frames from the host, finds mod JARs, gives each mod its own class loader, and dispatches events. |
| `tools/`, `tests/`, `docs/` | Address and safety tools, test harnesses, and [RUNNING.md](docs/RUNNING.md) for build, run, and crash-bisection instructions. |

Sacred Gold is a 32-bit process, so the JVM runs beside the game.
[ancaria-dev/protocol](https://github.com/ancaria-dev/protocol) is the Rust
host between them. It injects the agent, starts the JVM, and carries the line
protocol. The JVM reader thread routes command replies and queues events. The
`sal-dispatch` thread invokes mod listeners one event at a time.

## License

MIT. See [LICENSE](LICENSE).

---

Coderpack began as a proof of concept and comes with no support guarantee. It
answers one practical question: can Sacred Gold run Java mods?
