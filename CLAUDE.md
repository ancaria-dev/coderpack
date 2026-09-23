# CLAUDE.md: coderpack

## Repository purpose

Coderpack is the modding layer for Sacred Gold, a 32-bit game released in
2004. Every game address targets the community HD wrapper `pureHD.exe`
2.0.2.118. The host can also attach to `Sacred.exe` and `Game.exe`, but those
processes still receive the wrapper’s RVAs.

The runtime has strict boundaries:

- `agent/` contains plain JavaScript injected into the game by Frida. It hooks
  x86 instructions and exchanges messages with the host.
- `api/` is the Java contract used to compile mods. It is published as
  `dev.ancaria.coderpack:api` and intentionally has no runtime dependencies.
- `api-kotlin/` is that same contract said in Kotlin, published as
  `dev.ancaria.coderpack:api-kotlin`. It is optional sugar, not a second API:
  every declaration forwards to a method on `api`, and the loader does not
  provide it, so a mod that wants it packs it.
- `zygote/` is the JVM side of the wire. It is published as
  `dev.ancaria.coderpack:zygote`, has `api` as a runtime dependency, loads mod
  JARs, and dispatches events. The release ships the two thin JARs separately.
- A mod is one verified fat JAR. The Gradle plugin packs the mod’s runtime
  dependencies into it, adds the coderpack API as `compileOnly`, and rejects a
  fat JAR that embeds loader API classes.
- The sibling `protocol` repository owns injection, agent bundling, JVM
  startup, message routing, and the 250 ms verdict deadline.
- The sibling `launcher` repository owns player-facing startup and packaging.

Sacred and the JVM remain separate processes. Do not move host or launcher
responsibilities into the agent, API, or zygote.

## Repository layout

| Path | Purpose |
|---|---|
| `agent/src/*.js` | One agent module per concern. The numeric prefix defines load order. |
| `agent/src/gen/addr.js` | Gitignored output from `tools/addr.py`. It contains addresses and the build fingerprint. |
| `agent/signatures.json` | The bytes found at each hook site in a real `pureHD.exe`. |
| `api/` | Events, entities, registries, and the `SacredMod` base class exposed to mods. `api/internal` is the loader's side and not for mods. |
| `api-kotlin/` | Kotlin extensions over `api`, in package `dev.ancaria.coderpack.ktx`: registration helpers and the few things a getter cannot say. |
| `zygote/` | Mod loading and JVM-side protocol handling. `Ranges` defines version syntax and `Compat` applies its two compatibility checks. |
| `tools/` | `addr.py` generates addresses, `hooksafe.py` checks hook sites and writes signatures, and `paths.py` locates mappings. |
| `tests/` | `replay.py` and `buildcheck.js` run without the game. `inject_python.py` runs the agent through frida-python. |
| `docs/` | `RUNNING.md` covers build, launch, and crash bisection. `STYLE.md` defines style. |

## Build and validation

Use JDK 21 or newer and Python 3.11. `tools/hooksafe.py` also requires
`pefile` and `capstone`. `tests/buildcheck.js` requires Node.

Run this full set after code, address, or hook changes:

```text
python tools/addr.py
gradlew build
python tools/hooksafe.py
node tests/buildcheck.js
python tests/replay.py
```

The commands have distinct jobs:

- `python tools/addr.py` writes `agent/src/gen/addr.js`. The current registry
  produces 56 RVAs, 4 globals, and 38 site signatures. The generated RVA and
  global tables preserve their order from `mappings.json`.
- `gradlew build` writes artifacts under `api/build/libs`,
  `zygote/build/libs`, and `api-kotlin/build/libs`. It also runs the zygote
  and api-kotlin JUnit tests.
- `python tools/hooksafe.py` exits with status 1 when a hook site is unusable.
  It also reports whether the selected binary matches
  `agent/signatures.json`.
- `node tests/buildcheck.js` evaluates the build check against a fake process.
- `python tests/replay.py` sends a scripted event stream through the Java side
  and compares every verdict.

The Java release target is 21. The build uses `options.release` instead of a
toolchain, so Gradle does not download a JDK. The wrapper pins Gradle 9.7.1.
Configuration cache is enabled with `org.gradle.configuration-cache.problems=fail`.
`api-kotlin` sets `jvmTarget = JVM_21` separately, because the Kotlin compiler
does not read `options.release`.

## Address generation and build identity

Never type a game address into agent or zygote code. `tools/addr.py` reads
`mappings.json` from the first available source:

1. A command-line path
2. `$CODERPACK_MAPPINGS`
3. The sibling `../mappings` checkout
4. The cache at `build/mappings/mappings.json`, downloading
   `https://raw.githubusercontent.com/ancaria-dev/mappings/<ref>/mappings.json`
   when needed

`<ref>` comes from `.mappings-ref`, currently `master`. Use a tag or commit
there when the build must be reproducible. A standalone coderpack checkout
therefore builds without sibling repositories.

`tools/hooksafe.py` looks in
`D:\SteamLibrary\steamapps\common\Sacred Gold` by default. It asks
`tools/game.py` for `pureHD.exe`, `Sacred.exe`, or `Game.exe`, in that order,
with case-insensitive matching. Pass either an executable path or a game
directory when the install is elsewhere. If the default directory contains no
supported executable, the script prints `skipped` and exits successfully. A
skipped check is not evidence that a hook is safe.

The signature file is generated data. Run
`python tools/hooksafe.py --signatures [path]` against a real `pureHD.exe` to
write the first eight bytes from each of the 38 hook sites into
`agent/signatures.json`. Never edit those bytes by hand. `tools/addr.py`
copies them into `gen/addr.js` as `BUILD`, and `10-core.js` compares them with
the running process during attach.

A mismatch remains a warning. The agent installs the hooks at the current RVAs
even when the bytes differ. Stock `Sacred.exe` differs at all 38 sites, so its
hooks can land in unrelated functions. Refusing every mismatch would also
reject an unrecorded build that happens to work.

The agent checks instructions instead of trusting version metadata. The wrapper
retains the stock game’s `OriginalFilename`, reports the string FileVersion as
`2.28`, and reports `2.0.2.118` in the fixed version block. Reading the resource
from Frida would also require parsing the mapped resource directory of an
unrecognized process. The launcher can safely call `GetFileVersionInfo` on a
file and warn the player before startup.

Each signature is eight bytes. Several of the 38 signatures are the same generic
SEH prologue. One matching prologue proves little. The fingerprint is the full
set of bytes at 38 specific addresses.

## API and zygote compatibility

A mod JAR contains `META-INF/declaration.toml`. `id` and `entrypoint` are
required. The generated descriptor also contains `api`, while `loader` is
optional:

```toml
api = "[3,4)"
loader = "[0.1.20,)"
```

Both fields use the Maven range notation documented by `Ranges`, which is also
the notation NeoForge writes in `mods.toml`. The current Gradle plugin writes
`api = "[3,4)"` by default, meaning API contract 3 and no other major. A mod can
declare a different range such as `[3,5)` through `apiRange`, but that range must include
the contract used by the toolchain. The exact meaning of a bare value is a
deliberate difference from Maven’s soft bare-version rule and preserves older
descriptors.

The `api` range decides whether the code can run. Zygote compares it with
`Api.VERSION`. Missing, malformed, or incompatible `api` values refuse the mod
before its code starts. The plugin always writes this field, so a missing value
indicates a hand-built JAR or an older toolchain. Assuming compatibility can
turn the refusal into a `NoSuchMethodError` during dispatch.

The `loader` range describes Sacred Mod Loader releases. Zygote reads the
installed release from `<game>/launcher/VERSION`. An absent `loader` field
places no release restriction. When the version stamp itself is absent, as it
can be in a hand-built checkout, zygote skips the loader-version comparison.
The launcher performs the same checks before selection. It keeps incompatible
mods visible, disables their checkboxes, and shows the refusal reason.

Zygote logs one refusal that names the mod, the requested range, and the
available API or loader version. Each mod JAR receives its own
`URLClassLoader`. The reader thread handles command replies while the
`sal-dispatch` thread invokes mod code. Combining those duties can deadlock a
mod that calls the game from an event listener.

The JVM main thread is the protocol reader. It completes `RES` command replies
immediately and queues events for the single `sal-dispatch` daemon thread,
which invokes one listener at a time. The bounded queue holds 4,096 frames.
Ordinary events are dropped when it is full, with periodic warnings. An `ASK`
is enqueued with a blocking write and is never dropped by zygote. On `BYE` or
EOF, zygote gives queued work up to two seconds to drain and then calls
`System.exit(0)` so a mod’s non-daemon thread cannot keep the loader alive.
Registration and unregistration remain safe from a mod’s own threads. A
running dispatch uses an immutable listener snapshot.

Commands sent through `getContext().getGame()` wait up to two seconds for a `RES`.
Timeout, interruption, or another command failure logs a warning and returns
an empty result. Decidable events stop the game thread while the host waits. Its
250 ms deadline is checked by a 125 ms watchdog, so an unanswered fallback is
normally queued about 250 to 375 ms after the ask, plus scheduler and posting
delay. Keep listeners on those events short and do not treat 250 ms as a strict maximum
block time.

The API contract number exists in three repositories that cannot import one
another:

- `Api.VERSION` in coderpack
- `Verifier.API` in `build`
- `mods.API` in `launcher`

Raise all three together. The `api-contract` job in
`.github/workflows/build.yml` checks out the other repositories, extracts the
three constants, and fails if a value differs or cannot be found. Update that
job if any constant is renamed.

Range parsing is also implemented three times: `Ranges` here, `Ranges` in the
build repository’s verifier, and `pin` in the launcher. Their shared test
corpus is the specification. Add every new case to all three implementations
and test suites.

## Mod lifecycle and the registries

A mod extends the abstract class `SacredMod`. Zygote creates its entry point
through `api.internal.ModBinding`: it binds the mod's `Context` to the current
thread, calls the no-argument constructor, and clears the binding in `finally`.
`SacredMod`'s own constructor takes the binding, and with it tells the loader
which instance claimed it, so `getContext()` works in the subclass's field
initialisers and `getCurrentMod()` answers from inside the constructor. The
binding is taken once; any other instance, including one made with `new` in a
test, throws `IllegalStateException` from `getContext()`. `onLoad()` has no
parameter.

`Context` is `log`, `print`, `getGame()`, `getUptime()`, `getDescriptor()` and
`getRegistry()`. `log` goes to `<game>/logs/mods.log` through `ModLog`: one
shared daemon writer draining a bounded queue, batching and flushing on a short
timer, never blocking the caller and never throwing into it; a full queue drops
and counts. `print` goes to `System.out`, which `Main.claimStdout` has already
pointed at stderr. Both write `[yyyy-MM-dd HH:mm:ss.SSS] [<mod id>]: <message>`.

`Registry` has two halves. `ModRegistry` lists mods in load order, loads one
more with `register(Path)` (unchecked `ModLoadException` on failure) and takes
one out with `unregister(id)`. `EventRegistry`, which replaced `Events`, is
`register`, `on`, `decide`, the `getEvents()` snapshot of every mod's handles,
and three `unregister` forms. Every `Handle` knows its mod, event type,
priority, `ignoreVetoed` and, for `@Subscribe`, its class and method; `Bus`
keeps that per listener, which is what lets `Bus.dropMod` take a mod out whole.

`Mods` keeps one `URLClassLoader` per mod. Unregistering drops the mod's
listeners, calls `onUnload` (an exception is logged, not rethrown), drops
anything `onUnload` registered, then closes the loader. A mod whose `onLoad`
throws is taken back out the same way and never listed. On `BYE` or EOF
`Mods.shutdown` calls every `onUnload` newest first on one thread with a
three-second limit, then closes the log. It leaves class loaders open, because
a mod's shutdown hook may still load a class from its jar while the JVM exits.

Zygote has its own `Registry` class, the wire-name table. `ModContext` names
the API's `Registry` in full for that reason.

Every public accessor in `api` is a JavaBean getter (`getX()`, `isX()` for a
boolean), because Kotlin sees only those as properties. Records are final
classes with getters for the same reason. Static factories, `get(key)`,
`size()`, `iterator()`, `find(...)` and a mutation's `last()` keep their names.
Every collection the API returns is unmodifiable.

## Event model

`docs/EVENTS.md` is the contract. This is the shape of its implementation.

The decidable API events are `Gold`, `Experience`, `Damage`, `Skill`,
`Attribute`, `CombatArt`, `Pickup`, and `Console`, each a `Decision` and each naming its
own nested `Mutation` through `Decides`. Their hooks run before the relevant
game write. The typed read-only events are `LevelUp`, `Hero`, `World`,
`Position`, `Moved`, `Death`, `NearDeath`, `MobHit`, `MobDeath`, `Equip`,
`Stored`, the `*Changed` reports of what each decision came to, `Region`,
`Sector`, `Spawn`, `Despawn`, `Kill`, `Resurrection`, `Discovery`, `Save`,
`Load`, `Quest`, `Loot`, `Drink`, and `Trade`.
`zygote/src/test/.../RegistryTest` lists every wire name the agent sends and
fails when one of them would reach a mod as `Unknown`; add a name there with
its `Registry` entry.

The direct side of the API is `Game`: `getWorld()` is the `Realm` (region,
sector, `kill`, `setHp`) and its `getEntityRegistry()` (the player and the
creatures), `getTypeRegistry()` is type names, ids, `retype` and `reshape`, and
`getConsole()` prints into the game's console. The player's level, HP, gold,
experience and position are cached from events and free to read.
`getAttributes()`, `getSkills()`, `getCombatArts()`, `getStats()` and
`getSheet()` on `Player`, and everything on `Realm`, `EntityRegistry` and
`TypeRegistry`, are one command each and return a snapshot built from the flat
answer; `RealmLink.unpack`, `TypeLink.unpack` and the snapshot constructors are
where the packed formats are read, and `SnapshotTest` pins them. Each agent command lives in the module that owns its data (`45-world`,
`47-entities`, `52-journal`, `72-arts`, `90-cmd`), so leaving a module out with
`--skip` takes its commands with it and the Java side sees an empty answer.

An event is immutable to a mod. What a listener may do is its return type:
`void` observes, and anything else must be that event's `Mutation`, which
`Bus.register` checks and the mod linter checks earlier. `EventMutation` is
sealed with four kinds, `NONE`, `RESET`, `VETO` and `CHANGE`, plus `last()`
across all four (`isLast()` reads it back). `Fold` applies one answer and is the only thing that writes to
an event; there is no `Guard` any more, because there is no write to guard.

A wire event without a typed registry entry becomes `Unknown` with its original
name and fields. Listeners registered for `Event` therefore receive typed and
unknown events. Dispatch order is `FIRST`, `NORMAL`, `LAST`, then `MONITOR`,
with registration order inside each priority. `Bus` folds each answer before
calling the next listener, which is what makes `Amount.getValue()` mean "with
everyone before me in it" and what lets two mods scaling the same number
compose. A veto does not stop later listeners; `ignoreVetoed = true` skips a
listener once an earlier one has vetoed, and only a later `RESET` lifts it.
`last()` ends the deciding but never skips the `MONITOR` step. A `MONITOR`
listener that returns a mutation has it dropped with one warning.

The API uses JSR 305 nullability annotations. Each of its four packages has a
`package-info.java` with `@ParametersAreNonnullByDefault`. Mark nullable
parameters explicitly, and mark every reference return with `@Nonnull` or
`@Nullable`. The dependency
`com.google.code.findbugs:jsr305:3.0.2` is `compileOnly`. Its runtime-retained
annotations remain in API bytecode for IDEs, but the dependency does not enter
the published POM or a mod’s fat JAR. A mod can compile against the API without
adding JSR 305.

## The Kotlin API

`api-kotlin` is `dev.ancaria.coderpack:api-kotlin`, published from this
repository at the same version as `api`. It adds no capability. Every
declaration in it forwards to a method on `api`, and the registration helpers are `inline`, so what
a mod ends up with in bytecode is the call it would have written by hand.

Three constraints decide its shape, and each one is load-bearing:

- The package is `dev.ancaria.coderpack.ktx`, **not** anything under
  `dev.ancaria.coderpack.api`. This module is packed inside a mod jar, and the
  verifier's `Contents` check refuses, as an error, any class under
  `dev/ancaria/coderpack/api/` in a mod jar. A package under the API's would
  make every Kotlin mod unbuildable.
- `api` is a `compileOnlyApi` dependency. Gradle module metadata then puts it on
  a consumer's compile classpath and keeps it off the runtime one, which is the
  classpath Shadow packs, so the loader's own API never travels inside a mod.
  The published POM writes that scope as `compile` because Maven has no
  equivalent; a Maven build would have to declare `api` as `provided` itself.
- The loader does not provide this module. `zygote` neither knows nor loads it,
  and neither the launcher nor a release stages it. A mod that wants it declares
  it as `implementation` and it is packed, next to the Kotlin standard library
  that mod already carries.

There is no Kotlin `SacredMod`. The Java class already keeps the context and
hands it out as `getContext()`, which Kotlin reads as `context`, so a Kotlin mod
extends `dev.ancaria.coderpack.api.SacredMod` directly and overrides the
parameterless `onLoad()`. A second base class of the same name would only be an
import to get wrong.

Nor are there properties that rename API readers. Every accessor in `api` is a
getter now, so Kotlin sees `it.value`, `it.isVetoed`, `player.hp` (a `var`,
because `setHp` exists) and `game.world.entityRegistry.player` without help.
An extension property with a member's name is shadowed by the member and only
misleads. Do not add one back; add a getter to the Java class instead.

The contents:

| File | What it adds |
|---|---|
| `Events.kt` | `Context.events { }`, the block with `EventRegistry` as its receiver; `once<E> { }`; `Handle + Handle` as a list and `Iterable<Handle>.unregister()`. |
| `Scope.kt` | `on<E> { }` on `EventRegistry` and on `Context`, and the `On<E>` scope it runs in, with `mutate { }`. |
| `Event.kt` | `event["key"]`, `long(key)` and `int(key)` over the raw fields. |
| `Items.kt` | `item[modifierId]`, null when the item does not carry it. |
| `Game.kt` | `Pos`, `position` on `Player` and `Creature`, `teleport(Pos)`, `EntityRegistry.creaturesNear(Pos, r)`, `Realm.sector` and `TypeRegistry.typeIdOrNull`. |

`Handle + Handle` gives a `List<Handle>` rather than a merged handle, because a
handle now names its mod, event type and priority, and two handles have two
answers to each.

Kotlin gets one `on` where Java needs `on` and `decide`, because the body is
`Unit` on both paths and says what it decided by calling `mutate`. Two overloads
split by lambda return type would be worse than a second name rather than
better: Kotlin coerces any lambda to `() -> Unit`, so the observing overload
would win for a body that returns a mutation and drop it in silence. `mutate` is
an extension constrained to `E : Decides<M>`, so an event with nothing to decide
has no such function. The runtime branch between the two Java methods lives in
one internal function in `Scope.kt` and needs one cast through `Nothing`, which
its comment explains; nothing unsound reaches a mod through it.

`api-kotlin/src/test` covers the parts a signature cannot: that `value` reports
the fold while `initial` keeps the arrived number, that `once` unregisters on
both sides of its race, including the case where the event arrives before `on`
has returned the handle, and that a Kotlin subclass of the Java `SacredMod`
created through `ModBinding.create` has its context in a field initialiser and
registers from `onLoad()`. `Fakes.kt` holds the stand-in bus, context, game and
type table, and its bus keeps observing and deciding registrations in one list
because "still on the bus" is one idea. No test here reaches the loader or the
game.

Raising `Api.VERSION` does not require a change here. This module compiles
against the API and breaks with it, so it moves when the API's own artifact
version moves, which is every release.

## Replay test requirements

`tests/replay.py` requires built API and zygote JARs under `*/build/libs` and
built mod JARs. It searches `../mods/*/build/sacred-mod/*.jar` or accepts a
directory argument. If it finds no mod JARs, it prints `no mod jars found` and
exits nonzero. That is a failed prerequisite, not a passing test.

The script stages mods under `build/replay/mods` and starts:

```text
dev.ancaria.coderpack.zygote.Main --mods <staged> --enable tracer,old-huge-potions,all-my-runes
```

The harness answers commands issued by mods while the reader thread collects
verdicts. It checks every expected verdict and verifies the tracer’s complete
event sequence, including an unknown event. Only an in-game test can prove
that Sacred commits the requested memory change.

## Release process

CI reads `version` from `gradle.properties` on `master`. It publishes only when
the remote lacks `v<version>`, then creates that tag as the release record.
Raising the version is what ships a release. `tools/version.ps1` prints the
current one with no argument, or raises it everywhere it is written
(`gradle.properties`, the Javadoc in `Api.java`, and the three READMEs) with
`pwsh tools/version.ps1 0.99.1`.

The release contains `api.jar`, `zygote.jar`, and `agent.zip`.
`agent.zip` already includes `gen/addr.js`. It is what `protocol` builds itself
around when it has no checkout of this repository beside it, which is the only
consumer of that asset now: the launcher no longer unpacks agent scripts into a
game folder. CI also uploads all three Java modules
to Maven Central, which is where a mod build resolves
`dev.ancaria.coderpack:api` and `dev.ancaria.coderpack:api-kotlin` from. The JARs and agent archive are packed only
for a new version on `master`. `api-kotlin.jar` is not among the release
assets: nothing downloads it from a release, because no part of the loader
stages it. It reaches a mod through Maven Central only. The Central upload runs before the GitHub
release action, which creates `v<version>`. Pull requests still build and
upload their Gradle JARs as workflow artifacts, but they publish nothing.

Central is not a repository a publish task writes to. `nmcp` collects the
publications `maven-publish` produced, stages them, and posts one signed zip to
the Portal API. The task is `nmcpPublishAggregationToCentralPortal`, and
`nmcpZipAggregation` builds the same bundle locally without uploading it, which
is how to see what a release would contain.

`publishingType` in `build.gradle.kts` is `USER_MANAGED`, so an upload waits in
the portal for somebody to press Publish. A Central artifact can never be
deleted. Change this to `AUTOMATIC` once a deployment has been seen to be
right, and raising the version is again the only thing that ships.

Signing is a GPG key passed in as `SIGNING_KEY` and `SIGNING_PASSWORD`. Without
them the sign tasks are skipped rather than failing, so a checkout with no
secrets still builds, tests and publishes to the local Maven repository.

## Hook safety rules

Every rule in this section comes from a reproduced game crash or corruption.

- Never write a game address by hand. Addresses come from `mappings` through
  `tools/addr.py`, using the source selected by `tools/paths.py`. A wrong RVA
  can produce a hook that never fires and reports no error.
- Run `tools/hooksafe.py` before shipping a hook. Treat every `FATAL` as a hard
  stop. The script rejects a branch into the interior of a patch, separation of
  a flag-setting instruction from its conditional branch, and overlap between
  two patched regions. It warns about relocated control transfers,
  ESP-relative instructions, and scratch registers carried across a patch.
- Never edit `agent/signatures.json` manually. A false signature warns on the
  correct game and trains users to ignore the next warning.
- Do not hook an instruction shorter than five bytes when a branch targets the
  following instruction. Frida overwrites at least five bytes, so the patch
  spills forward and the branch enters the middle of a trampoline. The skill
  write at `+0x1827DA` is four bytes and has two jumps targeting the next
  address. It killed every character with an empty skill slot.
  `70-skills.js` hooks one instruction later.
- Do not split a flag-setting instruction from the branch that consumes its
  flags. Flags set in the trampoline can be changed on the return path before
  the branch runs. The old gold hook put `cmp [eax+0xc], ebx` inside the patch
  and left its `je` outside. It crashed on every world load.
- Avoid mid-function hooks when EAX, ECX, or EDX is loaded before the patch and
  read afterward. Three sites failed with a value in flight across the patch
  and none failed without one. Two AddGold sites returned with EDI destroyed.
  The level write at `+0x185EBE` crashed only while loading a save. Prefer a
  function entry or sample on the tick. `60-gold.js` and `80-level.js` moved
  work to `onTick` without losing behavior.
- Use `onLeave` only at a function entry. At a mid-function address, `[esp]` is
  not a return address and Frida tracks a bogus return site. The mid-function
  path in `50-health.js` uses `onEnter` only. The `--trace` wrapper in `hook()`
  adds `onLeave` only when the original hook already has one.
- Never attach to a writer that runs once per object during world load.
  `cObjectManager::load` unpacks about 3,000 objects. The six attribute
  recalculation writers at `+0x179B09..` run on that path. Frida can die in its
  JIT memory and Windows then reports `Faulting module: unknown`. Checking for
  the player inside the callback does not reduce callback volume.
  `40-session.js` hooks load and save entries, not their bodies.
- Never request a verdict while the game is loading. `ask()` stops the game
  thread. `isLoading()` in `10-core.js` converts the request to a plain event.
- All agent files share one JavaScript scope. Keep `"module": "none"` in
  `agent/jsconfig.json`. The host concatenates `gen/addr.js` followed by the
  numbered files in filename order. Duplicate helper names overwrite one
  another, so load order silently decides which implementation survives.
  Shared helpers belong in `10-core.js`. Other helpers need module-specific
  names.
- Snapshot a `NativePointer` from `retval` or `this.context.*` with `snapPtr`
  before storing it. The pointer can alias a live register. After the callback,
  EAX can become 0 and a later `.add(0x4D4)` faults at `0x4d4`, which is the
  offset rather than a heap address. `p === null` does not detect a NULL
  `NativePointer`. Use `live()`.
- Frida runs QuickJS. Use `globalThis`, not `global`. There is no DOM or npm
  package environment. A `globalThis` installation guard cannot detect a
  second injection because every script receives its own runtime.
  `10-core.js` instead checks game bytes for `0xE9` or `0xCC` at known sites
  and refuses a double install.
- Route integer verdict fields through `asked()` in `20-bus.js`. It validates
  parsed values, falls back when parsing fails, and caps values above
  `INT32_MAX` before the game receives them. A boosted value can overflow
  before the game’s own clamp runs.
- A single manual test cannot prove that a write hook is narrowly scoped.
  Count calls first with the counter-only Interceptor at
  `research/artifacts/probes/count_calls.py`. The HP writers measured 34 calls
  in 22 seconds of combat before receiving an ASK. A combat-art hook that
  looked safe after one click ran 80,000 times.
- Do not touch anti-cheat XOR mirrors unless a mod changed the mirrored field.
  The page behind `[0x182DDDC]` is protected and the game opens it only around
  its own access. Experience, gold, and level are mirrored. Boost the delta at
  the function entry instead of rewriting the committed total. Otherwise the
  checker sees a mismatch and resets the field to 1.
- Call the game’s own function when one exists instead of writing its field.
  For `thiscall`, Frida receives `this` as the first declared argument.

## Related repositories

These repositories are siblings of coderpack under `ancaria-dev`:

- `mappings` owns the address registry and is the only sibling this build
  reads directly. `tools/addr.py` consumes `mappings/mappings.json`, which is
  generated from `mappings.txt`. Run
  `python mappings.generator.py --check` in that repository to reject stale
  JSON. Coderpack CI runs it before address generation. The registry’s
  `hooked` list defines the sites checked by `tools/hooksafe.py`.
- `protocol` owns the Rust host. Its `build.rs` minifies `agent/src` into
  `protocol.exe`, and `protocol/src/agent.rs` assembles the injected script from
  it in memory, starts the JVM, enforces the verdict deadline, and implements
  `--skip`, `--only`, `--no-hook`, `--no-ask`, and `--trace`. The host takes the
  agent from `$PROTOCOL_AGENT`, then this sibling, then the `agent.zip` of the
  release pinned in its own `dependencies.json`, so a change here reaches a
  player through a release of that repository. Its folder-reading bundler tests
  use a fixture agent. Its end-to-end test can use built coderpack JARs from
  this sibling checkout or the local Maven repository.
- `launcher` owns packaging. `launcher/tools/build.ps1` copies both JARs when
  coderpack is available beside it and points the host build at `agent/src`.
  Otherwise it downloads `api.jar` and `zygote.jar` from the coderpack release
  pinned in `dependencies.json`. It stages no agent: a `protocol.exe`, built or
  downloaded, already has one inside it.
- `mods` supplies the mod JARs used by `tests/replay.py`.
- `research` contains the probes and static analysis that established the
  addresses and hook behavior.
- `build` owns the `dev.ancaria.coderpack` Gradle plugin, descriptor
  generation, packed-JAR verification, and mod templates. Its Kotlin templates
  declare `api-kotlin` and its `Contents` check is the reason that module's
  package sits outside `dev.ancaria.coderpack.api`. Its end-to-end test compiles
  a generated Kotlin mod against a stub of this module, so a declaration a
  template uses has to exist in both.

## Operational gotchas

- A fresh checkout has no `agent/src/gen/addr.js`. Run `python tools/addr.py`
  first. Without that file, the host reports it missing and installs no hooks.
- Frames travel on two named pipes the host creates and names with `--pipe`:
  `<base>.in` in and `<base>.out` out. `Pipe.over` opens both, in that order,
  because the host waits for them in that order. One per direction and not one
  duplex pipe: a synchronous handle serialises its operations, so a read parked
  in the reader thread holds up every write from the dispatch thread, and the
  loader goes quiet after exactly one frame.
- Without `--pipe`, `Pipe.overStdio` puts frames back on stdout, which is what
  the test harnesses drive and what an older host gives. There a mod printing
  with `System.out` can splice itself into a frame, so `Main.claimStdout`
  points `System.out` at stderr before the first mod loads. It runs on both
  transports, because the difference between them is one missing argument.
  Keep `Pipe` off `System.out` either way: rewritten to use it, every frame
  would go to the console and the host would hear nothing.
- Closing the host does not reliably unload an injected agent. Restart the game
  after changing a hook.
- If the game runs elevated, Frida cannot attach from a normal shell. The host
  waits indefinitely on a process it can see.
- `Faulting module: pureHD.exe` plus an offset gives an RVA and usually points
  to the hook. `Faulting module: unknown` points to Frida JIT memory and often
  means the hook is too hot.
- The host links frida-core 17.9.5. `tests/inject_python.py` injects the same
  bundle with the installed frida-python version, separating a Frida-version
  failure from a hook failure. It answers every ASK with no change.
- `70-skills.js` refers to `analysis/branch_targets_into.py`. That script now
  lives at `research/artifacts/static/branch_targets_into.py`.
- `tools/hooksafe.py` warnings are not failures. `getLocalHero`, `expWrite`,
  and `skillPoints` relocate a branch. `skillPoints` also carries EAX across
  the patch. These cases are known and shipping.
- An item’s type ID exists at `+0x10` and `+0x118`. The game reads the second
  copy, so writing only `+0x10` changes nothing visible.
- `tests/buildcheck.js` is the only no-game test that executes agent
  JavaScript. It stubs Frida globals and evaluates `gen/addr.js` plus
  `10-core.js` in one Node `vm` context, matching the host’s load order. It
  loads only core because the build check lives there. Keep the tested code
  within the QuickJS feature set.
