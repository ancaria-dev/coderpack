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

[English](README.EN.md) · [Deutsch](README.DE.md)

API для модов Sacred Gold и загрузчик, который эти моды запускает.

Coderpack следит за запущенной игрой и превращает происходящее в ней в
события: подобранное золото, нанесённый урон, новый уровень. Мод подписывается
на нужные ему события. Некоторые приходят до того, как игра запишет значение,
и тогда мод может его изменить или отменить.

Файлы игры остаются нетронутыми. Все хуки живут в памяти и исчезают вместе с
процессом игры.

## Как начать

Мод — это jar в папке `<Sacred Gold>/mods`. Его собирает Gradle-плагин из
[build](https://github.com/ancaria-dev/build). Быстрее всего готовый проект
создают [плагин для IntelliJ IDEA](https://github.com/ancaria-dev/idea) или
команда `coderpack new my-mod`. Вручную скрипт сборки выглядит так:

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

`apiVersion` добавляет `dev.ancaria.coderpack:api` как зависимость
`compileOnly`. API во время работы даёт загрузчик, поэтому в jar мода его быть
не должно.

Теперь сам мод:

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

`gradlew assembleSacredMod` кладёт готовый jar в `build/sacred-mod`. Плагин
создаёт `META-INF/declaration.toml` из блока `sacred`, упаковывает в jar
зависимости мода и отклоняет jar, в котором есть классы API загрузчика.

## Жизненный цикл мода

Точка входа наследует `SacredMod` и сохраняет публичный конструктор без
аргументов. Загрузчик создаёт экземпляр и сразу передаёт ему `Context`, так
что `getContext()` работает уже в инициализаторе поля.

В `onLoad()` мод регистрирует слушателей. `onUnload()` — последний вызов,
который мод получает, когда его выгружают или загрузчик завершает работу. К
этому моменту слушатели уже отключены.

## Слушатели

`register(this)` делает слушателем каждый публичный метод с `@Subscribe` и
ровно одним параметром-событием. Тип параметра выбирает событие, так что
отдельное имя события поддерживать не нужно.

Тип возвращаемого значения говорит, что метод может делать:

- `void` только наблюдает.
- `Mutation` своего события решает: `none()`, `reset()`, `veto()` или новое
  значение, например `change(value)`.

События доступны только для чтения. Изменить событие можно только
возвращённой мутацией.

Регистрировать можно и без аннотаций. У
`getContext().getRegistry().getEventRegistry()` метод `on(...)` добавляет
наблюдателя, а `decide(...)` — решающего слушателя. Оба возвращают `Handle`.
Он снимает слушателя и сообщает, какой мод его зарегистрировал, на какое
событие и с каким приоритетом. `getEvents()` перечисляет хэндлы всех модов.

Вторая половина реестра — `getModRegistry()`. Он перечисляет загруженные моды,
загружает ещё один jar через `register(path)` и выгружает мод вместе со всеми
слушателями через `unregister(id)`.

### Порядок и вето

`priority` у `@Subscribe` задаёт порядок вызова: `FIRST`, `NORMAL` (по
умолчанию), `LAST`, затем `MONITOR`. Внутри одного приоритета действует
порядок регистрации. Загрузчик учитывает каждый ответ до вызова следующего
слушателя, поэтому `getValue()` уже содержит все предыдущие решения.

Вето не останавливает рассылку. Слушатель с `ignoreVetoed = true`
пропускается, если кто-то до него наложил вето. Слушатель `MONITOR` видит
итоговое решение и обязан возвращать `void`. Линтер модов отклоняет метод
`MONITOR`, который возвращает мутацию, а загрузчик отбрасывает такую мутацию с
одним предупреждением.

Пока работают слушатели решаемого события, игровой поток ждёт, поэтому они
должны быть короткими. Срок ответа у хоста — 250 мс, проверяет он его каждые
125 мс. Когда срок истекает, хост пропускает исходное значение.

## События

Решать можно восемь событий: `Gold`, `Experience`, `Damage`, `Skill`,
`Attribute`, `CombatArt`, `Pickup` и `Console`. Вето на `Console` забирает
введённую строку как собственную команду мода.

Остальные сообщают о том, что уже произошло:

- Герой и сессия: `Hero`, `World`, `Save`, `Load`, `Position`, `LevelUp`,
  `Death`, `NearDeath`.
- Итог решения: `HealthChanged`, `MaxHealthChanged`, `GoldChanged`,
  `ExperienceChanged`, `SkillChanged`, `AttributeChanged`,
  `SkillPointsChanged`, `AttributePointsChanged`, `CombatArtChanged`.
- Мир: `Region`, `Sector`, `Spawn`, `Despawn`, `MobHit`, `MobDeath`.
- Журнал и квесты: `Kill`, `Resurrection`, `Discovery`, `Quest`.
- Предметы: `Loot`, `Drink`, `Trade`, `Moved`, `Equip`, `Stored`.
- `Unknown` — для события протокола, которому ещё нет своего типа.

Можно ли решать событие, зависит от места хука. Вето работает, только если
хук срабатывает до того, как игра запишет значение. Полный контракт описан в
[docs/EVENTS.md](docs/EVENTS.md).

## Работа с игрой

`getContext().getGame()` даёт прямой доступ к игре.

- `getWorld().getEntityRegistry().getPlayer()` возвращает героя. Уровень, HP,
  золото, опыт и позиция берутся из последнего известного состояния и
  читаются бесплатно. У героя есть `teleport`, `setGold`, `setHp`, `addExp` и
  `kill`.
- `getAttributes()`, `getSkills()`, `getCombatArts()`, `getStats()` (страница
  статистики в журнале) и `getSheet()` (броня, скорость атаки и движения,
  сопротивления) при каждом вызове спрашивают игру и возвращают снимок.
  `setAttribute`, `setSkill` и `setCombatArt` записывают значения обратно.
- Тот же `EntityRegistry` перечисляет существ, которых держит игра, рядом с
  точкой или всех, и читает одно по ссылке. `getWorld()` задаёт существу HP,
  убивает его и сообщает, в какой регион и сектор герой вошёл последним.
- `getTypeRegistry()` даёт `getTypeName`, `getTypeId`, `types`, `retype` и
  `reshape`. `retype` навсегда меняет у предмета название типа и внешний вид,
  но сохраняет поведение и модификаторы. `reshape` превращает предмет в копию
  другого вместе с модификаторами.
- `getUiString` читает локализованные тексты игры, `getConsole().print(text)`
  пишет строку в игровую консоль, а `getDirectory()` возвращает папку игры.

Все коллекции, которые возвращает API, неизменяемые. Команда ждёт ответа
агента до двух секунд.

## Логи

`getContext().log` дописывает строку в общий для всех модов
`<Sacred Gold>/logs/mods.log`:

```
[2026-09-23 14:05:31.042] [double-gold]: level 12
```

Метод не ждёт диска: строки пачками записывает поток загрузчика. Поэтому его
можно вызывать и в слушателе, который держит игру, и из собственных потоков
мода. `getContext().print` отправляет ту же строку в консоль хоста.

Любое другое логирование тоже работает. Log4j2, Logback, slf4j-simple и
`java.util.logging` не требуют настройки, а stdout и stderr JVM попадают в
консоль хоста. До загрузки первого мода загрузчик перенаправляет `System.out`
в stderr, поэтому обычный `println` оказывается там. И всё же лучше
пользоваться `log` и `print`: когда модов пять, только они показывают, какой
из них написал строку.

## Kotlin и Groovy

`dev.ancaria.coderpack:api-kotlin` добавляет к тому же API синтаксис Kotlin.
Новых возможностей он не даёт: каждое объявление вызывает метод из `api`.
Загрузчик модуль не поставляет, так что мод упаковывает его сам, рядом со
стандартной библиотекой Kotlin. В проектах из
`coderpack new --language kotlin` зависимость уже подключена.

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

`SacredMod` — тот же Java-класс, а его `getContext()` Kotlin читает как
`context`. `on<Gold>` принимает событие параметром типа, а
`context.events { }` собирает несколько регистраций в один блок.
`mutate { }` принимает решение. Он есть только у решаемых событий, поэтому
`on<Death> { mutate { ... } }` не компилируется. Все чтения в API — геттеры,
так что `it.value` и `it.isSpending` и так становятся свойствами.

`@Subscribe` и `context.registry.eventRegistry` работают так же, как в Java.
Расширения необязательны.

Groovy тоже поддерживается: `coderpack new --language groovy` создаёт проект и
упаковывает рантайм Groovy в jar мода.

## Совместимость

По умолчанию в дескрипторе стоит `api = "[3,4)"`. Это диапазон версий
API-контракта, который загрузчик проверяет перед запуском мода. Контракт не
совпадает с версией артефакта (сейчас `0.200.0`). Версия артефакта может
меняться с каждым релизом, а контракт — только когда моды, собранные под
прежний, перестанут работать.

- `apiRange` расширяет или сужает этот диапазон, например
  `apiRange = "[3,5)"`. Он должен включать контракт вашего инструментария.
- `loaderRange` ограничивает релизы Sacred Mod Loader, которым можно запускать
  мод.

Если какой-то диапазон исключает текущую версию, лаунчер показывает мод с
неактивным флажком и причиной отказа. JVM-загрузчик проверяет те же диапазоны
до запуска кода мода и пишет в лог один отказ. Отсутствующее или неверное поле
`api` тоже приводит к отказу. Неверный диапазон `loader` тоже, но если нет
файла `<Sacred Gold>/launcher/VERSION`, загрузчик пропускает проверку `loader`.

## Как это устроено

Sacred Gold — 32-битный процесс, поэтому JVM работает рядом с игрой, а не
внутри неё. Между ними стоит Rust-хост из
[protocol](https://github.com/ancaria-dev/protocol). Он внедряет агент,
запускает JVM и передаёт сообщения строчного протокола. На стороне JVM поток
чтения разносит ответы на команды и ставит события в очередь, а поток
`sal-dispatch` вызывает слушателей модов по одному событию за раз.

| Путь | Что внутри |
|---|---|
| `agent/` | JavaScript, который Frida внедряет в игру. Он ставит хуки на отдельные инструкции x86 и сообщает, что видит. Адреса берёт из `gen/addr.js`. |
| `api/` | `dev.ancaria.coderpack:api`, под который компилируются моды. Без зависимостей во время работы, JSR 305 только при компиляции. |
| `api-kotlin/` | `dev.ancaria.coderpack:api-kotlin`, inline-расширения Kotlin над `api` в пакете `dev.ancaria.coderpack.ktx`. |
| `zygote/` | `dev.ancaria.coderpack:zygote`. Читает кадры хоста, находит jar модов, даёт каждому моду свой загрузчик классов и рассылает события. |
| `tools/`, `tests/`, `docs/` | Инструменты для адресов и безопасности хуков, тестовые стенды и [RUNNING.md](docs/RUNNING.md) о сборке, запуске и поиске причин падений. |

## Сборка

Нужны JDK 21 или новее и Python 3.11. Gradle Wrapper сам скачает Gradle
9.7.1. `tools/hooksafe.py` также требует `pefile` и `capstone`, а
`tests/buildcheck.js` — Node.

```
gradlew build                  the three jars, in */build/libs
gradlew publishToMavenLocal    lets a mod build resolve the API from mavenLocal
python tools/addr.py           regenerates agent/src/gen/addr.js
python tools/hooksafe.py       refuses hook sites a trampoline would corrupt
node tests/buildcheck.js       checks the agent against a fake process
python tests/replay.py         checks the Java side with no game running
```

### Адреса

`addr.py` генерирует адреса игры из реестра в
[mappings](https://github.com/ancaria-dev/mappings). Вручную их никто не
копирует. Реестр ищется по порядку: путь из командной строки,
`$CODERPACK_MAPPINGS`, соседний чекаут `../mappings`, затем
`build/mappings/mappings.json`. Если ничего нет, файл скачивается с GitHub в
этот кэш. Ревизию задаёт `.mappings-ref`, сейчас там `master`. Для
воспроизводимой сборки укажите там тег или коммит.

Все адреса относятся к `pureHD.exe` 2.0.2.118. Загрузчик подключается и к
`Sacred.exe` и `Game.exe`, но агент предупреждает, если байты на местах хуков
не совпадают с `agent/signatures.json`. Хуки он всё равно ставит.

### Безопасность хуков

Frida заменяет на месте хука минимум пять байт. После короткой инструкции
патч залезает на следующую. `hooksafe.py` отклоняет место хука, если переход
попадает внутрь заменённых байт, два патча перекрываются или перенос отрывает
инструкцию, выставляющую флаги, от условного перехода. Другие рискованные
случаи дают предупреждения.

По умолчанию скрипт читает игру из
`D:\SteamLibrary\steamapps\common\Sacred Gold`. Чтобы взять другую, передайте
исполняемый файл или его папку. Если в папке нет поддерживаемого исполняемого
файла, проверка печатает `skipped`. `--signatures` переписывает
`agent/signatures.json` по выбранному файлу.

### Реплей

`replay.py` подаёт загрузчику заранее записанный поток событий, проверяет
каждое решение и сверяет трассу, включая неизвестное событие. Ему нужны два
собранных jar в `*/build/libs` и хотя бы один собранный мод. Мод он ищет в
`../mods/*/build/sacred-mod/` или берёт папку из аргумента. Без мода скрипт
останавливается с `no mod jars found` и ненулевым кодом. Доказать, что игра
действительно запишет запрошенное значение, реплей не может.

## Релизы

На `master` CI публикует версию из `gradle.properties`, если на сервере ещё
нет тега `v<version>`. Он загружает `api`, `api-kotlin` и `zygote` в Maven
Central одним подписанным пакетом, прикладывает `api.jar`, `zygote.jar` и
`agent.zip` к GitHub-релизу и создаёт тег. В `agent.zip` лежит
сгенерированная таблица адресов, поэтому `protocol` собирается и без соседнего
чекаута coderpack.

Загрузка в Central ждёт в портале, пока кто-нибудь не нажмёт Publish.
Артефакт из Central удалить нельзя, поэтому сначала проверьте его. Порядок
релизов между репозиториями описан в корневом
[CONTRIBUTING](https://github.com/ancaria-dev/.github/blob/master/CONTRIBUTING.md).

## Лицензия

MIT, см. [LICENSE](LICENSE).
