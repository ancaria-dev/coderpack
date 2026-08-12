# coderpack

<div align="center">

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.7.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-agent-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Version](https://img.shields.io/badge/version-0.1.0-4B5563?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-4B5563?style=for-the-badge)
![Sacred](https://img.shields.io/badge/Sacred-Community-8B1A1A?style=for-the-badge&labelColor=1C1410)

</div>

[Русский](README.md) · [English](README.EN.md)

Java-Mods für Sacred Gold, das Action-RPG aus dem Jahr 2004.

Coderpack klinkt sich in das laufende Spiel ein und leitet aus dem Spielcode
Ereignisse ab. Ein Mod abonniert nur die Ereignisse, die er braucht. Einige
treten auf, bevor das Spiel einen Wert schreibt. Dort kann der Mod den Wert
ändern oder den Schreibvorgang abbrechen. Die Dateien im Spielordner bleiben
unverändert. Beim Beenden des Spiels verschwinden auch die Hooks.

## Mod schreiben

Ein Mod ist eine JAR-Datei in `<Sacred Gold>/mods`. Gebaut wird sie mit dem
Gradle-Plugin aus [ancaria-dev/build](https://github.com/ancaria-dev/build):

```kotlin
plugins {
    id("dev.ancaria.coderpack") version "0.1.0"
}

version = "1.0.0"

sacred {
    id = "double-gold"
    displayName = "Double Gold"
    description = "Twice the loot, same purse"
    entrypoint = "com.example.DoubleGold"
    apiVersion = "0.1.0"
    author("you")
}
```

`gradlew assembleSacredMod` schreibt die geprüfte Fat JAR nach
`build/sacred-mod`. Aus dem `sacred`-Block erzeugt das Plugin
`META-INF/declaration.toml`. Diese Datei liest der Loader, bevor er Code aus dem
Mod ausführt. Die Runtime-Abhängigkeiten landen zusammen mit den Klassen des
Mods in derselben JAR-Datei. `apiVersion` fügt
`dev.ancaria.coderpack:api` als **compileOnly**-Abhängigkeit hinzu, da der Loader
die API bereits im Classpath bereitstellt. Die Prüfung weist eine Mod-JAR ab,
wenn sie trotzdem Klassen der Loader-API enthält.

Im Deskriptor steht außerdem `api = "1"`. Das Feld wird vom Plugin erzeugt und
bezeichnet den API-Vertrag, nicht die Artefaktversion. Das Artefakt
`dev.ancaria.coderpack:api` hat die Version 0.1.0 und ändert sich mit einem
Release. `Api.VERSION` steht derzeit auf `1` und wird erst erhöht, wenn ein gegen
den bisherigen Vertrag kompilierter Mod mit der neuen API nicht mehr
funktioniert.

Der Loader wertet `api` als Versionsbereich aus. Der Wert `"1"` meint exakt
Vertrag 1. Bereiche wie `"[1,2)"` sind ebenfalls zulässig. Fehlt das Feld, ist
der Bereich ungültig oder umfasst er `Api.VERSION` nicht, verweigert der Loader
den Start und protokolliert den Grund. Mit `apiRange` kann ein Mod einen anderen
Bereich angeben, solange er den Vertrag dieses Toolchains enthält. `loaderRange`
setzt die optionale Loader-Grenze. Ein ungültiger Wert wird abgewiesen.
Optional kann `loader` einen Bereich von
Sacred-Mod-Loader-Versionen angeben, etwa `"[0.1.20,)"`. Ohne dieses Feld stellt
der Mod keine Anforderung an die Loader-Version. Der Launcher führt einen
abgewiesenen Mod weiterhin auf, deaktiviert und mit einem roten Hinweis unter
der Beschreibung. Der JVM-Loader prüft beide Bereiche vor dem ersten Aufruf von
Mod-Code und protokolliert genau eine Ablehnung. Fehlt
`<Sacred Gold>/launcher/VERSION`, überspringt er die Prüfung des
`loader`-Bereichs.

Der Mod selbst sieht so aus:

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
            event.delta(event.delta() * 2);   // umgeschrieben, bevor das Spiel schreibt
        }
    }
}
```

Eine unterstützte Listener-Methode mit `@Subscribe` ist öffentlich, gibt nichts
zurück und nimmt genau ein Ereignis entgegen. Der Parametertyp bestimmt das
Abonnement. Ein separater Ereignisname muss daher nirgends gepflegt werden. Für
dynamische Registrierung gibt `context.events().on(...)` ein `Handle` zurück,
mit dem sich der Listener wieder entfernen lässt.

`@Subscribe` regelt auch die Reihenfolge. `priority` legt die Stufe fest:
`FIRST`, `NORMAL` als Standard, `LAST` und anschließend `MONITOR`. Innerhalb
einer Stufe gilt die Registrierungsreihenfolge. Ein Abbruch beendet die
Auslieferung nicht. Mit `ignoreCancelled = true` überspringt der Loader den
Listener, sobald das Ereignis bereits abgebrochen wurde. Ohne diese Option
erhält er das Ereignis weiterhin, was etwa zum Rückgängigmachen eigener
Nebenwirkungen nötig sein kann.

`MONITOR` dient nur zur Beobachtung. Der Listener sieht das Ergebnis aller
vorherigen Entscheidungen. Versucht er das Ereignis abzubrechen oder
umzuschreiben, verwirft der Bus die Änderung und protokolliert den Versuch
einmal für diesen Listener.

Sechs Ereignisse lassen sich umschreiben oder abbrechen: `Gold`, `Experience`,
`Damage`, `Skill`, `Attribute` und `Pickup`. Der Rest meldet etwas, das schon
passiert ist, und ist nur lesbar: `LevelUp`, `Hero`, `World`, `Position`,
`Moved`, `Death`, `NearDeath`, `MobHit`, `MobDeath`, `Equip`, `Stored`. Was wohin
gehört, ergibt sich aus der Stelle des Hooks. Ein Veto ist nur möglich, wenn der
Hook vor dem Schreibzugriff sitzt und Coderpack den betreffenden Wert ändern
kann.

Für Ereignisse ohne eigenen API-Typ gibt es `Unknown`. Es enthält den Namen aus
dem Protokoll und die unverarbeiteten Felder, sodass Listener auf `Event` auch
neue Ereignisse sehen, bevor dafür eine eigene Klasse in der API existiert.

Die Gegenrichtung läuft über `context.game()`. `player()` liefert den Helden mit
dem zuletzt von Coderpack beobachteten Zustand und den Aktionen `teleport`,
`gold`, `hp` und `addExp`.
`uiString`, `typeName`, `typeId` und `types` greifen auf die
Nachschlagetabellen des Spiels zu. Mit `retype` lässt sich die Typbezeichnung
eines Gegenstands dauerhaft ändern. Name und Darstellung ändern sich, das
ursprüngliche Verhalten und die Modifikatoren bleiben erhalten.

Während ein Veto-Listener läuft, wartet der Spiel-Thread auf die Antwort. Solche
Listener müssen kurz bleiben. Nach 250 ms beendet der Host die Wartezeit und
lässt den ursprünglichen Wert passieren. Aufrufe über `context.game()` warten
höchstens zwei Sekunden auf eine Antwort des Agents.

## Coderpack bauen

Benötigt werden ein JDK ab Version 21 und Python 3.11. Der Gradle-Wrapper lädt
Gradle 9.7.1 selbst. `hooksafe.py` benötigt zusätzlich `pefile` und `capstone`.
Für `tests/buildcheck.js` wird Node.js gebraucht.

```
gradlew build                  die zwei JARs, in api/build/libs und zygote/build/libs
gradlew publishToMavenLocal    damit ein Mod-Build die API aus mavenLocal zieht
python tools/addr.py           erzeugt agent/src/gen/addr.js neu
python tools/hooksafe.py       weist Hook-Stellen ab, die ein Trampolin zerlegt
node tests/buildcheck.js       prüft den Agent gegen einen simulierten Prozess
python tests/replay.py         die ganze Java-Seite, ohne Spiel
```

`addr.py` bezieht die Adressliste aus dem Repository
[mappings](https://github.com/ancaria-dev/mappings). Zuerst prüft das Skript
einen als Argument übergebenen Pfad, dann `$CODERPACK_MAPPINGS` und anschließend
den benachbarten Checkout `../mappings`. Danach verwendet es die
zwischengespeicherte Datei `build/mappings/mappings.json` oder lädt sie von
GitHub dorthin. Ein einzeln geklonter Checkout lässt sich somit ohne
benachbarte Repositorys bauen.

Die gewünschte Revision steht in `.mappings-ref`, derzeit `master`. Für einen
reproduzierbaren Build gehört dort ein Tag oder Commit hinein. Spieladressen
werden nicht von Hand in den Agent geschrieben. `addr.py` erzeugt daraus
`agent/src/gen/addr.js` mit 26 RVAs, drei globalen Adressen und den Signaturen
von 20 Hook-Stellen.

Ohne Argument sucht `hooksafe.py` unter
`D:\SteamLibrary\steamapps\common\Sacred Gold` nach `pureHD.exe`, `Sacred.exe`
oder `Game.exe`. Ein Pfad zum Installationsverzeichnis oder direkt zur
ausführbaren Datei kann übergeben werden. Findet das Skript keine dieser
Dateien, meldet es `skipped` und beendet sich erfolgreich.

Frida überschreibt an einer Hook-Stelle mindestens fünf Byte und verschiebt
dabei immer vollständige x86-Instruktionen in ein Trampolin. `hooksafe.py`
verweigert unter anderem Stellen, bei denen ein Sprung in den überschriebenen
Bereich führt, eine Flag-setzende Instruktion von ihrem bedingten Sprung
getrennt wird oder sich zwei Hook-Bereiche überlappen. Weitere riskante Formen
werden als Warnung ausgegeben. Mit `--signatures` schreibt das Skript außerdem
die ersten acht Byte jeder der 20 Hook-Stellen nach
`agent/signatures.json`. Der Agent vergleicht diese Signaturen beim Einhängen
mit dem laufenden Prozess und warnt bei einem abweichenden Build. Die Hooks
werden trotz der Warnung installiert.

`replay.py` setzt zuvor mit `gradlew jar` gebaute JAR-Dateien für `api` und
`zygote` sowie mindestens eine Mod-JAR voraus. Es sucht im benachbarten
`mods`-Checkout oder nimmt ein Verzeichnis als Argument entgegen. Das Skript
sendet einen vorbereiteten Ereignisstrom an den Loader und vergleicht jedes
Urteil. Fehlen Mod-JARs, endet es mit einem Fehler und der Meldung
`no mod jars found`. Der Trace muss auch das unbekannte Ereignis enthalten. Ob
das Spiel den gewünschten Wert anschließend wirklich übernimmt, kann nur ein
Test im Spiel klären.

Auf dem Branch `master` liest die CI `version` aus `gradle.properties`. Gibt es
auf dem Remote noch keinen Tag `v<version>`, lädt sie API und zygote als ein
signiertes Bündel über die Portal-API zu Maven Central hoch, legt `api.jar`,
`zygote.jar` und `agent.zip` als Release-Artefakte ab und erstellt den Tag.
`agent.zip` enthält bereits die erzeugte Adresstabelle. Eine höhere
Versionsnummer löst daher beim nächsten Build auf `master` ein Release aus.

Der Upload veröffentlicht noch nichts: er wartet im Portal darauf, dass jemand
Publish drückt. Ein Artefakt in Central lässt sich nie wieder löschen, die
ersten Releases sind also einen Blick wert.

## Was hier liegt

| | |
|---|---|
| `agent/` | Von Frida injiziertes JavaScript. Setzt Hooks auf x86-Instruktionen und sendet die beobachteten Vorgänge. Die Adressen kommen aus `gen/addr.js`. |
| `api/` | `dev.ancaria.coderpack:api`. Dagegen werden Mods kompiliert. Zur Laufzeit hat das Modul keine Abhängigkeiten. JSR 305 ist nur als compileOnly eingebunden. |
| `zygote/` | `dev.ancaria.coderpack:zygote`. Liest Frames vom Host, findet die Mod-JARs, gibt jeder einen eigenen Classloader und verteilt die Ereignisse. |
| `tools/`, `tests/`, `docs/` | Adressgenerator und Hook-Prüfung, Tests ohne laufendes Spiel sowie [RUNNING.md](docs/RUNNING.md) mit den Schritten zum Starten. |

Sacred Gold ist ein 32-Bit-Spiel. Coderpack bettet die JVM nicht in diesen
Prozess ein, sondern startet sie separat. Dazwischen vermittelt der Rust-Host
[ancaria-dev/protocol](https://github.com/ancaria-dev/protocol). Er injiziert
den Agent, startet die JVM und überträgt ein zeilenbasiertes Protokoll zwischen
beiden Seiten. Der lesende JVM-Thread ordnet Befehlsantworten zu und stellt
Ereignisse in die Warteschlange. `sal-dispatch` ruft die Mod-Listener
nacheinander für jeweils ein Ereignis auf.

## Lizenz

Der Code steht unter der MIT-Lizenz. Der vollständige Text befindet sich in
[LICENSE](LICENSE).

---

Das Projekt begann als Proof of Concept für Java-Mods in Sacred Gold. Ein
Supportversprechen ist damit nicht verbunden.
