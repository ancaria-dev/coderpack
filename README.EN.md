# Coderpack

<div align="center">

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.7.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-agent-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Version](https://img.shields.io/badge/version-0.99.0-4B5563?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-4B5563?style=for-the-badge)
![Sacred](https://img.shields.io/badge/Sacred-Community-8B1A1A?style=for-the-badge&labelColor=1C1410)

</div>

[Русский](README.md) · [Deutsch](README.DE.md)

Write Java mods for Sacred Gold, the 2004 action RPG.

Coderpack attaches to the running game and turns selected operations into
events. A mod subscribes to the events it needs. Events fired before a game
write can be changed or cancelled. Coderpack never patches files in the game
directory. Its hooks disappear when the game closes.

## Writing a mod

A mod is a JAR file installed in `<Sacred Gold>/mods`. The Gradle plugin from
[ancaria-dev/build](https://github.com/ancaria-dev/build) builds it:

```kotlin
plugins {
    id("dev.ancaria.coderpack") version "0.99.0"
}

version = "1.0.0"

sacred {
    id = "double-gold"
    displayName = "Double Gold"
    description = "Twice the loot, same purse"
    entrypoint = "com.example.DoubleGold"
    apiVersion = "0.99.0"
    author("you")
}
```

`gradlew assembleSacredMod` writes the verified fat JAR to
`build/sacred-mod`. The plugin generates `META-INF/declaration.toml` from the
`sacred` block and packs runtime dependencies beside the mod’s classes.
`apiVersion` adds `dev.ancaria.coderpack:api` as a **compileOnly** dependency.
The loader supplies the API at runtime, and the verifier rejects mod JARs that
contain loader API classes.

By default, the descriptor contains `api = "1"`. This is the API contract
range checked before the loader starts the mod. It is separate from the
`dev.ancaria.coderpack:api` artifact version, currently `0.99.0`. The artifact
version can change with each release. The contract changes only when a mod
compiled against the previous API would break.

Set `apiRange` when a mod supports a different range of contracts, for example
`apiRange = "[1,2)"`. The range must include the contract used by the
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

import dev.ancaria.coderpack.api.Context;
import dev.ancaria.coderpack.api.SacredMod;
import dev.ancaria.coderpack.api.Subscribe;
import dev.ancaria.coderpack.api.event.Gold;
import dev.ancaria.coderpack.api.event.LevelUp;

public final class DoubleGold implements SacredMod {

    private Context context;

    @Override
    public void onLoad(Context context) {
        this.context = context;
        context.events().register(this);
    }

    @Subscribe
    public void onLevelUp(LevelUp event) {
        context.log("level " + event.level());
    }

    @Subscribe
    public void onGold(Gold event) {
        if (!event.spending()) {
            event.delta(event.delta() * 2);   // rewritten before the game stores it
        }
    }
}
```

A supported listener method is public, returns `void`, and takes exactly one
event parameter. The parameter type selects the event, so there is no separate
event name to maintain. For dynamic registration, `context.events().on(...)`
returns a `Handle` that can unregister the listener.

The `priority` value on `@Subscribe` controls dispatch order: `FIRST`, `NORMAL` by default,
`LAST`, then `MONITOR`. Registration order decides the order within one
priority. Cancellation does not stop dispatch. With
`ignoreCancelled = true`, a listener is skipped if an earlier listener
cancelled the event. A `MONITOR` listener sees the final decision, but its own
cancellations and rewrites are discarded. The loader logs the first such
attempt once per listener.

Six events can be rewritten or cancelled: `Gold`, `Experience`, `Damage`,
`Skill`, `Attribute`, and `Pickup`. Read-only events report something that has
already happened: `LevelUp`, `Hero`, `World`, `Position`, `Moved`, `Death`,
`NearDeath`, `MobHit`, `MobDeath`, `Equip`, `Stored`, and `Unknown`. The hook
position determines which events are vetoable. A veto works only when the hook
runs before the game writes the value.

Use `context.game()` for direct operations. `player()` returns the hero handle,
backed by the latest state Coderpack observed, and provides `teleport`, `gold`,
`hp`, and `addExp`. The game interface also provides `uiString`, `typeName`,
`typeId`, `types`, and `retype`. `retype` permanently changes an item’s type
label and appearance, but keeps its original behavior and modifiers. Commands
wait up to two seconds for an agent reply.

Listener code for a veto runs while the game thread is stopped, so keep it
short. After 250 ms, the host abandons an unanswered veto and lets the original
value through.

### The same mod in Kotlin

`dev.ancaria.coderpack:api-kotlin` is the same API with Kotlin syntax on top. It
adds no capability: every declaration forwards to a method on `api`, and almost
all of them are inline. The loader does not hand it to a mod, so a mod that
wants it packs it, next to the standard library it already carries.

```kotlin
dependencies {
    implementation("dev.ancaria.coderpack:api-kotlin:0.99.0")
}
```

```kotlin
package com.example

import dev.ancaria.coderpack.api.Context
import dev.ancaria.coderpack.api.event.Gold
import dev.ancaria.coderpack.ktx.SacredMod
import dev.ancaria.coderpack.ktx.delta
import dev.ancaria.coderpack.ktx.on
import dev.ancaria.coderpack.ktx.spending

class DoubleGold : SacredMod() {

    override fun Context.load() {
        on<Gold> { if (!it.spending) it.delta *= 2 }
    }
}
```

Three things are doing the work there. `SacredMod` is the abstract class from
`…ktx`, which keeps the context and hands it to `load` as a receiver, so `on`
and `log` read as bare calls. `on<Gold>` takes the event as a type argument
instead of a class literal, and `events { }` groups several of those into one
block. And a field the API lets a listener rewrite is a `var`, which is the
whole of `it.delta *= 2`. Fields the API refuses to rewrite, `Gold.current`
among them, stay read-only here too.

`@Subscribe` works exactly as it does from Java, and so does implementing
`dev.ancaria.coderpack.api.SacredMod` directly. None of this is required.

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
26 RVAs, three global addresses, and eight-byte signatures for 20 hook sites.
Every address belongs to `pureHD.exe` 2.0.2.118. The loader can also attach to
`Sacred.exe` and `Game.exe`, but the agent warns when the instructions at the
20 hook sites do not match `agent/signatures.json`. It still installs the hooks.

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
