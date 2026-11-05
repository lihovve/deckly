# План 4: первая рабочая сборка — `ink`, `ui-contract`, `app`, `design-e`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** приложение Deckly, которое ставится на Go 10.3 Gen II и годится для ежедневной работы:
- первый запуск: доступ к файлам, первая колода;
- экран колоды с шестью секциями: обучение, просмотр, переходы, окна «нужно создать карточку» и «На
  сегодня всё»;
- меню карточки с удалением и возвратом в пул;
- редактор с пером, ластиком, отменой и черновиком.

**Не входит, это план 5:** список колод с меню (переименование, удаление, экспорт), страницы списка,
импорт из файла. В плане 4 список колод только показывает колоды, открывает их и создаёт новую.

**Architecture:**
- `ink` — холст: сырое перо Onyx и запасной Canvas за одним Composable, отрисовка штрихов своим кодом
  по формуле ручки, обновление экрана e-ink.
- `ui-contract` — состояния и действия экранов, интерфейс дизайна со слотами под холст. Ни одного
  типа `domain`.
- `app` — Application, одна activity, ViewModel, переходы между экранами через состояние, перевод
  `domain` в состояния контракта, единый писатель в хранилище.
- `design-e` — Compose Foundation по макетам варианта E.
Границы проверяет `verifyModuleGraph` (`ink → domain`, `design-e → ui-contract`, `app →` все).

**Tech Stack:** Compose BOM 2026.09.00 (Foundation, без Material), Onyx SDK (pen 1.5.8, device
1.3.6, base 1.8.9) с зависимостями прототипа, lifecycle 2.11.0, activity-compose 1.13.0, тесты —
JUnit 4 + Robolectric 4.17 + `compose-ui-test`.

**Spec:**
- ТЗ — разделы «Назначение и устройство» (правила e-ink), «Секции», «Действия с карточкой»,
  «Создание и редактирование», «Переход между эпохами и просмотр», «Техническая часть», «Onyx SDK»;
- [документ архитектуры](../specs/2026-11-03-architecture-design.md) — разделы `ink`,
  `ui-contract`, `design-e`, `app`, «Поток данных»;
- макеты — [`design/README.md`](../../../design/README.md), `design/build.py` (точная разметка и
  подписи каждого экрана), `design/styles.css` (размеры);
- [итоги прототипа](../specs/2026-11-03-device-spike-results.md), раздел «Решения (шаг 9)»;
- заметки в конце [плана 2](2026-11-04-plan-2-skeleton-and-domain.md) и
  [плана 3](2026-11-04-plan-3-data.md);
- код прототипа `spike/` — образец жизненного цикла пера (`OnyxPenView`, `CanvasPenView`).

## Global Constraints

- Правила e-ink из ТЗ:
  - без анимаций, ripple, overscroll, теней, градиентов;
  - только чёрный, белый и две-три градации серого;
  - элемент управления не меньше 64 dp, текст не меньше 18 sp;
  - ничего не меняется на экране само.
- Корень Compose отключает индикацию нажатия (`LocalIndication` без эффекта) и overscroll. Все
  `clickable` — без `indication`.
- Только портрет, одна activity. Системные панели скрыты: экран совпадает с кадром макета 992 × 1323.
- Единица макета = 1 dp, кегль макета = 1 sp. На Go 10.3 Gen II плотность 1,875, 992 dp = 1860 px.
  Это подтверждает раскладка прототипа: лист 1830 px при полях 8 dp.
- Тексты интерфейса — только в `design-e`, по макетам. Модель передаёт перечисления и числа.
- Порядок записи из архитектуры: экран показывает новое состояние только после успешной записи. Все
  записи в хранилище идут через один `SerialWriter`, в области приложения, а не ViewModel. Ошибки
  записи не ловятся: приложение падает, транзакции оставляют базу целой.
- Тесты `ink`, `app`, `design-e`: JUnit 4 + Robolectric (`@Config(sdk = [35])`), чистые функции — без
  Robolectric. Задачи Gradle — `:ink:testDebugUnitTest`, `:app:testDebugUnitTest`,
  `:design-e:testDebugUnitTest`.
- Имена методов — глаголы (правило владельца). SQL в этом плане не пишется.
- После каждой задачи `./gradlew build` зелёный. Коммиты в `main`, без пуша.

## Решения сверх архитектуры (мои, проверить при ревью)

1. **Система координат карточки.** Ширина 1000 единиц, высота
   `1000 × 963 / 928 ≈ 1037,72`, по пропорции листа макета. `x` вправо, `y` вниз.
   - Масштаб: `scale = ширина листа в px / 1000`.
   - Толщина штриха `Stroke.width` хранится в единицах карточки.
   - При рисовании толщина точки — `2 + (width·scale + 1)·p²` px. Это формула ручки прошивки при
     её толщине `width·scale`.
2. **Толщины пера** — 3, 6 и 10 px на листе редактора (тонкая, средняя, толстая), по умолчанию
   средняя. Это проверенные владельцем 4 ноября значения ручки. Значок «образец линии» берёт
   размеры из макета.
3. **Ластик-инструмент:** перо со `setRawDrawingRenderEnabled(false)`. Прошивка не рисует линию, а
   точки штриха идут в стирание. Кнопка пера стирает всегда, при любом инструменте.
4. **Обновление экрана:**
   - полное (`repaintEveryThing(GC)`) — при смене экрана, при смене показанной карточки и в
     редакторе при смене стороны, сохранении и выходе;
   - переворот карточки при обучении и просмотре — частичное, по ТЗ.
   Правило живёт в одном месте (`RefreshPolicy`), чтобы его можно было поменять после проверки на
   планшете.
5. **Пауза пера ради интерфейса.** В редакторе изменилось что-то видимое, кроме холста (инструмент,
   толщина, доступность отмены, диалог) → перо выключается, Compose перерисовывает, через
   `PEN_RESUME_DELAY_MS = 150` перо включается снова. Перерисовка при включённом пере на экран не
   попадает.
6. **Повреждённый файл штрихов** (`StrokeFileException`) — карточка показывается с пустым листом,
   описание для доступности «Рисунок не открылся». Удалить её можно как обычно.
7. **Окно «База повреждена» / «База не найдена» и экран запроса доступа** в макетах отсутствуют.
   `design-e` делает их по образцу диалога варианта E:
   - для базы: заголовок, одна строка, кнопка «Закрыть»;
   - для доступа: «Нужен доступ к файлам» и «Открыть настройки».

   Тексты (уточнено при выдаче задачи 10):

   | Состояние | Заголовок | Строка | Кнопка |
   |---|---|---|---|
   | `NEEDS_FILE_ACCESS` | Нужен доступ к файлам | Колоды хранятся в папке Deckly, без доступа их не открыть. | Открыть настройки |
   | `DATABASE_DAMAGED` | База повреждена | Файл Deckly/deckly.db не открылся, ничего не удалено. | Закрыть |
   | `DATABASE_MISSING` | База не найдена | В папке Deckly нет файла deckly.db, рисунки карточек не тронуты. | Закрыть |
8. **Недоступные элементы** — серый `#A0A0A0`, светлее серого прототипа (замечание владельца). Цвет
   проверяется на планшете.

## Review Focus

1. Приложение убито или ушло в фон посреди редактирования, сразу после штриха. После запуска
   открывается редактор с тем же черновиком на той же стороне, последний штрих на месте (задачи 6
   и 11, проверка на планшете — задача 12).
2. Пока перо включено, меняется что-то вне холста: стала доступна отмена, открылся диалог,
   переключился инструмент. Изменение видно на экране, штрихи не пропадают (задачи 2 и 11).
3. Нет доступа ко всем файлам при запуске, в том числе его отобрали после установки. Показывается
   экран запроса, а не «база повреждена». После выдачи приложение продолжает само, без перезапуска
   (задача 4).
4. Быстрая серия действий: несколько «Знал» подряд, двойное нажатие. Каждое действие применяется к
   тому состоянию, которое видел пользователь, а не к устаревшему (задача 5).
5. Карточка, файл штрихов которой пропал или повреждён. Экран открывается, лист пустой, с карточкой
   можно работать дальше (задачи 5 и 9).

---

### Task 1: `ink` — геометрия, формула толщины, отрисовка штрихов

**Files:**
- Modify: `ink/build.gradle.kts` (Compose, Onyx SDK как в `spike`, тесты)
- Create в `ink/src/main/kotlin/app/deckly/ink/`: `CardGeometry.kt`, `StrokeRenderer.kt`,
  `CardSideImage.kt`
- Test: `ink/src/test/kotlin/app/deckly/ink/CardGeometryTest.kt`

**Interfaces:**
- Produces:
  - `object CardGeometry` с `const val WIDTH = 1000f`, `val HEIGHT = 1000f * 963f / 928f`,
    `fun computeScale(sheetWidthPx: Float): Float`, `fun toCardUnits(px: Float, scale: Float): Float`,
    `fun toPixels(units: Float, scale: Float): Float`;
  - `fun computePointWidthPx(strokeWidth: Float, pressure: Float, scale: Float): Float` — решение 1;
  - `fun drawStrokes(canvas: android.graphics.Canvas, strokes: List<Stroke>, scale: Float)`:
    отрезки `drawLine` с круглыми концами, толщина отрезка — среднее двух точек, штрих из одной
    точки — круг;
  - `@Composable fun CardSideImage(strokes: List<Stroke>, modifier: Modifier, description: String)`:
    лист для обучения и просмотра, масштаб по ширине, без анимаций.

- [ ] **Step 1: Тесты** `CardGeometryTest`:
  - `widthFollowsFountainFormula`: при `scale = 1.83`, `width = 6/1.83`: нажим 0 → 2 px, 1 → 9 px,
    0.5 → 3.75 px. Это числа замеров `PEN_FOUNTAIN_CURVE` из итогов прототипа.
  - `unitsRoundTrip`: `toPixels(toCardUnits(x))` возвращает `x`.
  - `scaleUsesCardWidth`.
- [ ] **Step 2–4:** RED → реализация → `./gradlew :ink:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 5: Commit** — «ink: геометрия карточки и отрисовка штрихов».

### Task 2: `ink` — холст редактора и обновление экрана

**Files:**
- Create в `ink/src/main/kotlin/app/deckly/ink/`: `OnyxSdk.kt`, `DrawingSheet.kt`,
  `OnyxDrawingView.kt`, `CanvasDrawingView.kt`, `ScreenRefresh.kt`
- Test: `ink/src/test/kotlin/app/deckly/ink/CanvasDrawingViewTest.kt`

**Interfaces:**
- Produces:
  - `object OnyxSdk`:
    - `fun initialize(application: Application): Boolean` — `HiddenApiBypass`, `RxManager`,
      `ResManager`, как в прототипе. `false` при исключении или на устройстве не Onyx;
    - `val isPenAvailable: Boolean`;
  - `enum class DrawingTool { PEN, ERASER }`;
  - `@Composable fun DrawingSheet(...)` с параметрами:
    - `strokes: List<Stroke>`;
    - `tool: DrawingTool`;
    - `penWidthPx: Float` — толщина пера в px листа. В `onStroke` толщина штриха уже в единицах карточки;
    - `isPenEnabled: Boolean`;
    - `uiRevision: Int`;
    - `onStroke: (Stroke) -> Unit`;
    - `onErase: (List<StrokePoint>, radius: Float) -> Unit`;
    - `modifier: Modifier`.
    Это обёртка `AndroidView` над `OnyxDrawingView` при `OnyxSdk.isPenAvailable`, иначе над
    `CanvasDrawingView`. Координаты в колбэках — в единицах карточки, радиус ластика — 16 px в
    единицах карточки;
  - `object ScreenRefresh`:
    - `fun refreshFullScreen()` — `EpdController.repaintEveryThing(UpdateMode.GC)`;
    - без SDK ничего не делает.
- `OnyxDrawingView` — `SurfaceView`, `TouchHelper.create(view, callback)`, по итогам прототипа:
  - стиль `STROKE_STYLE_FOUNTAIN` и толщина ставятся после `openRawDrawing()` и после каждого
    `setRawDrawingEnabled(true)`;
  - трек ластика выключен (`setEraserRawDrawingEnabled(false, …)`), область —
    `setLimitRect(getLocalVisibleRect)`;
  - инструмент `ERASER` — `setRawDrawingRenderEnabled(false)`, точки штриха идут в `onErase`;
  - фиксация стирания: перо выключить → перерисовать поверхность → `refreshScreenRegion(…,
    ANIMATION_MONO)` по месту → включить через `PEN_RESUME_DELAY_MS = 150`. Без GC и без шага 200 мс;
  - `isPenEnabled = false` → перо выключено и ждёт;
  - смена `uiRevision` при включённом пере → выключить, дождаться кадра, включить через 150 мс
    (решение 5);
  - палец не рисует.
- `CanvasDrawingView` — `View` по образцу `CanvasPenView` прототипа:
  - фон — `drawRect` по своему размеру, не `drawColor`;
  - нажим у каждой точки свой (`getHistoricalPressure`);
  - текущий штрих рисуется `drawStrokes`;
  - ластик — `TOOL_TYPE_ERASER`, кнопка стилуса или инструмент `ERASER`.

- [ ] **Step 1: Тесты** `CanvasDrawingViewTest`, Robolectric, через `MotionEvent.obtain` с
  `TOOL_TYPE_STYLUS`:
  - `stylusStrokeIsReportedInCardUnits`: штрих на листе шириной 1830 px пришёл в `onStroke`,
    точки в единицах карточки, нажим 0..1 по точкам;
  - `fingerDoesNotDraw`;
  - `eraserToolReportsPath`: при `tool = ERASER` тот же жест приходит в `onErase`, а не в `onStroke`;
  - `stylusButtonErasesWithPenTool`.
- [ ] **Step 2–4:** RED → реализация → `./gradlew :ink:testDebugUnitTest && ./gradlew build`.
  `OnyxDrawingView` на JVM не проверяется, его проверяет задача 12.
- [ ] **Step 5: Commit** — «ink: холст редактора и обновление экрана».

### Task 3: `ui-contract` — экраны как абстракции

**Files:**
- Modify: `ui-contract/build.gradle.kts`
- Create в `ui-contract/src/main/kotlin/app/deckly/ui/contract/`: `Common.kt`, `StartupContract.kt`,
  `DeckContract.kt`, `EditorContract.kt`, `DeckListContract.kt`, `Design.kt`

**Interfaces** (весь контракт; тесты — у потребителей в задачах 5–10):
- `Common.kt`:
  - `enum class SectionTab { NEW, EPOCH_1, EPOCH_2, EPOCH_3, EPOCH_4, LEARNED }`,
    `enum class CardSideShown { FRONT, BACK }`;
  - `data class ActionState(val isAvailable: Boolean, val block: ActionBlock? = null)`,
    `enum class ActionBlock { FRONT_VISIBLE, ANSWER_REVEALED, ALREADY_POSTPONED, LAST_IN_PASS }`.
- `StartupContract.kt`: `enum class StartupState { NEEDS_FILE_ACCESS, DATABASE_DAMAGED, DATABASE_MISSING }`,
  `sealed interface StartupAction { OpenSettings; Close }`.
- `DeckContract.kt`:
  - `data class TabState(val section: SectionTab, val count: Int, val isOpen: Boolean, val isActive: Boolean)`;
  - `enum class PositionKind { LEARNING, BROWSING }`,
    `data class PositionMetric(val kind: PositionKind, val position: Int, val total: Int)`,
    `data class SuccessMetric(val successes: Int, val threshold: Int)`;
  - `data class DeckHeader(val deckName: String, val position: PositionMetric?, val successes: SuccessMetric?, val canCreateCard: Boolean, val cardMenu: List<CardMenuItem>)`,
    `enum class CardMenuItem { EDIT, RETURN_TO_NEW, DELETE }`. Открыто ли меню — состояние дизайна;
  - `sealed interface DeckBody`:
    - `data class Card(val side: CardSideShown, val isContentDamaged: Boolean)`;
    - `data class EmptySection(val isPool: Boolean)`;
    - `data class Transition(val epoch: Int, val kind: TransitionKind, val remaining: Int, val total: Int)`;
  - `enum class TransitionKind { REGULAR, INSERTED, RESUMED }`;
  - `sealed interface DeckFooter`:
    - `data class Learning(val postpone: ActionState, val flip: ActionState, val areRatingsShown: Boolean)`;
    - `data class Browsing(val canBrowse: Boolean)`;
    - `data class StartPass(val kind: TransitionKind)`;
    - `data object None`;
  - `enum class DeckDialog { CONFIRM_DELETE_CARD, CONFIRM_RETURN_TO_NEW, NEED_NEW_CARD, DONE_FOR_TODAY }`;
  - `data class DeckScreenState(val header: DeckHeader, val tabs: List<TabState>, val body: DeckBody, val footer: DeckFooter, val dialog: DeckDialog?)`;
  - `sealed interface DeckAction`:
    - `OpenSection(section)`;
    - `Flip`, `Knew`, `DidNotKnow`, `Postpone`;
    - `ShowPrevious`, `ShowNext`;
    - `StartPass`;
    - `ChooseMenuItem(item)`;
    - `ConfirmDialog`, `DismissDialog`;
    - `CreateCard`, `OpenDeckList`.
- `EditorContract.kt`:
  - `enum class Thickness { THIN, MEDIUM, THICK }`, `enum class EditorTool { PEN, ERASER }`;
  - `enum class EditorDialog { DISCARD_DRAFT, DELETE_CARD, SAVE_ON_BACK }`;
  - `data class EditorScreenState(val isNewCard: Boolean, val side: CardSideShown, val tool: EditorTool, val thickness: Thickness, val canUndo: Boolean, val dialog: EditorDialog?)`;
  - `sealed interface EditorAction`:
    - `SelectSide(side)`, `SelectTool(tool)`, `SelectThickness(t)`;
    - `Undo`, `Save`;
    - `Cancel` (новая карточка → `DISCARD_DRAFT`), `Delete` (правка → `DELETE_CARD`), `Back` →
      `SAVE_ON_BACK`;
    - `ConfirmDialog`, `DismissDialog`.
- `DeckListContract.kt`:
  - `data class DeckRow(val id: Long, val name: String, val cardCount: Int, val activeEpoch: Int?, val isAllLearned: Boolean, val isCurrent: Boolean)`;
  - `data class DeckListState(val decks: List<DeckRow>, val canGoBack: Boolean)`,
    `sealed interface DeckListAction { OpenDeck(id); CreateDeck; Back }`;
  - `data class DeckNameState(val name: String, val canSave: Boolean)`,
    `sealed interface DeckNameAction { ChangeName(text); Save; Cancel }`.
- `Design.kt` — `interface Design` с `@Composable`-функциями:
  - `StartupScreen(state, onAction)`;
  - `DeckScreen(state, onAction, sheet: @Composable (Modifier) -> Unit)`;
  - `EditorScreen(state, onAction, sheet: @Composable (Modifier) -> Unit)`;
  - `DeckListScreen(state, onAction)`;
  - `DeckNameScreen(state, onAction)`.

- [ ] **Step 1: Реализация** — только типы.
- [ ] **Step 2: Run:** `./gradlew :ui-contract:assembleDebug verifyModuleGraph` → `BUILD SUCCESSFUL`;
  `grep -r "app.deckly.domain" ui-contract/src` ничего не находит.
- [ ] **Step 3: Commit** — «ui-contract: состояния и действия экранов».

### Task 4: `app` — контейнер, единый писатель, запуск

**Files:**
- Modify: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create в `app/src/main/kotlin/app/deckly/`:
  - `DecklyApplication.kt`, `AppContainer.kt`, `SerialWriter.kt`, `DayClock.kt`;
  - `navigation/AppScreen.kt`, `navigation/AppViewModel.kt`, `startup/StartupLoader.kt`
- Test: `app/src/test/kotlin/app/deckly/startup/StartupLoaderTest.kt`, `SerialWriterTest.kt`,
  `app/src/test/kotlin/app/deckly/fakes/` (заглушки портов)

**Interfaces:**
- Манифест:
  - `MANAGE_EXTERNAL_STORAGE`, а разрешения Onyx SDK вырезаны (`tools:node="remove"`), как в
    прототипе;
  - activity с `screenOrientation="portrait"`;
  - `tools:replace` для `allowBackup`/`label`.
- `DecklyApplication.onCreate`: `OnyxSdk.initialize(this)`, `AppContainer(this)`.
- `class SerialWriter(scope: CoroutineScope)` с `suspend fun <T> write(block: suspend () -> T): T`.
  Блок выполняется в `scope` приложения строго по очереди, вызывающий ждёт результата. Отмена
  вызывающего не отменяет запись.
- `class DayClock(now: () -> LocalDateTime = LocalDateTime::now)` с `fun computeToday(): Int` через
  `computeDayNumber`.
- `sealed interface AppScreen`:
  - `Startup(state: StartupState)`;
  - `DeckList`;
  - `DeckName`;
  - `Deck(deckId: Long)`;
  - `Editor(deckId: Long, start: EditorStart)`, где `sealed interface EditorStart`:
    `NewCard(returnSection: Section)`, `EditCard(cardId: Long, returnSection: Section)` или
    `ResumeDraft`.
- `class StartupLoader(hasFileAccess: () -> Boolean, openData: suspend () -> DecklyData)` с
  `suspend fun loadStart(): StartResult`. Порядок:
  1. нет доступа → `Startup(NEEDS_FILE_ACCESS)`, `openData` не вызывается;
  2. `DatabaseDamagedException` → `Startup(DATABASE_DAMAGED)`, `DatabaseMissingException` →
     `Startup(DATABASE_MISSING)`;
  3. есть черновик → `Editor(stored.deckId, ResumeDraft)`;
  4. есть текущая колода → `Deck(id)`;
  5. есть колоды → `DeckList`;
  6. иначе `DeckList`, в котором показывается «Нет колод».
- `AppViewModel`:
  - `val screen: StateFlow<AppScreen>`, `fun navigate(to: AppScreen)`;
  - при `ON_RESUME` на `NEEDS_FILE_ACCESS` повторяет `loadStart`;
  - при `ON_STOP` вызывает `mediaStore.notifyChanged()`.

- [ ] **Step 1: Тесты** (заглушки портов в `fakes/`):
  - `noFileAccessNeverOpensStorage` (Review Focus 3);
  - `damagedAndMissingDatabaseHaveOwnStates`;
  - `draftOpensEditorFirst`;
  - `currentDeckOpensDeck`;
  - `noDecksOpensDeckList`;
  - `writesRunInCallOrderAndSurviveCallerCancellation` (`SerialWriter`).
- [ ] **Step 2–4:** RED → реализация → `./gradlew :app:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 5: Commit** — «app: контейнер, единый писатель, порядок запуска».

### Task 5: `app` — экран колоды: перевод состояния и действия

**Files:**
- Create: `app/src/main/kotlin/app/deckly/deck/DeckScreenMapper.kt`, `DeckViewModel.kt`,
  `CardContentCache.kt`
- Test: `app/src/test/kotlin/app/deckly/deck/DeckScreenMapperTest.kt`, `DeckViewModelTest.kt`

**Interfaces:**
- Consumes: задачи 3 и 4, `ReviewEngine` и `computeCardActions` из `domain`.
- Produces:
  - `fun buildDeckScreenState(state: DeckState, transition: ReviewOutcome.Transition?, dialog: DeckDialog?, isContentDamaged: Boolean): DeckScreenState` —
    чистая функция. Правила взяты из `design/README.md` и `build.py`:
    - `tabs` — шесть секций, `count` — число карточек в секции, `isOpen` — `activeSection`,
      `isActive` — `currentEpoch` (в ожидании это ожидающая эпоха, при вставке — I);
    - `position`:
      - в начатом проходе открытой активной эпохи — `LEARNING`, позиция `passCursor + 1` из
        размера прохода;
      - в другой секции с карточками — `BROWSING`, позиция `browseCursor + 1` из размера секции;
      - в пустой секции — `null`;
    - `successes`:
      - для карточки эпохи — её `successes` из порога эпохи (4, 4, 4, 3);
      - для новых и выученных — `null`;
    - `canCreateCard` — открыт пул новых;
    - `cardMenu`:
      - пул — `EDIT, DELETE`;
      - эпохи (активная, неактивная, прерванная) — `RETURN_TO_NEW, DELETE`;
      - выученные — `DELETE`;
      - пустая секция или переход — пусто;
    - `body`/`footer`:
      - `transition != null` → `Transition` и `StartPass(kind)`;
      - карточка активного прохода → `Card` и `Learning`: перенос и переворот из
        `computeCardActions`, `areRatingsShown = visibleSide == BACK`;
      - карточка другой секции → `Card` и `Browsing(canBrowse = size > 1)`;
      - пустая секция → `EmptySection` и `None`;
  - `class DeckViewModel(deckId, repositories, writer, clock, random)`:
    - `val state: StateFlow<DeckScreenState?>`, `val shownCard: StateFlow<ShownCard?>`
      (`cardId`, сторона, штрихи или признак повреждения), `fun onAction(action: DeckAction)`;
    - действие: `ReviewEngine.applyAction` → `collectDeckChanges` → `writer.write { saveDeckChanges }`
      → публикация;
    - действия идут по одному, следующее применяется к уже опубликованному состоянию. Нажатие,
      пришедшее во время записи, ждёт её конца;
    - итоги движка: `Transition` — экран перехода до `StartPass`; `NeedNewCard` и `DoneForToday` —
      соответствующий `dialog`, `ConfirmDialog` открывает пул (`OpenSection(NEW)`);
    - `CreateCard` → `navigate(Editor(deckId, NewCard(activeSection)))`. `ChooseMenuItem(EDIT)` →
      `navigate(Editor(deckId, EditCard(cardId, NEW)))`. Черновик создаёт задача 6;
    - удаление и возврат → `dialog` подтверждения, после `ConfirmDialog` — действие движка;
    - `OpenDeckList` → `navigate(DeckList)`; при открытии колоды `saveCurrentDeckId`;
  - `class CardContentCache(contents: CardContentRepository)`:
    - `suspend fun loadContent(cardId: Long): Result<CardContent>`;
    - хранит последние 4 карточки;
    - после показа карточки подгружает следующую карточку прохода, чтобы она открылась быстрее
      200 мс.

- [ ] **Step 1: Тесты `DeckScreenMapperTest`** — каждый строит `DeckState` фикстурами `domain`
  (скопировать нужные строители в `app/src/test`):
  - `learningShowsPositionOfPassAndSuccessesOfThreshold`: проход 40, `passCursor = 13`, у карточки 3
    успеха в эпохе 1 → `LEARNING 14/40`, `3/4`;
  - `ratingsOnlyOnBackAndPostponeBlockedAfterReveal`: лицо → оценок нет, перенос доступен. Оборот →
    оценки есть. Снова лицо → перенос `ANSWER_REVEALED`;
  - `insertPassMarksFirstEpochActiveAndBrowsesPausedEpoch`: вставка → ▶ у I. Открыта IV →
    `BROWSING 41/100`, `1/3`, меню `RETURN_TO_NEW, DELETE`, без оценок;
  - `waitingStateMarksWaitingEpochActive`: `DoneForToday` → открыт пул, ▶ у II, `dialog = DONE_FOR_TODAY`;
  - `emptySectionHasNoCardActions`, `poolOffersCreateAndEdit`, `learnedOffersOnlyDelete`;
  - `transitionKindsAndCounts`: обычный «II, 23», вставка «I, 40», возврат «IV, осталось 60 из 100».
- [ ] **Step 2: Тесты `DeckViewModelTest`**:
  - `stateIsPublishedOnlyAfterWrite`: заглушка записи на паузе — состояние прежнее до её конца;
  - `rapidKnewTapsApplySequentially` (Review Focus 4): три `Knew` подряд без ожидания — `passCursor`
    вырос на 3, каждое применено к предыдущему результату;
  - `deleteAsksForConfirmation`;
  - `needNewCardDialogOpensPool`;
  - `damagedContentShowsEmptySheet` (Review Focus 5).
- [ ] **Step 3–5:** RED → реализация → `./gradlew :app:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 6: Commit** — «app: экран колоды».

### Task 6: `app` — редактор

**Files:**
- Create: `app/src/main/kotlin/app/deckly/editor/EditorViewModel.kt`, `EditorScreenMapper.kt`
- Test: `app/src/test/kotlin/app/deckly/editor/EditorViewModelTest.kt`

**Interfaces:**
- Consumes: `EditorSession` и `saveDraft` из `domain`, `DraftRepository`, задачи 3–5.
- Produces `class EditorViewModel`:
  - `val state: StateFlow<EditorScreenState>`, `val canvas: StateFlow<CanvasState>`
    (`strokes` видимой стороны, `tool`, `penWidthPx`, `isPenEnabled`, `uiRevision`);
  - `fun onAction(action: EditorAction)`, `fun onStroke(stroke: Stroke)`,
    `fun onErase(path: List<StrokePoint>, radius: Float)`.
- Начало:
  - `EditorStart.NewCard` → `startNewCard(returnSection)`;
  - `EditorStart.EditCard` → `startEditing(card, content, returnSection)`;
  - `EditorStart.ResumeDraft` → `resumeDraft(stored.draft)`.
  Черновик появляется с первого штриха: до него `storeDraft` не вызывается (ТЗ).
- После каждого штриха, стирания, отмены и смены стороны (если черновик уже есть) —
  `writer.write { drafts.storeDraft(...) }`. Отмена не меняет историю в хранилище: она в памяти.
- `Save`:
  1. `allocateCardId`;
  2. `saveDraft(state, draft, id, today, now)`;
  3. `writer.write { commitDraft(collectDeckChanges(before, after), content) }`;
  4. `navigate(Deck)`, затем `OpenSection(returnSection)` — заметка плана 2.
  `Back` → `SAVE_ON_BACK`. `Cancel` → `DISCARD_DRAFT`, `ConfirmDialog` → `discardDraft` и выход.
  `Delete` → `DELETE_CARD`, `ConfirmDialog` → `DeleteCard` движка и `discardDraft` и выход.
- `isPenEnabled = dialog == null`.
- `uiRevision` растёт, когда меняется что-то вне холста: `tool`, `thickness`, `canUndo`, `dialog`
  (решение 5).
- Толщины `THIN/MEDIUM/THICK` → `penWidthPx` 3/6/10. В единицы карточки переводит `ink`.

- [ ] **Step 1: Тесты:**
  - `firstStrokeCreatesDraftAndEachActionStoresIt` (Review Focus 1): до штриха `storeDraft` не
    вызывался. После штриха, стирания и отмены — по вызову, в этом порядке, с верным содержимым;
  - `saveAppendsNewCardAndReturnsToSection`: `commitDraft` вызван, экран — колода, открыта
    `returnSection`, `visibleSide = FRONT`;
  - `cancelAsksBeforeDiscarding`, `backOffersSaveAndExit`, `deleteFromEditorRemovesCardAndDraft`;
  - `dialogDisablesPenAndBumpsRevision`, `undoAvailabilityChangeBumpsRevision`,
    `ordinaryStrokeDoesNotBumpRevision`;
  - `resumedDraftOpensOnSavedSide`.
- [ ] **Step 2–4:** RED → реализация → `./gradlew :app:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 5: Commit** — «app: редактор и черновик».

### Task 7: `app` — список колод и новая колода

**Files:**
- Create: `app/src/main/kotlin/app/deckly/decks/DeckListViewModel.kt`, `DeckNameViewModel.kt`
- Test: `app/src/test/kotlin/app/deckly/decks/DeckListViewModelTest.kt`, `DeckNameViewModelTest.kt`

**Interfaces:**
- `DeckListViewModel`:
  - строки из `listDecks()` и `loadDeckState` каждой колоды: число карточек, активная эпоха,
    признак «всё выучено» (все карточки в `LEARNED`), отметка текущей;
  - `OpenDeck` → `saveCurrentDeckId` и `navigate(Deck)`;
  - `CreateDeck` → `navigate(DeckName)`;
  - `Back` → к текущей колоде, если она есть.
- `DeckNameViewModel`: `canSave = name.isNotBlank()`. `Save` → `createDeck(name.trim())`,
  `saveCurrentDeckId`, `navigate(Deck)`. `Cancel` → `DeckList`.

- [ ] **Step 1: Тесты:**
  - `rowsShowCountActiveEpochAndCurrent`, `allLearnedDeckIsMarked`;
  - `openingDeckMakesItCurrent`;
  - `blankNameCannotBeSaved`, `createdDeckOpens`.
- [ ] **Step 2–4:** RED → реализация → `./gradlew :app:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 5: Commit** — «app: список колод и новая колода».

### Task 8: `design-e` — токены, значки, общие элементы

**Files:**
- Modify: `design-e/build.gradle.kts`
- Create в `design-e/src/main/kotlin/app/deckly/design/e/`: `Tokens.kt`, `Icons.kt`, `Components.kt`
- Test: `design-e/src/test/kotlin/app/deckly/design/e/ComponentsTest.kt`

**Interfaces:**
- `object Tokens` — размеры из `design/styles.css` в dp и sp:
  - кадр: поля 32, интервалы 16;
  - шапка 64, вкладки 72, нижняя строка 112, кнопки 112 и 64;
  - значки 32 и 18;
  - кегли: подписи 18, значения 26, название 28, кнопки 24–26, вкладки 20/24;
  - рамки 1/2/3, скругление 8 и 12;
  - цвета: чёрный, белый, недоступный `#A0A0A0`.
- `Icons.kt` — по `ImageVector` на каждый путь `PATHS` из `build.py`:
  `more, down, left, right, flip, plus, check, cross, defer, active, pen, eraser, undo, trash, file, cards, export`.
  Линия 1.8, круглые концы.
- `Components.kt`:
  - `DecklyButton(label, icon, kind: PRIMARY/NORMAL/QUIET/OFF, description, onClick)`, без
    индикации;
  - `TopBar`, `Metric`, `SectionTabs(tabs, onOpen)`;
  - `Sheet(content)` с четырьмя угловыми метками;
  - `Footer` (сетки `112 112 1fr 1fr`, `nav`, `one`, `two`);
  - `Dialog(title, text, actions)` — фон не затемняется и не принимает касания;
  - `Popup(title, items)` — закрывается касанием мимо.
  Подписи кнопок-значков — как `aria-label` в `build.py`.

- [ ] **Step 1: Тесты** (`createComposeRule`, Robolectric):
  - `buttonsAreAtLeast64dp`;
  - `iconButtonsHaveDescriptions`;
  - `offButtonIsDisabledAndGrey`;
  - `dialogBackgroundIgnoresTouches`.
- [ ] **Step 2–4:** RED → реализация → `./gradlew :design-e:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 5: Commit** — «design-e: токены, значки, общие элементы».

### Task 9: `design-e` — экран колоды

**Files:**
- Create: `design-e/src/main/kotlin/app/deckly/design/e/DeckScreen.kt`, `DeckTexts.kt`
- Test: `design-e/src/test/kotlin/app/deckly/design/e/DeckScreenTest.kt`

**Interfaces:**
- `DeckScreen(state, onAction, sheet)` по экранам `learn-*`, `browse-*`, `pool*`, `learned*`,
  `empty-*`, `transition*`, `menu-*`, `dlg-delete-*`, `dlg-return-*`, `dlg-need-card`,
  `dlg-today-done` из `build.py`:
  - шапка: название колоды со стрелкой → `OpenDeckList`. Метрики «Обучение» или «Просмотр»
    `14 / 40` и «Успехи» `3 / 4`. Плюс — в пуле. «⋯» → `Popup` с `cardMenu`;
  - вкладки «Новые · I · II · III · IV · Выучено» с числами, подчёркивание у открытой, ▶ у активной
    рядом с числом;
  - лист — слот `sheet`. Пустая секция — значок `cards` и «Нет карточек» (в пуле «Нет новых
    карточек»). Переход — значок, римская цифра, строка «23 карточки» или «Осталось 60 из 100»;
  - нижняя строка:
    - обучение — `[перенос, переворот, Не знал, Знал]`, оценки только при `areRatingsShown`.
      Недоступный перенос серый, причина входит в описание;
    - просмотр — `[←, переворот, —, →]`;
    - переход — одна кнопка «Начать проход» или «Продолжить»;
  - диалоги — тексты из `build.py`:
    - «Удалить карточку?» / «Вопрос и ответ будут удалены безвозвратно.» / «Оставить» «Удалить»;
    - «Вернуть в новые?» / «Успехи обнулятся.»;
    - «Пока нечего повторять»;
    - «На сегодня всё»;
- `DeckTexts.kt` — все строки экрана, в том числе склонение «карточка / карточки / карточек».

- [ ] **Step 1: Тесты:**
  - `questionHasNoRatings`, `answerHasEqualRatings`;
  - `blockedPostponeExplainsReasonInDescription`;
  - `flipIsSecondCellInLearningAndBrowsing`;
  - `activeMarkFollowsState`;
  - `cardCountWordAgrees` (1 карточка, 23 карточки, 40 карточек, 112 карточек);
  - `menuOffersStateItemsOnly`.
- [ ] **Step 2–4:** RED → реализация → `./gradlew :design-e:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 5: Commit** — «design-e: экран колоды».

### Task 10: `design-e` — редактор, колоды, запуск

**Files:**
- Create: `design-e/src/main/kotlin/app/deckly/design/e/EditorScreen.kt`, `DeckListScreen.kt`,
  `DeckNameScreen.kt`, `StartupScreen.kt`, `DesignE.kt`
- Test: `design-e/src/test/kotlin/app/deckly/design/e/EditorScreenTest.kt`, `OtherScreensTest.kt`

**Interfaces:**
- `EditorScreen` по `editor-*`, `dlg-discard`, `dlg-delete-edit`, `dlg-editor-back`:
  - шапка: «Новая карточка» или «Редактирование», слева «Отменить» или «Удалить» со значком,
    справа «Сохранить»;
  - вкладки «Вопрос / Ответ», лист — слот;
  - инструменты: перо, ластик, группа толщин из трёх образцов линии, отмена справа.
- `DeckListScreen` по `decks` и `decks-empty`, без меню колоды и импорта (план 5). Нижняя строка —
  одна кнопка «Новая колода». Пока нет страниц (план 5), список прокручивается, чтобы ни одна колода
  не потерялась за краем:
  - строки с номером или галочкой у текущей;
  - подпись «245 карточек · ▶ I» или «18 карточек · выучены»;
  - «Новая колода».
- `DeckNameScreen` по `deck-new`: поле, «Отмена», «Создать» (серое, пока имя пустое).
- `StartupScreen` — решение 7.
- `object DesignE : Design` собирает пять экранов.

- [ ] **Step 1: Тесты:**
  - `editorShowsToolsAndSideTabs`, `undoIsGreyWhenUnavailable`, `newAndEditHaveDifferentLeftAction`;
  - `emptyDeckListShowsNoDecks`, `currentDeckIsChecked`;
  - `createIsGreyUntilNameEntered`;
  - `startupStatesHaveTheirTexts`.
- [ ] **Step 2–4:** RED → реализация → `./gradlew :design-e:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 5: Commit** — «design-e: редактор, колоды, запуск».

### Task 11: `app` — activity и сборка экранов

**Files:**
- Create: `app/src/main/kotlin/app/deckly/MainActivity.kt`, `app/src/main/kotlin/app/deckly/ui/AppRoot.kt`,
  `ui/RefreshPolicy.kt`
- Test: `app/src/test/kotlin/app/deckly/ui/RefreshPolicyTest.kt`, `app/src/test/kotlin/app/deckly/ui/AppRootTest.kt`

**Interfaces:**
- `MainActivity`:
  - скрывает системные панели;
  - `setContent { AppRoot(container) }`;
  - системная «Назад» — действие текущего экрана: редактор → `Back`; колода → выход; список →
    `Back`; форма → `Cancel`;
  - `Close` на экране запуска → `finishAndRemoveTask()`;
  - `OpenSettings` → `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` для пакета.
- `AppRoot`:
  - `LocalIndication` без эффекта и overscroll выключен для всего дерева;
  - по `AppScreen` вызывает `DesignE`;
  - слоты: колода — `CardSideImage(shownCard.strokes)`, редактор — `DrawingSheet(canvas…)`;
  - описание листа ставит дизайн через модификатор слота (задача 9), поэтому у `CardSideImage`
    параметр становится `description: String?`, и слот передаёт `null`, чтобы описание не
    задвоилось;
  - форма имени и редактор не прячутся под клавиатурой: `adjustResize` или отступ под IME;
  - ViewModel экрана живёт ровно столько, сколько экран. Модели колоды и списка читают данные один
    раз в `init` (раздел, карточка фокуса), поэтому каждый переход `navigate(…)` получает новое
    хранилище ViewModel, а старое очищается. Это верно, даже если новый `AppScreen` равен старому
    (возврат из редактора в ту же колоду). Хранилище экрана держит `AppViewModel`, поэтому оно
    переживает смену конфигурации.
- системная «Назад», уточнения:
  - при открытом окне (`state.dialog != null`) → `DismissDialog`, и в колоде, и в редакторе;
  - открытое меню карточки — состояние дизайна, поэтому его «Назад» ловит сам `design-e`
    (`BackHandler` в `Popup`, зависимость `activity-compose`);
  - колода без окна, список без `canGoBack`, экран запуска — системное поведение (уход в фон).
- `fun isFullRefreshNeeded(previous: RefreshKey?, next: RefreshKey): Boolean`, где
  `RefreshKey(screen, cardId, editorSide, editorExitCount)` — решение 4. Полное обновление
  выполняется после кадра (`withFrameNanos` дважды). В редакторе — при выключенном пере.

- [ ] **Step 1: Тесты:**
  - `RefreshPolicyTest`:
    - `screenChangeRefreshesFully`, `newCardRefreshesFully`;
    - `flipRefreshesPartially`;
    - `editorSideSwitchRefreshesFully`;
    - `sameStateDoesNotRefresh`;
  - `AppRootTest`, Robolectric с заглушками портов: от запуска с черновиком открывается редактор;
    `Back` в редакторе показывает диалог.
- [ ] **Step 2–4:** RED → реализация → `./gradlew :app:testDebugUnitTest && ./gradlew build`.
- [ ] **Step 5: Commit** — «app: activity и сборка экранов».

### Task 12: проверка на планшете

Нужен владелец. Исполнитель ставит сборку, читает logcat и снимает экран.

**Files:**
- Create: `docs/install.md`. В нём:
  - сборка;
  - `tools/install-apk.sh <apk> app.deckly`;
  - выдача доступа;
  - шаг «режим обновления для Deckly в оптимизации приложений BOOX» (ТЗ).
- Modify: `docs/superpowers/specs/2026-11-03-architecture-design.md` — по итогам проверки.

- [ ] **Step 1: Установка.** `./gradlew :app:assembleDebug && tools/install-apk.sh app/build/outputs/apk/debug/app-debug.apk app.deckly`
  → код 0. Удалить `spike` с планшета не нужно: у него своя папка `Deckly/spike/`.
- [ ] **Step 2: Измерения** (исполнитель, logcat):
  - `wm density` и `wm size` — проверка «1 единица = 1 dp»;
  - время `openDecklyData` с `quick_check`;
  - время открытия карточки от действия до кадра на карточке с 30 штрихами, порог 200 мс;
  - сработал ли `force` папки `strokes/` на FUSE (нет `IOException` в логе);
  - хватает ли двух кадров перед полным обновлением: на снимке после обновления нет остатков
    прежнего экрана;
  - после «Закрыть» на экране запуска задачи нет в «Недавних».
- [ ] **Step 3: Владелец.**
  1. Первый запуск → экран доступа → настройки → назад → «Нет колод» → новая колода.
  2. Пул, плюс, редактор: штрихи ручкой с нажимом, ластик кнопкой и инструментом, отмена.
     Заметна ли пауза пера, когда становится доступна отмена? Смена стороны, «Сохранить».
  3. Ещё 4 карточки, затем вкладка I: переход → обучение → переворот → «Знал» и «Не знал»,
     перенос в конец, блокировка переноса после переворота.
  4. Меню карточки: возврат в новые, удаление с подтверждением.
  5. Посреди рисования убить приложение (`adb shell am force-stop app.deckly`) → запуск → черновик
     на месте (Review Focus 1).
  6. Размеры и серый цвет: читаемо ли, хватает ли полей, не мелко ли. Читаются ли ▶ и номера
     строк списка колод при плотности 1,875; совпадают ли высоты строк текста с макетом
     (Robolectric их округляет).
  7. Новая колода: клавиатура выходит сама при открытии формы; русское название с подсказками
     клавиатуры набирается без потерянных букв.
  8. Системные панели скрыты; свайп от края их показывает, после возврата в приложение они снова
     скрыты. «Назад»: при открытом меню и окне закрывает их, на колоде уводит приложение в фон.
- [ ] **Step 4: Итоги** — в `docs/superpowers/specs/2026-11-05-first-build-results.md`. Правки по
  итогам — отдельными задачами после согласования с владельцем.
- [ ] **Step 5: Commit** — «Первая рабочая сборка: проверка на планшете».

---

После плана 4 — план 5: управление колодами (меню, переименование, удаление, страницы списка),
экспорт и импорт со статичным экраном «идёт импорт».

Отложено в план 5:
- **Просмотр прерванной эпохи во время вставки.** Сейчас он открывается с первой карточки
  («1 из 100»). В макете `browse-e4-insert` — с карточки, на которой прервались («41 из 100»). Это
  правка `OpenSection` в движке: `browseCursor` = индекс карточки `pausedCursor` в секции.
