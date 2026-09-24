# coderpack

<div align="center">

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.7.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-agent-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Version](https://img.shields.io/badge/version-0.200.0-4B5563?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-4B5563?style=for-the-badge)
![Sacred](https://img.shields.io/badge/Sacred-Community-8B1A1A?style=for-the-badge&labelColor=1C1410)

</div>

[Русский](README.md) · [English](README.EN.md)

Die API, gegen die du Mods für Sacred Gold schreibst, und der Loader, der sie
ausführt.

Coderpack beobachtet das laufende Spiel und macht aus dem, was darin passiert,
Ereignisse: aufgehobenes Gold, ausgeteilter Schaden, ein neuer Level. Dein Mod
abonniert die Ereignisse, die ihn interessieren. Manche kommen, bevor das Spiel
einen Wert speichert. Dann kann dein Mod ihn ändern oder verhindern.

Die Spieldateien bleiben unangetastet. Alle Hooks leben im Arbeitsspeicher und
verschwinden, sobald das Spiel beendet wird.

## Erste Schritte

Ein Mod ist ein JAR in `<Sacred Gold>/mods`. Gebaut wird er vom Gradle-Plugin
aus [build](https://github.com/ancaria-dev/build). Am schnellsten kommst du mit
dem [Plugin für IntelliJ IDEA](https://github.com/ancaria-dev/idea) oder mit
`coderpack new my-mod` zu einem fertigen Projekt. Von Hand sieht das
Build-Skript so aus:

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

`apiVersion` fügt `dev.ancaria.coderpack:api` als `compileOnly`-Abhängigkeit
hinzu. Zur Laufzeit liefert der Loader die API, in deinem JAR hat sie also
nichts zu suchen.

Jetzt der Mod selbst:

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

`gradlew assembleSacredMod` legt das fertige JAR in `build/sacred-mod` ab. Das
Plugin erzeugt `META-INF/declaration.toml` aus dem Block `sacred`, packt die
Laufzeitabhängigkeiten deines Mods ins JAR und lehnt ein JAR ab, das Klassen
der Loader-API enthält.

## Lebenszyklus eines Mods

Dein Einstiegspunkt erbt von `SacredMod` und behält einen öffentlichen
Konstruktor ohne Argumente. Der Loader erzeugt die Instanz und übergibt ihr im
selben Schritt einen `Context`. `getContext()` funktioniert also schon in einem
Feldinitialisierer.

In `onLoad()` registrierst du deine Listener. `onUnload()` ist der letzte
Aufruf, den dein Mod bekommt – wenn er entfernt wird oder der Loader sich
beendet. Seine Listener sind dann schon abgemeldet.

## Listener

`register(this)` macht jede öffentliche Methode mit `@Subscribe` und genau
einem Ereignisparameter zum Listener. Der Parametertyp bestimmt das Ereignis,
einen Ereignisnamen musst du also nicht pflegen.

Der Rückgabetyp legt fest, was die Methode darf:

- `void` beobachtet nur.
- Die `Mutation` des Ereignisses entscheidet: `none()`, `reset()`, `veto()`
  oder ein neuer Wert wie `change(value)`.

Ereignisse sind schreibgeschützt. Ändern kannst du eins nur über die
zurückgegebene Mutation.

Es geht auch ohne Annotationen. An
`getContext().getRegistry().getEventRegistry()` fügt `on(...)` einen
Beobachter hinzu und `decide(...)` einen Entscheider. Beide geben ein `Handle`
zurück. Damit meldest du den Listener wieder ab, und es verrät dir, welcher Mod
ihn registriert hat, für welches Ereignis und mit welcher Priorität.
`getEvents()` listet die Handles aller Mods auf.

Die andere Hälfte der Registry ist `getModRegistry()`. Sie listet die geladenen
Mods auf, lädt mit `register(path)` ein weiteres JAR und entfernt mit
`unregister(id)` einen Mod samt seinen Listenern.

### Reihenfolge und Veto

`priority` an `@Subscribe` bestimmt die Reihenfolge: `FIRST`, `NORMAL`
(Standard), `LAST`, dann `MONITOR`. Innerhalb einer Priorität zählt die
Reihenfolge der Registrierung. Der Loader verrechnet jede Antwort, bevor der
nächste Listener läuft, `getValue()` enthält also schon alle früheren
Entscheidungen.

Ein Veto stoppt die Verteilung nicht. Ein Listener mit `ignoreVetoed = true`
wird übersprungen, sobald ein früherer ein Veto eingelegt hat. Ein
`MONITOR`-Listener sieht die endgültige Entscheidung und muss `void`
zurückgeben. Der Mod-Linter lehnt eine `MONITOR`-Methode mit Mutation ab, und
der Loader verwirft eine solche Mutation mit einer Warnung.

Listener auf ein entscheidbares Ereignis laufen, während der Spielthread
wartet. Halt sie also kurz. Die Frist des Hosts beträgt 250 ms, geprüft wird
alle 125 ms. Ist sie abgelaufen, lässt der Host den ursprünglichen Wert durch.

## Ereignisse

Acht Ereignisse lassen sich entscheiden: `Gold`, `Experience`, `Damage`,
`Skill`, `Attribute`, `CombatArt`, `Pickup` und `Console`. Ein Veto auf
`Console` übernimmt die eingetippte Zeile als eigenen Befehl deines Mods.

Die übrigen melden etwas, das schon passiert ist:

- Held und Sitzung: `Hero`, `World`, `Save`, `Load`, `Position`, `LevelUp`,
  `Death`, `NearDeath`.
- Das Ergebnis einer Entscheidung: `HealthChanged`, `MaxHealthChanged`,
  `GoldChanged`, `ExperienceChanged`, `SkillChanged`, `AttributeChanged`,
  `SkillPointsChanged`, `AttributePointsChanged`, `CombatArtChanged`.
- Die Welt: `Region`, `Sector`, `Spawn`, `Despawn`, `MobHit`, `MobDeath`.
- Tagebuch und Quests: `Kill`, `Resurrection`, `Discovery`, `Quest`.
- Gegenstände: `Loot`, `Drink`, `Trade`, `Moved`, `Equip`, `Stored`.
- `Unknown` für ein Protokollereignis, für das es noch keinen Typ gibt.

Ob sich ein Ereignis entscheiden lässt, hängt davon ab, wo sein Hook sitzt. Ein
Veto wirkt nur, wenn der Hook läuft, bevor das Spiel den Wert schreibt.
[docs/EVENTS.md](docs/EVENTS.md) beschreibt den Vertrag vollständig.

## Mit dem Spiel arbeiten

`getContext().getGame()` gibt dir direkten Zugriff.

- `getWorld().getEntityRegistry().getPlayer()` liefert den Helden. Level, HP,
  Gold, Erfahrung und Position stammen aus dem zuletzt beobachteten Zustand
  und kosten beim Lesen nichts. Dazu gibt es `teleport`, `setGold`, `setHp`,
  `addExp` und `kill`.
- `getAttributes()`, `getSkills()`, `getCombatArts()`, `getStats()` (die
  Statistikseite im Tagebuch) und `getSheet()` (Rüstung, Angriffs- und
  Laufgeschwindigkeit, Resistenzen) fragen bei jedem Aufruf das Spiel und
  liefern einen Schnappschuss. `setAttribute`, `setSkill` und `setCombatArt`
  schreiben zurück.
- Dieselbe `EntityRegistry` listet die Kreaturen auf, die das Spiel gerade
  hält, in der Nähe eines Punkts oder alle, und liest eine über ihre Referenz.
  `getWorld()` setzt die HP einer Kreatur, tötet sie und nennt Region und
  Sektor, die der Held zuletzt betreten hat.
- `getTypeRegistry()` bietet `getTypeName`, `getTypeId`, `types`, `retype` und
  `reshape`. `retype` ändert Typbezeichnung und Aussehen eines Gegenstands
  dauerhaft, behält aber Verhalten und Modifikatoren. `reshape` macht aus einem
  Gegenstand eine Kopie eines anderen, Modifikatoren eingeschlossen.
- `getUiString` liest die lokalisierten Texte des Spiels,
  `getConsole().print(text)` schreibt eine Zeile in die Spielkonsole, und
  `getDirectory()` liefert den Spielordner.

Jede Sammlung, die die API zurückgibt, ist unveränderlich. Ein Befehl wartet
bis zu zwei Sekunden auf die Antwort des Agenten.

## Protokollierung

`getContext().log` hängt eine Zeile an `<Sacred Gold>/logs/mods.log` an, eine
Datei für alle Mods:

```
[2026-09-23 14:05:31.042] [double-gold]: level 12
```

Die Methode wartet nie auf die Festplatte: Ein Loader-Thread schreibt die
Zeilen gesammelt. Du kannst sie also in einem Listener aufrufen, der das Spiel
aufhält, und genauso aus eigenen Threads. `getContext().print` schickt dieselbe
Zeile stattdessen an die Konsole des Hosts.

Jede andere Protokollierung funktioniert ebenfalls. Log4j2, Logback,
slf4j-simple und `java.util.logging` brauchen keine Einrichtung, und stdout
und stderr der JVM landen in der Konsole des Hosts. Vor dem ersten Mod leitet
der Loader `System.out` auf stderr um, deshalb taucht ein einfaches `println`
dort auf. Trotzdem sind `log` und `print` die bessere Wahl: Bei fünf
installierten Mods verraten nur sie, welcher Mod gesprochen hat.

## Kotlin und Groovy

`dev.ancaria.coderpack:api-kotlin` legt Kotlin-Syntax über dieselbe API. Neue
Fähigkeiten bringt es nicht mit: Jede Deklaration ruft eine Methode aus `api`
auf. Der Loader liefert das Modul nicht mit, ein Mod packt es also selbst ein,
neben die Kotlin-Standardbibliothek. Projekte aus
`coderpack new --language kotlin` binden es schon ein.

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

`SacredMod` ist dieselbe Java-Klasse, und Kotlin liest ihr `getContext()` als
`context`. `on<Gold>` nimmt das Ereignis als Typargument, und
`context.events { }` fasst mehrere Registrierungen in einem Block zusammen.
`mutate { }` entscheidet. Es gibt es nur bei entscheidbaren Ereignissen, darum
kompiliert `on<Death> { mutate { ... } }` nicht. Jeder Lesezugriff der API ist
ein Getter, `it.value` und `it.isSpending` sind also ohnehin Eigenschaften.

`@Subscribe` und `context.registry.eventRegistry` funktionieren genau wie in
Java. Keine der Erweiterungen ist Pflicht.

Groovy geht auch: `coderpack new --language groovy` legt das Projekt an und
packt die Groovy-Laufzeit ins JAR des Mods.

## Kompatibilität

Der Deskriptor enthält standardmäßig `api = "[3,4)"`. Das ist der Bereich der
API-Verträge, den der Loader vor dem Start des Mods prüft. Der Vertrag ist
nicht die Artefaktversion (derzeit `0.200.0`). Die Artefaktversion kann sich
mit jedem Release ändern, der Vertrag nur dann, wenn Mods für den alten
Vertrag nicht mehr laufen würden.

- `apiRange` erweitert oder verengt diesen Bereich, etwa
  `apiRange = "[3,5)"`. Er muss den Vertrag deiner Werkzeugkette enthalten.
- `loaderRange` begrenzt, welche Releases des Sacred Mod Loader den Mod
  ausführen dürfen.

Schließt einer der Bereiche die aktuelle Version aus, zeigt der Launcher den
Mod mit deaktiviertem Häkchen und nennt den Grund. Der JVM-Loader prüft
dieselben Bereiche, bevor er Code des Mods ausführt, und protokolliert eine
Ablehnung. Ein fehlendes oder ungültiges Feld `api` wird ebenfalls abgelehnt.
Ein ungültiger Bereich `loader` auch, doch fehlt
`<Sacred Gold>/launcher/VERSION`, überspringt der Loader die Prüfung von
`loader`.

## So funktioniert es

Sacred Gold ist ein 32-Bit-Prozess, deshalb läuft die JVM neben dem Spiel,
nicht darin. Dazwischen sitzt der Rust-Host aus
[protocol](https://github.com/ancaria-dev/protocol). Er injiziert den Agenten,
startet die JVM und überträgt das Zeilenprotokoll. Auf der JVM-Seite verteilt
ein Lesethread die Antworten auf Befehle und reiht Ereignisse ein, und der
Thread `sal-dispatch` ruft die Listener der Mods Ereignis für Ereignis auf.

| Pfad | Inhalt |
|---|---|
| `agent/` | JavaScript, das Frida ins Spiel injiziert. Es hängt sich an einzelne x86-Instruktionen und meldet, was es sieht. Die Adressen stammen aus `gen/addr.js`. |
| `api/` | `dev.ancaria.coderpack:api`, gegen das Mods kompiliert werden. Keine Laufzeitabhängigkeiten, JSR 305 nur beim Kompilieren. |
| `api-kotlin/` | `dev.ancaria.coderpack:api-kotlin`, Inline-Erweiterungen in Kotlin über `api` im Paket `dev.ancaria.coderpack.ktx`. |
| `zygote/` | `dev.ancaria.coderpack:zygote`. Liest Frames vom Host, findet Mod-JARs, gibt jedem Mod einen eigenen Classloader und verteilt Ereignisse. |
| `tools/`, `tests/`, `docs/` | Werkzeuge für Adressen und Hook-Sicherheit, Testumgebungen und [RUNNING.md](docs/RUNNING.md) zu Bauen, Starten und Absturzsuche. |

## Bauen

Du brauchst JDK 21 oder neuer und Python 3.11. Der Gradle-Wrapper lädt Gradle
9.7.1 selbst. `tools/hooksafe.py` braucht außerdem `pefile` und `capstone`,
`tests/buildcheck.js` braucht Node.

```
gradlew build                  the three jars, in */build/libs
gradlew publishToMavenLocal    lets a mod build resolve the API from mavenLocal
python tools/addr.py           regenerates agent/src/gen/addr.js
python tools/hooksafe.py       refuses hook sites a trampoline would corrupt
node tests/buildcheck.js       checks the agent against a fake process
python tests/replay.py         checks the Java side with no game running
```

### Adressen

`addr.py` erzeugt die Spieladressen aus der Registry in
[mappings](https://github.com/ancaria-dev/mappings). Von Hand kopiert sie
niemand. Die Registry sucht es in dieser Reihenfolge: ein Pfad auf der
Kommandozeile, `$CODERPACK_MAPPINGS`, der Nachbar-Checkout `../mappings`,
dann `build/mappings/mappings.json`. Gibt es nichts davon, lädt es die Datei
von GitHub in diesen Cache. Die Revision steht in `.mappings-ref`, derzeit
`master`. Für einen reproduzierbaren Build trägst du dort einen Tag oder
Commit ein.

Alle Adressen gehören zu `pureHD.exe` 2.0.2.118. Der Loader verbindet sich
auch mit `Sacred.exe` und `Game.exe`, doch der Agent warnt, wenn die Bytes an
den Hook-Stellen nicht zu `agent/signatures.json` passen. Die Hooks setzt er
trotzdem.

### Hook-Sicherheit

Frida ersetzt an einer Hook-Stelle mindestens fünf Bytes. Nach einer kurzen
Instruktion ragt der Patch in die nächste hinein. `hooksafe.py` lehnt eine
Stelle ab, wenn ein Sprung mitten in die ersetzten Bytes führt, zwei Patches
sich überlappen oder die Verschiebung eine Flag-setzende Instruktion von ihrem
bedingten Sprung trennt. Andere riskante Fälle erzeugen Warnungen.

Standardmäßig liest das Skript das Spiel aus
`D:\SteamLibrary\steamapps\common\Sacred Gold`. Für ein anderes übergibst du
die EXE oder ihren Ordner. Enthält der Ordner keine unterstützte EXE, gibt die
Prüfung `skipped` aus. `--signatures` schreibt `agent/signatures.json` aus der
gewählten Datei neu.

### Replay

`replay.py` spielt dem Loader einen vorbereiteten Ereignisstrom vor, prüft
jede Entscheidung und gleicht die Spur ab, ein unbekanntes Ereignis
eingeschlossen. Es braucht die beiden gebauten JARs in `*/build/libs` und
mindestens einen gebauten Mod. Den sucht es in `../mods/*/build/sacred-mod/`
oder nimmt einen Ordner als Argument. Ohne Mod bricht es mit
`no mod jars found` und einem Status ungleich null ab. Ob das Spiel den
gewünschten Wert wirklich übernimmt, kann der Replay nicht beweisen.

## Releases

Auf `master` veröffentlicht die CI die Version aus `gradle.properties`, wenn es
auf dem Server noch keinen Tag `v<version>` gibt. Sie lädt `api`, `api-kotlin`
und `zygote` als ein signiertes Paket in Maven Central, hängt `api.jar`,
`zygote.jar` und `agent.zip` an ein GitHub-Release und legt danach den Tag an.
`agent.zip` enthält die erzeugte Adresstabelle, deshalb baut `protocol` auch
ohne coderpack-Checkout daneben.

Der Upload wartet im Central-Portal, bis jemand auf Publish drückt. Ein
Artefakt in Central lässt sich nie wieder löschen, schau es dir also vorher
an. Die Reihenfolge der Releases über alle Repositories steht im
[CONTRIBUTING](https://github.com/ancaria-dev/.github/blob/master/CONTRIBUTING.DE.md)
des Wurzel-Repositorys.

## Lizenz

MIT, siehe [LICENSE](LICENSE).
