package app.deckly.design.e

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    fun frameIsOneDashedLineWhoseCenterLiesOnTheBorder() {
        host.show(buildEditorState(selectedShape = rectangle))

        val bitmap = captureFrame()
        val points = listHandlePoints(rectangle)
        val topLeft = points.getValue(ShapeHandleKind.TOP_LEFT)
        val topRight = points.getValue(ShapeHandleKind.TOP_RIGHT)
        val bottomLeft = points.getValue(ShapeHandleKind.BOTTOM_LEFT)
        val axis = (topLeft.x + topRight.x) / 2f
        // Штрих 3 и промежуток 2 толщины линии (в этом тесте 1 пиксель) на каждой стороне, без подгонки под её длину. Первые
        // и последние 14 пикселей закрыты знаками углов, у верхней стороны середину занимает черта ручки поворота.
        val sideColumns = (topLeft.x.toInt() + 14) until (topRight.x.toInt() - 14)
        val sideRows = (topLeft.y.toInt() + 14) until (bottomLeft.y.toInt() - 14)
        assertDashes("верхняя сторона", findDashRuns(sideColumns) { bitmap.measureBand(it, topLeft.y.toInt() - 1..topLeft.y.toInt() + 1, isColumn = true) }, (axis.toInt() - 5)..(axis.toInt() + 5))
        assertDashes("нижняя сторона", findDashRuns(sideColumns) { bitmap.measureBand(it, bottomLeft.y.toInt() - 1..bottomLeft.y.toInt() + 1, isColumn = true) }, null)
        assertDashes("левая сторона", findDashRuns(sideRows) { bitmap.measureBand(it, topLeft.x.toInt() - 1..topLeft.x.toInt() + 1, isColumn = false) }, null)
        assertDashes("правая сторона", findDashRuns(sideRows) { bitmap.measureBand(it, topRight.x.toInt() - 1..topRight.x.toInt() + 1, isColumn = false) }, null)
        // Линия лежит серединой на границе, а не снаружи (у `outline` макета) и не внутри: возьмём середину первого штриха.
        val dash = findDashRuns(sideColumns) { bitmap.measureBand(it, topLeft.y.toInt() - 1..topLeft.y.toInt() + 1, isColumn = true) }.first { it.last < axis.toInt() - 5 }
        val column = (dash.first + dash.last) / 2
        val rows = (topLeft.y.toInt() - 3)..(topLeft.y.toInt() + 3)
        val weight = rows.sumOf { bitmap.measureDarkness(column, it).toDouble() }
        val centerY = rows.sumOf { bitmap.measureDarkness(column, it) * (it + 0.5) } / weight
        assertEquals("центр линии на границе", topLeft.y.toDouble(), centerY, 0.6)
    }

    @Test
    fun squareMarksStandWithTheirCentersOnTheCorners() {
        host.show(buildEditorState(selectedShape = rectangle))

        val bitmap = captureFrame()
        val points = listHandlePoints(rectangle)
        for (corner in listOf(ShapeHandleKind.TOP_LEFT, ShapeHandleKind.TOP_RIGHT, ShapeHandleKind.BOTTOM_LEFT, ShapeHandleKind.BOTTOM_RIGHT)) {
            assertMarkCenter("$corner", bitmap, points.getValue(corner), angle = 0f, tolerance = 0.75f)
            assertMarkShape("$corner", bitmap, points.getValue(corner), isCircle = false)
        }
    }

    @Test
    fun squareMarksStandOnWholePixelsSoNoEdgeIsGrey() {
        host.show(buildEditorState(selectedShape = rectangle))

        // Редактор обновляет экран в режиме DU: серое он режет в чёрное или белое, и знак на дробном пикселе вышел бы с рамками
        // разной толщины. Внутри знака (квадрат 20 вокруг точки) промежуточного серого нет.
        val bitmap = captureFrame()
        val points = listHandlePoints(rectangle)
        for (corner in listOf(ShapeHandleKind.TOP_LEFT, ShapeHandleKind.TOP_RIGHT, ShapeHandleKind.BOTTOM_LEFT, ShapeHandleKind.BOTTOM_RIGHT)) {
            val point = points.getValue(corner)
            val grey = mutableListOf<Offset>()
            forEachPixelInSquare(bitmap, point, angle = 0f, half = 10f) { x, y ->
                if (bitmap.measureDarkness(x, y) in 0.1f..0.9f) grey += Offset(x.toFloat(), y.toFloat())
            }
            assertTrue("$corner: серые пиксели в знаке $grey", grey.isEmpty())
        }
    }

    @Test
    fun rotateHandleHasItsIconCenteredFortyEightAboveTheTopSideAndALineOnTheAxisBelowIt() {
        host.show(buildEditorState(selectedShape = rectangle))

        val bitmap = captureFrame()
        val points = listHandlePoints(rectangle)
        val rotate = points.getValue(ShapeHandleKind.ROTATE)
        val topLeft = points.getValue(ShapeHandleKind.TOP_LEFT)
        val axis = (topLeft.x + points.getValue(ShapeHandleKind.TOP_RIGHT).x) / 2f
        assertEquals("ручка на оси рамки", axis, rotate.x, 0.01f)
        assertEquals("центр на 48 выше верхней стороны", topLeft.y - 48f, rotate.y, 0.01f)
        // Значок 24 × 24 на белом, центром в точке (чернила значка не симметричны: допуск на его рисунок).
        val icon = measureInkCenter(bitmap, rotate, angle = 0f, half = 14f)
        assertNotNull("значка поворота нет", icon)
        assertEquals("значок: центр по x", rotate.x, icon!!.x, 1.5f)
        assertEquals("значок: центр по y", rotate.y, icon.y, 1.5f)
        // Чёрная черта 1 × 16 по оси, от низа цели (центр + 32) до верхней стороны: каждая строка черты тёмная и одна колонка.
        val lineTop = rotate.y + 32f
        for (row in (lineTop.toInt() + 2) until (topLeft.y.toInt() - 1)) {
            val columns = (axis.toInt() - 3)..(axis.toInt() + 3)
            val weight = columns.sumOf { bitmap.measureDarkness(it, row).toDouble() }
            assertEquals("строка $row: ровно один пиксель чёрного", 1.0, weight, 0.15)
            assertEquals("строка $row: центр по оси", axis.toDouble(), columns.sumOf { bitmap.measureDarkness(it, row) * (it + 0.5) } / weight, 0.6)
        }
        // Между значком и чертой пусто: черта начинается там, где кончается цель, а не от значка.
        val gapRows = (rotate.y.toInt() + 14)..(lineTop.toInt() - 1)
        assertTrue("между значком и чертой пусто", gapRows.none { row -> ((axis.toInt() - 3)..(axis.toInt() + 3)).any { bitmap.measureDarkness(it, row) > 0.2f } })
    }

    @Test
    fun rotateLineIsOneCrispPixelWhetherTheFrameIsEvenOrOddWide() {
        // Ширина рамки 232 и 233 пикселя: у чётной ось падает на границу пикселей, и линия 1 px вышла бы серой в два.
        for (right in listOf(450f, 451f)) {
            val narrow = rectangle.copy(frame = rectangle.frame.copy(right = right))
            host.show(buildEditorState(selectedShape = narrow))

            val bitmap = captureFrame()
            val points = listHandlePoints(narrow)
            val rotate = points.getValue(ShapeHandleKind.ROTATE)
            val axis = rotate.x
            val rows = (rotate.y.toInt() + 34)..(rotate.y.toInt() + 44)
            for (row in rows) {
                val values = ((axis.toInt() - 3)..(axis.toInt() + 3)).map { bitmap.measureDarkness(it, row) }
                assertTrue("правая граница $right, строка $row: серые пиксели $values", values.all { it < 0.1f || it > 0.9f })
                assertEquals("правая граница $right, строка $row: один чёрный пиксель", 1, values.count { it > 0.9f })
                val column = ((axis.toInt() - 3)..(axis.toInt() + 3)).first { bitmap.measureDarkness(it, row) > 0.9f }
                assertEquals("правая граница $right, строка $row: черта у оси", axis, column + 0.5f, 1f)
            }
        }
    }

    @Test
    fun linearShapeHasTwoRoundMarksCenteredOnTheEndsOfTheAxisAndNoDashedFrame() {
        host.show(buildEditorState(selectedShape = line))

        val bitmap = captureFrame()
        val frame = composeRule.onNodeWithContentDescription("Arrow").findBounds()
        val points = listHandlePoints(line)
        val start = composeRule.onNodeWithContentDescription("Move start point").findBounds()
        val end = composeRule.onNodeWithContentDescription("Move end point").findBounds()

        // Концы на 12 и 88 % ширины рамки, на середине её высоты; цель 64 с центром в той же точке, что и знак.
        for ((kind, target) in listOf(ShapeHandleKind.START to start, ShapeHandleKind.END to end)) {
            val point = points.getValue(kind)
            assertEquals("$kind: цель 64", 64f, target.right.value - target.left.value, 0.5f)
            assertEquals("$kind: центр цели по x", point.x, findCenterX(target), 0.75f)
            assertEquals("$kind: центр цели по y", point.y, findCenterY(target), 0.75f)
            assertMarkCenter("$kind", bitmap, point, angle = 0f, tolerance = 0.75f)
            assertMarkShape("$kind", bitmap, point, isCircle = true)
        }
        composeRule.onNodeWithContentDescription("Rotate shape").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Resize: top left corner").assertDoesNotExist()
        // Пунктира нет: ни выше, ни ниже оси между концами.
        val xs = (findCenterX(start).roundToInt() + 30) until (findCenterX(end).roundToInt() - 30)
        assertTrue(xs.none { x -> (frame.top.value.roundToInt() - 2..frame.top.value.roundToInt() + 1).any { bitmap.measureDarkness(x, it) > 0.2f } })
        assertTrue(xs.none { x -> (frame.bottom.value.roundToInt() - 2..frame.bottom.value.roundToInt() + 1).any { bitmap.measureDarkness(x, it) > 0.2f } })
    }

    @Test
    fun marksOfARotatedFrameStandOnTheRotatedPoints() {
        for (angle in listOf(90f, 30f)) {
            val turned = rectangle.copy(frame = rectangle.frame.copy(angle = angle))
            host.show(buildEditorState(selectedShape = turned))

            val bitmap = captureFrame()
            val points = listHandlePoints(turned)
            for (corner in listOf(ShapeHandleKind.TOP_LEFT, ShapeHandleKind.TOP_RIGHT, ShapeHandleKind.BOTTOM_LEFT, ShapeHandleKind.BOTTOM_RIGHT)) {
                assertMarkCenter("угол $corner при $angle°", bitmap, points.getValue(corner), angle, tolerance = 1f)
            }
            val rotate = points.getValue(ShapeHandleKind.ROTATE)
            val icon = measureInkCenter(bitmap, rotate, angle, half = 14f)
            assertNotNull("значка поворота при $angle° нет", icon)
            assertEquals("значок при $angle°: центр по x", rotate.x, icon!!.x, 2f)
            assertEquals("значок при $angle°: центр по y", rotate.y, icon.y, 2f)
        }
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

    // --- склейка дребезга: UP и DOWN пера почти подряд внутри касания --------------------------------

    // Система на планшете сообщает отрыв посреди контакта: пары UP→DOWN с паузой 0–60 мс (в проверке владельца 64 пары за 8
    // минут), настоящие отрывы от 100 мс. Слой на подъёме не шлёт конец жеста сразу, а ждёт касание пером 80 мс: пришло, это
    // тот же жест (без нового `Start` и без новой проверки ленты), не пришло, конец. Время управляется часами теста.

    @Test
    fun liftFollowedByATouchInsideTheWindowIsTheSameGesture() {
        composeRule.mainClock.autoAdvance = false
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")

        pen(MotionEvent.ACTION_DOWN, x, y, 0.5f)
        pen(MotionEvent.ACTION_MOVE, x + 30f, y, 0.6f)
        pen(MotionEvent.ACTION_UP, x + 30f, y, 0.6f)
        advance(20)
        pen(MotionEvent.ACTION_DOWN, x + 45f, y, 0.4f)
        pen(MotionEvent.ACTION_MOVE, x + 60f, y, 0.5f)
        pen(MotionEvent.ACTION_UP, x + 60f, y, 0.5f)
        advance(200)

        assertEquals(listOf("Start", "Move", "Move", "Move", "End"), actions.map { it::class.simpleName!!.removeSuffix("ShapeGesture") })
        val moves = actions.filterIsInstance<EditorAction.MoveShapeGesture>()
        val start = actions.first() as EditorAction.StartShapeGesture
        assertEquals(listOf(30f, 45f, 60f), moves.map { Math.round((it.x - start.x) * scale).toFloat() })
        assertEquals("нажим нового касания у его движения", 0.4f, moves[1].pressure, 0.001f)
    }

    @Test
    fun liftWithoutANewTouchSendsTheEndOnlyAfterTheWindow() {
        composeRule.mainClock.autoAdvance = false
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")
        pen(MotionEvent.ACTION_DOWN, x, y, 0.5f)
        pen(MotionEvent.ACTION_MOVE, x + 30f, y, 0.6f)
        pen(MotionEvent.ACTION_UP, x + 30f, y, 0.6f)

        advance(79)
        assertTrue("окно ещё не прошло", actions.none { it == EditorAction.EndShapeGesture })
        advance(1)

        assertEquals(EditorAction.EndShapeGesture, actions.last())
        assertEquals(1, actions.count { it == EditorAction.EndShapeGesture })
    }

    @Test
    fun touchAfterTheWindowIsANewGesture() {
        composeRule.mainClock.autoAdvance = false
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")
        pen(MotionEvent.ACTION_DOWN, x, y, 0.5f)
        pen(MotionEvent.ACTION_UP, x, y, 0.5f)
        advance(100)

        pen(MotionEvent.ACTION_DOWN, x + 40f, y, 0.5f)
        pen(MotionEvent.ACTION_UP, x + 40f, y, 0.5f)
        advance(100)

        assertEquals(listOf("Start", "End", "Start", "End"), actions.map { it::class.simpleName!!.removeSuffix("ShapeGesture") })
    }

    @Test
    fun severalReboundsInARowStayOneGesture() {
        composeRule.mainClock.autoAdvance = false
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")
        pen(MotionEvent.ACTION_DOWN, x, y, 0.5f)
        repeat(3) { step ->
            pen(MotionEvent.ACTION_UP, x + step * 20f, y, 0.5f)
            advance(10)
            pen(MotionEvent.ACTION_DOWN, x + step * 20f + 10f, y, 0.5f)
        }
        pen(MotionEvent.ACTION_UP, x + 60f, y, 0.5f)
        advance(200)

        assertEquals(1, actions.count { it is EditorAction.StartShapeGesture })
        assertEquals(1, actions.count { it == EditorAction.EndShapeGesture })
        assertEquals(EditorAction.EndShapeGesture, actions.last())
    }

    @Test
    fun reboundIsGluedWhateverTheDistanceBecauseAFalseCoordinateMustNotStartANewGesture() {
        composeRule.mainClock.autoAdvance = false
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")
        pen(MotionEvent.ACTION_DOWN, x, y, 0.5f)
        pen(MotionEvent.ACTION_UP, x, y, 0.5f)
        advance(30)

        // Точка на листе (лист начинается в кадре с (32, 200)) в сотнях пикселей от угла рамки.
        val origin = findFrameOrigin()
        pen(MotionEvent.ACTION_DOWN, origin.x + 32f + 40f, origin.y + 200f + 40f, 0.3f)
        pen(MotionEvent.ACTION_MOVE, x + 5f, y, 0.5f)
        pen(MotionEvent.ACTION_UP, x + 5f, y, 0.5f)
        advance(200)

        assertEquals(listOf("Start", "Move", "Move", "End"), actions.map { it::class.simpleName!!.removeSuffix("ShapeGesture") })
    }

    @Test
    fun cancelAfterAReboundIsStillACancel() {
        composeRule.mainClock.autoAdvance = false
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")
        pen(MotionEvent.ACTION_DOWN, x, y, 0.5f)
        pen(MotionEvent.ACTION_UP, x, y, 0.5f)
        advance(10)
        pen(MotionEvent.ACTION_DOWN, x + 10f, y, 0.5f)

        pen(MotionEvent.ACTION_CANCEL, x + 20f, y, 0.5f)
        advance(200)

        assertEquals(EditorAction.CancelShapeGesture, actions.last())
        assertTrue(actions.none { it == EditorAction.EndShapeGesture })
    }

    @Test
    fun aFingerInsideTheWindowDoesNotGlueTheGesture() {
        composeRule.mainClock.autoAdvance = false
        host.show(buildEditorState(selectedShape = rectangle))
        val (x, y) = findCornerCenter("Resize: bottom right corner")
        pen(MotionEvent.ACTION_DOWN, x, y, 0.5f)
        pen(MotionEvent.ACTION_UP, x, y, 0.5f)
        advance(10)
        composeRule.runOnUiThread {
            dispatchPointer(composeRule.activity, MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_DOWN, x + 30f, y, 2_000L)
            dispatchPointer(composeRule.activity, MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_UP, x + 30f, y, 2_000L)
        }
        advance(100)

        assertEquals(listOf("Start", "End"), actions.map { it::class.simpleName!!.removeSuffix("ShapeGesture") })
    }

    // Часы теста идут ровно на столько миллисекунд, без округления до кадра (окно склейки 80 мс не должно округляться).
    private fun advance(milliseconds: Long) = composeRule.mainClock.advanceTimeBy(milliseconds, ignoreFrameDuration = true)

    // Событие пера без ожидания: часы теста стоят, время идёт только по `advance`.
    private fun pen(action: Int, x: Float, y: Float, pressure: Float) {
        composeRule.runOnUiThread {
            dispatchPointer(composeRule.activity, MotionEvent.TOOL_TYPE_STYLUS, action, x, y, 1_000L, pressure = pressure)
        }
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
        advance(100)

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
        advance(100)
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
        advance(100)
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

    // Точки ручек в корне окна: лист занимает слот целиком, слой жестов лежит над ним; плотность mdpi, 1 dp = 1 пиксель.
    private fun listHandlePoints(shape: SelectedShape): Map<ShapeHandleKind, Offset> {
        val sheet = composeRule.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot
        return listHandlePlaces(shape.frame, sheet.width / 1000f, 1f).associate { it.handle to Offset(sheet.left + it.x, sheet.top + it.y) }
    }

    // Тёмность пикселя от 0 (белый) до 1 (чёрный): у сглаженных краёв линии доля неполная, центры считаются по ней.
    private fun Bitmap.measureDarkness(x: Int, y: Int): Float = (255 - AndroidColor.red(getPixel(x, y))) / 255f

    // Сумма тёмности поперёк стороны: по [band] строк у столбца [index] или по [band] столбцов у строки [index].
    private fun Bitmap.measureBand(index: Int, band: IntRange, isColumn: Boolean): Float =
        band.sumOf { (if (isColumn) measureDarkness(index, it) else measureDarkness(it, index)).toDouble() }.toFloat()

    // Штрихи вдоль стороны: вдоль [along] тёмность поперёк не меньше половины пикселя. Штрихи, упирающиеся в край диапазона,
    // обрезаны им и не считаются.
    private fun findDashRuns(along: IntRange, measure: (Int) -> Float): List<IntRange> =
        findRuns(along.map { measure(it) >= 0.5f })
            .filter { it.first > 0 && it.last < along.count() - 1 }
            .map { (it.first + along.first)..(it.last + along.first) }

    // Штрихи по 3 через промежуток 2; [skip] — участок, где сторону перекрывает чужая черта: его штрихи и промежутки рядом не считаются.
    private fun assertDashes(side: String, runs: List<IntRange>, skip: IntRange?) {
        val visible = runs.filter { skip == null || (it.first > skip.last || it.last < skip.first) }
        assertTrue("$side: штрихов мало: $runs", visible.size >= 20)
        assertTrue("$side: штрихи по 3: $visible", visible.all { it.last - it.first + 1 == 3 })
        val gaps = visible.zipWithNext { current, next -> next.first - current.last - 1 }.filter { skip == null || it < 3 }
        assertTrue("$side: промежутки по 2: $gaps", gaps.all { it == 2 })
    }

    // Центр тяжести тёмного в квадрате [half] вокруг [point] (в осях, повёрнутых на [angle]); вес пикселя — его тёмность.
    private fun findDarkCenter(bitmap: Bitmap, point: Offset, angle: Float, half: Float): Offset? {
        var sum = 0.0
        var sumX = 0.0
        var sumY = 0.0
        forEachPixelInSquare(bitmap, point, angle, half) { x, y ->
            val dark = bitmap.measureDarkness(x, y).toDouble()
            sum += dark
            sumX += dark * (x + 0.5)
            sumY += dark * (y + 0.5)
        }
        return if (sum == 0.0) null else Offset((sumX / sum).toFloat(), (sumY / sum).toFloat())
    }

    // Середина охвата чернил (пиксели темнее половины) в том же квадрате: так проверяется значок, чей рисунок несимметричен.
    private fun measureInkCenter(bitmap: Bitmap, point: Offset, angle: Float, half: Float): Offset? {
        val ink = mutableListOf<Offset>()
        forEachPixelInSquare(bitmap, point, angle, half) { x, y -> if (bitmap.isInkAt(x, y)) ink += Offset(x + 0.5f, y + 0.5f) }
        if (ink.isEmpty()) return null
        return Offset((ink.minOf { it.x } + ink.maxOf { it.x }) / 2f, (ink.minOf { it.y } + ink.maxOf { it.y }) / 2f)
    }

    private fun forEachPixelInSquare(bitmap: Bitmap, point: Offset, angle: Float, half: Float, visit: (Int, Int) -> Unit) {
        val cosine = cos(Math.toRadians(angle.toDouble())).toFloat()
        val sine = sin(Math.toRadians(angle.toDouble())).toFloat()
        val reach = ceil(half * 1.5f).toInt()
        for (y in (point.y.toInt() - reach).coerceAtLeast(0)..(point.y.toInt() + reach).coerceAtMost(bitmap.height - 1)) {
            for (x in (point.x.toInt() - reach).coerceAtLeast(0)..(point.x.toInt() + reach).coerceAtMost(bitmap.width - 1)) {
                // Середина пикселя в осях фигуры относительно точки: поворот обратно на угол рамки.
                val dx = x + 0.5f - point.x
                val dy = y + 0.5f - point.y
                val localX = dx * cosine + dy * sine
                val localY = -dx * sine + dy * cosine
                if (abs(localX) <= half && abs(localY) <= half) visit(x, y)
            }
        }
    }

    // Центр знака (квадрат или круг 16 с рамкой 2 снаружи) совпадает с точкой ручки: центр тяжести рамки знака в окне 12.
    private fun assertMarkCenter(label: String, bitmap: Bitmap, point: Offset, angle: Float, tolerance: Float) {
        val center = findDarkCenter(bitmap, point, angle, half = 12f)
        assertNotNull("$label: знака нет у $point", center)
        assertEquals("$label: центр знака по x", point.x, center!!.x, tolerance)
        assertEquals("$label: центр знака по y", point.y, center.y, tolerance)
    }

    // Знак 20 в поперечнике: по строке рамка 2, белая середина 16, рамка 2. У квадрата угол чёрный, у круга пуст. Квадрат смотрим
    // на 4 ниже центра: строка через центр угла рамки совпала бы с её линией, которая продолжает рамку знака.
    private fun assertMarkShape(label: String, bitmap: Bitmap, point: Offset, isCircle: Boolean) {
        val row = point.y.toInt() + if (isCircle) 0 else 4
        val xs = (point.x.toInt() - 12)..(point.x.toInt() + 12)
        val runs = findRuns(xs.map { bitmap.measureDarkness(it, row) >= 0.5f }).map { (it.first + xs.first)..(it.last + xs.first) }
        assertEquals("$label: два куска рамки в строке через центр $runs", 2, runs.size)
        assertEquals("$label: рамка 2", listOf(2, 2), runs.map { it.last - it.first + 1 })
        assertEquals("$label: белая середина 16", 16, runs[1].first - runs[0].last - 1)
        assertEquals("$label: размер 20", 20, runs[1].last - runs[0].first + 1)
        val corner = bitmap.isInkAt(runs[0].first + 1, point.y.toInt() - 7)
        assertEquals("$label: угол ${if (isCircle) "круга пуст" else "квадрата чёрный"}", !isCircle, corner)
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
