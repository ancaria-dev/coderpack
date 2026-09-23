"""Runs Coderpack against a scripted event stream, with no game involved.

This stands in for the host, which means answering commands as well as sending
events: a mod that builds a table at startup asks the game for type ids, and
without a reply it would sit there until its own timeout.  Answering them is
what makes the interesting half testable: that a mod's decision comes back as
the right verdict, on a canned type table nobody has to launch Sacred for.

The agent's side (does the game really commit that value) can only be checked
in-game.
"""
import pathlib
import queue
import shutil
import subprocess
import sys
import threading

ROOT = pathlib.Path(__file__).resolve().parent.parent
DIST = ROOT / "build" / "replay"


def jars():
    """The two jars this build just produced, not a copy installed somewhere."""
    found = []
    for part in ("api", "zygote"):
        libs = sorted((ROOT / part / "build" / "libs").glob(f"{part}-*.jar"))
        libs = [j for j in libs if not j.stem.endswith(("-sources", "-javadoc"))]
        if not libs:
            raise SystemExit("no " + part + " jar. Run `gradlew jar` first")
        found.append(libs[-1])
    return found


def mods(argv):
    """Mod jars to run against.

    They are built by their own repository, so a path is accepted. Without one
    this looks for the sibling checkout's build output.
    """
    for candidate in list(argv) + [ROOT.parent / "mods"]:
        root = pathlib.Path(candidate)
        if not root.is_dir():
            continue
        found = list(root.glob("*/build/sacred-mod/*.jar")) or list(root.glob("*.jar"))
        if found:
            return found
    raise SystemExit(
        "no mod jars found. Build them in the mods checkout beside this one, "
        "or pass a directory: python tests/replay.py <dir with mod jars>")

# A stand-in for the game's type table.  Only the shape matters: names carry
# the meaning, ids are whatever this build happens to use.
TYPES = {
    "TYPE_OBJECT_POTION_SMALL_RED": 5171,
    "TYPE_OBJECT_POTION_MEDIUM_RED": 5145,
    "TYPE_OBJECT_POTION_LARGE_RED": 5100,
    "TYPE_OBJECT_POTION_SMALL_GREEN": 5180,
    "TYPE_OBJECT_POTION_LARGE_GREEN": 5110,
    "TYPE_SMOVE_UPGRADE_HARDHIT_SERA": 6001,
    "TYPE_SMOVE_UPGRADE_ZEALHIT_SERA": 6002,
    "TYPE_SMOVE_UPGRADE_DEM_ATTACKE": 6100,
    "TYPE_SPELL_UPGRADE_LIGHTNINGSTRIKE": 5387,
}

SCRIPT = [
    ("EVT 0 agent.ready base=0x400000", None),
    ("EVT 0 session.world_loaded", None),
    # A Seraphim, so a Daemon rune below is somebody else's.
    ("EVT 0 hero.captured cls=1 clsName=Seraphim level=10 hp=100 maxHp=100 "
     "gold=50 exp=0", None),

    ("ASK 1 health.damage kind=damage damage=30 prev=100 next=70 max=100",
     "END 1 ok=1"),
    ("ASK 2 gold.delta delta=100 current=50 dir=gain", "END 2 ok=1"),

    # A small red potion becomes the large one of its own colour.
    ("ASK 3 item.pickup ref=1083 type=5171 name=TYPE_OBJECT_POTION_SMALL_RED "
     "level=0 min=0 atk=0 prot=0 pct=20 player=1", "END 3 set.type=5100"),
    # A potion that is already the largest is left alone.
    ("ASK 4 item.pickup ref=1084 type=5100 name=TYPE_OBJECT_POTION_LARGE_RED "
     "level=0 min=0 atk=0 prot=0 pct=20 player=1", "END 4 ok=1"),
    # A Daemon rune before any rune of the hero's own has been seen: nothing
    # to copy, so nothing happens.  Inventing one is what the first version did.
    ("ASK 5 item.pickup ref=857 type=6100 "
     "name=TYPE_SMOVE_UPGRADE_DEM_ATTACKE level=1 min=1 atk=0 prot=0 pct=21 "
     "price=5 mods=1009:1,814:10 player=1", "END 5 ok=1"),
    # One of hers: kept as a template, untouched.
    ("ASK 8 item.pickup ref=1334 type=6001 "
     "name=TYPE_SMOVE_UPGRADE_HARDHIT_SERA level=1 min=1 atk=0 prot=0 pct=21 "
     "price=6 mods=22:1,807:10 player=1", "END 8 ok=1"),
    # Now the same Daemon rune becomes a copy of it: type, price, level,
    # minimum level and, the part that matters, the modifier list.
    ("ASK 9 item.pickup ref=858 type=6100 "
     "name=TYPE_SMOVE_UPGRADE_DEM_ATTACKE level=1 min=1 atk=0 prot=0 pct=21 "
     "price=5 mods=1009:1,814:10 player=1",
     "END 9 set.type=6001 set.price=6 set.level=1 set.min=1 set.mods=22:1,807:10"),
    # A rune the table says nothing about stays exactly as it is.  This is the
    # case that must never be "helpfully" rewritten.
    ("ASK 6 item.pickup ref=1288 type=5387 "
     "name=TYPE_SPELL_UPGRADE_LIGHTNINGSTRIKE level=1 min=1 atk=0 prot=0 "
     "pct=21 player=1", "END 6 ok=1"),
    # Someone else picking something up is never asked about in the game, but
    # if it ever is, the mods must keep their hands off it.
    ("ASK 7 item.pickup ref=1085 type=5171 name=TYPE_OBJECT_POTION_SMALL_RED "
     "level=0 min=0 atk=0 prot=0 pct=20 player=0", "END 7 ok=1"),

    ("EVT 0 item.stored ref=1083 type=5100 "
     "name=TYPE_OBJECT_POTION_LARGE_RED player=1", None),
    ("EVT 0 item.equip slot=10 ref=8814 type=1204 "
     "name=TYPE_OBJECT_RING_FIRE01 level=30 off=0 player=1", None),
    ("EVT 0 item.moved from=4 to=17", None),
    ("EVT 0 pos.changed x=222850 y=137608 uiX=4152 uiY=2564 src=hero", None),
    ("EVT 0 entity.death type=50 name=TYPE_NPC_GHUL01 level=5 prev=10 next=0 "
     "max=10 damage=10 kind=lethal", None),
    ("EVT 0 level.changed prev=10 next=11", None),
    ("EVT 0 gold.changed next=150 delta=100", None),
    ("EVT 0 world.region_enter id=1999 from=1779", None),
    ("EVT 0 entity.spawn ref=812 type=50 name=TYPE_NPC_GHUL01 level=5 hp=10 "
     "maxHp=10 x=222900 y=137600 player=0", None),
    ("EVT 0 journal.kill total=1502 type=50 name=TYPE_NPC_GHUL01 a2=5", None),
    # No mod here decides combat arts, so the game's own level stands.
    ("ASK 10 art.raise index=3 id=67 aspect=0 prev=13 next=14 step=1",
     "END 10 ok=1"),
    # An event no SDK class covers: it must still reach the mods.
    ("EVT 0 weather.rain_start intensity=3", None),
    ("EVT 0 session.hero_terminated", None),
]

# One trace line per frame above, in order.
TRACED = [
    "World.ATTACHED", "World.LOADED", "Hero", "Damage", "Gold", "Pickup", "Pickup", "Pickup",
    "Pickup", "Pickup", "Pickup", "Pickup", "Stored", "Equip", "Moved",
    "Position", "MobDeath",
    "LevelUp", "GoldChanged", "Region", "Spawn", "Kill", "CombatArt",
    "weather.rain_start", "World.HERO_TERMINATED",
]


def answer(name, fields):
    """The host's half of a command."""
    if name == "type.list":
        prefix = fields.get("prefix", "")
        pairs = [f"{n}:{i}" for n, i in TYPES.items() if n.startswith(prefix)]
        return f"ok=1 types={','.join(pairs)}"
    if name == "type.find":
        found = TYPES.get(fields.get("name", ""))
        return "ok=1" if found is None else f"ok=1 id={found}"
    if name == "item.reshape":
        return "ok=1 ref=" + fields.get("ref", "0")
    return "err=unknown%20command"


def newest_trace():
    logs = sorted((DIST / "logs").glob("logs-*.txt"))
    return logs[-1] if logs else None


def main():
    before = newest_trace()
    classpath = ";".join(str(j) for j in jars())
    # Under build/ rather than a temp directory: the zygote treats the parent
    # of the mods folder as the game folder, and the tracer writes its log
    # there, which is what newest_trace reads back.
    staged = DIST / "mods"
    shutil.rmtree(staged, ignore_errors=True)
    staged.mkdir(parents=True)
    for jar in mods(sys.argv[1:]):
        shutil.copy2(jar, staged / jar.name)
    sal = subprocess.Popen(
        # Named, not "whatever is in the folder": the mods directory also holds
        # test tools that would rewrite every pickup out from under this script.
        ["java", "-cp", classpath, "dev.ancaria.coderpack.zygote.Main",
         "--mods", str(staged),
         "--enable", "tracer,old-huge-potions,all-my-runes"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        text=True, encoding="utf-8", bufsize=1)

    verdicts = queue.Queue()

    def pump():
        for line in sal.stdout:
            parts = line.strip().split(" ")
            if not parts or not parts[0]:
                continue
            if parts[0] == "CMD":
                fields = dict(p.split("=", 1) for p in parts[3:] if "=" in p)
                sal.stdin.write(f"RES {parts[1]} {answer(parts[2], fields)}\n")
                sal.stdin.flush()
            elif parts[0] == "END":
                verdicts.put(line.strip())

    reader = threading.Thread(target=pump, daemon=True)
    reader.start()

    for line, _ in SCRIPT:
        sal.stdin.write(line + "\n")
        sal.stdin.flush()

    asks = [line.split(" ")[1] for line, _ in SCRIPT if line.startswith("ASK")]
    got = {}
    for _ in asks:
        try:
            reply = verdicts.get(timeout=15)
        except queue.Empty:
            break
        got[reply.split(" ")[1]] = reply
    sal.stdin.write("BYE\n")
    sal.stdin.flush()
    sal.wait(timeout=15)

    failures = 0
    for line, want in SCRIPT:
        if not line.startswith("ASK"):
            continue
        seq = line.split(" ")[1]
        have = got.get(seq, "(nothing)")
        ok = have == want
        failures += 0 if ok else 1
        print(f"  {'ok  ' if ok else 'FAIL'}  {have:<34}"
              f"{'' if ok else ' expected ' + want}")
    print(f"\n{len(asks) - failures}/{len(asks)} verdicts correct")

    trace = newest_trace()
    if trace is None or trace == before:
        print("  FAIL  the tracer wrote no file (is mods/tracer-mod.jar built?)")
        return 1
    names = [line.split()[1] for line in
             trace.read_text(encoding="utf-8").splitlines() if line.strip()]
    if names != TRACED:
        print(f"  FAIL  traced {names}\n        expected {TRACED}")
        failures += 1
    else:
        print(f"{len(names)} event(s) traced in {trace.name}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
