package app.deckly.editor

import app.deckly.DayClock
import app.deckly.SerialWriter
import app.deckly.domain.editor.Draft
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Side
import app.deckly.domain.model.StrokePoint
import app.deckly.domain.port.StoredDraft
import app.deckly.fakes.FakeCardContentRepository
import app.deckly.fakes.FakeDeckRepository
import app.deckly.fakes.FakeDraftRepository
import app.deckly.fakes.FakeOutlineWidthStore
import app.deckly.fixtures.buildCard
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import app.deckly.fixtures.buildStroke
import app.deckly.ink.CardGeometry
import app.deckly.navigation.AppScreen
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.ShapeHandleKind
import app.deckly.ui.contract.ShapeKind
import app.deckly.ui.contract.Thickness
import java.time.LocalDateTime
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Фигуры в `EditorViewModel`: палитра, фигура в руках, жесты, применение и отмена. Строки таблицы состояний плана 4.3
 * (задача 6), по тесту на строку. Пишется только применённое: фигура в руках на диск не идёт.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelShapesTest {
    private val scheduler = TestCoroutineScheduler()
    private val now = LocalDateTime.of(2026, 11, 4, 12, 0)
    private val navigated = mutableListOf<AppScreen>()
    private val contents = FakeCardContentRepository()
    private val drafts = FakeDraftRepository()
    private val outlineWidths = FakeOutlineWidthStore()
    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: EditorViewModel

    // Единица макета в единицах карточки: размеры вставленной фигуры заданы в единицах макета.
    private val unit = CardUnits.PER_DESIGN_UNIT

    // Две фигуры, уже стоящие на лице, далеко от центра листа, где встаёт новая, и друг от друга.
    private val rectangle = Shape(Shape.Kind.RECTANGLE, 100f, 100f, 300f, 250f, 0f, 3f * unit)
    private val oval = Shape(Shape.Kind.OVAL, 600f, 100f, 800f, 250f, 0f, 3f * unit)

    // Левая кромка прямоугольника на середине высоты и левая точка овала: в ленте выбора своей фигуры.
    private val onRectangle = 128f to 175f
    private val onOval = 624f to 175f

    // Точка листа, где нет ни одной фигуры.
    private val emptySpot = 900f to 900f

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        repository = FakeDeckRepository(buildPoolState())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- палитра -------------------------------------------------------------------------------

    @Test
    fun openingPaletteShowsItAndSwitchesPenOff() {
        startNewCard()

        viewModel.onAction(EditorAction.OpenShapePalette)
        runUntilIdle()

        assertTrue(shown.isShapePaletteOpen)
        assertNull(shown.selectedShape)
        assertFalse(viewModel.canvas.value.isPenEnabled)
        assertTrue("палитра ничего не пишет на диск", drafts.storedCalls.isEmpty())
    }

    @Test
    fun paletteShowsTheRememberedOutlineWidth() {
        outlineWidths.width = 8
        startNewCard()

        assertEquals(8, shown.paletteOutlineWidth)
    }

    @Test
    fun settingOutlineWidthRemembersItAndShowsIt() {
        startNewCard()
        viewModel.onAction(EditorAction.OpenShapePalette)

        viewModel.onAction(EditorAction.SetOutlineWidth(9))
        viewModel.onAction(EditorAction.SetOutlineWidth(5))
        runUntilIdle()

        assertEquals(listOf(9, 5), outlineWidths.writes)
        assertEquals(5, shown.paletteOutlineWidth)
        assertTrue(shown.isShapePaletteOpen)
    }

    @Test
    fun outlineWidthOutsideTheSliderIsClamped() {
        startNewCard()
        viewModel.onAction(EditorAction.OpenShapePalette)

        viewModel.onAction(EditorAction.SetOutlineWidth(40))
        viewModel.onAction(EditorAction.SetOutlineWidth(0))
        runUntilIdle()

        assertEquals(listOf(12, 1), outlineWidths.writes)
    }

    @Test
    fun outlineWidthDoesNotChangeShapesThatAreAlreadyPlaced() {
        startWithShapes(rectangle)
        viewModel.onAction(EditorAction.OpenShapePalette)

        viewModel.onAction(EditorAction.SetOutlineWidth(11))
        viewModel.onAction(EditorAction.CloseShapePalette)
        runUntilIdle()

        assertEquals(listOf(rectangle), viewModel.canvas.value.side.shapes)
    }

    @Test
    fun choosingShapeClosesPaletteAndHoldsNewShapeAtCenterWithRememberedOutline() {
        outlineWidths.width = 7
        startNewCard()

        chooseShape(ShapeKind.OVAL)

        assertFalse(shown.isShapePaletteOpen)
        val selected = checkNotNull(shown.selectedShape)
        assertEquals(ShapeKind.OVAL, selected.kind)
        assertEquals(7, selected.outlineWidth)
        // Рамка 220 × 220 единиц макета по центру листа 928 × 963.
        assertEquals(354f * unit, selected.frame.left, 0.01f)
        assertEquals(371.5f * unit, selected.frame.top, 0.01f)
        assertEquals(574f * unit, selected.frame.right, 0.01f)
        assertEquals(591.5f * unit, selected.frame.bottom, 0.01f)
        assertEquals(0f, selected.frame.angle, 0f)
        assertFalse(selected.frame.isLinear)
        assertTrue("новая фигура на листе поверх", viewModel.canvas.value.side.shapes.single().kind == Shape.Kind.OVAL)
        assertTrue("в руках, не в черновике: на диск не идёт", drafts.storedCalls.isEmpty())
    }

    @Test
    fun insertedLinearShapeIs220By80() {
        startNewCard()

        chooseShape(ShapeKind.ARROW)

        val frame = checkNotNull(shown.selectedShape).frame
        assertTrue(frame.isLinear)
        assertEquals(220f * unit, frame.right - frame.left, 0.01f)
        assertEquals(80f * unit, frame.bottom - frame.top, 0.01f)
    }

    @Test
    fun closingPaletteReturnsToOrdinaryState() {
        startNewCard()
        viewModel.onAction(EditorAction.OpenShapePalette)

        viewModel.onAction(EditorAction.CloseShapePalette)
        runUntilIdle()

        assertFalse(shown.isShapePaletteOpen)
        assertNull(shown.selectedShape)
        assertTrue("penReturnsAfterPaletteClose", viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun systemBackClosesThePalette() {
        startNewCard()
        viewModel.onAction(EditorAction.OpenShapePalette)

        viewModel.onAction(EditorAction.Back)
        runUntilIdle()

        assertFalse(shown.isShapePaletteOpen)
        assertNull("окна нет: «Назад» закрыла только палитру", shown.dialog)
    }

    @Test
    fun paletteIgnoresEverythingButItsOwnActions() {
        startWithShapes(rectangle)
        viewModel.onAction(EditorAction.OpenShapePalette)
        runUntilIdle()
        val before = shown

        viewModel.onAction(EditorAction.Undo)
        viewModel.onAction(EditorAction.SelectThickness(Thickness.THICK))
        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        viewModel.onAction(EditorAction.Save)
        viewModel.onAction(EditorAction.ApplyShape)
        viewModel.onAction(EditorAction.CancelShape)
        runUntilIdle()

        assertEquals(before, shown)
        assertTrue(drafts.storedCalls.isEmpty())
        assertTrue(drafts.commits.isEmpty())
    }

    // --- выбор размещённой фигуры --------------------------------------------------------------

    @Test
    fun tapSelectsPlacedShapeAndHoldsItAsItWas() {
        startWithShapes(rectangle, oval)

        viewModel.onShapeTap(1)
        runUntilIdle()

        val selected = checkNotNull(shown.selectedShape)
        assertEquals(ShapeKind.OVAL, selected.kind)
        assertEquals(oval.left, selected.frame.left, 0f)
        assertEquals(oval.bottom, selected.frame.bottom, 0f)
        assertEquals(3, selected.outlineWidth)
        assertFalse(viewModel.canvas.value.isPenEnabled)
        assertEquals("лист показывает те же фигуры", listOf(rectangle, oval), viewModel.canvas.value.side.shapes)
        assertTrue("выбор ничего не пишет", drafts.storedCalls.isEmpty())
    }

    @Test
    fun tapIsPublishedWithoutTheWriterSoThePenIsOffAtOnce() {
        // Писатель стоит: его планировщик не двигается. Выбор не пишет, поэтому публикуется сразу, а цикл пера в
        // `OnyxDrawingView` (150 мс) должен застать запрет.
        val writerScheduler = TestCoroutineScheduler()
        startWithShapes(rectangle, writerScheduler = writerScheduler)

        viewModel.onShapeTap(0)
        runUntilIdle()

        assertFalse(viewModel.canvas.value.isPenEnabled)
        assertNotNull(shown.selectedShape)
    }

    @Test
    fun tapOnUnknownShapeOrWhileAnythingIsOpenIsIgnored() {
        startWithShapes(rectangle)
        viewModel.onShapeTap(3)
        viewModel.onShapeTap(-1)
        runUntilIdle()
        assertNull(shown.selectedShape)
        assertTrue(viewModel.canvas.value.isPenEnabled)

        viewModel.onAction(EditorAction.Back)
        viewModel.onShapeTap(0)
        runUntilIdle()
        assertNull("под окном выбирать нечего", shown.selectedShape)
        viewModel.onAction(EditorAction.DismissDialog)

        viewModel.onAction(EditorAction.OpenShapePalette)
        viewModel.onShapeTap(0)
        runUntilIdle()
        assertNull("под палитрой тоже", shown.selectedShape)
    }

    @Test
    fun tapWhileAnotherShapeIsHeldIsIgnored() {
        startWithShapes(rectangle, oval)
        selectPlaced(0)

        viewModel.onShapeTap(1)
        runUntilIdle()

        assertEquals(ShapeKind.RECTANGLE, shown.selectedShape?.kind)
    }

    // --- жесты ---------------------------------------------------------------------------------

    @Test
    fun cornerHandleResizesAndWritesNothing() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val before = checkNotNull(shown.selectedShape).frame

        viewModel.onAction(EditorAction.StartShapeGesture(ShapeHandleKind.BOTTOM_RIGHT, before.right, before.bottom))
        viewModel.onAction(EditorAction.MoveShapeGesture(before.right + 40f * unit, before.bottom + 20f * unit))
        runUntilIdle()

        val after = checkNotNull(shown.selectedShape).frame
        assertEquals(before.left, after.left, 0.01f)
        assertEquals(before.top, after.top, 0.01f)
        assertEquals(before.right + 40f * unit, after.right, 0.05f)
        assertEquals(before.bottom + 20f * unit, after.bottom, 0.05f)
        assertTrue("движение не пишет на диск", drafts.storedCalls.isEmpty())
        assertEquals("лист видит новый размер", after.right, viewModel.canvas.value.side.shapes.single().right, 0f)
    }

    @Test
    fun rotateHandleTurnsTheFrame() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val frame = checkNotNull(shown.selectedShape).frame
        val centerX = (frame.left + frame.right) / 2
        val centerY = (frame.top + frame.bottom) / 2

        // Указатель ушёл с точки над центром на точку справа от него: четверть оборота по часовой стрелке.
        viewModel.onAction(EditorAction.StartShapeGesture(ShapeHandleKind.ROTATE, centerX, centerY - 150f * unit))
        viewModel.onAction(EditorAction.MoveShapeGesture(centerX + 150f * unit, centerY))
        runUntilIdle()

        assertEquals(90f, checkNotNull(shown.selectedShape).frame.angle, 0.01f)
    }

    @Test
    fun endpointHandleStretchesAnArrow() {
        startNewCard()
        chooseShape(ShapeKind.ARROW)
        val before = checkNotNull(shown.selectedShape).frame
        val centerY = (before.top + before.bottom) / 2

        viewModel.onAction(EditorAction.StartShapeGesture(ShapeHandleKind.END, before.left + 0.88f * (before.right - before.left), centerY))
        viewModel.onAction(EditorAction.MoveShapeGesture(700f * unit, centerY))
        runUntilIdle()

        val after = checkNotNull(shown.selectedShape).frame
        assertTrue(after.right - after.left > before.right - before.left)
        assertEquals(0f, after.angle, 0.01f)
    }

    @Test
    fun contourOfSelectedShapeMovesIt() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val before = checkNotNull(shown.selectedShape).frame
        // Левая кромка прямоугольника на середине высоты: `14 %` рамки от её левого края.
        val edgeX = before.left + 0.14f * (before.right - before.left)
        val middleY = (before.top + before.bottom) / 2

        viewModel.onAction(EditorAction.StartShapeGesture(null, edgeX, middleY))
        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 50f * unit, middleY + 30f * unit))
        runUntilIdle()

        val after = checkNotNull(shown.selectedShape).frame
        assertEquals(before.left + 50f * unit, after.left, 0.05f)
        assertEquals(before.top + 30f * unit, after.top, 0.05f)
        assertEquals(before.right - before.left, after.right - after.left, 0.05f)
    }

    @Test
    fun contourOfAnotherShapeAppliesTheCurrentOneSelectsThatOneAndMovesItWithTheSameTouch() {
        startWithShapes(rectangle)
        chooseShape(ShapeKind.OVAL)
        assertTrue(drafts.storedCalls.isEmpty())

        viewModel.onAction(EditorAction.StartShapeGesture(null, onRectangle.first, onRectangle.second))
        viewModel.onAction(EditorAction.MoveShapeGesture(onRectangle.first + 20f, onRectangle.second + 10f))
        runUntilIdle()

        // Прежняя применена: фигура на листе и шаг отмены; выделена другая, и жест её уже двигает.
        assertEquals(listOf(Shape.Kind.RECTANGLE, Shape.Kind.OVAL), drafts.storedCalls.single().draft.front.shapes.map { it.kind })
        val selected = checkNotNull(shown.selectedShape)
        assertEquals(ShapeKind.RECTANGLE, selected.kind)
        assertEquals(rectangle.left + 20f, selected.frame.left, 0.05f)
        assertEquals(rectangle.top + 10f, selected.frame.top, 0.05f)
        assertTrue(shown.canUndo)
        assertFalse(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun touchAwayFromEverythingAppliesTheShapeAndLeavesSelection() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)

        viewModel.onAction(EditorAction.StartShapeGesture(null, emptySpot.first, emptySpot.second))
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertEquals(listOf(Shape.Kind.OVAL), drafts.storedCalls.single().draft.front.shapes.map { it.kind })
        assertTrue(shown.canUndo)
    }

    @Test
    fun moveCancelAndEndWithoutGestureChangeNothing() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val before = shown

        viewModel.onAction(EditorAction.MoveShapeGesture(500f, 500f))
        viewModel.onAction(EditorAction.EndShapeGesture)
        viewModel.onAction(EditorAction.CancelShapeGesture)
        runUntilIdle()

        assertEquals(before, shown)
        assertTrue(drafts.storedCalls.isEmpty())
    }

    @Test
    fun gestureEventsInOrdinaryStateChangeNothing() {
        startWithShapes(rectangle)
        val before = shown
        val revision = viewModel.canvas.value.uiRevision

        viewModel.onAction(EditorAction.StartShapeGesture(null, onRectangle.first, onRectangle.second))
        viewModel.onAction(EditorAction.MoveShapeGesture(500f, 500f))
        viewModel.onAction(EditorAction.EndShapeGesture)
        viewModel.onAction(EditorAction.CancelShapeGesture)
        viewModel.onAction(EditorAction.ApplyShape)
        viewModel.onAction(EditorAction.CancelShape)
        runUntilIdle()

        assertEquals(before, shown)
        assertEquals(revision, viewModel.canvas.value.uiRevision)
        assertTrue(drafts.storedCalls.isEmpty())
    }

    @Test
    fun moveAfterTheLiftChangesNothing() {
        // Подъём закончил линию: поздний `Move` без жеста (например, от оборванного слоя жестов) ничего не меняет.
        startWithShapes(rectangle)
        selectPlaced(0)
        touchAway()
        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()
        val applied = viewModel.canvas.value.side
        val before = shown

        viewModel.onAction(EditorAction.MoveShapeGesture(onRectangle.first + 50f, onRectangle.second + 50f))
        runUntilIdle()

        assertEquals(applied, viewModel.canvas.value.side)
        assertEquals(before, shown)
    }

    // --- касание мимо: линия с первой точки ----------------------------------------------------------

    @Test
    fun touchAwayAppliesTheShapeAndShowsNothingWhileThePenStaysInsideTheRadius() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)

        touchAway(pressure = 0.6f)
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 3f, emptySpot.second + 2f, 0.7f))
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertTrue(shown.isCapturingStroke)
        val side = viewModel.canvas.value.side
        assertEquals(listOf(Shape.Kind.OVAL), side.shapes.map { it.kind })
        assertTrue("пока перо не вышло за радиус, на листе ничего", side.strokes.isEmpty())
        assertFalse("перо выключено, пока линия собирается", viewModel.canvas.value.isPenEnabled)
        val written = drafts.storedCalls.single().draft.front
        assertEquals(listOf(Shape.Kind.OVAL), written.shapes.map { it.kind })
        assertTrue("линия на диск ещё не идёт", written.strokes.isEmpty())
    }

    @Test
    fun leavingTheRadiusShowsTheWholeLineFromTheFirstPoint() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway(pressure = 0.6f)
        val first = StrokePoint(emptySpot.first, emptySpot.second, 0.6f)
        val inside = StrokePoint(emptySpot.first + 2f, emptySpot.second, 0.7f)
        val outside = StrokePoint(emptySpot.first + 10f, emptySpot.second, 0.8f)

        viewModel.onAction(EditorAction.MoveShapeGesture(inside.x, inside.y, inside.pressure))
        runUntilIdle()
        assertTrue(viewModel.canvas.value.side.strokes.isEmpty())
        viewModel.onAction(EditorAction.MoveShapeGesture(outside.x, outside.y, outside.pressure))
        runUntilIdle()

        val pieces = viewModel.canvas.value.side.strokes
        assertEquals("линия с первой точки, точки внутри радиуса тоже", listOf(listOf(first, inside), listOf(inside, outside)), pieces.map { it.points })
    }

    @Test
    fun movesAfterTouchAwayExtendTheLineByPiecesOfTwoPointsWithTheirOwnPressure() {
        // Линия на листе идёт отрезками из двух точек: растущий штрих менялся бы целиком (`Replaced`, область всего штриха),
        // а так лист каждый раз только дорисовывает новый кусок (`Appended`). Нажим у каждой точки свой, толщина одна.
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway(pressure = 0.6f)
        val first = StrokePoint(emptySpot.first, emptySpot.second, 0.6f)
        val second = StrokePoint(emptySpot.first + 10f, emptySpot.second + 5f, 0.8f)
        val third = StrokePoint(emptySpot.first + 20f, emptySpot.second + 15f, 0.3f)

        viewModel.onAction(EditorAction.MoveShapeGesture(second.x, second.y, second.pressure))
        runUntilIdle()
        val afterFirstMove = viewModel.canvas.value.side.strokes
        viewModel.onAction(EditorAction.MoveShapeGesture(third.x, third.y, third.pressure))
        runUntilIdle()
        val afterSecondMove = viewModel.canvas.value.side.strokes

        assertEquals(listOf(listOf(first, second)), afterFirstMove.map { it.points })
        assertEquals(listOf(listOf(first, second), listOf(second, third)), afterSecondMove.map { it.points })
        assertEquals("прежние куски остаются как были", afterFirstMove, afterSecondMove.take(afterFirstMove.size))
        assertEquals(1, afterSecondMove.map { it.width }.distinct().size)
        assertEquals("пишется только применение фигуры", 1, drafts.storedCalls.size)
        assertTrue(shown.isCapturingStroke)
    }

    @Test
    fun liftWritesTheWholeLineAsOneStrokeAndReturnsThePen() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway(pressure = 0.6f)
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 10f, emptySpot.second + 5f, 0.8f))
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 20f, emptySpot.second + 15f, 0.3f))

        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()

        assertEquals(2, drafts.storedCalls.size)
        val written = drafts.storedCalls.last().draft.front
        val stroke = written.strokes.single()
        assertEquals(listOf(0.6f, 0.8f, 0.3f), stroke.points.map { it.pressure })
        assertEquals(listOf(emptySpot.first, emptySpot.first + 10f, emptySpot.first + 20f), stroke.points.map { it.x })
        assertEquals(listOf(Shape.Kind.OVAL), written.shapes.map { it.kind })
        assertFalse(shown.isCapturingStroke)
        assertTrue(viewModel.canvas.value.isPenEnabled)
        assertEquals(listOf(stroke), viewModel.canvas.value.side.strokes)
    }

    @Test
    fun theLineAndTheAppliedShapeAreSeparateUndoSteps() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway()
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 30f, emptySpot.second))
        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()

        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()
        assertTrue("первая отмена снимает линию", viewModel.canvas.value.side.strokes.isEmpty())
        assertEquals(listOf(Shape.Kind.OVAL), viewModel.canvas.value.side.shapes.map { it.kind })

        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()
        assertTrue("вторая отмена снимает фигуру", viewModel.canvas.value.side.shapes.isEmpty())
    }

    @Test
    fun lineWidthIsThePenWidthOfTheThicknessInCardUnitsOfTheSheetScale() {
        for (thickness in Thickness.entries) {
            startNewCard()
            viewModel.onAction(EditorAction.SelectThickness(thickness))
            chooseShape(ShapeKind.OVAL)

            touchAway(scale = 2f)
            viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 20f, emptySpot.second))
            runUntilIdle()

            // Ту же ширину в единицах карточки даёт лист обычному штриху: `buildStroke` переводит `penWidthPx` через `CardGeometry`.
            assertEquals("$thickness", CardGeometry.toCardUnits(thickness.toPenWidthPx(), 2f), viewModel.canvas.value.side.strokes.first().width, 0.0001f)
        }
    }

    @Test
    fun cancelledTouchAwayDropsTheLineAndKeepsTheAppliedShape() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway()
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 30f, emptySpot.second))

        viewModel.onAction(EditorAction.CancelShapeGesture)
        runUntilIdle()

        assertTrue(viewModel.canvas.value.side.strokes.isEmpty())
        assertEquals(listOf(Shape.Kind.OVAL), viewModel.canvas.value.side.shapes.map { it.kind })
        assertEquals("записана только фигура", 1, drafts.storedCalls.size)
        assertFalse(shown.isCapturingStroke)
        assertTrue(viewModel.canvas.value.isPenEnabled)
        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()
        assertTrue("шаг один: фигура", viewModel.canvas.value.side.shapes.isEmpty())
    }

    @Test
    fun touchAwayWithoutMovementOnlyReleasesTheSelectionAndLeavesNoStrokeAndNoStep() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway(pressure = 0.5f)

        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()

        assertTrue(viewModel.canvas.value.side.strokes.isEmpty())
        assertEquals("записана только фигура", 1, drafts.storedCalls.size)
        assertTrue(drafts.storedCalls.single().draft.front.strokes.isEmpty())
        assertFalse(shown.isCapturingStroke)
        assertTrue(viewModel.canvas.value.isPenEnabled)
        assertNull(shown.selectedShape)
        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()
        assertTrue("шаг один, это применение фигуры", viewModel.canvas.value.side.shapes.isEmpty())
    }

    @Test
    fun liftInsideTheRadiusAfterSmallMovesLeavesNoStrokeEither() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway()
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 3f, emptySpot.second - 2f))
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first - 4f, emptySpot.second + 1f))

        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()

        assertTrue(viewModel.canvas.value.side.strokes.isEmpty())
        assertEquals(1, drafts.storedCalls.size)
        assertTrue(viewModel.canvas.value.isPenEnabled)
        assertFalse(shown.isCapturingStroke)
    }

    @Test
    fun theRadiusIsTwelvePixelsOfTheSheetInCardUnitsAndTheEdgeStillIsInside() {
        // 12 px листа от первой точки, как у выбора фигуры касанием: при масштабе 2 это 6 единиц карточки.
        for ((scale, outside) in listOf(1f to 12.01f, 2f to 6.01f, 4f to 3.01f)) {
            val radius = 12f / scale
            for ((distance, isLine) in listOf(radius to false, outside to true)) {
                startNewCard()
                chooseShape(ShapeKind.OVAL)
                touchAway(scale = scale)

                viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + distance, emptySpot.second))
                runUntilIdle()

                assertEquals("масштаб $scale, расстояние $distance", isLine, viewModel.canvas.value.side.strokes.isNotEmpty())
            }
        }
    }

    @Test
    fun returningInsideTheRadiusAfterLeavingKeepsTheLine() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway(pressure = 0.6f)
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 30f, emptySpot.second, 0.7f))
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 1f, emptySpot.second, 0.5f))
        runUntilIdle()
        assertEquals("линия на листе идёт и после возврата", emptySpot.first + 1f, viewModel.canvas.value.side.strokes.last().points.last().x, 0.001f)

        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()

        assertEquals(listOf(0.6f, 0.7f, 0.5f), drafts.storedCalls.last().draft.front.strokes.single().points.map { it.pressure })
    }

    @Test
    fun everythingButTheGestureIsIgnoredWhileTheLineIsCollected() {
        // Палец или ладонь на кнопках во время письма пером: ни «Back», ни «Save», ни «Undo» линию не обрывают.
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        touchAway()
        val before = viewModel.canvas.value.side

        for (action in listOf(
            EditorAction.Undo,
            EditorAction.SelectThickness(Thickness.THICK),
            EditorAction.SelectSide(CardSideShown.BACK),
            EditorAction.OpenShapePalette,
            EditorAction.Save,
            EditorAction.Back,
            EditorAction.Cancel,
            EditorAction.ApplyShape,
            EditorAction.CancelShape,
        )) {
            viewModel.onAction(action)
        }
        runUntilIdle()

        assertEquals(before, viewModel.canvas.value.side)
        assertTrue(shown.isCapturingStroke)
        assertFalse(shown.isShapePaletteOpen)
        assertNull(shown.dialog)
        assertEquals(Thickness.MEDIUM, shown.thickness)
        assertEquals(CardSideShown.FRONT, shown.side)
        assertEquals(1, drafts.storedCalls.size)
        assertTrue(navigated.isEmpty())
    }

    @Test
    fun tapOnAPlacedShapeWhileTheLineIsCollectedIsIgnored() {
        startWithShapes(rectangle)
        selectPlaced(0)
        touchAway()

        viewModel.onShapeTap(0)
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertTrue(shown.isCapturingStroke)
    }

    @Test
    fun revisionGrowsOnceWhenTheLineStartsAndNotWhileItGrows() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        val revision = viewModel.canvas.value.uiRevision

        touchAway()
        val atStart = viewModel.canvas.value.uiRevision
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 10f, emptySpot.second))
        viewModel.onAction(EditorAction.MoveShapeGesture(emptySpot.first + 20f, emptySpot.second))
        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()

        assertEquals("панель фигуры сменилась обычной", revision + 1, atStart)
        assertEquals(atStart, viewModel.canvas.value.uiRevision)
    }

    @Test
    fun cancelledGestureReturnsTheShapeToWhereItWasAtTheStartOfTheGesture() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val first = checkNotNull(shown.selectedShape).frame
        val edgeX = first.left + 0.14f * (first.right - first.left)
        val middleY = (first.top + first.bottom) / 2
        // Первый жест довёл фигуру до новой позиции и закончился.
        viewModel.onAction(EditorAction.StartShapeGesture(null, edgeX, middleY))
        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 30f * unit, middleY))
        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()
        val settled = checkNotNull(shown.selectedShape).frame
        assertEquals(first.left + 30f * unit, settled.left, 0.05f)

        // Второй жест отменён системой: фигура как в начале второго, а не как в начале первого.
        viewModel.onAction(EditorAction.StartShapeGesture(null, edgeX + 30f * unit, middleY))
        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 200f * unit, middleY + 100f * unit))
        viewModel.onAction(EditorAction.CancelShapeGesture)
        runUntilIdle()

        assertEquals(settled, shown.selectedShape?.frame)
    }

    @Test
    fun gestureDoesNotSurviveApplyAndReselection() {
        startWithShapes(rectangle)
        selectPlaced(0)
        viewModel.onAction(EditorAction.StartShapeGesture(null, onRectangle.first, onRectangle.second))
        // Галочка нажата, не дожидаясь конца жеста; фигуру выбрали снова.
        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()
        selectPlaced(0)

        viewModel.onAction(EditorAction.MoveShapeGesture(onRectangle.first + 80f, onRectangle.second + 80f))
        runUntilIdle()

        assertEquals(rectangle.left, checkNotNull(shown.selectedShape).frame.left, 0f)
    }

    @Test
    fun gestureDoesNotContinueUnderOrAfterADialog() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val frame = checkNotNull(shown.selectedShape).frame
        val edgeX = frame.left + 0.14f * (frame.right - frame.left)
        val middleY = (frame.top + frame.bottom) / 2
        viewModel.onAction(EditorAction.StartShapeGesture(null, edgeX, middleY))
        viewModel.onAction(EditorAction.Back)
        viewModel.onAction(EditorAction.DismissDialog)

        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 90f, middleY + 90f))
        runUntilIdle()

        assertEquals(frame, shown.selectedShape?.frame)
    }

    @Test
    fun repeatedSameOutlineWidthIsNotWrittenAgain() {
        startNewCard()
        viewModel.onAction(EditorAction.OpenShapePalette)
        viewModel.onAction(EditorAction.SetOutlineWidth(6))
        runUntilIdle()
        val revision = viewModel.canvas.value.uiRevision

        viewModel.onAction(EditorAction.SetOutlineWidth(6))
        viewModel.onAction(EditorAction.SetOutlineWidth(6))
        runUntilIdle()

        assertEquals(listOf(6), outlineWidths.writes)
        assertEquals(revision, viewModel.canvas.value.uiRevision)
    }

    @Test
    fun moveAfterCancelledGestureMovesNothing() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val before = checkNotNull(shown.selectedShape).frame
        val edgeX = before.left + 0.14f * (before.right - before.left)
        val middleY = (before.top + before.bottom) / 2
        viewModel.onAction(EditorAction.StartShapeGesture(null, edgeX, middleY))
        viewModel.onAction(EditorAction.CancelShapeGesture)

        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 90f, middleY + 90f))
        runUntilIdle()

        assertEquals(before, shown.selectedShape?.frame)
    }

    @Test
    fun endedGestureKeepsTheShapeWhereItIs() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val before = checkNotNull(shown.selectedShape).frame
        val edgeX = before.left + 0.14f * (before.right - before.left)
        val middleY = (before.top + before.bottom) / 2

        viewModel.onAction(EditorAction.StartShapeGesture(null, edgeX, middleY))
        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 30f * unit, middleY))
        viewModel.onAction(EditorAction.EndShapeGesture)
        // Движение после конца жеста уже ничего не двигает.
        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 300f * unit, middleY))
        runUntilIdle()

        assertEquals(before.left + 30f * unit, checkNotNull(shown.selectedShape).frame.left, 0.05f)
    }

    // --- применение и отмена -------------------------------------------------------------------

    @Test
    fun applyingShapeIsOneUndoStepOneDraftWriteAndReturnsThePen() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        assertFalse(viewModel.canvas.value.isPenEnabled)

        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertTrue("penReturnsAfterApply", viewModel.canvas.value.isPenEnabled)
        assertTrue(shown.canUndo)
        assertEquals(1, drafts.storedCalls.size)
        assertEquals(listOf(Shape.Kind.OVAL), drafts.storedCalls.single().draft.front.shapes.map { it.kind })
        assertEquals(1, viewModel.canvas.value.side.shapes.size)
    }

    @Test
    fun firstApplyStartsTheDraft() {
        startNewCard()
        chooseShape(ShapeKind.STAR)
        // До применения черновика нет: выбор вида, палитра и жесты его не создают.
        assertTrue(drafts.storedCalls.isEmpty())

        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()

        assertEquals(1, drafts.storedCalls.size)
        assertEquals(DECK_ID, drafts.storedCalls.single().deckId)
    }

    @Test
    fun appliedShapeKeepsItsMovedPosition() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val before = checkNotNull(shown.selectedShape).frame
        val edgeX = before.left + 0.14f * (before.right - before.left)
        val middleY = (before.top + before.bottom) / 2
        viewModel.onAction(EditorAction.StartShapeGesture(null, edgeX, middleY))
        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 40f * unit, middleY))
        viewModel.onAction(EditorAction.EndShapeGesture)

        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()

        assertEquals(before.left + 40f * unit, drafts.storedCalls.single().draft.front.shapes.single().left, 0.05f)
    }

    @Test
    fun undoRemovesAppliedShape() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()

        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()

        assertTrue(viewModel.canvas.value.side.shapes.isEmpty())
        assertFalse(shown.canUndo)
        assertTrue(drafts.storedCalls.last().draft.front.shapes.isEmpty())
    }

    @Test
    fun cancellingNewShapeMakesItDisappearWithoutStepOrWrite() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)

        viewModel.onAction(EditorAction.CancelShape)
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertTrue("penReturnsAfterCancel", viewModel.canvas.value.isPenEnabled)
        assertTrue(viewModel.canvas.value.side.shapes.isEmpty())
        assertFalse(shown.canUndo)
        assertTrue(drafts.storedCalls.isEmpty())
    }

    @Test
    fun cancellingEditOfPlacedShapeRestoresItAsItWasWithoutStepOrWrite() {
        startWithShapes(rectangle, oval)
        selectPlaced(0)
        viewModel.onAction(EditorAction.StartShapeGesture(null, onRectangle.first, onRectangle.second))
        viewModel.onAction(EditorAction.MoveShapeGesture(onRectangle.first + 60f, onRectangle.second + 60f))
        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()
        assertTrue("в руках она уже сдвинута", checkNotNull(shown.selectedShape).frame.left > rectangle.left)

        viewModel.onAction(EditorAction.CancelShape)
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertEquals(listOf(rectangle, oval), viewModel.canvas.value.side.shapes)
        assertFalse(shown.canUndo)
        assertTrue(drafts.storedCalls.isEmpty())
        assertTrue(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun applyingPlacedShapeThatWasNotChangedMakesNoStepAndNoWrite() {
        startWithShapes(rectangle)
        selectPlaced(0)

        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertFalse(shown.canUndo)
        assertTrue(drafts.storedCalls.isEmpty())
        assertTrue(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun applyingEditOfPlacedShapeReplacesItInPlaceAsOneStep() {
        startWithShapes(rectangle, oval)
        selectPlaced(0)
        viewModel.onAction(EditorAction.StartShapeGesture(null, onRectangle.first, onRectangle.second))
        viewModel.onAction(EditorAction.MoveShapeGesture(onRectangle.first + 60f, onRectangle.second))
        viewModel.onAction(EditorAction.EndShapeGesture)
        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()

        val stored = drafts.storedCalls.single().draft.front.shapes
        assertEquals(listOf(Shape.Kind.RECTANGLE, Shape.Kind.OVAL), stored.map { it.kind })
        assertEquals(rectangle.left + 60f, stored[0].left, 0.05f)
        assertEquals(oval, stored[1])

        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()

        assertEquals(listOf(rectangle, oval), viewModel.canvas.value.side.shapes)
    }

    @Test
    fun erasingShapeIsUndoable() {
        startWithShapes(rectangle, oval)

        viewModel.onErase(listOf(StrokePoint(onRectangle.first, onRectangle.second, 1f)), 5f)
        runUntilIdle()
        assertEquals(listOf(oval), viewModel.canvas.value.side.shapes)

        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()

        assertEquals(listOf(rectangle, oval), viewModel.canvas.value.side.shapes)
    }

    @Test
    fun undoAndThicknessAreIgnoredWhileShapeIsHeld() {
        startNewCard()
        viewModel.onStroke(buildStroke(1f to 1f, 5f to 5f))
        runUntilIdle()
        chooseShape(ShapeKind.OVAL)
        val before = shown
        val writes = drafts.storedCalls.size

        viewModel.onAction(EditorAction.Undo)
        viewModel.onAction(EditorAction.SelectThickness(Thickness.THICK))
        runUntilIdle()

        assertEquals(before, shown)
        assertEquals("штрих на месте", 1, viewModel.canvas.value.side.strokes.size)
        assertEquals(writes, drafts.storedCalls.size)
    }

    @Test
    fun strokeThatArrivesWhileShapeIsHeldIsKeptAndTheShapeStaysInHand() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)

        viewModel.onStroke(buildStroke(1f to 1f, 5f to 5f))
        runUntilIdle()

        assertEquals(1, viewModel.canvas.value.side.strokes.size)
        assertNotNull(shown.selectedShape)
        assertFalse(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun eraseThatArrivesWhileShapeIsHeldIsIgnored() {
        // Стирание сняло бы фигуру из черновика из-под рук: её место (индекс) уже не её.
        startWithShapes(rectangle)
        selectPlaced(0)

        viewModel.onErase(listOf(StrokePoint(onRectangle.first, onRectangle.second, 1f)), 5f)
        runUntilIdle()

        assertEquals(listOf(rectangle), viewModel.canvas.value.side.shapes)
        assertNotNull(shown.selectedShape)
        assertTrue(drafts.storedCalls.isEmpty())
    }

    // --- действия, которые применяют фигуру первыми -------------------------------------------

    @Test
    fun openingPaletteAppliesSelectedShape() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)

        viewModel.onAction(EditorAction.OpenShapePalette)
        runUntilIdle()

        assertTrue(shown.isShapePaletteOpen)
        assertNull(shown.selectedShape)
        assertEquals(listOf(Shape.Kind.OVAL), drafts.storedCalls.single().draft.front.shapes.map { it.kind })
        assertTrue(shown.canUndo)
        assertFalse(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun savingWithSelectedShapeAppliesItFirst() {
        startNewCard()
        chooseShape(ShapeKind.STAR)

        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        assertEquals(listOf(Shape.Kind.STAR), drafts.commits.single().second.front.shapes.map { it.kind })
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW, focusCardId = NEW_CARD_ID)), navigated)
    }

    @Test
    fun switchingSideWithSelectedShapeAppliesItFirst() {
        startNewCard()
        chooseShape(ShapeKind.STAR)

        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertEquals(CardSideShown.BACK, shown.side)
        val stored = drafts.storedCalls.last().draft
        assertEquals("фигура осталась на лице", listOf(Shape.Kind.STAR), stored.front.shapes.map { it.kind })
        assertEquals(Side.BACK, stored.visibleSide)
        assertTrue("на обороте листа её нет", viewModel.canvas.value.side.shapes.isEmpty())
        assertTrue(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun switchingToTheSameSideWithSelectedShapeStillShowsTheApplication() {
        startNewCard()
        chooseShape(ShapeKind.STAR)

        viewModel.onAction(EditorAction.SelectSide(CardSideShown.FRONT))
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertTrue(viewModel.canvas.value.isPenEnabled)
        assertEquals(1, viewModel.canvas.value.side.shapes.size)
    }

    // --- окна при выделенной фигуре ------------------------------------------------------------

    @Test
    fun backDialogKeepEditingKeepsSelection() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val before = checkNotNull(shown.selectedShape).frame
        val edgeX = before.left + 0.14f * (before.right - before.left)
        val middleY = (before.top + before.bottom) / 2
        viewModel.onAction(EditorAction.StartShapeGesture(null, edgeX, middleY))
        viewModel.onAction(EditorAction.MoveShapeGesture(edgeX + 40f * unit, middleY))
        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()
        val moved = checkNotNull(shown.selectedShape)

        viewModel.onAction(EditorAction.Back)
        runUntilIdle()
        assertEquals(EditorDialog.SAVE_ON_BACK, shown.dialog)
        assertEquals("под окном выделение на месте", moved, shown.selectedShape)
        assertFalse(viewModel.canvas.value.isPenEnabled)
        assertTrue("окно ничего не применяет", drafts.storedCalls.isEmpty())

        viewModel.onAction(EditorAction.DismissDialog)
        runUntilIdle()

        assertNull(shown.dialog)
        assertEquals("«Keep editing» возвращает к выделению", moved, shown.selectedShape)
        assertFalse(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun saveAndExitFromBackDialogAppliesTheShapeAndSaves() {
        startNewCard()
        chooseShape(ShapeKind.STAR)
        viewModel.onAction(EditorAction.Back)

        viewModel.onAction(EditorAction.ConfirmDialog)
        runUntilIdle()

        assertEquals(listOf(Shape.Kind.STAR), drafts.commits.single().second.front.shapes.map { it.kind })
        assertEquals(1, navigated.size)
    }

    @Test
    fun cancelAndDeleteOpenTheirDialogsWithShapeInHandAndDiscardDropsIt() {
        startNewCard()
        chooseShape(ShapeKind.STAR)

        viewModel.onAction(EditorAction.Cancel)
        runUntilIdle()
        assertEquals(EditorDialog.DISCARD_DRAFT, shown.dialog)
        assertNotNull(shown.selectedShape)
        viewModel.onAction(EditorAction.DismissDialog)
        runUntilIdle()
        assertNotNull("«Keep editing»", shown.selectedShape)

        viewModel.onAction(EditorAction.Cancel)
        viewModel.onAction(EditorAction.ConfirmDialog)
        runUntilIdle()

        assertEquals(1, drafts.discardCount)
        assertTrue("неприменённая фигура не сохраняется", drafts.commits.isEmpty())
    }

    @Test
    fun deleteOfEditedCardWithShapeInHandOpensItsDialog() {
        drafts.stored = StoredDraft(DECK_ID, Draft(2, CardSide(), CardSide(), Side.FRONT, Section.NEW))
        startResume()
        runUntilIdle()
        chooseShape(ShapeKind.STAR)

        viewModel.onAction(EditorAction.Delete)
        runUntilIdle()

        assertEquals(EditorDialog.DELETE_CARD, shown.dialog)
        assertNotNull(shown.selectedShape)
    }

    // --- перо ----------------------------------------------------------------------------------

    @Test
    fun penIsOffWhileShapeIsSelectedOrPaletteIsOpen() {
        startNewCard()
        assertTrue(viewModel.canvas.value.isPenEnabled)

        viewModel.onAction(EditorAction.OpenShapePalette)
        runUntilIdle()
        assertFalse(viewModel.canvas.value.isPenEnabled)

        viewModel.onAction(EditorAction.ChooseShape(ShapeKind.OVAL))
        runUntilIdle()
        assertFalse("penIsOffWhileSelected", viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun uiRevisionGrowsWhenPaletteOrSelectionAppearOrGo() {
        startNewCard()
        val initial = viewModel.canvas.value.uiRevision

        viewModel.onAction(EditorAction.OpenShapePalette)
        runUntilIdle()
        assertEquals(initial + 1, viewModel.canvas.value.uiRevision)

        viewModel.onAction(EditorAction.ChooseShape(ShapeKind.OVAL))
        runUntilIdle()
        // Палитра ушла, выделение появилось: вне холста изменилось всё видимое, но подпись сменилась в одном кадре.
        assertEquals(initial + 2, viewModel.canvas.value.uiRevision)

        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()
        assertTrue(viewModel.canvas.value.uiRevision > initial + 2)
    }

    @Test
    fun selectionByTapAndItsCancelGrowTheRevisionByThemselves() {
        // Только выделение и больше ничего не изменилось: палитры нет, истории нет, сторона та же.
        startWithShapes(rectangle)
        val initial = viewModel.canvas.value.uiRevision

        selectPlaced(0)
        assertEquals(initial + 1, viewModel.canvas.value.uiRevision)

        viewModel.onAction(EditorAction.CancelShape)
        runUntilIdle()
        assertEquals(initial + 2, viewModel.canvas.value.uiRevision)
    }

    @Test
    fun movingShapeDoesNotGrowTheRevision() {
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val frame = checkNotNull(shown.selectedShape).frame
        viewModel.onAction(EditorAction.StartShapeGesture(ShapeHandleKind.BOTTOM_RIGHT, frame.right, frame.bottom))
        runUntilIdle()
        val revision = viewModel.canvas.value.uiRevision

        viewModel.onAction(EditorAction.MoveShapeGesture(frame.right + 10f, frame.bottom + 10f))
        viewModel.onAction(EditorAction.MoveShapeGesture(frame.right + 20f, frame.bottom + 20f))
        runUntilIdle()

        assertEquals(revision, viewModel.canvas.value.uiRevision)
    }

    // --- перезапуск и запомненная толщина ------------------------------------------------------

    @Test
    fun killWhileSelectedReopensWithAppliedContent() {
        startNewCard()
        chooseShape(ShapeKind.OVAL)
        viewModel.onAction(EditorAction.ApplyShape)
        chooseShape(ShapeKind.STAR)
        runUntilIdle()
        assertNotNull("вторая фигура в руках, не применена", shown.selectedShape)

        // Процесс убит: новый редактор на том же хранилище читает черновик.
        startResume()
        runUntilIdle()

        assertNull(shown.selectedShape)
        assertTrue(viewModel.canvas.value.isPenEnabled)
        assertEquals(listOf(Shape.Kind.OVAL), viewModel.canvas.value.side.shapes.map { it.kind })
    }

    @Test
    fun appliedShapeWithOutline12StretchedAndRotatedSurvivesKill() {
        outlineWidths.width = 12
        startNewCard()
        chooseShape(ShapeKind.RECTANGLE)
        val inserted = checkNotNull(shown.selectedShape).frame
        // Растянуть за правый нижний угол.
        viewModel.onAction(EditorAction.StartShapeGesture(ShapeHandleKind.BOTTOM_RIGHT, inserted.right, inserted.bottom))
        viewModel.onAction(EditorAction.MoveShapeGesture(inserted.right + 60f * unit, inserted.bottom + 30f * unit))
        viewModel.onAction(EditorAction.EndShapeGesture)
        runUntilIdle()
        val stretched = checkNotNull(shown.selectedShape).frame
        val centerX = (stretched.left + stretched.right) / 2
        val centerY = (stretched.top + stretched.bottom) / 2
        // Повернуть на 45°: указатель ушёл с точки над центром на точку справа-сверху от него.
        viewModel.onAction(EditorAction.StartShapeGesture(ShapeHandleKind.ROTATE, centerX, centerY - 150f * unit))
        viewModel.onAction(EditorAction.MoveShapeGesture(centerX + 100f * unit, centerY - 100f * unit))
        viewModel.onAction(EditorAction.EndShapeGesture)
        viewModel.onAction(EditorAction.ApplyShape)
        runUntilIdle()
        val applied = drafts.storedCalls.last().draft.front.shapes.single()
        assertEquals(12f * unit, applied.width, 1e-3f)
        assertEquals(45f, applied.angle, 0.01f)
        assertEquals(280f * unit, applied.right - applied.left, 0.05f)

        // Процесс убит: новый редактор открывает ту же фигуру без потерь.
        startResume()
        runUntilIdle()

        assertEquals(listOf(applied), viewModel.canvas.value.side.shapes)
    }

    @Test
    fun editingCardWithShapesKeepsThemOnSave() {
        val content = app.deckly.domain.model.CardContent(2, CardSide(shapes = listOf(rectangle)), CardSide(shapes = listOf(oval)))
        viewModel = EditorViewModel(
            deckId = DECK_ID,
            start = EditorStart.EditCard(2, Section.NEW),
            decks = repository,
            contents = FakeCardContentRepository(mapOf(2L to content)),
            drafts = drafts,
            writer = SerialWriter(CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))),
            clock = DayClock { now },
            random = Random(1),
            nowMillis = { NOW_MILLIS },
            outlineWidths = outlineWidths,
            navigate = { navigated += it },
        )
        runUntilIdle()
        assertEquals(listOf(rectangle), viewModel.canvas.value.side.shapes)

        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        val saved = drafts.commits.single().second
        assertEquals(listOf(rectangle), saved.front.shapes)
        assertEquals(listOf(oval), saved.back.shapes)
    }

    @Test
    fun rememberedOutlineSurvivesNewEditor() {
        startNewCard()
        viewModel.onAction(EditorAction.OpenShapePalette)
        viewModel.onAction(EditorAction.SetOutlineWidth(10))
        runUntilIdle()

        startNewCard()
        assertEquals(10, shown.paletteOutlineWidth)
        chooseShape(ShapeKind.OVAL)

        assertEquals(10, checkNotNull(shown.selectedShape).outlineWidth)
    }

    @Test
    fun resumedDraftWithShapesShowsThemOnTheSheet() {
        startWithShapes(rectangle, oval)

        assertEquals(listOf(rectangle, oval), viewModel.canvas.value.side.shapes)
        assertTrue(viewModel.canvas.value.isPenEnabled)
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun buildPoolState(): DeckState {
        val pool = (1L..3L).map { buildCard(it, Section.NEW, position = it.toInt() - 1) }
        return buildDeckState(buildDeck(id = DECK_ID, activeSection = Section.NEW, currentEpoch = 1), pool)
    }

    private fun startNewCard(writerScheduler: TestCoroutineScheduler = scheduler) {
        startWith(EditorStart.NewCard(Section.NEW), writerScheduler)
        runUntilIdle()
    }

    private fun startResume() {
        startWith(EditorStart.ResumeDraft)
    }

    // Черновик новой карточки с фигурами на лице: фигуры стоят и уже в базе (черновик уже есть).
    private fun startWithShapes(vararg shapes: Shape, writerScheduler: TestCoroutineScheduler = scheduler) {
        drafts.stored = StoredDraft(DECK_ID, Draft(null, CardSide(shapes = shapes.toList()), CardSide(), Side.FRONT, Section.NEW))
        startWith(EditorStart.ResumeDraft, writerScheduler)
        runUntilIdle()
    }

    private fun startWith(start: EditorStart, writerScheduler: TestCoroutineScheduler = scheduler) {
        viewModel = EditorViewModel(
            deckId = DECK_ID,
            start = start,
            decks = repository,
            contents = contents,
            drafts = drafts,
            writer = SerialWriter(CoroutineScope(SupervisorJob() + StandardTestDispatcher(writerScheduler))),
            clock = DayClock { now },
            random = Random(1),
            nowMillis = { NOW_MILLIS },
            outlineWidths = outlineWidths,
            navigate = { navigated += it },
        )
    }

    // Предусловие всех тестов с фигурой в руках: она в руках. Без него «отмена» и «мимо» прошли бы вхолостую.
    private fun chooseShape(kind: ShapeKind) {
        viewModel.onAction(EditorAction.OpenShapePalette)
        viewModel.onAction(EditorAction.ChooseShape(kind))
        runUntilIdle()
        assertEquals("выбран вид $kind", kind, checkNotNull(shown.selectedShape) { "фигура не в руках" }.kind)
    }

    // Касание пером мимо всех фигур при фигуре в руках: фигура применяется, линия начинается с этой точки.
    private fun touchAway(pressure: Float = 0.6f, scale: Float = 1.74f) {
        viewModel.onAction(EditorAction.StartShapeGesture(null, emptySpot.first, emptySpot.second, pressure, scale))
        runUntilIdle()
    }

    // То же для выбора размещённой фигуры касанием: после него она в руках.
    private fun selectPlaced(index: Int) {
        viewModel.onShapeTap(index)
        runUntilIdle()
        assertNotNull("фигура $index не выбрана", shown.selectedShape)
    }

    private val shown: EditorScreenState get() = checkNotNull(viewModel.state.value) { "Редактор ещё не загружен" }

    private fun runUntilIdle() = scheduler.advanceUntilIdle()

    private companion object {
        const val DECK_ID = 7L
        const val NEW_CARD_ID = 100L
        const val NOW_MILLIS = 1_794_000_000_000L
    }
}
