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

Coderpack позволяет писать моды на Java для Sacred Gold, экшн-RPG 2004 года.

Он подключается к запущенной игре, перехватывает отдельные инструкции её
32-битного кода и передаёт происходящее в виде событий. Мод подписывается только
на нужные типы. Некоторые события возникают до записи значения. Их можно
изменить или запретить (вето). Файлы игры остаются нетронутыми, а после закрытия
процесса хуки исчезают.

## Как написать мод

Мод поставляется как jar в `<Sacred Gold>/mods`. Его собирает Gradle-плагин из
[ancaria-dev/build](https://github.com/ancaria-dev/build):

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

`gradlew assembleSacredMod` собирает проверенный fat jar в
`build/sacred-mod` вместе с runtime-зависимостями мода. Из блока `sacred`
плагин создаёт `META-INF/declaration.toml`. Загрузчик читает этот дескриптор до
запуска кода. Значение `apiVersion` добавляет
`dev.ancaria.coderpack:api` в конфигурацию **compileOnly**, поскольку API уже
находится в classpath загрузчика и не должен попадать внутрь jar мода.
Проверка отклоняет jar, в который всё же попали классы API загрузчика.

По умолчанию дескриптор содержит `api = "[3,4)"`. Это диапазон совместимых
контрактов API, а не версия артефакта. Артефакт
`dev.ancaria.coderpack:api` сейчас имеет версию 0.200.0 и меняется вместе с
релизами. Номер контракта, сейчас 3, повышается только при несовместимом
изменении API. Загрузчик поддерживает Maven-диапазоны вроде `[3,4)`. Одиночное
значение `3` означает ровно контракт 3. Для другого диапазона контрактов служит
`apiRange`. Он обязан включать контракт текущего набора инструментов.
`loaderRange` ограничивает подходящие версии Sacred Mod Loader.

Перед запуском загрузчик проверяет, входит ли `Api.VERSION` в диапазон из
дескриптора. Отсутствующее или неверное поле `api`, как и несовместимый
диапазон, останавливает загрузку мода с одной записью в журнале. Необязательное
поле `loader` задаёт диапазон версий Sacred Mod Loader. Если файл
`<Sacred Gold>/launcher/VERSION` отсутствует, загрузчик пропускает эту вторую
проверку. Launcher проводит те же проверки заранее. Несовместимый мод остаётся
в списке, но его нельзя включить, а рядом отображается причина отказа.
Заданный, но неверный диапазон `loader` тоже приводит к отказу.

Сам мод:

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
        return Gold.Mutation.change(event.getValue() * 2);   // решено до того, как игра запишет
    }
}
```

Точка входа наследует `SacredMod` и сохраняет публичный конструктор без
аргументов. Загрузчик создаёт её и сразу передаёт ей `Context`, поэтому
`getContext()` работает уже в инициализаторе поля. `onUnload()` — последний
вызов, который получает мод: при снятии с регистрации или при остановке
загрузчика. К этому моменту его слушатели уже сняты.

Поддерживаемый метод с `@Subscribe` публичен и принимает ровно один объект
события. Тип параметра задаёт подписку, поэтому строковое имя события указывать
не приходится. Тип возвращаемого значения определяет, что метод может сделать.
Метод с `void` только наблюдает. Метод, который принимает решение, возвращает
`Mutation` своего события: `none()`, `reset()`, `veto()` или новое значение,
например `change(value)`. События доступны только для чтения, и вернуть мутацию
— единственный способ их изменить. Для динамической регистрации есть
`getContext().getRegistry().getEventRegistry().on(...)` для наблюдателя и
`decide(...)` для решающего слушателя. Оба возвращают `Handle`, через который
слушатель можно снять с регистрации. `Handle` также сообщает, какой мод его
зарегистрировал, на какое событие и с каким приоритетом, а `getEvents()`
перечисляет слушателей всех модов. Вторая половина реестра, `getModRegistry()`,
перечисляет загруженные моды, загружает ещё один jar через `register(path)` и
снимает мод целиком, со всеми слушателями, через `unregister(id)`.

Слушатели выполняются по приоритетам `FIRST`, `NORMAL`, `LAST`, `MONITOR`.
Внутри одного приоритета сохраняется порядок регистрации. Загрузчик применяет
каждый ответ до вызова следующего слушателя, поэтому `getValue()` уже учитывает
все предыдущие решения. Вето само по себе не прекращает рассылку. Параметр
`ignoreVetoed = true` пропускает слушатель, если предыдущий уже наложил вето.
На этапе `MONITOR` видно итоговое решение, а метод должен возвращать `void`.
Линтер модов отклоняет метод `MONITOR`, возвращающий мутацию, а загрузчик
отбрасывает такую мутацию с одним предупреждением.

Решать можно `Gold`, `Experience`, `Damage`, `Skill`, `Attribute`,
`CombatArt`, `Pickup` и `Console`: veto на строке консоли забирает её как
команду мода. Остальные события только сообщают о случившемся:

- герой и сессия: `Hero`, `World`, `Save`, `Load`, `Position`, `LevelUp`,
  `Death`, `NearDeath`;
- чем закончилось решение: `HealthChanged`, `MaxHealthChanged`,
  `GoldChanged`, `ExperienceChanged`, `SkillChanged`, `AttributeChanged`,
  `SkillPointsChanged`, `AttributePointsChanged`, `CombatArtChanged`;
- мир: `Region`, `Sector`, `Spawn`, `Despawn`, `MobHit`, `MobDeath`;
- журнал и квесты: `Kill`, `Resurrection`, `Discovery`, `Quest`;
- предметы: `Loot`, `Drink`, `Trade`, `Moved`, `Equip`, `Stored`.

Неизвестное загрузчику строковое событие не теряется. Оно приходит подписчикам
`Event` как `Unknown`. Полный контракт описан в [docs/EVENTS.md](docs/EVENTS.md).

За команды в игру отвечает `getContext().getGame()`.
`getWorld().getEntityRegistry().getPlayer()` возвращает героя. Уровень,
здоровье, золото, опыт и позиция берутся из последнего состояния, которое видел
Coderpack, и читаются бесплатно. Операции героя: `teleport`, `setGold`,
`setHp`, `addExp` и `kill`. Методы `getAttributes()`, `getSkills()`,
`getCombatArts()`, `getStats()` (страница статистики журнала) и `getSheet()`
(броня, скорость атаки и бега, сопротивления) при каждом вызове спрашивают игру
и возвращают снимок. Первые три — коллекции с `get` и `forEach`, а
`setAttribute`, `setSkill` и `setCombatArt` записывают значения обратно. Тот же
`EntityRegistry` перечисляет существ, которых держит игра, все или рядом с
точкой, и читает одно существо по ref. Сам `getWorld()` меняет здоровье
существа или убивает его и сообщает, в какой регион и сектор герой вошёл
последним. `getTypeRegistry()` даёт `getTypeName`, `getTypeId` и `types`,
которые читают таблицы игры, а `retype` меняет название типа и внешний вид
существующего предмета. Изменение сохраняется, но исходное поведение и
модификаторы предмета остаются прежними. `reshape` делает предмет копией
другого, вместе с модификаторами. `getUiString` читает локализованный текст
игры, `getConsole().print(text)` выводит строку во внутриигровую консоль, а
`getDirectory()` — папка игры. Все коллекции, которые возвращает API, нельзя
изменить. Команды ждут ответ до двух секунд.

Пока работают слушатели решаемого события, игровой поток остановлен. Срок хоста
— 250 мс, и проверяет он его раз в 125 мс. Когда срок прошёл, хост разрешает
игре использовать исходное значение. Поэтому слушатели должны завершаться
быстро.

### Логи

`getContext().log` дописывает строку в `<Sacred Gold>/logs/mods.log`, один
файл на все моды: `[2026-09-23 14:05:31.042] [double-gold]: level 12`. Диск
он не ждёт: строки пачками пишет поток загрузчика, поэтому вызов безопасен и в
слушателе, пока игра стоит, и из собственных потоков мода.
`getContext().print` выводит ту же строку в консоль хоста.

Печатать можно и как угодно иначе: кадры
протокола идут по отдельным именованным каналам, а stdout и stderr самой JVM
принадлежат модам и попадают в консоль хоста. Log4j2, Logback, slf4j-simple,
`java.util.logging` — любой из них работает без единой настройки.

Загрузчик всё же перенаправляет `System.out` в stderr до загрузки первого
мода, поэтому обычный `println` виден именно там. Это страховка на случай
запуска без выделенного канала, и на мод она не влияет.

`log` и `print` при этом остаются лучшей привычкой: когда установлено пять
модов, только они говорят, кто именно это сказал.

### Тот же мод на Kotlin

`dev.ancaria.coderpack:api-kotlin` — это тот же API, записанный синтаксисом
Kotlin. Новых возможностей он не добавляет: каждое объявление вызывает метод из
`api`, и почти все они inline. Загрузчик его моду не выдаёт, поэтому мод
упаковывает модуль внутрь своего jar, рядом со стандартной библиотекой, которую
и так несёт.

```kotlin
dependencies {
    implementation("dev.ancaria.coderpack:api-kotlin:0.200.0")
}
```

```kotlin
package com.example

import dev.ancaria.coderpack.api.Context
import dev.ancaria.coderpack.api.event.Gold
import dev.ancaria.coderpack.ktx.SacredMod
import dev.ancaria.coderpack.ktx.mutate
import dev.ancaria.coderpack.ktx.on
import dev.ancaria.coderpack.ktx.spending
import dev.ancaria.coderpack.ktx.value

class DoubleGold : SacredMod() {

    override fun Context.load() {
        on<Gold> { if (!it.spending) mutate { Gold.Mutation.change(it.value * 2) } }
    }
}
```

Работают здесь три вещи. `SacredMod` — абстрактный класс из `…ktx`: он хранит
контекст и передаёт его в `load` как receiver, поэтому `on` и `log` пишутся без
префикса. `on<Gold>` принимает событие параметром типа, а не литералом класса, а
блок `events { }` собирает несколько таких вызовов вместе. А `mutate { }` —
это то, как тело слушателя принимает решение. Он существует только для
событий, которые можно решать, поэтому `on<Death> { mutate { ... } }` не
компилируется. Все свойства здесь — `val` только для чтения, `it.value` в их
числе.

`@Subscribe` работает ровно так же, как из Java, и реализовать
`dev.ancaria.coderpack.api.SacredMod` напрямую по-прежнему можно. Ничего из
этого не обязательно.

## Сборка coderpack

Нужны JDK 21 или новее и Python 3.11. Wrapper сам скачает Gradle 9.7.1.
Для `tools/hooksafe.py` нужны `pefile` и `capstone`, а для
`tests/buildcheck.js` нужен Node.js. Этот тест запускается командой
`node tests/buildcheck.js` и проверяет агент на поддельном процессе.

```
gradlew build                  три jar, в */build/libs
gradlew publishToMavenLocal    чтобы сборка мода взяла API из mavenLocal
python tools/addr.py           заново генерирует agent/src/gen/addr.js
python tools/hooksafe.py       отбраковывает места, где трамплин всё поломает
python tests/replay.py         вся Java-часть, игра не нужна
```

`addr.py` берёт адреса из репозитория
[mappings](https://github.com/ancaria-dev/mappings). Сначала он проверяет путь
из аргумента, затем `CODERPACK_MAPPINGS`, соседний каталог `../mappings` и кеш
`build/mappings/mappings.json`. Если локальной копии нет, скрипт скачивает
`mappings.json` с GitHub. Ревизию задаёт `.mappings-ref`, где сейчас записан
`master`. Для воспроизводимой сборки укажите в этом файле тег или хеш коммита.

`addr.py` создаёт таблицу из 52 RVA и четырёх глобальных адресов. Он также
переносит в `agent/src/gen/addr.js` отпечаток поддерживаемой сборки игры.
Сейчас он описывает `pureHD.exe` версии 2.0.2.118 и содержит 38
восьмибайтовых сигнатур мест хуков. Загрузчик может подключиться к
`Sacred.exe` или `Game.exe`. При несовпадении сигнатур агент предупреждает о
другой сборке, но всё равно устанавливает хуки.

Для `hooksafe.py` нужны `pefile`, `capstone` и путь к исполняемому файлу игры
или каталогу Sacred Gold. Без аргумента скрипт ищет `pureHD.exe`, `Sacred.exe`
или `Game.exe` в `D:\SteamLibrary\steamapps\common\Sacred Gold`. Если игры там
нет, проверка завершается со строкой `skipped`.

Frida ставит переход длиной не менее пяти байт и при необходимости переносит
несколько целых инструкций. `hooksafe.py` отклоняет место, если ветвление входит
в середину заменяемого участка, два участка пересекаются или перенос отделяет
инструкцию, которая выставляет флаги, от условного перехода. Остальные опасные
формы он помечает предупреждениями. Режим `--signatures` заново записывает
`agent/signatures.json` по реальному бинарнику.

`replay.py` требует сначала `gradlew jar` и хотя бы один собранный jar мода.
Скрипт ищет моды в соседнем каталоге `../mods` или в переданном каталоге,
запускает `dev.ancaria.coderpack.zygote.Main` с подготовленным потоком событий
и сравнивает ответы. Без jar модов он завершает работу с ненулевым кодом и
сообщением `no mod jars found`. Скрипт также проверяет, что трассировщик получил
события, включая неизвестное. Фактическую запись результата в память проверяет
только запущенная игра.

CI читает `version` из `gradle.properties`. При отправке в `master` новая
версия без тега `v<version>` отправляет артефакты API, его Kotlin-расширений и
zygote в Maven Central
— одним подписанным архивом через Portal API, а не деплоем в репозиторий. Тот
же запуск создаёт релиз с `api.jar`, `zygote.jar` и `agent.zip`, где уже
находится сгенерированная таблица адресов, а затем ставит тег. Именно этот
архив хост встраивает в себя, когда рядом с ним нет checkout coderpack. Для выпуска
следующего релиза достаточно повысить номер версии.

Загрузка в Central не публикует сразу: она ждёт в портале, пока человек нажмёт
Publish. Артефакт в Central нельзя удалить никогда, поэтому первые релизы стоит
посмотреть глазами.

## Что здесь лежит

| | |
|---|---|
| `agent/` | Обычный JavaScript для Frida. Он ставит хуки на инструкции x86 и отправляет события. Адреса загружаются из `gen/addr.js`. |
| `api/` | `dev.ancaria.coderpack:api`, публичный контракт для модов. У него нет runtime-зависимостей. JSR 305 подключён только как `compileOnly`. |
| `api-kotlin/` | `dev.ancaria.coderpack:api-kotlin`, тот же контракт на Kotlin. Inline-расширения над `api` в пакете `dev.ancaria.coderpack.ktx`. Загрузчик его не раздаёт: мод, которому он нужен, упаковывает его сам. |
| `zygote/` | `dev.ancaria.coderpack:zygote`, загрузчик на стороне JVM. Он читает кадры от хоста, проверяет дескрипторы, создаёт отдельный class loader для каждого jar и рассылает события. |
| `tools/`, `tests/`, `docs/` | Генератор адресов, проверка хуков, тестовые стенды и [RUNNING.md](docs/RUNNING.md) с инструкциями по запуску. |

32-битная игра и JVM работают в разных процессах. Между ними находится
Rust-хост [ancaria-dev/protocol](https://github.com/ancaria-dev/protocol). Он
внедряет агент, запускает JVM и передаёт между ними строки протокола. Внутри
zygote читающий поток направляет ответы на команды и ставит события в очередь,
а поток `sal-dispatch` последовательно вызывает слушатели модов по одному
событию.

## Лицензия

Проект распространяется по лицензии MIT. Полный текст находится в
[LICENSE](LICENSE).

---

Coderpack начинался как proof of concept для Java-модов в Sacred Gold и не
предоставляет гарантий поддержки.
