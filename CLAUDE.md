# coderpack

Workspace rules, target build, release chain, and elevation: see `../CLAUDE.md`.

## Boundaries

- `agent/`: plain JavaScript Frida injects into the game. `api/`: the Java contract mods compile against, no runtime dependencies; `api/internal` is the loader's side. `api-kotlin/`: Kotlin sugar over `api`. `zygote/`: the JVM side that loads mod jars and dispatches events.
- `protocol` owns injection, agent bundling, JVM startup, routing, and the verdict deadline. `launcher` owns player-facing startup and packaging. Never move their duties into the agent, API, or zygote.
- A mod is one verified fat jar that packs its runtime dependencies and never the loader API (see `../build/CLAUDE.md`).

## Validation

- After code, address, or hook changes run all five:
  - `python tools/addr.py`
  - `gradlew build`
  - `python tools/hooksafe.py`
  - `node tests/buildcheck.js`
  - `python tests/replay.py`
- `tools/hooksafe.py` needs `pefile` and `capstone`; `tests/buildcheck.js` needs Node.
- A fresh checkout has no `agent/src/gen/addr.js`. Run `python tools/addr.py` first; without it the host installs no hooks.
- `hooksafe.py` looks in `D:\SteamLibrary\steamapps\common\Sacred Gold` by default; pass an executable or game directory otherwise. When it finds no game it prints `skipped` and exits 0. A skip is not evidence of safety.
- `hooksafe.py` warnings are not failures. `getLocalHero`, `expWrite`, and `skillPoints` relocate a branch, and `skillPoints` carries EAX across the patch; these are known and shipping.
- `tests/replay.py` needs built `api` and `zygote` jars and built mod jars from `../mods/*/build/sacred-mod/` (or a directory argument). `no mod jars found` with a nonzero exit is a failed prerequisite, not a pass.
- replay enables `tracer`, `old-huge-potions`, and `all-my-runes`. Only an in-game test proves Sacred commits a requested change.
- `tests/buildcheck.js` is the only no-game test that runs agent JavaScript: `gen/addr.js` plus `10-core.js` in one Node `vm`. Keep tested code within the QuickJS feature set.
- `tests/inject_python.py` injects the same bundle with the installed frida-python, separating a Frida-version failure from a hook failure. It answers every ASK with no change.
- Never add a Java toolchain. The build uses `options.release = 21` so Gradle downloads no JDK. `api-kotlin` sets `jvmTarget = JVM_21` itself because Kotlin ignores `options.release`.
- Configuration cache runs with `problems=fail`. Keep tasks compatible.

## Addresses and build identity

- `tools/addr.py` takes `mappings.json` from, in order: a command-line path, `$CODERPACK_MAPPINGS`, sibling `../mappings`, then GitHub at the ref in `.mappings-ref`, cached under `build/mappings/`. `tools/paths.py` owns this order. Put a tag or commit in `.mappings-ref` for a reproducible build.
- `agent/signatures.json` is generated. Write it only with `python tools/hooksafe.py --signatures [path]` against a real `pureHD.exe`. Never edit its bytes; a false signature warns on the correct game and trains users to ignore warnings.
- `addr.py` copies the signatures into `gen/addr.js` as `BUILD`; `10-core.js` compares them with the running process at attach.
- Keep a signature mismatch a warning. The agent still hooks at the current RVAs. Refusing would also block an unrecorded build that works. Stock `Sacred.exe` differs at every site, so its hooks can land in unrelated code.
- The agent checks instruction bytes, never version metadata. The fingerprint is the whole set of sites, since several signatures are the same generic SEH prologue. Version reading belongs to the launcher.

## Descriptor compatibility

- A mod jar carries `META-INF/declaration.toml`. `id`, `entrypoint`, and `api` are required by zygote; `loader` is optional.
- Both ranges use Maven notation, parsed by `Ranges`. A bare value means exactly that version, unlike Maven; this is deliberate and keeps old descriptors working.
- Zygote refuses a missing, malformed, or incompatible `api` before any mod code runs. Never assume compatibility; that turns a refusal into a `NoSuchMethodError` during dispatch.
- `loader` is checked against `<game>/launcher/VERSION`. No `loader` field means no restriction. No `VERSION` file (a hand-built checkout) skips the check.
- The API contract number lives in three repositories that cannot import each other: `Api.VERSION` here, `Verifier.API` in `build`, `mods.API` in `launcher`. Raise all three together. The `api-contract` job in `.github/workflows/build.yml` fails when they differ; update it if a constant is renamed.
- Range parsing exists three times: `Ranges` here, `Ranges` in `build`'s verifier, `pin` in `launcher`. Their shared test corpus is the specification. Add every new case to all three.

## Zygote runtime

- The JVM main thread reads the protocol and completes `RES` replies at once. Mod code runs only on the single `sal-dispatch` daemon thread. Never merge the two; a mod that calls the game from a listener would deadlock.
- The dispatch queue is bounded. Ordinary events are dropped when it is full, with periodic warnings. An `ASK` is enqueued with a blocking put and never dropped.
- On `BYE` or EOF, zygote drains for up to two seconds, then calls `System.exit(0)` so a mod's non-daemon thread cannot keep the JVM alive.
- `getContext().getGame()` commands wait up to two seconds for `RES`. Timeout or failure logs a warning and returns an empty result.
- Verdict deadline and its real timing: see `../protocol/CLAUDE.md`. Keep listeners on decidable events short.
- Frames travel on two named pipes from `--pipe`: `<base>.in` and `<base>.out`. `Pipe.over` opens them in that order because the host waits in that order. Why one pipe per direction: see `../protocol/CLAUDE.md`.
- Without `--pipe`, `Pipe.overStdio` uses stdout; test harnesses use this. `Main.claimStdout` points `System.out` at stderr before the first mod loads, on both transports. Never route `Pipe` through `System.out`.
- Each mod jar gets its own `URLClassLoader`.
- Unregistering a mod drops its listeners, calls `onUnload` (logs, never rethrows), drops what `onUnload` registered, then closes its class loader. A mod whose `onLoad` throws is removed the same way.
- `Mods.shutdown` calls every `onUnload` newest first on one thread with a three-second limit, then closes the log. It leaves class loaders open, because a shutdown hook may still load a class.
- Zygote's own `Registry` is the wire-name table. `ModContext` names the API's `Registry` in full for that reason.

## API rules

- `docs/EVENTS.md` is the event contract: mutations, the fold, dispatch order, vetoes, `last()`, `MONITOR`. Read it before changing `Bus` or an event.
- A listener returns `void` to observe, or exactly its event's `Mutation` to decide. `Bus.register` checks this and the linter checks it earlier. `Fold` is the only thing that writes to an event.
- Zygote creates the entry point through `api.internal.ModBinding`, which binds the `Context` to the thread around the no-argument constructor. `getContext()` then works in field initialisers. Any other instance, including `new` in a test, throws `IllegalStateException`.
- Every public accessor in `api` is a JavaBean getter (`getX()`, `isX()`), because Kotlin sees only those as properties. Records are final classes with getters. Static factories, `get(key)`, `size()`, `iterator()`, `find(...)`, and `last()` keep their names.
- Every collection the API returns is unmodifiable.
- `context.log` writes to `<game>/logs/mods.log` through a shared writer that never blocks or throws into the caller; a full queue drops and counts.
- A wire event without a typed registry entry reaches mods as `Unknown`. When the agent sends a new wire name, add it to `Registry` and to `RegistryTest`.
- Each agent command lives in the module that owns its data, so `--skip` of a module removes its commands and Java sees an empty answer.
- Every API package has `@ParametersAreNonnullByDefault` in `package-info.java`. Mark nullable parameters, and mark every reference return `@Nonnull` or `@Nullable`.
- Keep `com.google.code.findbugs:jsr305` `compileOnly`, out of the POM and out of mod jars.

## Kotlin API

- `api-kotlin` adds no capability. Every declaration forwards to `api`; registration helpers are `inline`.
- Keep its package `dev.ancaria.coderpack.ktx`. The linter's `Contents` check refuses any class under `dev/ancaria/coderpack/api/` in a mod jar, so a package there makes every Kotlin mod unbuildable.
- Keep `api` a `compileOnlyApi` dependency so it stays off the runtime classpath Shadow packs. The POM says `compile`; a Maven consumer must declare `api` as `provided`.
- The loader never provides `api-kotlin`. A mod that wants it declares it `implementation` and packs it.
- Never add a Kotlin `SacredMod`. Kotlin mods extend the Java class and override `onLoad()`.
- Never add an extension property that renames an API reader; a member shadows it. Add a getter to the Java class instead.
- Keep one `on` with `mutate { }`. Never split it into overloads by lambda return type: Kotlin coerces any lambda to `() -> Unit` and would silently drop a returned mutation.
- Raising `Api.VERSION` needs no change here. The module moves with the API artifact version.
- `build`'s end-to-end test compiles a generated Kotlin mod against a stub of this module. A declaration a template uses must exist in both.

## Hook safety

Every rule here comes from a reproduced crash or corruption.

- Never write a game address by hand. It comes from `mappings` through `tools/addr.py`.
- Run `tools/hooksafe.py` before shipping a hook. Every `FATAL` is a hard stop.
- Never hook an instruction shorter than five bytes when a branch targets the next instruction. Frida's patch spills forward and the branch lands inside the trampoline. The four-byte skill write at `+0x1827DA` killed every character with an empty skill slot; `70-skills.js` hooks one instruction later.
- Never split a flag-setting instruction from the branch that reads its flags. The old gold hook put `cmp [eax+0xc], ebx` inside the patch and its `je` outside, and crashed on every world load.
- Avoid mid-function hooks where EAX, ECX, or EDX is loaded before the patch and read after it. Three such sites failed; two AddGold sites returned with EDI destroyed; the level write at `+0x185EBE` crashed while loading a save. Prefer a function entry or sampling on the tick, as `60-gold.js` and `80-level.js` do.
- Use `onLeave` only at a function entry. Mid-function, `[esp]` is not a return address. The `--trace` wrapper adds `onLeave` only when the hook already has one.
- Never attach to a writer that runs once per object during world load. `cObjectManager::load` unpacks thousands of objects; the attribute recalculation writers at `+0x179B09..` killed Frida's JIT memory (`Faulting module: unknown`). A player check inside the callback does not reduce the call volume. Hook load and save entries, as `40-session.js` does.
- Never request a verdict while the game is loading. `isLoading()` in `10-core.js` turns the ask into a plain event.
- All agent files share one JavaScript scope. The host concatenates `gen/addr.js` and the numbered files in filename order; a duplicate helper name silently overwrites. Put shared helpers in `10-core.js` and give others module-specific names. Keep `"module": "none"` in `agent/jsconfig.json`.
- Snapshot a `NativePointer` from `retval` or `this.context.*` with `snapPtr` before storing it; it can alias a live register. `p === null` never detects NULL; use `live()`.
- Frida runs QuickJS: use `globalThis`, never `global`; no DOM, no npm. A `globalThis` guard cannot detect a second injection, so `10-core.js` refuses a double install by checking for `0xE9` or `0xCC` at known sites.
- Route integer verdict fields through `asked()` in `20-bus.js`. It validates, falls back on parse failure, and caps above `INT32_MAX`, because a boosted value can overflow before the game's own clamp.
- Count calls before trusting a write hook, with `research/artifacts/probes/count_calls.py`. A combat-art hook that looked safe after one click ran 80,000 times.
- Do not touch the anti-cheat XOR mirrors unless a mod changed the mirrored field. Experience, gold, and level are mirrored behind the protected page at `[0x182DDDC]`. Boost the delta at the function entry; rewriting the committed total makes the checker reset the field to 1.
- Call the game's own function instead of writing its field when one exists. For `thiscall`, Frida takes `this` as the first declared argument.
- An item's type ID exists at `+0x10` and `+0x118`. The game reads `+0x118`; writing only `+0x10` changes nothing visible.

## Debugging

- Closing the host does not reliably unload the agent. Restart the game after changing a hook.
- `Faulting module: pureHD.exe` plus an offset gives an RVA and usually points at the hook. `Faulting module: unknown` means Frida JIT memory and often a hook that is too hot.
- `docs/RUNNING.md` covers build, launch, and crash bisection. `docs/STYLE.md` defines style.

## Release

- `tools/version.ps1` writes the version to `gradle.properties`, the Javadoc in `Api.java`, and the three READMEs.
- A release carries `api.jar`, `zygote.jar`, and `agent.zip`. `agent.zip` includes `gen/addr.js`; only `protocol` consumes it, to build without a coderpack checkout.
- CI uploads `api`, `api-kotlin`, and `zygote` to Maven Central before creating the GitHub release. `api-kotlin` reaches mods only through Central, never as a release asset.
- The Central task is `nmcpPublishAggregationToCentralPortal`. `nmcpZipAggregation` builds the same bundle locally without uploading; use it to inspect a release.
- A Central artifact can never be deleted. `publishingType` stays `USER_MANAGED` until a deployment has been seen to be right (see `../CLAUDE.md`).
- Signing reads `SIGNING_KEY` and `SIGNING_PASSWORD`. Without them the sign tasks are skipped, so a checkout without secrets still builds and publishes to Maven Local.
