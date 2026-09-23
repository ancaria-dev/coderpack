# Running it

## Build

The pieces live in their own repositories, checked out beside each other:

```
gradlew jar                     api.jar and zygote.jar, in */build/libs
python tools/addr.py            agent/src/gen/addr.js, from ../mappings and
                                agent/signatures.json
python tools/hooksafe.py        refuses hook sites a trampoline would corrupt,
                                and says whether the binary is still the one
                                those signatures came from
```

The host is next door: `cd ../protocol && cargo build --release`. Putting the
whole thing together — host, agent, jars, mods — into one installable file is
the launcher's job: `cd ../launcher && pwsh tools/build.ps1`.

Needs a JDK 21 or newer, Python 3.11, Node for `tests/buildcheck.js`, and for
the host Rust 1.98 with the MSVC toolchain plus LLVM (frida-sys runs bindgen).
A mod author needs none of that: the API comes from a repository.

## Run

1. Start Sacred Gold and load a character.
2. Run `<Sacred Gold>\coderpack\protocol.exe`, or press Play in the
   launcher. **Elevated if the game is elevated** — otherwise
   Frida cannot attach and the host waits forever on a process it can see.

The host waits for the game (`pureHD.exe`, `Sacred.exe` or `Game.exe`, the
first of those that is running), injects, and starts the JVM. Restarting the
game is fine: the host reattaches and the JVM keeps running, so mods just see
another `World.Phase.LOADED`.

## Where it goes in the game folder

```
<Sacred Gold>/launcher/  protocol.exe, the jars, run.cmd
<Sacred Gold>/mods/       the mod jars
```

No agent folder: the modules are minified into `protocol.exe` when the host is
built, and the injected script is assembled in memory. To run the host against
this checkout instead, point it at the sources with
`--agent <coderpack>\agent\src`. That reads the files as they are, so an edit
takes effect on the next host start with no rebuild.

Paths resolve next to the executable, so no arguments are needed: mods are
looked for one level up from the host, which is where they belong. They are
the game's, not Coderpack's. `--dist`, `--mods` and `--java` override the rest.

## Where the output is

Everything appears in the host's console window: `[host]` is the host,
`[agent]` is the injected JavaScript, `[coderpack]` is the loader, and
`[<time>] [<mod id>]:` is a mod's own `getContext().print`. A mod that prints
with `System.out` shows up here too and corrupts nothing: the protocol has
pipes of its own. Prefer `print` anyway, because it says which mod spoke.

`getContext().log` does not go to the console. It appends the same kind of
line to `<Sacred Gold>/logs/mods.log`, one file for every mod, through one
writer thread that never makes the caller wait. That file is what to read
after a session; the console is for watching one.

## Restart the game after changing a hook

Closing the host does not reliably unload an injected agent. The agent refuses
to install on top of an existing one — it checks whether the hook sites are
already patched — but the way out is a game restart, not another host launch.

## Testing without the game

```
python tests\replay.py
node tests\buildcheck.js
```

`replay.py` feeds Coderpack a scripted event stream and checks every verdict. It
exercises the loader, the bus and the mod's logic — everything except whether the
game really commits the value the mod asked for, which only the game can answer.

`buildcheck.js` exercises the other thing nobody sees on a working install: the
warning the agent prints when the module it attached to is not the build the
addresses were found in. It builds a fake process out of `agent/signatures.json`,
evaluates `gen/addr.js` and `10-core.js` against it, and checks that the right
build is silent and a changed one is not. Frida runs QuickJS rather than Node, so
the harness stays inside what both of them have.

## When it is not the build the addresses came from

The host attaches to `pureHD.exe`, `Sacred.exe` or `Game.exe`, whichever is
running, and every address belongs to the first of those. On anything else the
agent says so on the console and carries on:

```
[agent] !! this is not the game build Coderpack's addresses were found in.
[agent] !! expected pureHD.exe 2.0.2.118, found Sacred.exe. 38 of 38 hook
           sites hold different instructions (commitStats, expWrite, ...).
[agent] !! hooking it anyway, at whatever those addresses now point at. Mods
           may not behave as expected.
```

Those three lines are the reason to stop reading the crash below as a hook bug.
The launcher says the same thing before the game starts, out of the executable's
version resource, so a player meets it while they can still do something about
it.

## Checking the host without the game

```
cd ../protocol && cargo run --example message_check
```

Attaches to a throwaway process and asserts that a `send()` from an injected
script reaches the host. It exists because that path was broken by a bug in the
`frida` crate that nothing else caught: the host faulted on the first message
and the game died with it, which read as "our hooks crash Sacred". See
`protocol/vendor/README.md`.

## When the game crashes

Get the faulting address before changing anything:

```powershell
Get-WinEvent -FilterHashtable @{LogName='Application'; ProviderName='Application Error'} |
    Select-Object -First 5 | Format-List TimeCreated, Message
```

`Faulting module: pureHD.exe` with a fault offset is an RVA — disassemble around
it (`artifacts/` in the research repo) and it usually names the
hook. `Faulting module: unknown` means the fault is in Frida's own JIT memory,
which is what hooking something too hot looks like.

`<Sacred Gold>/DEBUG.LOG` shows how far the game got; its last line is the game's
own trace, not ours.

To bisect without rebuilding, drop agent modules:

```
protocol.exe --skip gold,position
```

Module names are the file names without the number prefix: `core`, `bus`,
`names`, `session`, `health`, `position`, `gold`, `exp`, `skills`, `attrs`,
`level`, `cmd`. `core` and `bus` are not optional.

## Bisecting a crash

`run.cmd` passes its arguments through, so no rebuild is needed:

```
run.cmd --only none        core, bus and names only, no hooks but hero capture
run.cmd --only health      that plus one module
run.cmd --skip position    everything except one
```

`core`, `bus` and `names` always load: the first two are the runtime, and
`names` only wraps two of the game's lookups, so leaving it out breaks its
callers without proving anything.

If `--only none` still crashes, the agent is not the culprit — inject the same
bundle with a different frida:

```
python tests\inject_python.py --only none
```

That uses frida-python (17.17 here) instead of the frida-core the host links
(17.9.5, whatever the devkit shipped). It answers every ASK with "no change", so
a crash there points at the frida version or the injection itself rather than at
a mod.

## Bisecting down to one instruction

`--skip`/`--only` work at module level; `--no-hook` works at the site level:

```
run.cmd --only gold --no-hook goldEpilogue,goldSyncFull
```

Site names are the ones in `agent/src/*.js`: `heroCapture`, `loadWindowWorld`,
`loadWindowHero`, `goldDelta`, `goldSyncFull`, `goldWrite`, `goldEpilogue`.
The agent prints which ones it left alone.

`--no-ask` keeps every hook installed but disables verdicts entirely, which
separates "the hook is there" from "the hook stops the game thread".

## When a crash leaves no clue

```
run.cmd --only gold --trace
```

`--trace` makes every hook announce itself as it runs. The host prints those as
they arrive, so the last line before the game dies names the hook that was
executing. A crash that kills the process leaves nothing else behind.
