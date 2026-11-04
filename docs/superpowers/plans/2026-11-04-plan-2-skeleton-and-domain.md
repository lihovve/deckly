# План 2: каркас сборки и модуль `domain`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** шесть Gradle-модулей с границами из архитектуры и модуль `domain` с алгоритмом обучения и
логикой редактора, закрытый тестами на правила и приёмочные проверки ТЗ.

**Architecture:** `domain` — Kotlin/JVM без Android и без хранения. Всё в нём — чистые функции над
неизменяемыми data class: на вход состояние колоды, действие, номер дня и `Random`, на выход новое
состояние и итог. Остальные пять модулей в этом плане — пустые каркасы с правильными зависимостями.
Их наполняют планы 3 (`data`) и 4 (`ink`, `ui-contract`, `app`, `design-e`).

**Tech Stack:** Gradle 9.8.2, AGP 9.5.0 (встроенный Kotlin), Kotlin 2.4.21, JUnit 6.1.3.

**Spec:** [`ТЗ`](../../../ТЗ%20приложение-карточки%20с%20эпохами%20повторения%20(Onyx%20Boox%20Go%2010.3).md)
(разделы «Модель данных», «Секции», «Действия с карточкой», «Алгоритм обучения», «Создание и
редактирование», «Приёмочные проверки») и
[документ архитектуры](../specs/2026-11-03-architecture-design.md) (разделы «Модули», `domain`,
«Черновик редактора», «Тесты»). Правила алгоритма план не пересказывает: исполнитель читает их в ТЗ.
План фиксирует имена, сигнатуры, решения сверх ТЗ и тесты.

## Global Constraints

- `domain` — модуль `org.jetbrains.kotlin.jvm`. Зависимости: только стандартная библиотека Kotlin, в
  тестах — JUnit 6.1.3. Ни Android, ни корутин, ни ввода-вывода, ни часов: номер дня и `Random`
  приходят параметрами.
- Java и Kotlin target 21, как в `spike`. Android-модули: `compileSdk` 37, `minSdk` 35,
  `targetSdk` 37, приложение только `arm64-v8a`.
- Версии — в `gradle/libs.versions.toml`, новых библиотек сверх JUnit не добавлять.
- Имя метода начинается с глагола, булев — с `is`/`has`/`can` (правило владельца). Исключения:
  операторы (`get`), имена тестов JUnit (предложения-спецификации), свойства data class.
- Константы ТЗ: пороги успехов 4, 4, 4, 3; нижние границы эпох 2, 3, 4 — 1, 3, 7 дней; добор
  эпохи 1 до 40; порция 40; проверка выученных — до 10 карточек, пролежавших не меньше 30 дней, при
  нечётном `pass4`; `tail` до 5; `m = min(5, ⌈N/4⌉)`, при `N = 1` правило не действует; спуск
  даёт 3 успеха, неудачная проверка — 2; расписание по модулю 5, 3, 2; день начинается в 4:00;
  отрицательная разница дней — ноль; цепочка пустых проходов — не больше 100 итераций, потом
  `IllegalStateException`.
- Коммиты на русском, тесты — до реализации и отдельным шагом проверки, что они падают.
- После каждой задачи: `./gradlew :domain:test` и `./gradlew build` зелёные.

## Решения сверх ТЗ (мои, проверить при ревью)

1. **`cursor` из ТЗ разделён на два поля.** `passCursor` — позиция в проходе активной эпохи,
   `browseCursor` — позиция при листании открытой неактивной секции. В ТЗ поле одно, но ТЗ же
   требует, чтобы позиция прохода сохранялась, пока пользователь смотрит другую секцию.
2. **`passStarted` у колоды:** начат ли проход активной эпохи. Он не начат у новой колоды и после окон
   «нужно создать карточку» и «На сегодня всё». Открытие активной эпохи тогда запускает старт заново,
   как требует ТЗ.
3. **`excludedFromPass` у карточки:** карточка попала в эпоху не на старте её прохода, поэтому в
   текущем проходе этой эпохи не участвует. Флаг сбрасывается на старте прохода этой эпохи. Нужен
   для одного случая: вставной проход эпохи 1 выпускает карточку в эпоху 2, проход которой прерван.
   Без флага карточка показалась бы в прерванном проходе, а приёмочная проверка 3 это запрещает.
4. **Штрихи — не поле `Card`.** Алгоритму они не нужны, а хранятся отдельно, файлами. Их держит
   `CardContent`, и движок обучения работает без них.
5. **Проход непустой**, если к его концу в нём осталась хоть одна карточка. Если все удалены, в
   `lastPassDay` ничего не пишется.
6. **Отмена в редакторе** хранит последние 100 шагов (ТЗ требует не меньше 20). Отмена шага на другой
   стороне переключает видимую сторону на неё, чтобы изменение было видно.
7. **Ластик** удаляет штрих, если любой его отрезок проходит ближе радиуса к любому отрезку пути
   ластика. Расстояние меряется между отрезками, а не между точками: прототип по точкам пропускал
   быстрые штрихи с редкими точками. Радиус в единицах карточки передаёт вызывающий.
8. **Порты репозиториев** в этот план не входят. Их форма зависит от хранения, поэтому план 3
   добавляет их в `domain` первой задачей. Здесь есть только `DeckChanges`: что записать одним
   атомарным вызовом.

## Review Focus

1. Карточку удалили или вернули в пул новых посреди прохода: до курсора, на курсоре или в прерванном
   проходе. Остальные идут в прежнем порядке, следующей показывается та же карточка, что и была бы.
   Если удалили последнюю непоказанную, проход завершается (тесты задач 5 и 6).
2. Дату перевели назад, и `today` меньше `sectionDay` или `lastPassDay`. Правило ночи эпохи 1 не
   срабатывает раньше времени, выученные не становятся готовыми к проверке, ничего не падает
   (тесты задач 3 и 4).
3. В `tail` лежат карточки, которых в эпохе уже нет, или в проходе 1–3 карточки. Перемешивание
   соблюдает правило и не зацикливается (тест задачи 4).
4. Вставной проход, эпоха 1 которого опустела после выпуска на старте. И прерванный проход, все
   оставшиеся карточки которого удалили во время вставки. В первом случае следующая порция идёт
   сразу, во втором прерванный проход завершается при возврате (тесты задачи 6).
5. Выпуск в эпоху, проход которой прерван. Новая карточка не показывается в прерванном проходе
   (тест задачи 6).

---

### Task 1: шесть модулей и проверка их связей

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `domain/build.gradle.kts`, `data/build.gradle.kts`, `ink/build.gradle.kts`,
  `ui-contract/build.gradle.kts`, `design-e/build.gradle.kts`, `app/build.gradle.kts`,
  `app/src/main/AndroidManifest.xml`, `domain/src/test/kotlin/app/deckly/domain/BuildSmokeTest.kt`

**Interfaces:**
- Produces:
  - модули `:domain` (`app.deckly.domain`), `:data` (`app.deckly.data`), `:ink` (`app.deckly.ink`),
    `:ui-contract` (`app.deckly.ui.contract`), `:design-e` (`app.deckly.design.e`), `:app`
    (`app.deckly`, `applicationId` `app.deckly`); `:spike` остаётся;
  - задача `verifyModuleGraph`, подключённая к `check`.

- [ ] **Step 1: Каталог.** `junit = "6.1.3"`; библиотеки `junit-bom`, `junit-jupiter`,
  `junit-platform-launcher` (`org.junit.platform:junit-platform-launcher`, без версии, из BOM);
  библиотека `compose-runtime` (`androidx.compose.runtime:runtime`, из Compose BOM).

- [ ] **Step 2: Модули.**
  - `domain`: плагин `kotlin.jvm`, target 21 (`java` и `kotlin.compilerOptions.jvmTarget`).
    `testImplementation(platform(junit-bom))`, `junit-jupiter`,
    `testRuntimeOnly(junit-platform-launcher)`, `tasks.test { useJUnitPlatform() }`.
  - `data`, `ink`: `android.library`, `implementation(project(":domain"))`.
  - `ui-contract`: `android.library` и `kotlin.compose`, `api(platform(compose-bom))` и
    `api(compose-runtime)`.
  - `design-e`: `android.library` и `kotlin.compose`, `implementation(project(":ui-contract"))`.
  - `app`: `android.application`, `abiFilters += "arm64-v8a"`, зависимости на все пять модулей.
    В манифесте только `<application android:label="Deckly" />`.

- [ ] **Step 3: `verifyModuleGraph` в корневом `build.gradle.kts`.** Таблица разрешённых связей
  между модулями проекта:
  - `app` → `domain`, `data`, `ink`, `ui-contract`, `design-e`;
  - `data` → `domain`;
  - `ink` → `domain`;
  - `design-e` → `ui-contract`;
  - `ui-contract`, `domain` → ничего;
  - `spike` в проверке не участвует.
  Задача обходит `ProjectDependency` во всех конфигурациях каждого модуля и падает со списком
  нарушений вида `ui-contract → domain`. Задача `check` каждого модуля зависит от неё.

- [ ] **Step 4: Дымовой тест** `BuildSmokeTest.junitRuns`: `assertEquals(2, 1 + 1)`.

- [ ] **Step 5: Проверить.**
  Run: `./gradlew build && ./gradlew :domain:dependencies --configuration runtimeClasspath`
  Expected: `BUILD SUCCESSFUL`; в `runtimeClasspath` домена только `kotlin-stdlib` и её
  `annotations`. Затем временно добавить `implementation(project(":domain"))` в `ui-contract`:
  `./gradlew verifyModuleGraph` падает с `ui-contract → domain`. Правку убрать.

- [ ] **Step 6: Commit** — «Каркас: шесть модулей и проверка их связей».

### Task 2: модель, номер дня, изменения колоды

**Files:**
- Create в `domain/src/main/kotlin/app/deckly/domain/`: `model/Section.kt`, `model/EpochValues.kt`,
  `model/Card.kt`, `model/Deck.kt`, `model/DeckState.kt`, `model/Stroke.kt`, `day/DayNumber.kt`,
  `model/DeckChanges.kt`
- Test: `domain/src/test/kotlin/app/deckly/domain/Fixtures.kt`, `day/DayNumberTest.kt`,
  `model/DeckModelTest.kt`

**Interfaces:**
- Produces:
  - `enum class Section { NEW, EPOCH_1, EPOCH_2, EPOCH_3, EPOCH_4, LEARNED }`,
    `val Section.epochNumber: Int?`, `fun findEpochSection(epoch: Int): Section`;
  - `data class EpochValues<T>(val epoch1: T, val epoch2: T, val epoch3: T, val epoch4: T)` с
    `operator fun get(epoch: Int): T` и `fun replace(epoch: Int, value: T): EpochValues<T>`;
  - `data class Card(id: Long, section: Section, position: Int, successes: Int, sectionDay: Int,
    lastSuccessDay: Int?, createdAt: Long, postponed: Boolean, answerRevealed: Boolean,
    failed: Boolean, checking: Boolean, excludedFromPass: Boolean)`;
  - `data class Deck(id: Long, name: String, activeSection: Section, currentEpoch: Int,
    passStarted: Boolean, passCount: EpochValues<Int>, passCursor: Int, browseCursor: Int,
    lastPassDay: EpochValues<Int?>, waitingEpoch: Int, pausedEpoch: Int, pausedCursor: Int,
    tail: EpochValues<List<Long>>)`;
  - `data class DeckState(val deck: Deck, val cards: List<Card>)`,
    `fun DeckState.listSectionCards(section: Section): List<Card>` (по `position`);
  - `fun createDeck(id: Long, name: String): Deck`;
  - `data class StrokePoint(val x: Float, val y: Float, val pressure: Float)`,
    `data class Stroke(val width: Float, val points: List<StrokePoint>)`,
    `enum class Side { FRONT, BACK }`,
    `data class CardContent(val cardId: Long, val front: List<Stroke>, val back: List<Stroke>)`;
  - `fun computeDayNumber(dateTime: java.time.LocalDateTime): Int`;
  - `data class DeckChanges(val deck: Deck, val upsertedCards: List<Card>, val deletedCardIds: Set<Long>)`,
    `fun collectDeckChanges(before: DeckState, after: DeckState): DeckChanges`.
  - Тестовые помощники в `Fixtures.kt`: `buildCard(id, section, …)` со значениями по умолчанию,
    `buildDeck(…)`, `buildDeckState(deck, vararg cards)`. Ими пользуются все задачи ниже.

- [ ] **Step 1: Тесты.**
  - `dayStartsAtFourInTheMorning`: 2026-11-04T03:59 даёт тот же номер, что 2026-11-03T12:00;
    2026-11-04T04:00 — на один больше.
  - `newDeckStartsAtPoolWithFirstEpochNotStarted`: `createDeck(1, "Слова")` — `activeSection = NEW`,
    `currentEpoch = 1`, `passStarted = false`, счётчики 0, `lastPassDay` пустые, `waitingEpoch = 0`,
    `pausedEpoch = 0`, `tail` пустые.
  - `epochValuesReplaceOnlyOneEpoch`: `replace(3, 7)` меняет `[3]` и не трогает остальные.
  - `changesListChangedNewAndDeletedCards`: из двух состояний `collectDeckChanges` возвращает
    изменённую и новую карточки в `upsertedCards`, исчезнувшую — в `deletedCardIds`, а неизменённую
    не возвращает.

- [ ] **Step 2: Убедиться, что падают.** Run: `./gradlew :domain:test` → компиляция падает, типов нет.

- [ ] **Step 3: Реализация.** Номер дня: `dateTime.minusHours(4).toLocalDate().toEpochDay().toInt()`.

- [ ] **Step 4: Run:** `./gradlew :domain:test` → `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit** — «domain: модель колоды и карточки, номер дня».

### Task 3: расписание и созревание эпох

**Files:**
- Create: `domain/src/main/kotlin/app/deckly/domain/review/Schedule.kt`
- Test: `domain/src/test/kotlin/app/deckly/domain/review/ScheduleTest.kt`

**Interfaces:**
- Consumes: модель задачи 2.
- Produces:
  - `fun chooseNextEpoch(finishedEpoch: Int, passCountAfter: Int, waitingEpoch: Int): Int` — правило
    из «Расписания эпох». При `waitingEpoch != 0` возвращает 1: решение о созревании принимает движок;
  - `fun isEpochEmpty(state: DeckState, epoch: Int, today: Int): Boolean` — для эпохи 4 при нечётном
    `pass4` учитывает выученные, готовые к проверке;
  - `fun isEpochMature(state: DeckState, epoch: Int, today: Int): Boolean` — эпоха 1 всегда созрела;
    пустая созрела; `lastPassDay == null` — созрела; иначе `max(0, today - lastPassDay) >= граница`;
  - `fun isReadyForCheck(card: Card, today: Int): Boolean` — `LEARNED` и `max(0, today - sectionDay) >= 30`.

- [ ] **Step 1: Тесты.**
  - `scheduleMatchesReferenceSequence` (проверка 1): от нулевых счётчиков 39 раз записать эпоху,
    увеличить её счётчик и вызвать `chooseNextEpoch`. Результат равен
    `1 1 1 1 1 2 1 1 1 1 1 2 1 1 1 1 1 2 3 1 1 1 1 1 2 1 1 1 1 1 2 1 1 1 1 1 2 3 4`.
  - `waitingEpochGivesTurnToFirstEpoch`: `chooseNextEpoch(1, 5, waitingEpoch = 2) == 1`.
  - `secondEpochMaturesNextDay` (проверка 13): `lastPassDay[2] = 10`, в эпохе карточка — при
    `today = 10` не созрела, при 11 созрела. Так же эпоха 3 (12 нет, 13 да) и эпоха 4 (16 нет, 17 да).
  - `emptyEpochDoesNotWait`: пустая эпоха 2 с `lastPassDay[2] = today` созрела.
  - `fourthEpochWithReadyLearnedIsNotEmpty` (раздел «Нижняя граница интервала»): эпоха 4 пуста,
    `pass4 = 1`, в выученных карточка с `sectionDay = today - 30` — не пустая и не созрела при
    `lastPassDay[4] = today`. При `pass4 = 2` — пустая.
  - `dateMovedBackKeepsEpochWaiting` (проверка 19): `lastPassDay[3] = 20`, `today = 15` — не созрела,
    исключений нет.
  - `learnedCardReadyAfterThirtyDays`: 29 дней — нет, 30 — да, `today < sectionDay` — нет.

- [ ] **Step 2: Run:** `./gradlew :domain:test` → новые тесты не компилируются.
- [ ] **Step 3: Реализация** по сигнатурам и правилам ТЗ.
- [ ] **Step 4: Run:** `./gradlew :domain:test` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** — «domain: расписание эпох и нижние границы».

### Task 4: старт прохода

**Files:**
- Create: `domain/src/main/kotlin/app/deckly/domain/review/PassStart.kt`
- Test: `domain/src/test/kotlin/app/deckly/domain/review/PassStartTest.kt`

**Interfaces:**
- Consumes: задачи 2 и 3.
- Produces:
  - `fun startPass(state: DeckState, epoch: Int, today: Int, random: Random): DeckState` — четыре шага
    старта из ТЗ по порядку, плюс сброс `postponed`, `answerRevealed`, `failed` и
    `excludedFromPass` у карточек эпохи. Поля прохода в `Deck` не трогает: их ставит движок;
  - `fun listPassCards(state: DeckState, epoch: Int): List<Card>` — карточки эпохи без
    `excludedFromPass`, по `position`;
  - `fun shuffleWithOffset(cardIds: List<Long>, previousTail: List<Long>, random: Random): List<Long>`.
- Правила переезда (`moveCard` внутри файла):
  - карточка, уехавшая в эпоху, получает `excludedFromPass = true`;
  - `sectionDay = today`, `successes = 0`;
  - `position` — в конец секции назначения;
  - флаги прохода сброшены.

- [ ] **Step 1: Тесты.**
  - `firstEpochCardLeavesOnlyAfterNight` (проверка 2): `successes = 4`, `sectionDay = 5`.
    При `lastSuccessDay = 5` после `startPass(…, 1, today = 9, …)` она в `EPOCH_1`. При
    `lastSuccessDay = 6` — в `EPOCH_2` с `successes = 0`, `sectionDay = 9`, `excludedFromPass = true`.
  - `cardsReleasedByThreshold`: эпоха 2 с 4 успехами уходит в `EPOCH_3`, эпоха 3 с 4 — в `EPOCH_4`, с
    3 — остаётся. Эпоха 4 с 3 уходит в конец `LEARNED`: её `position` больше всех в стеке.
  - `nightRuleHoldsWhenDateMovedBack` (Review Focus 2): `sectionDay = 10`, `lastSuccessDay = 8`,
    `successes = 4` — карточка остаётся в эпохе 1.
  - `firstEpochRefillsUpToForty` (проверка 4): в эпохе 35, в пуле 10 — в эпохе 40, взяты пять первых
    по `position`. В пуле 10 при пустой эпохе — в эпохе 10. В эпохе 41 — добора нет.
  - `learnedCheckOnOddFourthPass` (проверка 18): `pass4 = 1`, в стеке 12 готовых и 1 свежая (29
    дней). В `EPOCH_4` приходят 10 первых готовых с `checking = true`, свежая остаётся. При
    `pass4 = 2` не приходит никто.
  - `passFlagsResetOnStart` (проверка 12): `postponed`, `answerRevealed`, `failed`,
    `excludedFromPass` у карточек эпохи после старта `false`. У карточек других эпох не тронуты.
  - `shuffleKeepsLastShownOutOfFront` (проверка 17): для `N` от 2 до 12 и 200 сидов
    `Random(seed)`, при `previousTail` из 5 последних карточек прошлого прохода. Ни одна из последних
    `m = min(5, ceil(N/4))` записей `tail` не стоит среди первых `m` результата. Результат — перестановка
    входа.
  - `twoCardsLastShownNeverFirst`: `N = 2`, `tail = [a, b]` — первая никогда не `b` (200 сидов).
  - `shuffleIgnoresCardsGoneFromEpoch` (Review Focus 3): в `tail` id, которых нет во входе, —
    правило для оставшихся соблюдено. `N = 1` возвращает вход.
  - `positionsAreContiguousAfterStart`: позиции карточек эпохи после старта — `0 until N` в
    порядке `shuffleWithOffset`.

- [ ] **Step 2: Run:** `./gradlew :domain:test` → новые тесты не компилируются.
- [ ] **Step 3: Реализация.** Перемешивание (тестам годится любой корректный алгоритм, этот —
  простой):
  1. `random`-перестановка входа.
  2. `forbidden` — последние `m` записей `tail`, которые есть во входе.
  3. Каждую `forbidden` на месте `i < m` поменять с первой карточкой на месте `j >= m`, которой нет
     в `forbidden`. Она есть всегда: `m <= N/2`.
- [ ] **Step 4: Run:** `./gradlew :domain:test` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** — «domain: старт прохода».

### Task 5: действия с карточкой и их доступность

**Files:**
- Create: `domain/src/main/kotlin/app/deckly/domain/review/PassActions.kt`,
  `domain/src/main/kotlin/app/deckly/domain/review/CardActions.kt`
- Test: `domain/src/test/kotlin/app/deckly/domain/review/PassActionsTest.kt`, `CardActionsTest.kt`

**Interfaces:**
- Consumes: задачи 2 и 4 (`listPassCards`).
- Produces (все функции — над `DeckState`, без старта и конца прохода; это работа движка):
  - `fun findCurrentCard(state: DeckState): Card?`:
    - открыта активная эпоха и проход начат — карточка `passCursor` прохода;
    - открыта другая секция — карточка `browseCursor` секции;
    - карточек нет — `null`;
  - `fun revealAnswer(state: DeckState): DeckState` — `answerRevealed = true` текущей карточке
    активного прохода. Вне начатого прохода открытой активной эпохи возвращает состояние без
    изменений: просмотр неактивной секции флаг не меняет;
  - `fun rateKnew(state: DeckState, today: Int): DeckState` — `successes + 1`, `lastSuccessDay = today`,
    `passCursor + 1`;
  - `fun rateDidNotKnow(state: DeckState): DeckState` — эпоха 1: `successes - 1`, не ниже 0; эпохи
    2–4: `failed = true`. Затем `passCursor + 1`;
  - `fun postponeCard(state: DeckState): DeckState` — `position` в конец прохода, `postponed = true`,
    курсор на месте;
  - `fun deleteCard(state: DeckState, cardId: Long): DeckState`,
    `fun returnToNew(state: DeckState, cardId: Long, today: Int): DeckState` — после них поправлены
    `passCursor`, `pausedCursor` и `browseCursor`;
  - `fun browseSection(state: DeckState, step: Int): DeckState` — `browseCursor` по кругу;
  - `enum class CardAction { DELETE, RETURN_TO_NEW, EDIT, CREATE, KNEW, DID_NOT_KNOW, POSTPONE, BROWSE, FLIP }`,
    `enum class UnavailableReason { FRONT_VISIBLE, ANSWER_REVEALED, ALREADY_POSTPONED, LAST_IN_PASS }`,
    `sealed interface Availability { data object Available; data class Unavailable(val reason: UnavailableReason) }`,
    `fun computeCardActions(state: DeckState, visibleSide: Side): Map<CardAction, Availability>` —
    в карте только действия, которые секция предлагает по таблице «Действия с карточкой».
- Предусловия (`require`): оценка и перенос — только в начатом проходе открытой активной эпохи;
  оценка — при `answerRevealed`; `returnToNew` — только из эпох.

- [ ] **Step 1: Тесты `PassActionsTest`.**
  - `knewAddsSuccessAndMovesOn`: `successes` 1 → 2, `lastSuccessDay = today`, `passCursor` 0 → 1.
  - `didNotKnowKeepsZeroInFirstEpoch` (проверка 5): 0 → 0. 2 → 1. В эпохе 3 — `failed = true`,
    успехи те же.
  - `postponedCardGoesToEndOfPass` (проверка 11): проход `[a, b, c]`, перенос `a`. Проход
    `[b, c, a]`, текущая — `b`, `a.postponed`, успехи `a` прежние.
  - `returnToNewPutsCardAtEndOfPoolWithZero` (проверка 8): уходит в конец пула, `successes = 0`,
    `answerRevealed = false`, `checking = false`, `sectionDay = today`.
  - `removalBeforeCursorKeepsNextCard` (Review Focus 1): проход `[a, b, c, d]`, `passCursor = 2`
    (текущая `c`). После `deleteCard(a)` текущая по-прежнему `c`. После `returnToNew(c)` текущая
    `d`, порядок остальных прежний.
  - `removalInPausedPassKeepsItsPosition` (Review Focus 1): прерванный проход эпохи 4
    `[a, b, c, d]`, `pausedCursor = 2`. После `deleteCard(b)` — `pausedCursor = 1`, следующей
    останется `c`.
  - `browsingWrapsAround`: в секции 3 карточки, `browseCursor = 2`, шаг +1 даёт 0, шаг −1 из 0 даёт 2.
  - `revealOutsideActivePassChangesNothing`: открыта неактивная эпоха — `revealAnswer` возвращает то
    же состояние.

- [ ] **Step 2: Тесты `CardActionsTest`** (проверка 12 и таблица действий).
  - `postponeAvailableBeforeReveal`: в начатом проходе непоследняя неотложенная карточка, ответ не
    открыт — `POSTPONE` доступен.
  - `revealBlocksPostponeEvenOnFront`: после `revealAnswer` и при `visibleSide = FRONT` —
    `POSTPONE` недоступен с `ANSWER_REVEALED`, `KNEW` и `DID_NOT_KNOW` недоступны с `FRONT_VISIBLE`.
    При `BACK` оценки доступны.
  - `postponeUnavailableForLastAndPostponed`: последняя в проходе — `LAST_IN_PASS`; уже отложенная —
    `ALREADY_POSTPONED`.
  - `sectionsOfferTheirActions`:
    - активная эпоха в начатом проходе — `DELETE`, `RETURN_TO_NEW`, `KNEW`, `DID_NOT_KNOW`,
      `POSTPONE`, `FLIP`, без `BROWSE`;
    - пул новых с карточкой — `DELETE`, `EDIT`, `CREATE`, `BROWSE`, `FLIP`;
    - пустой пул — только `CREATE`;
    - неактивная эпоха — `DELETE`, `RETURN_TO_NEW`, `BROWSE`, `FLIP`;
    - выученные — `DELETE`, `BROWSE`, `FLIP`;
    - пустая неактивная секция — пустая карта.

- [ ] **Step 3: Run:** `./gradlew :domain:test` → новые тесты не компилируются.
- [ ] **Step 4: Реализация.**
- [ ] **Step 5: Run:** `./gradlew :domain:test` → `BUILD SUCCESSFUL`.
- [ ] **Step 6: Commit** — «domain: действия с карточкой и их доступность».

### Task 6: движок обучения

**Files:**
- Create: `domain/src/main/kotlin/app/deckly/domain/review/PassEnd.kt`,
  `domain/src/main/kotlin/app/deckly/domain/review/ReviewEngine.kt`
- Test: `domain/src/test/kotlin/app/deckly/domain/review/ReviewEngineTest.kt`,
  `domain/src/test/kotlin/app/deckly/domain/review/ReviewSimulationTest.kt`

**Interfaces:**
- Consumes: задачи 2–5.
- Produces:
  - `fun finishPass(state: DeckState, epoch: Int, today: Int): DeckState`:
    - шаги 1–4 завершения из ТЗ: `tail`, спуск, итог проверки выученных, `lastPassDay`;
    - у вставного прохода (`pausedEpoch != 0`, `epoch == 1`) — только `tail`;
    - шаг 5 (счётчик проходов и выбор следующей эпохи) и возврат к прерванному проходу делает движок;
  - `sealed interface ReviewAction`:
    - `data class OpenSection(val section: Section)`;
    - `data class Browse(val step: Int)`;
    - `data object RevealAnswer`, `Knew`, `DidNotKnow`, `Postpone`;
    - `data class DeleteCard(val cardId: Long)`, `data class ReturnToNew(val cardId: Long)`;
  - `enum class TransitionKind { REGULAR, INSERTED, RESUMED }`;
  - `sealed interface ReviewOutcome`:
    - `data class ShowCard(val cardId: Long)`;
    - `data object EmptySection`;
    - `data class Transition(val epoch: Int, val remaining: Int, val kind: TransitionKind)`;
    - `data object NeedNewCard`;
    - `data object DoneForToday`;
  - `data class ReviewResult(val state: DeckState, val outcome: ReviewOutcome)`;
  - `object ReviewEngine`:
    - `fun applyAction(state: DeckState, action: ReviewAction, today: Int, random: Random): ReviewResult`;
    - `fun computeCurrentOutcome(state: DeckState): ReviewOutcome` — что показать после перезапуска.

- [ ] **Step 1: Тесты `ReviewEngineTest`.** Каждый строит колоду через `Fixtures` и действует
  только через `applyAction`.
  - `emptySecondEpochPassesSilently` (проверка 6): `pass1 = 4`, эпоха 1 непустая, эпоха 2 пустая. Все
    карточки эпохи 1 оценены, последняя — `Knew`. Тогда `pass2` +1, итог — `Transition(1, …, REGULAR)`,
    перехода к эпохе 2 нет.
  - `emptyDeckAsksForNewCard` (проверка 7): пустая колода, `OpenSection(EPOCH_1)` → `NeedNewCard`,
    `activeSection = NEW`, `passStarted = false`. То же, если есть только выученные моложе 30 дней.
  - `onlyFourthEpochReachedWithinThirtyPasses` (проверка 7): карточки только в эпохе 4, она созрела.
    `OpenSection(EPOCH_1)` → `Transition(4, …)`, `pass1` вырос не больше чем на 30. Если не созрела →
    `DoneForToday`.
  - `immatureEpochWaitsAndFirstEpochPlays` (проверка 13):
    - эпоха 2 непустая, `lastPassDay[2] = today`, расписание её выбирает;
    - эпоха 1 непустая — `waitingEpoch = 2`, ход у эпохи 1;
    - эпоха 1 и пул пусты — `DoneForToday`, `currentEpoch = 2`, открыт пул;
    - на следующий день `OpenSection(EPOCH_2)` → `Transition(2, …, REGULAR)`, `waitingEpoch = 0`.
  - `waitingEpochCheckedAfterEachFirstEpochPass`: при `waitingEpoch = 3` после прохода эпохи 1 правило
    `pass1 % 5` не действует. Если эпоха 3 созрела, ход у неё.
  - `demotedThirdEpochCardReturnsAfterOneKnew` (проверка 15): «не знал» в проходе эпохи 3 — в
    конце прохода карточка в `EPOCH_2` с 3 успехами. «Знал» в ближайшем проходе эпохи 2 — на старте
    следующего прохода эпохи 2 она в `EPOCH_3`.
  - `demotedSecondEpochCardWaitsForNight` (проверка 15): после спуска в эпоху 1 с 3 успехами «знал» в
    день спуска её не выпускает, «знал» на следующий день выпускает на ближайшем старте. Спуск в
    эпоху 1, где 40 карточек, даёт 41, и добор не срабатывает.
  - `longFourthEpochPassGoesInPortions` (проверка 16): в эпохе 4 100 карточек, эпоха 1 непустая.
    - Итоги: 40 `ShowCard` → `Transition(1, _, INSERTED)` → проход эпохи 1 → `Transition(4, 60, RESUMED)`
      → 40 → `INSERTED` → `Transition(4, 20, RESUMED)` → 20.
    - `pass1` не изменился, `pass4` +1, `lastPassDay[4]` записан только после последней порции.
    - Карточка, отложенная в первой порции, показана в последней.
  - `learnedCheckOutcome` (проверка 18): `checking` с «знал» в конце прохода — в конце `LEARNED` с
    `sectionDay = today`, `successes = 0`. С «не знал» — в `EPOCH_4` с 2 успехами, `checking = false`,
    не спущена.
  - `onlyLearnedReachCheckWithinSeventyEightPasses` (проверка 18): в колоде только выученные, часть
    готова. `OpenSection(EPOCH_1)` → `Transition(4, …)`, `pass1` вырос не больше чем на 78.
  - `tailRememberedAtPassEnd`: после прохода из 7 карточек `tail[e]` — последние 5 в порядке показа.
  - `deletingLastUnshownCardFinishesPass` (Review Focus 1): на последней карточке прохода
    `DeleteCard` даёт итог следующего прохода, а не `ShowCard`.
  - `insertSkippedWhenFirstEpochEmptiesOnStart` (Review Focus 4): в эпохе 1 одна карточка, она
    выпускается на старте вставки, пул пуст. После 40-й оценки сразу `ShowCard` 41-й карточки, а
    `pausedEpoch = 0`.
  - `pausedPassWithNothingLeftFinishesOnResume` (Review Focus 4): во время вставки удалены все
    оставшиеся карточки прерванного прохода эпохи 3. В конце вставки прерванный проход завершается:
    `pass3` +1, дальше итог расписания.
  - `promotedIntoPausedEpochWaitsForItsPass` (Review Focus 5): эпоха 2 прервана, вставка эпохи 1
    выпускает карточку в эпоху 2. После возврата её нет среди оставшихся карточек прохода, а на
    старте следующего прохода эпохи 2 она есть.
  - `openingInactiveSectionDoesNotMovePass`: во время прохода эпохи 1 открыть `EPOCH_3`, листать,
    вернуться в `EPOCH_1` — `ShowCard` той же карточки, `passCursor` прежний.
  - `deletingFirstCardOfFreshPassDoesNotInsertFirstEpochPass`: удаление первой карточки свежего
    прохода эпохи 2–4 и удаление сразу после `RESUMED` дают `ShowCard`, а не вставку.
  - `restartShowsCurrentCard`: `computeCurrentOutcome` для начатого прохода — `ShowCard` по
    `passCursor`; для открытой пустой секции — `EmptySection`.

- [ ] **Step 2: Тест `ReviewSimulationTest.cardReachesLearnedNotBeforeDay38`** (проверка 14).
  - В колоде одна карточка в пуле.
  - Дни 0…60. Каждый день:
    1. `OpenSection(EPOCH_1)` или `OpenSection` текущей эпохи;
    2. пока итог `Transition` или `ShowCard` — `RevealAnswer`, затем `Knew`;
    3. на `DoneForToday` или `NeedNewCard` — следующий день.
  - Защита: не больше 1000 действий в день.
  - Проверки:
    - карточка не покидает эпоху 1 в день попадания;
    - в `LEARNED` она оказывается в день 38.

- [ ] **Step 3: Run:** `./gradlew :domain:test` → новые тесты не компилируются.

- [ ] **Step 4: Реализация `ReviewEngine`.** Порядок решений не определяется ни сигнатурами, ни
  тестами, поэтому зафиксирован здесь:
  - **Цепочка старта** (`OpenSection` активной эпохи при `passStarted = false` и после каждого
    конца прохода), до 100 итераций, потом `IllegalStateException`:
    1. Пул, эпохи 1–4 и готовые выученные пусты → `passStarted = false`, `activeSection = NEW`,
       итог `NeedNewCard`.
    2. `waitingEpoch != 0`: если она созрела → `currentEpoch = waitingEpoch`, `waitingEpoch = 0`, к
       шагу 3. Если нет и эпоха 1 с пулом пусты → `currentEpoch = waitingEpoch`,
       `passStarted = false`, `activeSection = NEW`, итог `DoneForToday`. Иначе `currentEpoch = 1`.
    3. `currentEpoch` в 2..4 и не созрела → `waitingEpoch = currentEpoch`, `currentEpoch = 1`, к шагу 2.
    4. `startPass`. Проход непустой → `passStarted = true`, `passCursor = 0`,
       `activeSection` = секция эпохи, итог `Transition(epoch, size, REGULAR)`. Пустой →
       `finishPass`, `passCount + 1`, `currentEpoch = chooseNextEpoch(…)`, к шагу 1.
  - **После оценки или удаления** в активном проходе:
    - проход исчерпан → его конец;
    - иначе, только после `Knew` и `DidNotKnow` (удаление ничего не оценивает): эпоха 2–4 без
      прерывания, `passCursor % 40 == 0` и в эпохе 1 или в пуле есть карточки →
      `pausedEpoch`, `pausedCursor = passCursor`, `currentEpoch = 1`, `startPass(1)`.
      Вставка непустая → `Transition(1, size, INSERTED)`. Пустая → её `finishPass` и сразу возврат;
    - иначе `ShowCard`.
  - **Конец прохода:**
    - вставка (`pausedEpoch != 0`) → `finishPass` с одним `tail`, затем возврат:
      `currentEpoch = pausedEpoch`, `passCursor = pausedCursor`, `pausedEpoch = 0`. Остались
      карточки → `Transition(epoch, remaining, RESUMED)`, иначе конец этого прохода;
    - обычный → `finishPass`, `passCount + 1`, `chooseNextEpoch`, цепочка старта.

- [ ] **Step 5: Run:** `./gradlew :domain:test` → `BUILD SUCCESSFUL`.
- [ ] **Step 6: Commit** — «domain: движок обучения».

### Task 7: редактор и черновик

**Files:**
- Create: `domain/src/main/kotlin/app/deckly/domain/editor/EditorSession.kt`,
  `domain/src/main/kotlin/app/deckly/domain/editor/Eraser.kt`,
  `domain/src/main/kotlin/app/deckly/domain/editor/Draft.kt`
- Test: `domain/src/test/kotlin/app/deckly/domain/editor/EditorSessionTest.kt`, `EraserTest.kt`,
  `DraftTest.kt`

**Interfaces:**
- Consumes: `Stroke`, `StrokePoint`, `Side`, `CardContent`, `Card`, `DeckState` (задача 2).
  Удаление карточки из редактора — действие `DeleteCard` движка (задача 6), его вызывает `app`.
- Produces:
  - `data class Draft(val cardId: Long?, val front: List<Stroke>, val back: List<Stroke>,
    val visibleSide: Side, val returnSection: Section)` — то, что хранится. `cardId == null` —
    создание новой карточки;
  - `data class EditorSession(val draft: Draft, val history: List<EditorStep>)`, история не хранится.
    `EditorStep` — sealed-тип шага отмены: сторона и добавленный штрих или удалённые штрихи с их
    индексами. `const val UNDO_DEPTH = 100`;
  - `fun startNewCard(returnSection: Section): EditorSession`,
    `fun startEditing(card: Card, content: CardContent, returnSection: Section): EditorSession` —
    `require(card.section == NEW)`;
  - `fun resumeDraft(draft: Draft): EditorSession` — после перезапуска, история пустая;
  - `fun EditorSession.addStroke(stroke: Stroke): EditorSession` — на `visibleSide`;
  - `fun EditorSession.eraseAlong(path: List<StrokePoint>, radius: Float): EditorSession`;
  - `fun EditorSession.undoLastStep(): EditorSession`;
  - `fun EditorSession.switchSide(side: Side): EditorSession` — шагом отмены не считается;
  - `fun isStrokeTouched(stroke: Stroke, path: List<StrokePoint>, radius: Float): Boolean`;
  - `fun saveDraft(state: DeckState, draft: Draft, newCardId: Long, today: Int, now: Long): Pair<DeckState, CardContent>`:
    - новая карточка — в конец пула: `successes = 0`, `answerRevealed = false`,
      `sectionDay = today`, `createdAt = now`, id `newCardId`;
    - правка — карточка не меняется, меняется только `CardContent`; `newCardId` не используется.

- [ ] **Step 1: Тесты.**
  - `strokeAddedAndUndone`: штрих на лицевой стороне, отмена — сторона пуста.
  - `eraseRemovesWholeTouchedStrokes`: из трёх штрихов путь задевает два — удалены оба целиком,
    третий на месте. Одна отмена возвращает оба на прежние места (порядок списка прежний).
  - `eraseMissingEverythingAddsNoStep`: путь мимо всех — история не выросла.
  - `fastStrokeWithSparsePointsIsErased`: штрих из двух точек `(0,0)` и `(100,0)`, путь ластика —
    точка `(50, 3)` при радиусе 5 — задет.
  - `tapErasesWithinRadius`: путь из одной точки на расстоянии 4 от штриха при радиусе 5 — задет,
    при 6 — нет.
  - `undoReachesTwentyFiveStepsAndCapsAtHundred`: 25 действий снимаются все. После 101 действия
    снимаются 100, 101-я отмена ничего не меняет.
  - `undoOnOtherSideShowsThatSide`: штрих на лице, переключение на оборот, отмена — штриха нет,
    `visibleSide = FRONT`.
  - `savingNewDraftAppendsCardToPool`: новая карточка в конце пула со значениями из сигнатуры, у
    `CardContent` штрихи черновика.
  - `savingEditKeepsCardPlace`: правка карточки пула с `position = 3` — `Card` прежняя целиком,
    `CardContent` новый.
  - `emptySidesAreAllowed`: черновик с двумя пустыми сторонами сохраняется.
  - `editingOnlyFromPool`: `startEditing` карточки эпохи бросает `IllegalArgumentException`.

- [ ] **Step 2: Run:** `./gradlew :domain:test` → новые тесты не компилируются.
- [ ] **Step 3: Реализация.**
  - Расстояние «отрезок — отрезок»: 0 при пересечении, иначе минимум из четырёх расстояний
    «точка — отрезок».
  - Штрих и путь из одной точки — вырожденные отрезки.
  - Шаг отмены хранит сторону и удалённые штрихи с их индексами.
- [ ] **Step 4: Run:** `./gradlew :domain:test && ./gradlew build` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** — «domain: редактор и черновик».

---

После плана 2 план 3 (`data`) начинается с портов репозиториев в `domain`.
