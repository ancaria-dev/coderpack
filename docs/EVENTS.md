# Events

This is the contract mods are written against. `api` implements it. The fold
that makes `value()` mean what it says lives in the bus in `zygote`.
`api-kotlin`, the mod linter in `build` and the four mods follow it.

## Two kinds of listener

A listener is a method taking one event. What it returns decides what it may
do, and nothing else does:

```java
@Subscribe public void onXp(Experience e)                { context.log("xp " + e.gain()); }
@Subscribe public Experience.Mutation onXp(Experience e) { return ...; }
```

A `void` listener is an observer by construction. There is no flag to set and
no rule to remember: it has nothing to return, so it cannot decide anything.

A returning listener must return exactly `<its event>.Mutation`. An event with
no `Mutation` type cannot be decided, and the compiler says so at the return
statement rather than the loader saying so at run time. Eight events have one:
`Gold`, `Experience`, `Damage`, `Skill`, `Attribute`, `CombatArt`, `Pickup`
and `Console`. `Console` has no `Change`: a veto is its whole answer, and it
means the line was a mod's own command.

A mod that takes a line can answer in the console with
`getGame().getConsole().print(text)`. That is the agent command
`console.print` with one field, `text`, and the agent shows it the way the
game shows its HELP list, through the game's own console event. It is one
line: control characters become spaces, anything past 255 characters is cut,
and an empty line is refused. The reply means the line is queued. The agent
sends it from the engine thread on its next tick, at most eight lines per
tick with 64 waiting. The mapping behind it is static so far, so it has not
yet been seen working in the game.

The old design had the event itself carry the writes. That is why this one
does not: a getter read the value as it arrived while a setter wrote into a
separate map, so a second mod editing the same field never saw the first one
and silently overwrote it. Nothing in the API could express the difference,
and nothing in the loader could see the collision.

## What a listener reads

The event is immutable. Two readings of the number under decision:

    initial()   what the game sent, before any mod touched it
    value()     the same number with every earlier listener's work folded in

`value()` is the one to read. A mod that doubles experience writes
`e.value() * 2`, and two such mods compose into four times without either
knowing the other exists. `initial()` is for a listener that needs to report
or reason about what the game itself intended.

Both live on the events that decide a number, not on `Event`. `Pickup` decides
which object is picked up and what that object becomes, neither of which is a
quantity, so it carries neither and answers `ref()` and `edited()` instead.

The event is immutable to a mod, not to the loader: `value()` reads a field
only the bus writes, between listeners. One object per frame, no copying, and
no way for a mod to write into it.

## What a listener returns

`EventMutation` is sealed, so the fold is an exhaustive switch and a new case
is a compile error everywhere it is not handled yet.

| | Meaning | What the game is told |
|---|---|---|
| `None` | I add nothing. The fold stands as it is. | nothing |
| `Reset` | Discard every earlier mod's work, back to `initial`. | nothing |
| `Veto` | This must not happen at all. | `cancel` |
| `Change` | A value. `Experience.Mutation` and the other six. | `set.<field>` |
| `.last()` | Any of the above, and the chain stops here. | as the mutation |

`Reset` and `Veto` are different and both are needed. `Reset` puts the game's
own number back: the damage still lands, at the size the game computed.
`Veto` stops the write: the damage does not land. Calling either one "cancel"
is what made this worth writing down.

A veto is not sticky. A later listener can return `Reset` and lift it, which
is the only way to lift it. One rule instead of a rule with an exception, and
the undo is loud rather than incidental.

`last()` is the answer to "my mod is authoritative here". It is blunt on
purpose, and it hands the win to whoever runs earlier, which is decided by
priority and then by mod load order — alphabetical by jar filename. Reach for
`Priority` first.

## The fold

Dispatch order is unchanged: `FIRST`, `NORMAL`, `LAST`, `MONITOR`, and inside
one step the order listeners registered. The bus folds each returned mutation
before calling the next listener, which is what makes `value()` mean what it
says.

Three of the cases throw away work another mod did. Each one is logged every
time, naming the listener, the event and what was discarded. The fold moved
into the bus so that these are visible; a quiet `Reset` would be the old
silent overwrite with a better name.

`Priority` is ordering and nothing else now. `MONITOR` is where a tracer, a
counter or a statistics mod belongs, and a `MONITOR` listener that returns a
mutation is a lint error at mod build time and an ignored value with one
warning at run time.

`last()` does not skip the `MONITOR` step. A mod may cut the deciding short;
it may not blind the trackers. Whatever the monitors see is the final state,
including who ended the chain.

`@Subscribe(ignoreVetoed = true)` skips a listener once an earlier one has
vetoed. Off by default, because a listener undoing its own side effects has to
hear about the veto too.

## Registering

Java has two methods, because one overloaded name cannot carry both lambda
shapes without becoming ambiguous:

```java
events.on(Experience.class, e -> context.log("xp " + e.gain()));
events.decide(Experience.class, e -> Experience.Mutation.change(e.value() * 2));
```

`decide` is closed statically: it takes an `E extends Decides<M>` and returns
`M`, so `events.decide(Death.class, ...)` does not compile and the returned
type is pinned to that event's own `Mutation`.

Kotlin has one `on`, through a scope receiver rather than an overload:

```kotlin
on<Experience> { log("xp ${it.gain}") }
on<Experience> { mutate { Experience.Mutation.change(it.value * 2) } }
on<Death>      { mutate { ... } }              // does not compile
```

`mutate` is an extension that exists only when `E : Decides<M>`, so an event
with nothing to decide has no such function. It has to be shaped this way.
Kotlin coerces any lambda to `() -> Unit`, so two overloads split by lambda
return type would let a returned mutation be silently discarded — the exact
failure this design exists to remove.

## What crosses the wire

Nothing here changes the protocol. The host and the agent still receive
`cancel` or a set of `set.<field>` entries, and every case above collapses
into one of those. `last()`, `Reset` and `None` are decided on the JVM and
are never seen by the game.

A `Change` turns into wire fields through a package-private method on the
event. Mods cannot reach it and cannot implement their own `Change`, so the
string keys stay at the edge where the wire is.

## Open questions

- `Skill.initial()` is a fiction at both ends of the range, and this is
  settled rather than suspected: the disassembly puts the game's clamp-to-255
  and clamp-to-0 branches before the hook, each storing the byte and jumping
  straight to it, so the agent's `stored - delta` is wrong whenever a clamp
  ran and the hook cannot tell a clamped write from a normal one. `RESET` on
  `Skill` inherits that. A true previous value needs the agent to read it at
  `0x005827AB`, which `hooksafe.py` has to bless first. Recorded on the
  `skillWrite` row in `mappings`. `Attribute` is fine, it snapshots on entry;
  `Experience` reconstructs too but can read the sheet instead.
- `ignoreVetoed` is a working name.
