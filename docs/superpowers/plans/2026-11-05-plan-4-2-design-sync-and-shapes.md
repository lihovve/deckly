# План 4.2: актуальный макет в приложении и фигуры в редакторе

> **Для исполнителя:** задачи выполняются по одной, после каждой отчёт и ожидание проверки: хэш, RED и GREEN, мутации, решения. Шаги отмечаются `- [ ]`.

**Цель:** перенести в приложение весь макет `design/` от владельца: английский интерфейс, мелкие правки экранов, экран загрузки и фигуры при создании и правке карточки.

**Архитектура:** внешний вид и жесты над листом живут в `design-e`, контуры сохранённых фигур в `domain` и `ink` (вид сохранённой карточки не зависит от варианта дизайна), состояние режима фигур в `EditorViewModel`. На стороне карточки появляются фигуры рядом со штрихами (`CardSide`), файл штрихов и файл колоды получают версию 2 и читают версию 1. База остаётся версии 1.

**Технологии:** Kotlin, Compose Foundation без Material, Onyx SDK, JUnit 5 (`domain`), JUnit 4 и Robolectric (остальные модули).

**Спек:** решение владельца от 5 ноября, `design/README.md` (разделы «Shapes in the editor», «Startup and error screens», «Voice»), `design/build.py`, `design/styles.css`, `design/editor-shapes.js`.

## Общие условия

- `design/` правит только владелец. Агент коммитит его как есть (задача 1) и больше не меняет. Чего в макете нет, агент не додумывает, а передаёт владельцу списком; временные проверочные файлы лежат вне дерева.
- Весь интерфейс на английском, фразы дословно из `design/build.py`. Комментарии и документы остаются на русском.
- База Sonar для всего плана: `b87e678`. Она не сдвигается.
- Старые данные читаются: файл штрихов версии 1 и файл колоды версии 1. Схема базы не меняется, миграции нет.
- Коммиты с префиксом `fix: #comment`, прямо в `main`, по явному списку файлов. Не пушить. К планшету не обращаться, пока планировщик не передаст, что владелец его подключил.
- Меняется поведение: ТЗ в том же коммите. Меняется устройство кода: `docs/architecture.md` в том же коммите.
- Имена методов и вложенные типы по глобальному `AGENTS.md`; глубина вложенных вызовов не больше 4.

## На что смотреть при проверке

1. Карточка из старого файла (`DKS1` версии 1, колода версии 1) открывается, правится и сохраняется без потерь. Тесты в задаче 4.
2. «Save», «Back» и смена стороны при выделенной фигуре не теряют её молча. Тесты в задаче 7.
3. Точка внутри пустой фигуры и штрих через контур остаются штрихами; выделяет только касание контура. Тесты в задаче 5.
4. Палец и ладонь в режиме фигур ничего не двигают и не ставят. Тесты в задаче 6.
5. Перо снова включается после применения, отмены и закрытия палитры, а убийство приложения посреди размещения открывает черновик с уже применёнными фигурами. Тесты в задаче 7.

## Решения за нами (владелец может поменять, в макете этого нет)

Макет прямо оставляет это вне прототипа: «Saving, switching sides, erasing and undo history are not implemented».

| Случай | Что делаем |
| --- | --- |
| Касание размещённой фигуры пером | Выделяет, только если короткое касание пришлось на контур (радиус 32 единицы макета). Внутри пустой фигуры пером можно писать, и точка остаётся точкой. В демо макета выделяет вся рамка, но там нельзя писать пером |
| Ластик | Снимает фигуру целиком, если задел контур |
| Отмена | Применение новой фигуры, правка фигуры и стирание: по одному шагу. Движение до галочки шагом не считается |
| Save или смена стороны при выделенной фигуре | Сначала применить, потом действие. В ожидании размещения фигуры ещё нет: режим просто заканчивается |
| Back при выделенной фигуре | Обычное окно «Save and exit?»; «Keep editing» возвращает к выделению, «Save and exit» применяет и сохраняет |
| Черновик | Пишется после применения; неприменённая фигура перезапуск не переживает |
| Новая панель при первом открытии | Вид star, контур 3 (начальные значения `editor-shapes.js`); внутри одного открытия редактора помнятся последние |
| Слово «cards» | «1 card», иначе «N cards». В макете только множественное |

---

### Задача 1: макет и английский интерфейс

**Файлы:**
- Закоммитить как есть: весь `design/`, этот план.
- Изменить: `design-e/.../Texts.kt`, `ScreenTexts.kt`, `DeckTexts.kt`, `Components.kt:186`, `Overlays.kt:117`, `Icons.kt`; `AGENTS.md`, `README.md`, `docs/architecture.md`, ТЗ.
- Тесты: все тесты `design-e` и `app`, где есть фразы интерфейса (`DeckScreenTest`, `OtherScreensTest`, `AppRootTest`, `EditorScreenTest`, `OverlaysTest`, `ComponentsTest`, `DeckTextsTest`, `ScreenTextsTest`, `TextsTest`, `IconsTest`, `TokensTest`, `MainActivityTest`, `BackHandlingTest`, `DesignETest`).

**Интерфейсы:**
- Даёт: `IconId.SHAPES` и `DecklyIcons.Shapes`; `DeckTexts.DRAWING_UNAVAILABLE`. Объект `DeckTextsBeyondMockup` удалён.

- [ ] **Шаг 1.** Проверить, что макет цел: скопировать `design/` вне дерева, запустить там `python3 design/build.py`, `diff -rq` с деревом не показывает различий. В `design/` нет кириллицы, адресов почты и абсолютных путей.
- [ ] **Шаг 2.** Убедиться в RED: `./gradlew :design-e:testDebugUnitTest --rerun` и `:app:testDebugUnitTest` падают на фразах и токенах.
- [ ] **Шаг 3.** Заменить все константы фразами из `build.py`, дословно. Составные фразы:

| Функция | Результат |
| --- | --- |
| `formatCardCount` | `1 card`, `0 cards`, `23 cards` |
| `describePosition` | `Learning, card 14 of 40`, `Browse, card 2 of 112` |
| `describeDeckHeading` | `English A2. Choose deck` |
| `formatRemaining` | `60 of 100 remaining` |
| `describeNextPass` | `Next pass: epoch II` |
| `describePostpone` | `Move to end of pass`, `…: answer already revealed`, `…: already postponed`, `…: last card` |
| `describeThickness` | `Thin line`, `Medium line`, `Thick line` |
| `formatCaptionBeforeEpoch`, `formatLearnedCaption` | `245 cards ·`, `18 cards · learned` |
| описание вкладки | `New, 112 cards, currently learning` |

  «Закрыть меню» из `Overlays.kt` и хвост вкладки из `Components.kt` переходят в `*Texts`, литералов в экранах нет.
- [ ] **Шаг 4.** Обновить проверки сверки:
  - `StartupTexts` проверяется тем же тестом «каждая фраза есть в макете»; `startupPhrasesOutsideMockupAreExactlyTheDocumentedOnes` и `phrasesBeyondMockupAreOnlyTheDocumentedOnes` удалить, их причин больше нет.
  - `everyRussianLiteralOfScreensIsInMockupOrDocumented` заменить на два теста: `noCyrillicLiteralInScreens` и `everyLiteralWithLettersOfScreensIsInMockup` (кроме `Icons.kt` с контурами значков). Строка не на месте должна ловиться и по-английски, как раньше ловилась по-русски.
  - `TokensTest`: селекторы `.metric>span`, `.metric-value`.
  - Русские имена колод в `data` и `domain` не трогать: это данные пользователя.
- [ ] **Шаг 5.** Добавить значок `SHAPES` из `PATHS` (`IconsTest` требует совпадения состава). Круг записать дугами, как уже сделано для других.
- [ ] **Шаг 6.** Правила:
  - `AGENTS.md`, «Модули и макет»: решения по дизайну принимает владелец в `design/`; агент макет не меняет и не додумывает, чего не хватает, передаёт владельцу. Строку «сначала правится макет, потом код» убрать; то же в разделе «Соглашения» `docs/architecture.md`.
  - `README.md`: подписи интерфейса английские (**New**, **Learned**, **Missed it**, **Nailed it**, **All done for today**, **Nothing to review yet**, **Start pass**, **Open New** и другие), остальной текст по-русски.
  - ТЗ: в начале строка «Интерфейс на английском, формулировки заданы в `design/`»; названия окон заменить на английские. Термины алгоритма «знал» и «не знал» оставить, рядом один раз указать: в интерфейсе **Nailed it** и **Missed it**.
- [ ] **Шаг 7.** GREEN: `./gradlew build` и `./gradlew :design-e:testDebugUnitTest --rerun`.
- [ ] **Шаг 8.** Коммит (`git add design/`, остальное списком): `fix: #comment Макет design/ от владельца и английский интерфейс по нему`.

### Задача 2: мелкие правки экранов и сверка с макетом

Структурное сравнение старых и новых экранов дало: 56 экранов (обучение, просмотр и всё поверх них) меняют счётчик, `transition` меняет цифру эпохи, у 28 экранов изменился только текст, 7 экранов редактора идут в задачу 6.

**Файлы:** `design-e/.../Components.kt`, `DeckScreen.kt`, `Tokens.kt`, `StartupScreen.kt`, `DesignE.kt`, `Texts`; `ui-contract/.../Design.kt`; `app/.../ui/AppRoot.kt`.

**Интерфейсы:**
- Даёт: `Metric(label: String, current: Int, total: Int, modifier: Modifier = Modifier)`; `Design.LoadingScreen()`.

- [ ] **Шаг 1. Тесты счётчика (RED).** `metricLabelIsCenteredOverSlash` для `3/4`, `14/40`, `41/100`, `100/100`: центр подписи совпадает с центром косой черты с точностью до 1 dp, левая и правая колонки равной ширины, левое число прижато вправо, правое влево, мин. ширина 132 сохранена. `metricValueMatchesStyles`: `column-gap` 8 и строка `minmax(3ch,1fr) auto minmax(3ch,1fr)` из `.metric-value`. Счётчик позиции остаётся одним узлом для диктора.
- [ ] **Шаг 2. Реализация.** Значение строится по сетке `.metric-value`: боковые колонки равные, не уже трёх ширин цифры «0» стиля значения (через `TextMeasurer`), подпись по центру. `formatPositionValue` и `formatSuccessesValue` убрать, если не используются.
- [ ] **Шаг 3. Тест и реализация цифры эпохи.** `Tokens.NextEpochShift` = −6 dp, сверка с `.card .next-epoch` (`transform`). Сдвиг только у `TransitionKind.REGULAR` и без влияния на раскладку (`offset`). `regularTransitionNumeralIsShiftedLeft`: центр цифры левее центра подписи на 6 dp ±1; `insertedAndResumedNumeralsAreNotShifted`.
- [ ] **Шаг 4. Экран загрузки.** Тест: `AppScreen.Loading` показывает `Deckly` по центру, кегль 64, жирный, интервал −2, высота строки 1,1 (`.loading-brand h1`). `Design.LoadingScreen()`, `StartupTexts.APP_NAME`. `RefreshEffect` на загрузке не добавлять: первым полным обновлением остаётся первый настоящий экран.
- [ ] **Шаг 5. Сверка с макетом глазами.** Вне дерева снять `google-chrome --headless=new … --window-size=992,1323 --screenshot` каждого реализованного экрана и снимок Compose тех же состояний на 992 × 1323 dp, сложить парами и просмотреть. Планы 5 (меню колоды, переименование, страницы, импорт) не входят. В отчёт: список расхождений положения больше 2 dp, кроме разницы шрифта. Расхождение чинится в коде; нет в макете — передаётся владельцу.
- [ ] **Шаг 6.** GREEN и коммит: `fix: #comment Счётчики шапки, сдвиг цифры эпохи и экран загрузки по макету`.

### Задача 3: модель фигур и сессия редактора

**Файлы:**
- Создать: `domain/.../model/Shape.kt`, `domain/.../editor/ShapeOutline.kt`, `domain/.../editor/HeldShape.kt`, их тесты.
- Изменить: `domain/.../model/Stroke.kt`, `editor/Draft.kt`, `editor/EditorSession.kt`, `editor/Eraser.kt`, `domain/build.gradle.kts` (входы тестов: `design/build.py`). Вызовы в `data`, `ink`, `app` приводятся к новым типам без смены поведения.

**Интерфейсы:**
- Даёт:

```kotlin
data class Shape(val kind: Kind, val left: Float, val top: Float, val right: Float, val bottom: Float, val width: Float) {
    enum class Kind(val code: Int, val key: String, val svg: String)
}
data class CardSide(val strokes: List<Stroke> = emptyList(), val shapes: List<Shape> = emptyList())
data class CardContent(val cardId: Long, val front: CardSide, val back: CardSide)   // Draft так же
fun Shape.listPolylines(): List<List<StrokePoint>>       // в единицах карточки, нажим 0
fun isShapeTouched(shape: Shape, path: List<StrokePoint>, radius: Float): Boolean
fun findShapeAt(shapes: List<Shape>, x: Float, y: Float, radius: Float): Int?   // верхняя (последняя) из задетых
fun EditorSession.addShape(shape: Shape): EditorSession
fun EditorSession.changeShape(index: Int, shape: Shape): EditorSession          // та же фигура: шага нет
data class HeldShape(val shape: Shape, val editedIndex: Int?)                   // в руках, в черновик не входит
fun EditorSession.applyHeldShape(held: HeldShape): EditorSession
fun EditorSession.listVisibleContent(held: HeldShape?): CardSide
```

  Шаги отмены: `ShapeAdded(side)`, `ShapeChanged(side, index, before)`, а `StrokesErased` становится `ContentErased(side, strokes, shapes)`.

- [ ] **Шаг 1. Тесты (RED).**
  - `Kind` идут в порядке `SHAPES` из `build.py`, `key` и `svg` совпадают дословно, `code` закреплены 1…15 (формат файла).
  - `Shape` не создаётся с `left >= right`, `top >= bottom`, нечисловыми значениями, `width <= 0`.
  - Контуры в сетке 100 × 100: линия из двух точек; у стрелки две ломаные `[12,50 → 88,50 → 68,30]` и `[88,50 → 68,70]`; вершины прямоугольника; крайние точки овала `12,50`, `88,50`, `50,20`, `50,80`; луна и капля проходят через свои опорные точки; все контуры внутри 0…100; отрезок дуги и кривой не длиннее 2 единиц.
  - `listPolylines` растягивает сетку на рамку независимо по осям (`preserveAspectRatio="none"`).
  - `isShapeTouched`: ластик по контуру задевает, внутри пустой фигуры нет. `findShapeAt` то же, из двух задетых возвращает последнюю.
  - `eraseAlong` снимает штрихи и фигуры одним шагом, отмена возвращает их на прежние места.
  - Отмена `ShapeAdded`, `ShapeChanged` и на другой стороне показывает ту сторону. `changeShape` без изменения шага не даёт.
- [ ] **Шаг 2. Реализация.** Разбор `svg` в чистом Kotlin: `<path d>`, `<rect>`, `<ellipse>`, `<circle>`; команды `M m L l H h V v C c A a Z z` с неявным повтором (`M88 50 68 70`, `m50 10 12 27 29 3-22 20`). Дуга по преобразованию из спецификации SVG. Ломаные считаются один раз на вид. Сторонних библиотек нет.
- [ ] **Шаг 3.** `Draft` и `CardContent` на `CardSide`; вызовы в остальных модулях приводятся механически, файлы пока пишут только штрихи.
- [ ] **Шаг 4.** GREEN всего `./gradlew build`, правка раздела `domain` в `docs/architecture.md`, коммит: `fix: #comment Фигуры на стороне карточки: модель, контуры и шаги отмены`.

### Задача 4: хранение

**Файлы:** `data/.../strokes/StrokeFileFormat.kt`, `StrokeFiles.kt`, `repository/RoomDraftRepository.kt`, `repository/FileCardContentRepository.kt`, `transfer/DeckFile.kt`, `transfer/JsonDeckTransfer.kt` и их тесты.

**Интерфейсы:**
- Даёт: `writeStrokeFile(output, front: CardSide, back: CardSide)`, `readStrokeFile(input): Pair<CardSide, CardSide>`. Файл колоды версии 2: `CardDto.frontShapes` и `backShapes`, `ShapeDto(kind: String, left, top, right, bottom, width)`, в `kind` хранится `Shape.Kind.key`.

Формат: сигнатура `DKS1` прежняя, версия 2. После штрихов каждой стороны `int32` число фигур, фигура: `int32 code`, `float32 left, top, right, bottom, width`. Пишется всегда версия 2, читаются обе.

- [ ] **Шаг 1. Тесты (RED).**
  - `readsVersionOneFileWithoutShapes`: на фиксированных байтах файла версии 1, не через `writeStrokeFile`. Там же штрихи читаются без изменений.
  - Круговая запись с фигурами на обеих сторонах. Черновик и карточка переживают запись.
  - `StrokeFileException`: неизвестный `code`, `left >= right`, NaN, обрыв посреди фигуры, число фигур больше остатка, версия 3.
  - Импорт колоды версии 1 из фиксированной строки JSON: фигур нет, остальное прежнее. Экспорт и импорт с фигурами сохраняют всё. Неизвестный `kind` даёт `DAMAGED`, `version: 3` даёт `UNSUPPORTED_VERSION`.
- [ ] **Шаг 2. Реализация.** Проверка `DECK_FILE_VERSION` принимает 1 и 2.
- [ ] **Шаг 3.** GREEN, правка форматов в `docs/architecture.md`, в ТЗ описание стороны карточки: штрихи и фигуры (вид, рамка, толщина контура). Коммит: `fix: #comment Файл штрихов и файл колоды версии 2: фигуры, старые файлы читаются`.

### Задача 5: `ink`

**Файлы:** `ink/.../StrokeRenderer.kt`, `CardGeometry.kt`, `SheetInput.kt`, `DrawingSheet.kt`, `CardSideImage.kt`, `CanvasDrawingView.kt`, `OnyxDrawingView.kt`; создать `ShapeGlyph.kt`; тесты.

**Интерфейсы:**
- Даёт:

```kotlin
fun drawCardSide(canvas: Canvas, side: CardSide, scale: Float)
const val CardGeometry.SHEET_WIDTH_DESIGN_UNITS = 928f
@Composable fun ShapeGlyph(kind: Shape.Kind, outlineDesignUnits: Int, modifier: Modifier)
@Composable fun CardSideImage(side: CardSide, modifier: Modifier, description: String?)
@Composable fun DrawingSheet(side: CardSide, penWidthPx: Float, isPenEnabled: Boolean, uiRevision: Int,
                             onStroke: …, onErase: …, onShapeTap: (Int) -> Unit, modifier: Modifier)
internal fun findTappedShape(points: List<RawPoint>, shapes: List<Shape>, scale: Float): Int?
```

Толщина контура постоянная: `shape.width * scale`, круглые концы и стыки, без формулы нажима и без зависимости от размера рамки. Касание: все точки в пределах 12 px листа от первой. Радиус попадания 32 единицы макета, в единицы карточки через `SHEET_WIDTH_DESIGN_UNITS`.

- [ ] **Шаг 1. Тесты (RED).**
  - `findTappedShape`: `tapOnOutlineSelectsShape`, `dotInsideShapeIsAStroke`, `longStrokeAcrossOutlineIsAStroke`, без фигур `null`.
  - Рисование: контур растянутой рамки той же толщины, что у нерастянутой (измерение по изображению); заливки нет, штрих внутри виден.
  - `CanvasDrawingView`: касание контура зовёт `onShapeTap`, штрих не добавляется ни в список, ни на лист; в режиме `isPenEnabled=false` ничего не зовётся.
  - `computeChangedBounds` учитывает фигуры: прямоугольник рамки плюс половина толщины.
- [ ] **Шаг 2. Реализация.** В `OnyxDrawingView` проверка касания идёт в `reportStroke` до `appendLocal`: след прошивки стирается `drawSurface` и `refreshRegion` вокруг касания. Изменение списка фигур от хозяина считается заменой: лист перерисован, область обновлена частично, при выключенном пере цикла нет. Шапка класса: что из этого не проверено на устройстве.
- [ ] **Шаг 3.** GREEN, раздел `ink` в `docs/architecture.md`, коммит: `fix: #comment Лист рисует фигуры, касание контура выделяет фигуру`.

### Задача 6: контракт и `design-e`

**Файлы:** `ui-contract/.../EditorContract.kt`, `Design.kt`; `design-e/.../EditorScreen.kt`, `Tokens.kt`, `ScreenTexts.kt`, `DesignE.kt`, создать `ShapePalette.kt`, `ShapeStage.kt`, `ShapeFrames.kt`; `design-e/build.gradle.kts` (вход `design/editor-shapes.js`), `MockupSources.kt`; тесты.

**Интерфейсы:**
- Даёт:

```kotlin
enum class ShapeKind { LINE, ARROW, DOUBLE_ARROW, RECTANGLE, OVAL, TRIANGLE, DIAMOND, HEXAGON, STAR, MOON, SUN, HEART, CLOUD, BOLT, DROP }
/** Рамка в долях листа, 0..1 по каждой оси. */
data class ShapeFrame(val left: Float, val top: Float, val right: Float, val bottom: Float)
sealed interface ShapeMode {
    data object WaitingForPlacement : ShapeMode
    data class Selected(val frame: ShapeFrame) : ShapeMode
}
// EditorScreenState: shapeKind: ShapeKind, outlineWidth: Int, isShapePaletteOpen: Boolean, shapeMode: ShapeMode?
// EditorAction: OpenShapePalette, CloseShapePalette, ChooseShape(kind), SetOutlineWidth(width: Int),
//               PlaceShape(frame), ChangeShapeFrame(frame), TapSheet(x: Float, y: Float), ApplyShape, CancelShape
// Design.EditorScreen(state, onAction, sheet, shapeGlyph: @Composable (ShapeKind, Int, Modifier) -> Unit)
```

- [ ] **Шаг 1. Геометрия (RED, затем GREEN).** В `ShapeFrames.kt` чистые `transformFrame(box, dx, dy, corner, sheetWidth, sheetHeight)` и `placeFrame(x, y, sheetWidth, sheetHeight)`: перенос `transformShape` и размещения из `editor-shapes.js`. Тесты: поле 32, минимум 80, начальный размер 220, центр на точке касания, прижатие к листу; углы меняют ширину и высоту независимо; сверка чисел с самим `editor-shapes.js` (`const margin = 32`, `const minimum = 80`, `point.x - 110`, `w: 220`).
- [ ] **Шаг 2. Токены.** Всё из блока фигур `styles.css`, сверка в `TokensTest`: `.shape-palette` (низ 160, лево 32, ширина 672, поля 24/32/28, рамка 2, скругление 12, промежуток 20), заголовок 28, `.shape-grid` (5 колонок, промежуток 8), `.shape-choice` (высота 104, рамка 2, скругление 8, значок 56), `.shape-width` (черта 1, отступ 24, колонки `1fr auto 80px`, промежуток 20, кегль 22), образец линии 80 × 24, ползунок (высота 64, отступ 8, дорожка 2, бегунок 32 с рамкой 3), `.shape-handle` (цель 64, знак 16, рамка 2, смещение −32), `.shape-object.is-selected` (пунктир 1), образец на панели 40.
- [ ] **Шаг 3. Панели (тесты и код).**
  - Обычная: группа толщин, значок `Shapes` шириной 112 (`.tools>.btn` идёт в файле позже `.btn.square`), отмена справа.
  - В режиме фигур: крест, образец вида, галочка справа. В ожидании размещения галочка выключена. Шапка и вкладки сторон видны и работают.
  - Подписи дословно: `Shapes`, `Close shape menu`, `Outline width`, `Cancel shape insertion or changes`, `Shape and outline width`, `Apply shape`, имена 15 видов, `Resize: top left corner` и другие.
- [ ] **Шаг 4. Палитра (тесты и код).** 15 видов в порядке `SHAPES`, текущий с рамкой. Прозрачный фон и крест закрывают, фон не принимает касаний. Выбор вида закрывает. Ползунок 1…12 с шагом 1 работает от пера и пальца, меняет значение сразу (число, образец, значки палитры). `shapeGlyph` приходит слотом.
- [ ] **Шаг 5. Жесты на листе (тесты и код).** `ShapeStage` лежит над слотом листа только при `shapeMode != null`. Обрабатывает только перо (`PointerType.Stylus`). Тесты подают `MotionEvent` с `TOOL_TYPE_STYLUS` и `TOOL_TYPE_FINGER`:
  - ожидание: касание даёт `PlaceShape`, движение того же касания двигает;
  - выделение: цель 64 у угла меняет размер, внутри рамки двигает, вне рамки `TapSheet`;
  - `fingerDoesNotMovePlaceOrResize`; перо с нажатой кнопкой не двигает;
  - отмена жеста возвращает рамку начала жеста.
  Рамка пунктиром и четыре знака рисует дизайн; контур фигуры рисует лист.
- [ ] **Шаг 6. Сверка глазами** всех досок `editor-*-shape*`, `editor-*-shapes` с Compose, как в задаче 2. Расхождения в отчёт.
- [ ] **Шаг 7.** GREEN `./gradlew :design-e:testDebugUnitTest --rerun`, раздел `design-e` в `docs/architecture.md`, коммит: `fix: #comment Панель, палитра и рамка фигур по макету`. Экран в приложении ещё не подключён: `app` передаёт слот и поля с начальными значениями.

### Задача 7: `EditorViewModel` и сборка экрана

**Файлы:** `app/.../editor/EditorViewModel.kt`, `EditorScreenMapper.kt`, `ui/SheetSlots.kt`, `ui/AppRoot.kt`, `deck/*` (сторона вместо списка штрихов); ТЗ, `README.md`, `docs/architecture.md`; тесты.

**Интерфейсы:**
- Потребляет: всё из задач 3, 5, 6.
- Даёт: `CanvasState(side: CardSide, penWidthPx, isPenEnabled, uiRevision)`; `EditorViewModel.onShapeTap(index: Int)`; `SheetSlots.ShapeGlyph(...)`, `EditorSheet(canvas, onStroke, onErase, onShapeTap, modifier)`, `DeckSheet(side, modifier)`. Перевод в `EditorScreenMapper`: `ShapeFrame ↔` единицы карточки, `outlineWidth ↔ Shape.width`.

Таблица состояний (в базу пишется только применённое):

| Состояние | Действие | Итог |
| --- | --- | --- |
| обычное, ожидание, выделение | `OpenShapePalette` | палитра открыта, перо выключено |
| палитра, нет фигуры в руках | `ChooseShape(k)` | ожидание размещения, вид `k` |
| палитра, фигура в руках | `ChooseShape(k)`, `SetOutlineWidth(w)` | фигура меняется сразу, палитру закрывает только выбор вида |
| палитра | `CloseShapePalette` | прежнее состояние |
| ожидание | `PlaceShape(f)` | новая фигура в руках, выделена |
| ожидание | `CancelShape`, `SelectSide`, `Save` | режим заканчивается; дальше действие как обычно |
| обычное | `onShapeTap(i)` | фигура `i` в руках, вид и контур берутся у неё |
| выделение | `ChangeShapeFrame(f)` | рамка меняется, записи нет |
| выделение | `ApplyShape` | один шаг отмены, запись черновика, обычное состояние |
| выделение | `CancelShape` | новая исчезает, старая возвращается |
| выделение | `TapSheet(x, y)` по другой фигуре | текущая применяется, та выделяется; мимо: ничего |
| выделение | `SelectSide`, `Save` | применить, затем действие |
| ожидание, выделение | `Back`, `Cancel`, `Delete` | обычное окно; «Keep editing» возвращает в тот же режим, «Save and exit» при выделении применяет и сохраняет |
| режим фигур | `Undo`, `SelectThickness` | ничего |

`isPenEnabled` выключено при палитре и режиме фигур; `UiSignature` учитывает палитру и вид режима. Системная «Назад» при открытой палитре закрывает её.

- [ ] **Шаг 1. Тесты (RED).** По строке на каждую строку таблицы, а также:
  - `savingWithSelectedShapeAppliesItFirst`, `switchingSideWithSelectedShapeAppliesItFirst`, `backDialogKeepEditingKeepsSelection`;
  - `penReturnsAfterApply`, `penReturnsAfterCancel`, `penReturnsAfterPaletteClose`, `penIsOffWhilePlacing`;
  - `killWhileSelectedReopensWithAppliedContent` (новый `EditorViewModel` на том же хранилище);
  - `firstApplyStartsDraft`; `undoRemovesAppliedShape`; `erasingShapeIsUndoable`;
  - маппер: все 15 видов туда и обратно, контур 1…12 без потери при округлении, рамка в долях и единицах;
  - `AppRootTest`: значок открывает палитру, карточка с фигурой показывается в обучении и просмотре (`DeckSheet` получает фигуры).
- [ ] **Шаг 2. Реализация.** Фигура в руках показывается на листе через `listVisibleContent(held)`.
- [ ] **Шаг 3. Документы.**
  - ТЗ, «Создание и редактирование»: убрать «ни фигур», добавить раздел о фигурах: 15 контуров без заливки, контур 1…12 единиц макета отдельно от пера, размещение, рамка, применение и отмена, отмена шага, ластик. Строки выбора из раздела «Решения за нами». Добавить приёмочные проверки: фигура с контуром 12, растянутая во весь лист, переживает убийство приложения; старая карточка открывается и сохраняется; точка в фигуре остаётся точкой.
  - `README.md`: фигуры в разделе о новых карточках.
  - `docs/architecture.md`: поток «фигура от касания до файла», таблица «проверено только на планшете».
- [ ] **Шаг 4.** GREEN `./gradlew build`, коммит: `fix: #comment Фигуры при создании и правке карточки`.

### Задача 8: Sonar

- [ ] Отдельный проход, ничего параллельно. Контейнеры подняты перед запуском и остановлены сразу после него при любом результате. В дереве правок вне коммитов быть не должно; если они появились, запускать из worktree. Запуск: `python3 <скилл>/scripts/sonar_check.py --base b87e678`. Цель: `SONAR: GREEN`. `design/` в анализ не входит: скрипт берёт только исходники модулей.
- [ ] Отчёт: число итераций, покрытие нового кода, что непокрыто и почему.

### Задача 9: планшет

Ждёт, пока владелец подключит планшет и планировщик это передаст. Выполняется вместе с задачей 7 плана 4.1, её список остаётся в силе. К нему добавляется:

- [ ] английские тексты на всех экранах; счётчики `41 / 100` и `3 / 4` без сдвига черты; цифра эпохи; `Deckly` при запуске;
- [ ] старые карточки на планшете открываются, правятся, сохраняются;
- [ ] перо в режиме фигур приходит как `TOOL_TYPE_STYLUS`; касание ставит фигуру, тянется центр и угол, палец не мешает;
- [ ] скорость и следы при перетаскивании: какой режим обновления нужен, не надо ли реже обновлять;
- [ ] след касания контура стирается; после применения и отмены перо сразу пишет; штрих внутри фигуры пишется;
- [ ] стирание фигуры кнопкой, отмена возвращает; контур 12 и 1 одинаковы на листе и в просмотре; изломов на дугах растянутой фигуры не видно;
- [ ] итоги в `docs/superpowers/specs/2026-11-05-first-build-results.md`, раздел владельца не трогать. Коммит: `fix: #comment Первая рабочая сборка: проверка на планшете`. Уборка: сначала список.
