package app.deckly.design.e

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.SelectedShape
import app.deckly.ui.contract.ShapeFrame
import app.deckly.ui.contract.ShapeHandleKind
import app.deckly.ui.contract.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** Панели с фигурой и без, рамка с ручками над листом и жесты пером (`.shape-edit-tools`, `.shape-handle`, `.shape-rotate`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeEditorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val host by lazy { EditorHost(composeRule) }
    private val actions get() = host.actions

    // Лист стоит в (32, 200) шириной 928: 0,928 пикселя кадра на единицу карточки.
    private val scale = 928f / 1000f
    private val rectangle = SelectedShape(
        ShapeKind.RECTANGLE, ShapeFrame(left = 200f, top = 300f, right = 600f, bottom = 500f, angle = 0f, isLinear = false), outlineWidth = 5,
    )
    private val line = SelectedShape(
        ShapeKind.ARROW, ShapeFrame(left = 100f, top = 400f, right = 900f, bottom = 480f, angle = 0f, isLinear = true), outlineWidth = 4,
    )

    // --- обычная панель ------------------------------------------------------------------------

    @Test
    fun normalToolbarHasShapesButtonBetweenThicknessGroupAndUndo() {
        host.show(buildEditorState())

        val group = composeRule.onNodeWithContentDescription("Pen width").findBounds()
        val shapes = composeRule.onNodeWithContentDescription("Shapes").findBounds()
        val undo = composeRule.onNodeWithContentDescription("Undo stroke").findBounds()

        // Квадрат 112 × 112 на нижней строке, после группы толщин через зазор 16, до отмены, которая осталась справа.
        assertEquals(112f, shapes.right.value - shapes.left.value, 0.5f)
        assertEquals(112f, shapes.bottom.value - shapes.top.value, 0.5f)
        assertEquals(undo.top.value, shapes.top.value, 0.5f)
        assertEquals(group.right.value + 16f, shapes.left.value, 0.5f)
        assertTrue(shapes.right.value <= undo.left.value)
        assertEquals(960f, undo.right.value, 0.5f)
        assertIconInButton("значок фигур", composeRule.onNodeWithContentDescription("Shapes"), IconId.SHAPES)
    }

    @Test
    fun shapesButtonOpensThePalette() {
        host.show(buildEditorState())

        composeRule.onNodeWithContentDescription("Shapes").performClick()

        assertEquals(listOf<EditorAction>(EditorAction.OpenShapePalette), actions)
    }

    @Test
    fun noFrameOrHandlesWithoutSelectedShape() {
        host.show(buildEditorState())

        for (description in listOf("Rotate shape", "Resize: top left corner", "Move start point", "Rectangle")) {
            composeRule.onNodeWithContentDescription(description).assertDoesNotExist()
        }
    }

    // --- панель фигуры -------------------------------------------------------------------------

    @Test
    fun selectedShapeReplacesTheNormalToolbar() {
        host.show(buildEditorState(selectedShape = rectangle))

        for (description in listOf("Pen width", "Thin line", "Undo stroke", "Shapes")) {
            composeRule.onNodeWithContentDescription(description).assertDoesNotExist()
        }
        for (description in listOf("Cancel shape insertion or changes", "Shape and outline width", "Apply shape")) {
            composeRule.onNodeWithContentDescription(description).assertExists()
        }
    }

    @Test
    fun shapeToolbarHasThreeSquaresCrossSampleAndApplyAtTheRightEdge() {
        host.show(buildEditorState(selectedShape = rectangle))

        val cross = composeRule.onNodeWithContentDescription("Cancel shape insertion or changes").findBounds()
        val sample = composeRule.onNodeWithContentDescription("Shape and outline width").findBounds()
        val apply = composeRule.onNodeWithContentDescription("Apply shape").findBounds()

        val top = 1323f - 32f - 112f
        for (box in listOf(cross, sample, apply)) {
            assertEquals(112f, box.right.value - box.left.value, 0.5f)
            assertEquals(112f, box.bottom.value - box.top.value, 0.5f)
            assertEquals(top, box.top.value, 0.5f)
        }
        assertEquals(32f, cross.left.value, 0.5f)
        assertEquals(cross.right.value + 16f, sample.left.value, 0.5f)
        assertEquals("галочка прижата вправо", 960f, apply.right.value, 0.5f)
    }

    @Test
    fun crossAndCheckCarryTheirIconsAndApplyIsPrimary() {
        host.show(buildEditorState(selectedShape = rectangle))

        assertIconInButton("отмена фигуры", composeRule.onNodeWithContentDescription("Cancel shape insertion or changes"), IconId.CROSS)
        // У кнопки «применить» в макете нет класса icon-only (`<button class="btn primary apply-shape">`), поэтому значок
        // обычный из `.icon`, 26, а не 32 как у кнопок без подписи (крест и образец).
        val plainIcon = MockupSources.findCssNumber(".icon", "width")
        assertEquals(26f, plainIcon, 0f)
        assertIconInButton("применить фигуру", composeRule.onNodeWithContentDescription("Apply shape"), IconId.CHECK, sizeDp = plainIcon)
        // Главное действие: рамка 3 px, у тихих кнопок рамки нет.
        assertEquals(3, measureLeftBorder(composeRule.onNodeWithContentDescription("Apply shape")))
        assertEquals(0, measureLeftBorder(composeRule.onNodeWithContentDescription("Cancel shape insertion or changes")))
        assertEquals(0, measureLeftBorder(composeRule.onNodeWithContentDescription("Shape and outline width")))
    }

    @Test
    fun sampleShowsTheKindOfTheSelectedShapeWithItsOutlineWidthAt40() {
        host.show(buildEditorState(selectedShape = rectangle))

        val glyph = composeRule.onNodeWithTag("glyph-RECTANGLE-5", useUnmergedTree = true)
        glyph.assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
        val sample = composeRule.onNodeWithContentDescription("Shape and outline width").findBounds()
        val box = glyph.findBounds()
        assertEquals((sample.left.value + sample.right.value) / 2f, (box.left.value + box.right.value) / 2f, 0.6f)
        assertEquals((sample.top.value + sample.bottom.value) / 2f, (box.top.value + box.bottom.value) / 2f, 0.6f)
    }

    @Test
    fun shapeToolbarButtonsSendTheirActions() {
        host.show(buildEditorState(selectedShape = rectangle))

        composeRule.onNodeWithContentDescription("Cancel shape insertion or changes").performClick()
        composeRule.onNodeWithContentDescription("Shape and outline width").performClick()
        composeRule.onNodeWithContentDescription("Apply shape").performClick()

        assertEquals(listOf<EditorAction>(EditorAction.CancelShape, EditorAction.OpenShapePalette, EditorAction.ApplyShape), actions)
    }

    @Test
    fun headerAndSideTabsKeepWorkingWithAShapeInHand() {
        host.show(buildEditorState(selectedShape = rectangle))

        composeRule.onNodeWithText("Answer").performClick()
        composeRule.onNodeWithText("Save").performClick()

        assertEquals(listOf<EditorAction>(EditorAction.SelectSide(CardSideShown.BACK), EditorAction.Save), actions)
    }

    // --- рамка и ручки -------------------------------------------------------------------------

    @Test
    fun frameAndHandleTargetsStandWhereTheMockupPutsThem() {
        host.show(buildEditorState(selectedShape = rectangle))

        val frame = composeRule.onNodeWithContentDescription("Rectangle").findBounds()
        // Лист в (32, 200); рамка в единицах карточки 200…600 × 300…500, пиксели округляются.
        assertEquals(32f + 200f * scale, frame.left.value, 1f)
        assertEquals(200f + 300f * scale, frame.top.value, 1f)
        assertEquals(400f * scale, frame.right.value - frame.left.value, 1f)
        assertEquals(200f * scale, frame.bottom.value - frame.top.value, 1f)
        val corners = mapOf(
            "Resize: top left corner" to (frame.left.value to frame.top.value),
            "Resize: top right corner" to (frame.right.value to frame.top.value),
            "Resize: bottom left corner" to (frame.left.value to frame.bottom.value),
            "Resize: bottom right corner" to (frame.right.value to frame.bottom.value),
        )
        for ((description, corner) in corners) {
            val target = composeRule.onNodeWithContentDescription(description).findBounds()
            assertEquals("$description: цель 64", 64f, target.right.value - target.left.value, 0.5f)
            assertEquals("$description: цель 64", 64f, target.bottom.value - target.top.value, 0.5f)
            assertEquals("$description: центр на углу", corner.first, findCenterX(target), 1f)
            assertEquals("$description: центр на углу", corner.second, findCenterY(target), 1f)
        }
        // Ручка поворота: цель верхом на 80 выше рамки, по середине её ширины.
        val rotate = composeRule.onNodeWithContentDescription("Rotate shape").findBounds()
        assertEquals(64f, rotate.right.value - rotate.left.value, 0.5f)
        assertEquals(frame.top.value - 80f, rotate.top.value, 1f)
        assertEquals(findCenterX(frame), findCenterX(rotate), 1f)
    }

    @Test
    fun plainFrameHasDashedOutlineAndSquareMarksAtTheCorners() {
        host.show(buildEditorState(selectedShape = rectangle))

        val bitmap = captureFrame()
        val frame = composeRule.onNodeWithContentDescription("Rectangle").findBounds()
        val left = frame.left.value.roundToInt()
        val top = frame.top.value.roundToInt()
        val right = frame.right.value.roundToInt()
        val bottom = frame.bottom.value.roundToInt()
        // Пунктир 1 снаружи рамки, как у Chrome: на строке над верхней кромкой штрихи по 3 через промежуток 2, отсчёт от угла
        // внешнего контура (на 1 левее рамки). Углы закрыты знаками 16, поэтому смотрим на середину стороны; внутри рамки
        // и ещё выше пусто.
        val row = ((left - 1)..right).map { bitmap.isInkAt(it, top - 1) }
        assertDashPattern("верхняя сторона", row)
        // Кроме середины, где от ручки поворота спускается чёрная черта.
        val xs = ((left + 40) until (right - 40)).filter { kotlin.math.abs(it - (left + right) / 2) > 3 }
        assertTrue(xs.none { bitmap.isInkAt(it, top) })
        assertTrue(xs.none { bitmap.isInkAt(it, top - 2) })
        // Левая сторона тем же рисунком сверху вниз.
        assertDashPattern("левая сторона", ((top - 1)..bottom).map { bitmap.isInkAt(left - 1, it) })
        // Нижняя и правая стороны: тоже с начала стороны, слева направо и сверху вниз.
        assertDashPattern("нижняя сторона", ((left - 1)..right).map { bitmap.isInkAt(it, bottom) })
        assertDashPattern("правая сторона", ((top - 1)..bottom).map { bitmap.isInkAt(right, it) })
        // Четыре квадрата 20 (16 и рамка 2 снаружи) с белой серединой на углах рамки.
        for ((cx, cy) in listOf(left to top, right to top, left to bottom, right to bottom)) {
            assertMark(bitmap, cx, cy, isCircle = false)
        }
    }

    @Test
    fun rotateHandleHasIconOnWhiteAndOneByTwelveLineBelowIt() {
        host.show(buildEditorState(selectedShape = rectangle))

        val bitmap = captureFrame()
        val rotate = composeRule.onNodeWithContentDescription("Rotate shape").findBounds()
        val frame = composeRule.onNodeWithContentDescription("Rectangle").findBounds()
        val cx = findCenterX(rotate).roundToInt()
        val cy = findCenterY(rotate).roundToInt()
        // Значок 24 × 24 в центре цели и чёрная черта 1 × 16 от низа цели до верха рамки (`top:64px; left:31px`).
        val icon = measureInkBox(bitmap, (cx - 12) until (cx + 12), (cy - 12) until (cy + 12))
        assertNotNull("значка поворота нет", icon)
        assertTrue("значок не больше 24: $icon", icon!!.width <= 24f && icon.height <= 24f && icon.width >= 12f && icon.height >= 12f)
        val lineTop = rotate.bottom.value.roundToInt()
        val lineX = (rotate.left.value + 31f).roundToInt()
        assertEquals(16, (lineTop until lineTop + 16).count { bitmap.isInkAt(lineX, it) })
        assertTrue("черта в один пиксель", !bitmap.isInkAt(lineX - 1, lineTop + 8) && !bitmap.isInkAt(lineX + 1, lineTop + 8))
        // Над чертой белая цель, ниже черта упирается в верх рамки: она кончается там, где начинается рамка.
        assertTrue(!bitmap.isInkAt(lineX, lineTop - 1))
        assertEquals(frame.top.value, (lineTop + 16).toFloat(), 1f)
    }

    @Test
    fun linearShapeHasTwoRoundMarksAtTheEndsOfTheAxisAndNoDashedFrame() {
        host.show(buildEditorState(selectedShape = line))

        val bitmap = captureFrame()
        val frame = composeRule.onNodeWithContentDescription("Arrow").findBounds()
        val start = composeRule.onNodeWithContentDescription("Move start point").findBounds()
        val end = composeRule.onNodeWithContentDescription("Move end point").findBounds()

        // Концы на 12 и 88 % ширины рамки, на середине её высоты; цель 64.
        assertEquals(frame.left.value + 0.12f * (frame.right.value - frame.left.value), findCenterX(start), 1f)
        assertEquals(frame.left.value + 0.88f * (frame.right.value - frame.left.value), findCenterX(end), 1f)
        assertEquals((frame.top.value + frame.bottom.value) / 2f, findCenterY(start), 1f)
        assertEquals(64f, start.right.value - start.left.value, 0.5f)
        composeRule.onNodeWithContentDescription("Rotate shape").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Resize: top left corner").assertDoesNotExist()
        for (target in listOf(start, end)) assertMark(bitmap, findCenterX(target).roundToInt(), findCenterY(target).roundToInt(), isCircle = true)
        // Пунктира нет: ни выше, ни ниже оси между концами.
        val xs = (findCenterX(start).roundToInt() + 30) until (findCenterX(end).roundToInt() - 30)
        assertTrue(xs.none { bitmap.isInkAt(it, frame.top.value.roundToInt() - 1) })
        assertTrue(xs.none { bitmap.isInkAt(it, frame.bottom.value.roundToInt()) })
    }

    @Test
    fun rotatedFrameTurnsHandlesAroundItsCenter() {
        host.show(buildEditorState(selectedShape = rectangle.copy(frame = rectangle.frame.copy(angle = 90f))))

        val frame = composeRule.onNodeWithContentDescription("Rectangle").findBounds()
        val frameCenterX = findCenterX(frame)
        val frameCenterY = findCenterY(frame)
        val width = 400f * scale
        val height = 200f * scale
        // Неповёрнутая рамка: верхний левый угол слева-сверху, ручка поворота над рамкой на 48 выше верха. После поворота
        // на 90° по часовой стрелке они уходят: угол вправо-вверх, ручка вправо от рамки.
        val topLeft = composeRule.onNodeWithContentDescription("Resize: top left corner").findBounds()
        val rotate = composeRule.onNodeWithContentDescription("Rotate shape").findBounds()
        assertEquals(frameCenterX + height / 2f, findCenterX(topLeft), 1.5f)
        assertEquals(frameCenterY - width / 2f, findCenterY(topLeft), 1.5f)
        assertEquals(frameCenterX + height / 2f + 48f, findCenterX(rotate), 1.5f)
        assertEquals(frameCenterY, findCenterY(rotate), 1.5f)
    }

    // --- жесты пером ---------------------------------------------------------------------------

    @Test
    fun penOnCornerHandleStartsAGestureWithThatHandleAndFollowsIt() {
        host.show(buildEditorState(selectedShape = rectangle))
        val corner = composeRule.onNodeWithContentDescription("Resize: top left corner").findBounds()
        val origin = findFrameOrigin()
        val x = origin.x + findCenterX(corner) + 6f
        val y = origin.y + findCenterY(corner) + 4f

        dispatchPen(listOf(x to y, (x + 30f) to (y + 10f), (x + 60f) to (y + 20f)))

        assertEquals(4, actions.size)
        val start = actions[0] as EditorAction.StartShapeGesture
        assertEquals(ShapeHandleKind.TOP_LEFT, start.handle)
        // Координаты уходят в единицах карточки: пиксель листа делится на 0,928.
        assertEquals((findCenterX(corner) + 6f - 32f) / scale, start.x, 0.7f)
        assertEquals((findCenterY(corner) + 4f - 200f) / scale, start.y, 0.7f)
        val firstMove = actions[1] as EditorAction.MoveShapeGesture
        assertEquals(start.x + 30f / scale, firstMove.x, 0.7f)
        assertEquals(start.y + 10f / scale, firstMove.y, 0.7f)
        val secondMove = actions[2] as EditorAction.MoveShapeGesture
        assertEquals(start.x + 60f / scale, secondMove.x, 0.7f)
        assertEquals(EditorAction.EndShapeGesture, actions[3])
    }

    @Test
    fun penOnEveryHandleReportsItsKind() {
        host.show(buildEditorState(selectedShape = rectangle))
        val origin = findFrameOrigin()
        val targets = mapOf(
            "Resize: top left corner" to ShapeHandleKind.TOP_LEFT,
            "Resize: top right corner" to ShapeHandleKind.TOP_RIGHT,
            "Resize: bottom left corner" to ShapeHandleKind.BOTTOM_LEFT,
            "Resize: bottom right corner" to ShapeHandleKind.BOTTOM_RIGHT,
            "Rotate shape" to ShapeHandleKind.ROTATE,
        )

        for ((description, kind) in targets) {
            actions.clear()
            val box = composeRule.onNodeWithContentDescription(description).findBounds()

            dispatchPen(listOf((origin.x + findCenterX(box)) to (origin.y + findCenterY(box))))

            assertEquals(description, kind, (actions.first() as EditorAction.StartShapeGesture).handle)
        }
    }

    @Test
    fun penOnLinearEndsReportsStartAndEnd() {
        host.show(buildEditorState(selectedShape = line))
        val origin = findFrameOrigin()

        for ((description, kind) in mapOf("Move start point" to ShapeHandleKind.START, "Move end point" to ShapeHandleKind.END)) {
            actions.clear()
            val box = composeRule.onNodeWithContentDescription(description).findBounds()

            dispatchPen(listOf((origin.x + findCenterX(box) + 5f) to (origin.y + findCenterY(box) - 5f)))

            assertEquals(description, kind, (actions.first() as EditorAction.StartShapeGesture).handle)
        }
    }

    @Test
    fun penAwayFromHandlesStartsAGestureWithoutHandle() {
        host.show(buildEditorState(selectedShape = rectangle))
        val frame = composeRule.onNodeWithContentDescription("Rectangle").findBounds()
        val origin = findFrameOrigin()

        dispatchPen(listOf((origin.x + findCenterX(frame)) to (origin.y + findCenterY(frame)), (origin.x + findCenterX(frame) + 40f) to (origin.y + findCenterY(frame))))

        val start = actions.first() as EditorAction.StartShapeGesture
        assertEquals(null, start.handle)
        assertEquals((findCenterX(frame) - 32f) / scale, start.x, 0.7f)
        assertEquals((findCenterY(frame) - 200f) / scale, start.y, 0.7f)
        assertEquals(EditorAction.EndShapeGesture, actions.last())
    }

    @Test
    fun cancelledGestureIsReportedAsCancelled() {
        host.show(buildEditorState(selectedShape = rectangle))
        val box = composeRule.onNodeWithContentDescription("Rotate shape").findBounds()
        val origin = findFrameOrigin()
        val x = origin.x + findCenterX(box)
        val y = origin.y + findCenterY(box)

        dispatchPen(listOf(x to y, (x + 20f) to y), isCancelled = true)

        assertEquals(EditorAction.CancelShapeGesture, actions.last())
        assertTrue(actions.none { it == EditorAction.EndShapeGesture })
    }

    // Подъём приходит без движения и может оказаться не там, где последнее движение. Положение подъёма должно дойти до
    // модели последним движением, иначе фигура остановится там, где было последнее движение.

    @Test
    fun liftPointFarFromTheLastMoveIsReportedAsTheLastMove() {
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        dispatchPen(listOf(x to y, (x + 30f) to (y + 10f)), liftAt = (x + 120f) to (y + 40f))

        val start = actions.first() as EditorAction.StartShapeGesture
        val moves = actions.filterIsInstance<EditorAction.MoveShapeGesture>()
        assertEquals(2, moves.size)
        assertEquals(start.x + 120f / scale, moves.last().x, 0.7f)
        assertEquals(start.y + 40f / scale, moves.last().y, 0.7f)
        assertEquals(EditorAction.EndShapeGesture, actions.last())
        assertEquals(actions.lastIndex - 1, actions.indexOf(moves.last()))
    }

    @Test
    fun liftPointWithoutAnyMoveBeforeItIsReportedAsAMove() {
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        dispatchPen(listOf(x to y), liftAt = (x + 80f) to y)

        val start = actions.first() as EditorAction.StartShapeGesture
        assertEquals(3, actions.size)
        assertEquals(start.x + 80f / scale, (actions[1] as EditorAction.MoveShapeGesture).x, 0.7f)
        assertEquals(EditorAction.EndShapeGesture, actions[2])
    }

    @Test
    fun liftAtTheLastMovePointAddsNoExtraMove() {
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        dispatchPen(listOf(x to y, (x + 30f) to (y + 10f)))

        assertEquals(listOf("Start", "Move", "End"), actions.map { it::class.simpleName!!.removeSuffix("ShapeGesture") })
    }

    @Test
    fun cancelledGestureReportsNoLiftPoint() {
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        dispatchPen(listOf(x to y, (x + 30f) to y), isCancelled = true, liftAt = (x + 150f) to y)

        assertEquals(1, actions.filterIsInstance<EditorAction.MoveShapeGesture>().size)
        assertEquals(EditorAction.CancelShapeGesture, actions.last())
    }

    @Test
    fun fingerAndPalmNeverStartAGesture() {
        host.show(buildEditorState(selectedShape = rectangle))
        val box = composeRule.onNodeWithContentDescription("Resize: top left corner").findBounds()
        val origin = findFrameOrigin()
        val x = origin.x + findCenterX(box)
        val y = origin.y + findCenterY(box)

        dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_FINGER, listOf(x to y, (x + 40f) to (y + 40f)))

        assertTrue(actions.isEmpty())
    }

    @Test
    fun penWithSideButtonNeverStartsAGesture() {
        host.show(buildEditorState(selectedShape = rectangle))
        val box = composeRule.onNodeWithContentDescription("Resize: top left corner").findBounds()
        val origin = findFrameOrigin()
        val x = origin.x + findCenterX(box)
        val y = origin.y + findCenterY(box)

        // Боковая кнопка стилуса стирает, а не двигает; кончик без неё (BUTTON_PRIMARY) двигает — это другие тесты.
        dispatchGesture(
            composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, listOf(x to y, (x + 40f) to (y + 40f)),
            buttons = MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_STYLUS_PRIMARY,
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun eraserToolNeverStartsAGesture() {
        host.show(buildEditorState(selectedShape = rectangle))
        val box = composeRule.onNodeWithContentDescription("Resize: top left corner").findBounds()
        val origin = findFrameOrigin()

        dispatchGesture(
            composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_ERASER,
            listOf((origin.x + findCenterX(box)) to (origin.y + findCenterY(box)), (origin.x + findCenterX(box) + 40f) to (origin.y + findCenterY(box))),
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun targetOfARotatedFrameIsASquareInTheAxesOfTheShape() {
        host.show(buildEditorState(selectedShape = rectangle.copy(frame = rectangle.frame.copy(angle = 45f))))
        val corner = composeRule.onNodeWithContentDescription("Resize: top left corner").findBounds()
        val origin = findFrameOrigin()
        val cx = origin.x + findCenterX(corner)
        val cy = origin.y + findCenterY(corner)

        // Сдвиг на 40 по горизонтали листа внутри цели, повёрнутой на 45° (28 и −28 в осях фигуры), хотя по осям листа 40 > 32.
        dispatchPen(listOf((cx + 40f) to cy))
        assertEquals(ShapeHandleKind.TOP_LEFT, (actions.first() as EditorAction.StartShapeGesture).handle)
        actions.clear()
        // Сдвиг на (30, 30) в осях фигуры (42; 0): за краем цели, хотя по осям листа 30 < 32.
        dispatchPen(listOf((cx + 30f) to (cy + 30f)))
        assertEquals(null, (actions.first() as EditorAction.StartShapeGesture).handle)
    }

    @Test
    fun gestureUsesTheFrameThatIsOnScreenNow() {
        host.show(buildEditorState(selectedShape = rectangle))
        val moved = rectangle.copy(frame = rectangle.frame.copy(left = 400f, right = 800f))
        host.show(buildEditorState(selectedShape = moved))
        val box = composeRule.onNodeWithContentDescription("Resize: top right corner").findBounds()
        val origin = findFrameOrigin()

        dispatchPen(listOf((origin.x + findCenterX(box)) to (origin.y + findCenterY(box))))

        // Рамка уехала вправо, и цель угла уехала вместе с ней: жест видит новую рамку, а не ту, с которой экран построен.
        assertEquals(ShapeHandleKind.TOP_RIGHT, (actions.first() as EditorAction.StartShapeGesture).handle)
    }

    @Test
    fun gestureSurvivesTheStateChangesItCausesItself() {
        host.show(buildEditorState(selectedShape = rectangle))
        val corner = composeRule.onNodeWithContentDescription("Resize: bottom right corner").findBounds()
        val origin = findFrameOrigin()
        val x = origin.x + findCenterX(corner)
        val y = origin.y + findCenterY(corner)
        val downTime = 1_000L
        fun send(action: Int, dx: Float) {
            composeRule.runOnUiThread { dispatchPointer(composeRule.activity, MotionEvent.TOOL_TYPE_STYLUS, action, x + dx, y, downTime) }
            composeRule.waitForIdle()
        }

        send(MotionEvent.ACTION_DOWN, 0f)
        send(MotionEvent.ACTION_MOVE, 20f)
        // Модель на каждое движение отдаёт новую рамку: экран перекомпонуется посреди жеста, а жест не обрывается.
        host.show(buildEditorState(selectedShape = rectangle.copy(frame = rectangle.frame.copy(right = 640f))))
        send(MotionEvent.ACTION_MOVE, 40f)
        host.show(buildEditorState(selectedShape = rectangle.copy(frame = rectangle.frame.copy(right = 680f))))
        send(MotionEvent.ACTION_UP, 40f)

        assertEquals(
            listOf("Start", "Move", "Move", "End"),
            actions.map {
                when (it) {
                    is EditorAction.StartShapeGesture -> "Start"
                    is EditorAction.MoveShapeGesture -> "Move"
                    EditorAction.EndShapeGesture -> "End"
                    else -> it.toString()
                }
            },
        )
    }

    // --- нажим, отсчёты и сбор линии при касании мимо ------------------------------------------------

    @Test
    fun startAndMovesCarryThePressureOfThePen() {
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        dispatchPen(listOf(x to y, (x + 30f) to y, (x + 60f) to y), pressures = listOf(0.4f, 0.7f, 0.2f))

        assertEquals(0.4f, (actions[0] as EditorAction.StartShapeGesture).pressure, 0.001f)
        assertEquals(listOf(0.7f, 0.2f), actions.filterIsInstance<EditorAction.MoveShapeGesture>().map { it.pressure })
    }

    @Test
    fun liftMoveCarriesTheLastPressureAndNotTheZeroOfTheLift() {
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        dispatchPen(listOf(x to y, (x + 30f) to y), liftAt = (x + 90f) to y, pressures = listOf(0.4f, 0.7f))

        assertEquals(listOf(0.7f, 0.7f), actions.filterIsInstance<EditorAction.MoveShapeGesture>().map { it.pressure })
    }

    @Test
    fun startReportsHowManyPixelsOfTheSheetAreInOneCardUnit() {
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        dispatchPen(listOf(x to y, (x + 30f) to y))

        assertEquals(scale, (actions[0] as EditorAction.StartShapeGesture).scale, 0.0001f)
    }

    @Test
    fun everySampleOfABatchGoesToTheModelWithItsOwnPressure() {
        // Система отдаёт отсчёты пера пачкой за кадр (`historical`); линии нужны все, иначе быстрая выйдет ломаной хордами.
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        dispatchGestureWithBatch(x, y)

        assertBatchGoesToTheModel(x, y)
    }

    @Test
    fun pressureOfTheBatchIsFoundForEveryGestureNotOnlyForTheFirstOfTheWindow() {
        // Номер указателя у Compose (`PointerId`) не равен номеру в `MotionEvent`: Compose считает касания с создания окна,
        // и совпадает с нулём в `MotionEvent` только первое. Со второго жеста поиск по `change.id` не находил указатель, и
        // все отсчёты пачки молча получали нажим текущего.
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")
        dispatchGestureWithBatch(x, y)
        dispatchGestureWithBatch(x, y)
        actions.clear()

        dispatchGestureWithBatch(x, y)

        assertBatchGoesToTheModel(x, y)
    }

    // Касание с нажимом 0,5, одно движение с двумя накопленными отсчётами и текущим (0,6 / 0,7 / 0,8), подъём.
    private fun dispatchGestureWithBatch(x: Float, y: Float) {
        val downTime = 1_000L
        composeRule.runOnUiThread {
            dispatchPointer(composeRule.activity, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, x, y, downTime, pressure = 0.5f)
            dispatchPointer(
                composeRule.activity, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, x + 30f, y + 3f, downTime, pressure = 0.8f,
                history = listOf(PenSample(x + 10f, y + 1f, 0.6f), PenSample(x + 20f, y + 2f, 0.7f)),
            )
            dispatchPointer(composeRule.activity, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, x + 30f, y + 3f, downTime, pressure = 0f)
        }
        composeRule.waitForIdle()
    }

    private fun assertBatchGoesToTheModel(x: Float, y: Float) {
        val moves = actions.filterIsInstance<EditorAction.MoveShapeGesture>()
        val start = actions.first() as EditorAction.StartShapeGesture
        assertEquals(listOf(0.6f, 0.7f, 0.8f), moves.map { it.pressure })
        for ((step, move) in moves.withIndex()) {
            assertEquals(10f * (step + 1), (move.x - start.x) * scale, 0.01f)
            assertEquals(1f * (step + 1), (move.y - start.y) * scale, 0.01f)
        }
        assertEquals(EditorAction.EndShapeGesture, actions.last())
        assertEquals(5, actions.size)
    }

    @Test
    fun stageStaysWhileAStrokeIsCollectedAndShowsNoFrameOrHandles() {
        host.show(buildEditorState(selectedShape = null, isCapturingStroke = true))
        val origin = findFrameOrigin()

        dispatchPen(listOf((origin.x + 300f) to (origin.y + 600f), (origin.x + 340f) to (origin.y + 640f)))

        assertEquals(listOf("Start", "Move", "End"), actions.map { it::class.simpleName!!.removeSuffix("ShapeGesture") })
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Resize: top left corner").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Rotate shape").fetchSemanticsNodes().size)
    }

    @Test
    fun gestureSurvivesTheSelectionGoingAwayWhenTheTouchBecomesALine() {
        // Как делает модель при касании мимо: на `StartShapeGesture` она снимает выделение и начинает собирать линию.
        host.show(buildEditorState(selectedShape = rectangle))
        val origin = findFrameOrigin()
        val x = origin.x + 700f
        val y = origin.y + 700f
        val downTime = 1_000L
        fun send(action: Int, dx: Float, pressure: Float = 0.6f) {
            composeRule.runOnUiThread { dispatchPointer(composeRule.activity, MotionEvent.TOOL_TYPE_STYLUS, action, x + dx, y, downTime, pressure = pressure) }
            composeRule.waitForIdle()
        }

        send(MotionEvent.ACTION_DOWN, 0f)
        host.show(buildEditorState(selectedShape = null, isCapturingStroke = true))
        send(MotionEvent.ACTION_MOVE, 20f)
        send(MotionEvent.ACTION_MOVE, 40f)
        send(MotionEvent.ACTION_UP, 40f, pressure = 0f)
        host.show(buildEditorState(selectedShape = null, isCapturingStroke = false))

        assertEquals(listOf("Start", "Move", "Move", "End"), actions.map { it::class.simpleName!!.removeSuffix("ShapeGesture") })
        assertEquals(listOf(0.6f, 0.6f), actions.filterIsInstance<EditorAction.MoveShapeGesture>().map { it.pressure })
    }

    @Test
    fun noGestureWithoutSelectedShape() {
        host.show(buildEditorState())
        val origin = findFrameOrigin()

        dispatchPen(listOf((origin.x + 300f) to (origin.y + 600f), (origin.x + 340f) to (origin.y + 640f)))

        assertTrue(actions.isEmpty())
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun dispatchPen(
        points: List<Pair<Float, Float>>,
        isCancelled: Boolean = false,
        liftAt: Pair<Float, Float>? = null,
        pressures: List<Float>? = null,
    ) = dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, points, isCancelled = isCancelled, liftAt = liftAt, pressures = pressures)

    // Центр ручки в координатах окна: туда и ставится перо, чтобы жест начался с этой ручки.
    private fun findCornerCenter(description: String): Pair<Float, Float> {
        val corner = composeRule.onNodeWithContentDescription(description).findBounds()
        val origin = findFrameOrigin()
        return (origin.x + findCenterX(corner)) to (origin.y + findCenterY(corner))
    }

    // Начало кадра в окне: активность может сдвигать содержимое, касания идут в координатах окна.
    private fun findFrameOrigin(): Offset =
        composeRule.onNodeWithTag("frame").fetchSemanticsNode().positionInWindow

    // `getUnclippedBoundsInRoot` у повёрнутого узла отдаёт положение без поворота, `boundsInRoot` — настоящий охват.
    // Плотность mdpi: 1 dp = 1 пиксель.
    private fun SemanticsNodeInteraction.findBounds(): DpRect =
        fetchSemanticsNode().boundsInRoot.let { DpRect(it.left.dp, it.top.dp, it.right.dp, it.bottom.dp) }

    private fun captureFrame(): Bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()

    private fun findCenterX(box: DpRect) = (box.left.value + box.right.value) / 2f

    private fun findCenterY(box: DpRect) = (box.top.value + box.bottom.value) / 2f

    // Знак ручки в том виде, в каком его рисует Chrome: 16 и рамка 2 снаружи, то есть 20 × 20, левый верхний угол знака в
    // (24, 24) цели 64, а значит середина знака на 2 правее и ниже середины цели. По строке через середину цели два куска
    // рамки по 2 с белой серединой 16 между ними; у квадрата угол знака чёрный, у круга нет.
    private fun assertMark(bitmap: Bitmap, cx: Int, cy: Int, isCircle: Boolean) {
        // Окно по знаку (cx − 8…cx + 11) плюс пиксель по краям: за ним у угла рамки идёт пунктир.
        val runs = findInkRuns(bitmap, cy, (cx - 9)..(cx + 12))
        assertEquals("знак ($cx, $cy): два куска рамки в строке через центр $runs", 2, runs.size)
        assertEquals("знак ($cx, $cy): рамка 2", listOf(2, 2), runs.map { it.last - it.first + 1 })
        assertEquals("знак ($cx, $cy): белая середина 16", 16, runs[1].first - runs[0].last - 1)
        assertEquals("знак ($cx, $cy): размер 20", 20, runs[1].last - runs[0].first + 1)
        assertEquals("знак ($cx, $cy): левый край на 8 левее центра цели", cx - 8, runs[0].first)
        val corner = bitmap.isInkAt(runs[0].first + 1, cy - 7)
        assertEquals("знак ($cx, $cy): угол ${if (isCircle) "круга пуст" else "квадрата чёрный"}", !isCircle, corner)
    }

    private fun findInkRuns(bitmap: Bitmap, y: Int, xs: IntRange): List<IntRange> =
        findRuns(xs.map { bitmap.isInkAt(it, y) }).map { (it.first + xs.first)..(it.last + xs.first) }

    // Штрихи пунктира вне знаков в углах (первые и последние 14 пикселей закрыты знаками 20): по 3, начало кратно периоду 5.
    private fun assertDashPattern(side: String, flags: List<Boolean>) {
        val visible = findRuns(flags).filter { it.first >= 14 && it.last <= flags.lastIndex - 14 }
        assertTrue("$side: штрихов мало: ${findRuns(flags)}", visible.size >= 20)
        assertTrue("$side: штрихи по 3: $visible", visible.all { it.last - it.first + 1 == 3 })
        assertTrue("$side: штрихи через 5 от угла: $visible", visible.all { it.first % 5 == 0 })
    }

    // Отрезки подряд идущих `true`: индексы первого и последнего.
    private fun findRuns(flags: List<Boolean>): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        for ((index, isInk) in flags.withIndex()) {
            if (isInk && start < 0) start = index
            if (!isInk && start >= 0) { runs += start until index; start = -1 }
        }
        if (start >= 0) runs += start..flags.lastIndex
        return runs
    }

    // Ширина чёрной рамки у левого края кнопки на середине высоты: 3 у главной, 0 у тихой.
    private fun measureLeftBorder(node: SemanticsNodeInteraction): Int {
        val bitmap = node.captureToImage().asAndroidBitmap()
        return (0 until 8).takeWhile { bitmap.isInkAt(it, bitmap.height / 2) }.size
    }
}
