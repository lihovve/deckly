package app.deckly.domain.editor

import app.deckly.domain.DesignSources
import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class ShapeTransformTest {
    private val unit = CardUnits.PER_DESIGN_UNIT

    // --- перевод между единицами макета и карточки ----------------------------------------------------

    private fun shapeOf(kind: String, box: DemoBox): Shape =
        Shape(
            requireNotNull(Shape.Kind.findByKey(kind)),
            (box.x * unit).toFloat(), (box.y * unit).toFloat(),
            ((box.x + box.w) * unit).toFloat(), ((box.y + box.h) * unit).toFloat(),
            box.angle.toFloat(), 3f,
        )

    private fun boxOf(shape: Shape) = DemoBox(
        shape.left / unit.toDouble(), shape.top / unit.toDouble(),
        (shape.right - shape.left) / unit.toDouble(), (shape.bottom - shape.top) / unit.toDouble(),
        shape.angle.toDouble(),
    )

    private fun assertBox(expected: DemoBox, actual: DemoBox, message: String) {
        // Точность 0,01 единицы макета; угол в градусах с тем же допуском.
        assertEquals(expected.x, actual.x, 0.01, "$message: x")
        assertEquals(expected.y, actual.y, 0.01, "$message: y")
        assertEquals(expected.w, actual.w, 0.01, "$message: ширина")
        assertEquals(expected.h, actual.h, 0.01, "$message: высота")
        assertEquals(expected.angle, actual.angle, 0.01, "$message: угол")
    }

    // Углы повёрнутой рамки в единицах макета.
    private fun listCorners(box: DemoBox): List<Pair<Double, Double>> {
        val radians = Math.toRadians(box.angle)
        val centerX = box.x + box.w / 2
        val centerY = box.y + box.h / 2
        return listOf(-1, 1).flatMap { sx ->
            listOf(-1, 1).map { sy ->
                val dx = sx * box.w / 2
                val dy = sy * box.h / 2
                (centerX + dx * Math.cos(radians) - dy * Math.sin(radians)) to (centerY + dx * Math.sin(radians) + dy * Math.cos(radians))
            }
        }
    }

    // Все точки контура (ось и острия) в единицах макета: тот же код, что рисует фигуру на листе.
    private fun listContour(shape: Shape): List<Pair<Double, Double>> =
        shape.listPolylines().flatten().map { it.x / unit.toDouble() to it.y / unit.toDouble() }

    private fun corner(name: String): ShapeHandle = when (name) {
        "nw" -> ShapeHandle.TOP_LEFT
        "ne" -> ShapeHandle.TOP_RIGHT
        "sw" -> ShapeHandle.BOTTOM_LEFT
        "se" -> ShapeHandle.BOTTOM_RIGHT
        else -> error("Нет угла $name")
    }

    private fun run(case: DemoCase): Shape {
        val shape = shapeOf(case.kind, case.box)
        val a = case.args
        fun d(index: Int) = (a[index] as Double) * unit
        return when (case.op) {
            "move" -> moveShape(shape, d(0).toFloat(), d(1).toFloat())
            "resize" -> resizeShape(shape, corner(a[0] as String), (a[1] as Double).toFloat() * unit, (a[2] as Double).toFloat() * unit)
            "rotate" -> rotateShape(shape, d(0).toFloat(), d(1).toFloat(), d(2).toFloat(), d(3).toFloat())
            "start" -> moveShapeEndpoint(shape, ShapeHandle.START, d(0).toFloat(), d(1).toFloat())
            "end" -> moveShapeEndpoint(shape, ShapeHandle.END, d(0).toFloat(), d(1).toFloat())
            else -> error("Нет операции ${case.op}")
        }
    }

    // --- значения демо ----------------------------------------------------------------------------------

    @Test
    fun everyMovementMatchesTheDemoFunction() {
        assertTrue(DEMO_CASES.size >= 70, "таблица значений демо слишком короткая")
        for (case in DEMO_CASES) {
            assertBox(case.result, boxOf(run(case)), case.name)
        }
    }

    @Test
    fun referenceTableCoversEveryMovementAndTheirLimits() {
        val byOp = DEMO_CASES.groupingBy { it.op }.eachCount()

        // Перенос, четыре угла, поворот, оба конца стрелки; углы 0° и 30° (упор у края листа проверяют свои тесты).
        for (op in listOf("move", "resize", "rotate", "start", "end")) assertTrue((byOp[op] ?: 0) >= 7, op)
        for (corner in listOf("nw", "ne", "sw", "se")) assertTrue(DEMO_CASES.any { it.name.startsWith("resize $corner a30") }, corner)
        assertTrue(DEMO_CASES.any { it.name.startsWith("rotate a0 -170->170") })
        assertTrue(DEMO_CASES.any { it.name.startsWith("rotate a0 170->-170") })
        assertTrue(DEMO_CASES.any { it.name.startsWith("rotate limited") })
        assertTrue(DEMO_CASES.any { it.name.startsWith("endpoint end (336,540)") })
    }

    @Test
    fun numbersAreTheOnesOfTheDemoFile() {
        val script = DesignSources.shapesScript

        // Рамка не меньше 80, кратчайшая ось 64, ось 0,76 рамки, концы по 0,38, 20 делений, вставка размера ползунка. Поле 32 и
        // ручка поворота в `canFitShape` демо в приложение не вошли: решение владельца 18 ноября, см. тест ниже.
        assertTrue(script.contains("left = Math.min(left + localX, right - 80)"))
        assertTrue(script.contains("right = Math.max(right + localX, left + 80)"))
        assertTrue(script.contains("top = Math.min(top + localY, bottom - 80)"))
        assertTrue(script.contains("bottom = Math.max(bottom + localY, top + 80)"))
        assertTrue(script.contains("if (length < 64)"))
        assertTrue(script.contains("dx = Math.cos(angle) * 64; dy = Math.sin(angle) * 64; length = 64;"))
        assertTrue(script.contains("const w = length / 0.76;"))
        assertTrue(script.contains("(endpoint === 'start' ? -0.38 : 0.38) * box.w"))
        assertTrue(script.contains("(endpoint === 'start' ? 0.38 : -0.38) * box.w"))
        assertTrue(script.contains("for (let i = 0; i < 20; i++)"))
        assertTrue(script.contains("const size = Number(sizeSlider.value);"))
        assertTrue(script.contains("const height = isLinearShape(kind) ? 80 : size;"))
        assertTrue(script.contains("x: (stage.clientWidth - size) / 2, y: (stage.clientHeight - height) / 2, w: size, h: height"))
        assertTrue(script.contains("y: fixed.y + dy / 2 - 40, w, h: 80"))
    }

    @Test
    fun demoStillKeepsTheFieldAndTheRotateHandleThatTheAppDropped() {
        // Решение владельца 18 ноября: приложение отпускает фигуру до самого края листа, а в демо макета поле осталось.
        // Если макет догонит приложение, этот тест напомнит убрать пометку в ТЗ и в шапке `ShapeTransform.kt`.
        val script = DesignSources.shapesScript

        assertTrue(script.contains("point.x >= 32 && point.x <= width - 32 && point.y >= 32 && point.y <= height - 32"))
        assertTrue(script.contains("-box.h / 2 - 48"))
    }

    // --- вставка ----------------------------------------------------------------------------------------

    @Test
    fun insertedShapeIsAtTheCenterOfTheSheet() {
        for (kind in Shape.Kind.entries) {
            val shape = insertShape(kind, 3f, 220f)

            val height = if (kind.isLinear) 80.0 else 220.0
            // Лист макета 928 × 963: рамка 220 × 220 (у линий 220 × 80) по центру, без поворота.
            val box = boxOf(shape)
            assertBox(DemoBox((928.0 - 220.0) / 2, (963.0 - height) / 2, 220.0, height, 0.0), box, kind.key)
            assertEquals(kind, shape.kind)
            assertEquals(3f, shape.width)
            assertEquals(CardUnits.WIDTH / 2, (shape.left + shape.right) / 2, 0.01f, "центр по x")
            assertEquals(CardUnits.HEIGHT / 2, (shape.top + shape.bottom) / 2, 0.01f, "центр по y")
            assertTrue(canFitShape(shape), kind.key)
        }
    }

    @Test
    fun insertedShapeHasTheGivenSizeAtTheCenterOfTheSheet() {
        // Ползунок размера идёт от 100 до 440 с шагом 10; рамка size × size, у линий и стрелок size × 80, по центру.
        for (size in listOf(100f, 220f, 330f, 440f)) {
            for (kind in Shape.Kind.entries) {
                val shape = insertShape(kind, 3f, size)

                val height = if (kind.isLinear) 80.0 else size.toDouble()
                assertBox(DemoBox((928.0 - size) / 2, (963.0 - height) / 2, size.toDouble(), height, 0.0), boxOf(shape), "${kind.key} $size")
                assertEquals(CardUnits.WIDTH / 2, (shape.left + shape.right) / 2, 0.01f, "${kind.key} $size: центр по x")
                assertEquals(CardUnits.HEIGHT / 2, (shape.top + shape.bottom) / 2, 0.01f, "${kind.key} $size: центр по y")
                assertTrue(canFitShape(shape), "${kind.key} $size")
            }
        }
    }

    @Test
    fun insertedShapeKeepsTheGivenOutlineWidth() {
        assertEquals(12f * unit, insertShape(Shape.Kind.HEART, 12f * unit, 220f).width, 0.0001f)
    }

    @Test
    fun sheetOfTheDemoIsTheSheetOfTheCard() {
        // 928 × 963 единиц макета — это 1000 × HEIGHT единиц карточки.
        assertEquals(CardUnits.WIDTH, 928f * unit, 0.01f)
        assertEquals(CardUnits.HEIGHT, 963f * unit, 0.01f)
    }

    // --- помещается ли фигура на лист (решение владельца 18 ноября: всё поле листа, без отступа) -----------

    @Test
    fun ordinaryShapeFitsWhenItsFourCornersAreOnTheSheetAndTheRotateHandleDoesNotCount() {
        // Угол в самом углу листа: поля нет, а ручка поворота оказалась бы на 48 выше листа.
        assertTrue(canFitShape(shapeOf("star", DemoBox(0.0, 0.0, 100.0, 100.0, 0.0))))
        assertTrue(canFitShape(shapeOf("star", DemoBox(828.0, 863.0, 100.0, 100.0, 0.0))))
        assertTrue(canFitShape(shapeOf("star", DemoBox(300.0, 10.0, 200.0, 100.0, 0.0))))
        assertFalse(canFitShape(shapeOf("star", DemoBox(-1.0, 0.0, 100.0, 100.0, 0.0))))
        assertFalse(canFitShape(shapeOf("star", DemoBox(0.0, -1.0, 100.0, 100.0, 0.0))))
        assertFalse(canFitShape(shapeOf("star", DemoBox(829.0, 863.0, 100.0, 100.0, 0.0))))
        assertFalse(canFitShape(shapeOf("star", DemoBox(828.0, 864.0, 100.0, 100.0, 0.0))))
    }

    @Test
    fun rotatedFrameFitsByItsRotatedCorners() {
        // Квадрат 100 × 100 по 45° вокруг центра (60, 60): верхний угол на 60 − 70,7 выше листа.
        assertFalse(canFitShape(shapeOf("star", DemoBox(10.0, 10.0, 100.0, 100.0, 45.0))))
        // Центр на 71 от краёв: все углы на листе.
        assertTrue(canFitShape(shapeOf("star", DemoBox(21.0, 21.0, 100.0, 100.0, 45.0))))
    }

    @Test
    fun lineFitsByItsContourAndNotByTheInvisibleEightyHighFrame() {
        // Ось горизонтальной линии на самом верхнем крае листа (y = 0), рамка 80 высотой торчит выше листа на 40.
        assertTrue(canFitShape(shapeOf("line", DemoBox(100.0, -40.0, 300.0, 80.0, 0.0))))
        assertFalse(canFitShape(shapeOf("line", DemoBox(100.0, -41.0, 300.0, 80.0, 0.0))))
        // Нижний край: ось на 963.
        assertTrue(canFitShape(shapeOf("line", DemoBox(100.0, 923.0, 300.0, 80.0, 0.0))))
        assertFalse(canFitShape(shapeOf("line", DemoBox(100.0, 924.0, 300.0, 80.0, 0.0))))
        // Концы оси на 0,12 и 0,88 ширины: у левого края начало на 0 при x = −36.
        assertTrue(canFitShape(shapeOf("line", DemoBox(-36.0, 400.0, 300.0, 80.0, 0.0))))
        assertFalse(canFitShape(shapeOf("line", DemoBox(-37.0, 400.0, 300.0, 80.0, 0.0))))
    }

    @Test
    fun arrowheadMustStayOnTheSheetToo() {
        // Остриё стрелки шире оси на 16 с каждой стороны: у верхнего края ось не ближе 16.
        assertTrue(canFitShape(shapeOf("arrow", DemoBox(100.0, -24.0, 300.0, 80.0, 0.0))))
        assertFalse(canFitShape(shapeOf("arrow", DemoBox(100.0, -25.0, 300.0, 80.0, 0.0))))
        assertTrue(canFitShape(shapeOf("double-arrow", DemoBox(100.0, 907.0, 300.0, 80.0, 0.0))))
        assertFalse(canFitShape(shapeOf("double-arrow", DemoBox(100.0, 908.0, 300.0, 80.0, 0.0))))
    }

    // --- перенос ----------------------------------------------------------------------------------------

    @Test
    fun moveShiftsTheFrameAndKeepsItsSizeAndAngle() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 30.0))

        val moved = moveShape(shape, 40f * unit, -25f * unit)

        assertBox(DemoBox(360.0, 385.0, 220.0, 220.0, 30.0), boxOf(moved), "перенос")
        assertEquals(shape.kind, moved.kind)
        assertEquals(shape.width, moved.width)
    }

    @Test
    fun moveReachesEachOfTheFourEdgesOfTheSheetAndNotBeyond() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))

        // Двадцать делений пополам дают точность в 2^-20 пути: для пути в 5000 единиц упор отличается от края до 0,005.
        val left = boxOf(moveShape(shape, -5000f * unit, 0f))
        val right = boxOf(moveShape(shape, 5000f * unit, 0f))
        val top = boxOf(moveShape(shape, 0f, -5000f * unit))
        val bottom = boxOf(moveShape(shape, 0f, 5000f * unit))

        assertEquals(0.0, left.x, 0.01)
        assertEquals(928.0, right.x + right.w, 0.01)
        assertEquals(0.0, top.y, 0.01)
        assertEquals(963.0, bottom.y + bottom.h, 0.01)
        assertTrue(left.x >= -0.001 && right.x + right.w <= 928.0 + 0.001 && top.y >= -0.001 && bottom.y + bottom.h <= 963.0 + 0.001)
    }

    @Test
    fun rotatedShapeAlsoReachesEachEdgeWithItsFarthestCorner() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 30.0))

        val corners = mapOf(
            "слева" to listCorners(boxOf(moveShape(shape, -5000f * unit, 0f))).minOf { it.first },
            "справа" to listCorners(boxOf(moveShape(shape, 5000f * unit, 0f))).maxOf { it.first },
            "сверху" to listCorners(boxOf(moveShape(shape, 0f, -5000f * unit))).minOf { it.second },
            "снизу" to listCorners(boxOf(moveShape(shape, 0f, 5000f * unit))).maxOf { it.second },
        )

        assertEquals(0.0, corners.getValue("слева"), 0.01)
        assertEquals(928.0, corners.getValue("справа"), 0.01)
        assertEquals(0.0, corners.getValue("сверху"), 0.01)
        assertEquals(963.0, corners.getValue("снизу"), 0.01)
    }

    @Test
    fun lineEndReachesTheEdgeAndAnArrowTipNeverLeavesTheSheet() {
        val line = shapeOf("line", DemoBox(300.0, 500.0, 300.0, 80.0, 0.0))
        val arrow = shapeOf("arrow", DemoBox(300.0, 500.0, 300.0, 80.0, 0.0))

        // Конец ведут далеко за каждый край: ось (а у стрелки и остриё) кончается ровно на краю листа.
        val lineRight = listContour(moveShapeEndpoint(line, ShapeHandle.END, 5000f * unit, 540f * unit))
        val lineLeft = listContour(moveShapeEndpoint(line, ShapeHandle.START, -5000f * unit, 540f * unit))
        val lineTop = listContour(moveShapeEndpoint(line, ShapeHandle.END, 450f * unit, -5000f * unit))
        val lineBottom = listContour(moveShapeEndpoint(line, ShapeHandle.END, 450f * unit, 5000f * unit))
        assertEquals(928.0, lineRight.maxOf { it.first }, 0.01)
        assertEquals(0.0, lineLeft.minOf { it.first }, 0.01)
        assertEquals(0.0, lineTop.minOf { it.second }, 0.01)
        assertEquals(963.0, lineBottom.maxOf { it.second }, 0.01)
        for ((x, y) in lineRight + lineLeft + lineTop + lineBottom) assertTrue(x >= -0.001 && x <= 928.001 && y >= -0.001 && y <= 963.001, "($x, $y)")

        // Конец стрелки: острие тоже на листе, где бы ни был указатель.
        for ((x, y) in listOf(5000.0 to 540.0, -5000.0 to 540.0, 450.0 to -5000.0, 450.0 to 5000.0, 5000.0 to 5000.0, -5000.0 to -5000.0)) {
            val moved = moveShapeEndpoint(arrow, ShapeHandle.END, (x * unit).toFloat(), (y * unit).toFloat())
            for ((px, py) in listContour(moved)) assertTrue(px >= -0.001 && px <= 928.001 && py >= -0.001 && py <= 963.001, "указатель ($x, $y): ($px, $py)")
        }
    }

    @Test
    fun horizontalLineTouchesTheTopEdgeButHorizontalArrowStopsSixteenBelowIt() {
        val line = shapeOf("line", DemoBox(300.0, 500.0, 300.0, 80.0, 0.0))
        val arrow = shapeOf("arrow", DemoBox(300.0, 500.0, 300.0, 80.0, 0.0))

        val movedLine = moveShape(line, 0f, -5000f * unit)
        val movedArrow = moveShape(arrow, 0f, -5000f * unit)

        // Ось линии на y = 0 (рамка 80 на 40 выше листа), у стрелки остриё шире оси на 16, ось на y = 16.
        assertEquals(0.0, listContour(movedLine).minOf { it.second }, 0.01)
        assertEquals(-40.0, boxOf(movedLine).y, 0.01)
        assertEquals(0.0, listContour(movedArrow).minOf { it.second }, 0.01)
        assertEquals(-24.0, boxOf(movedArrow).y, 0.01)
    }

    @Test
    fun penBackReturnsTheShapeAfterItWasPressedAgainstTheEdge() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))
        val gesture = ActiveShapeGesture(null, 500f, 500f, shape)

        // Перо ушло далеко за край и вернулось: упор не накопил ошибку, фигура на старте, и по пути помещалась всегда.
        for (dx in listOf(-5000f, -2000f, -600f)) assertTrue(canFitShape(gesture.applyTo(500f + dx, 500f)))
        val outside = boxOf(gesture.applyTo(-5000f, 500f))
        assertEquals(0.0, outside.x, 0.01)

        assertBox(boxOf(shape), boxOf(gesture.applyTo(500f, 500f)), "перо вернулось")
    }

    @Test
    fun rotateStopsWhenACornerWouldLeaveTheSheet() {
        // Рамка 300 × 100 у верхнего края (верх на 10): поворот на 90° вывел бы углы за лист, на самом краю ровно предел.
        val shape = shapeOf("star", DemoBox(350.0, 10.0, 300.0, 100.0, 0.0))

        val turned = boxOf(rotateShape(shape, 700f * unit, 60f * unit, 500f * unit, 260f * unit))

        assertTrue(turned.angle > 3.0 && turned.angle < 5.0, "угол ${turned.angle}")
        assertEquals(0.0, listCorners(turned).minOf { it.second }, 0.05)
    }

    @Test
    fun resizeReachesTheEdgesToo() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))

        // Углы идут вдоль осей, чтобы упереться в один край (диагональ упёрлась бы в тот, до которого ближе).
        val west = boxOf(resizeShape(shape, ShapeHandle.TOP_LEFT, -5000f * unit, 0f))
        val north = boxOf(resizeShape(shape, ShapeHandle.TOP_LEFT, 0f, -5000f * unit))
        val east = boxOf(resizeShape(shape, ShapeHandle.BOTTOM_RIGHT, 5000f * unit, 0f))
        val south = boxOf(resizeShape(shape, ShapeHandle.BOTTOM_RIGHT, 0f, 5000f * unit))

        assertEquals(0.0, west.x, 0.01)
        assertEquals(0.0, north.y, 0.01)
        assertEquals(928.0, east.x + east.w, 0.01)
        assertEquals(963.0, south.y + south.h, 0.01)
        // Диагональ, плавный упор: угол остановился на первом достигнутом крае и за лист не вышел.
        val diagonal = boxOf(resizeShape(shape, ShapeHandle.TOP_LEFT, -5000f * unit, -5000f * unit))
        assertEquals(0.0, diagonal.x, 0.01)
        assertTrue(diagonal.y >= -0.001)
    }

    // --- размер -----------------------------------------------------------------------------------------

    @Test
    fun resizeCannotMakeTheFrameSmallerThanEighty() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))

        val squeezed = boxOf(resizeShape(shape, ShapeHandle.BOTTOM_RIGHT, -900f, -900f))

        assertEquals(80.0, squeezed.w, 0.01)
        assertEquals(80.0, squeezed.h, 0.01)
        assertEquals(320.0, squeezed.x, 0.01)
        assertEquals(410.0, squeezed.y, 0.01)
    }

    @Test
    fun resizeKeepsTheOppositeCornerInPlaceWhateverTheAngle() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 30.0))

        val resized = resizeShape(shape, ShapeHandle.BOTTOM_RIGHT, 40f * unit, 20f * unit)

        // Верхний левый угол в мире стоит там же: считаем его через центр и поворот.
        fun cornerOf(s: Shape): Pair<Double, Double> {
            val box = boxOf(s)
            val radians = Math.toRadians(box.angle)
            val dx = -box.w / 2
            val dy = -box.h / 2
            return box.x + box.w / 2 + dx * Math.cos(radians) - dy * Math.sin(radians) to
                box.y + box.h / 2 + dx * Math.sin(radians) + dy * Math.cos(radians)
        }
        assertEquals(cornerOf(shape).first, cornerOf(resized).first, 0.01)
        assertEquals(cornerOf(shape).second, cornerOf(resized).second, 0.01)
    }

    @Test
    fun resizeRejectsHandlesThatAreNotCorners() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))

        assertThrows<IllegalArgumentException> { resizeShape(shape, ShapeHandle.ROTATE, 1f, 1f) }
        assertThrows<IllegalArgumentException> { resizeShape(shape, ShapeHandle.START, 1f, 1f) }
    }

    // --- поворот ----------------------------------------------------------------------------------------

    @Test
    fun rotateFollowsTheShortestWayAcrossTheBoundary() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))
        val cx = (430.0 * unit).toFloat()
        val cy = (520.0 * unit).toFloat()

        fun at(degrees: Double, radius: Double = 200.0): Pair<Float, Float> {
            val radians = Math.toRadians(degrees)
            return (cx + radius * unit * Math.cos(radians)).toFloat() to (cy + radius * unit * Math.sin(radians)).toFloat()
        }
        val (sx, sy) = at(-170.0)
        val (px, py) = at(170.0)

        // Указатель прошёл через −180°, а не через 0: поворот на −20°, а не на 340°.
        assertEquals(-20.0, rotateShape(shape, sx, sy, px, py).angle.toDouble(), 0.01)
    }

    @Test
    fun fullTurnOfThePointerLeavesTheAngleUnchanged() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 25.0))
        val cx = (430.0 * unit).toFloat()
        val cy = (520.0 * unit).toFloat()

        val turned = rotateShape(shape, cx + 100f, cy, cx + 100f, cy)

        assertEquals(25f, turned.angle, 0.001f)
    }

    // --- концы линий ------------------------------------------------------------------------------------

    @Test
    fun endpointMoveBuildsTheFrameFromTheFixedEnd() {
        val shape = shapeOf("arrow", DemoBox(300.0, 500.0, 300.0, 80.0, 0.0))
        val fixedX = 450.0 - 0.38 * 300.0
        val fixedY = 540.0

        val moved = boxOf(moveShapeEndpoint(shape, ShapeHandle.END, 700f * unit, 400f * unit))

        // Ось 0,76 рамки идёт от неподвижного начала к указателю, рамка высотой 80.
        val length = hypot(700.0 - fixedX, 400.0 - fixedY)
        assertEquals(length / 0.76, moved.w, 0.01)
        assertEquals(80.0, moved.h, 0.01)
        assertEquals(Math.toDegrees(Math.atan2(400.0 - fixedY, 700.0 - fixedX)), moved.angle, 0.01)
    }

    @Test
    fun axisIsNeverShorterThanSixtyFour() {
        val shape = shapeOf("arrow", DemoBox(300.0, 500.0, 300.0, 80.0, 0.0))

        // Указатель в 14 единицах от неподвижного конца (336, 540): ось всё равно 64, значит рамка 64 / 0,76.
        val moved = boxOf(moveShapeEndpoint(shape, ShapeHandle.END, 350f * unit, 540f * unit))

        assertEquals(64.0 / 0.76, moved.w, 0.01)
    }

    @Test
    fun endpointOnTheFixedEndKeepsTheOldDirection() {
        val shape = shapeOf("arrow", DemoBox(300.0, 500.0, 300.0, 80.0, 40.0))
        val fixed = boxOf(shape).let { box ->
            val radians = Math.toRadians(box.angle)
            val offset = -0.38 * box.w
            box.x + box.w / 2 + offset * Math.cos(radians) to box.y + box.h / 2 + offset * Math.sin(radians)
        }

        val moved = moveShapeEndpoint(shape, ShapeHandle.END, (fixed.first * unit).toFloat(), (fixed.second * unit).toFloat())

        // Длина нулевая: направление берётся от неподвижного конца к прежнему подвижному, то есть 40°.
        assertEquals(40.0, moved.angle.toDouble(), 0.01)
        assertEquals(64.0 / 0.76, boxOf(moved).w, 0.01)
    }

    @Test
    fun endpointRejectsHandlesThatAreNotEnds() {
        val shape = shapeOf("arrow", DemoBox(300.0, 500.0, 300.0, 80.0, 0.0))

        assertThrows<IllegalArgumentException> { moveShapeEndpoint(shape, ShapeHandle.TOP_LEFT, 1f, 1f) }
        assertThrows<IllegalArgumentException> { moveShapeEndpoint(shape, ShapeHandle.ROTATE, 1f, 1f) }
    }

    // --- жест ---------------------------------------------------------------------------------------------

    @Test
    fun gestureWithoutHandleMovesTheShape() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))
        val gesture = ActiveShapeGesture(null, 500f, 500f, shape)

        assertEquals(moveShape(shape, 40f, -30f), gesture.applyTo(540f, 470f))
    }

    @Test
    fun gestureAlwaysStartsFromTheShapeAtItsStart() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))
        val gesture = ActiveShapeGesture(ShapeHandle.BOTTOM_RIGHT, 500f, 500f, shape)

        // Каждое движение считается от начала жеста, а не от прошлого движения: то же положение даёт то же.
        val first = gesture.applyTo(600f, 600f)
        gesture.applyTo(900f, 900f)
        assertEquals(first, gesture.applyTo(600f, 600f))
        assertEquals(resizeShape(shape, ShapeHandle.BOTTOM_RIGHT, 100f, 100f), first)
    }

    @Test
    fun gestureRoutesEveryHandleToItsMovement() {
        val box = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))
        val arrow = shapeOf("arrow", DemoBox(300.0, 500.0, 300.0, 80.0, 0.0))

        for (handle in listOf(ShapeHandle.TOP_LEFT, ShapeHandle.TOP_RIGHT, ShapeHandle.BOTTOM_LEFT, ShapeHandle.BOTTOM_RIGHT)) {
            assertEquals(resizeShape(box, handle, 30f, 20f), ActiveShapeGesture(handle, 100f, 100f, box).applyTo(130f, 120f), handle.name)
        }
        assertEquals(rotateShape(box, 100f, 100f, 130f, 120f), ActiveShapeGesture(ShapeHandle.ROTATE, 100f, 100f, box).applyTo(130f, 120f))
        assertEquals(moveShapeEndpoint(arrow, ShapeHandle.START, 130f, 120f), ActiveShapeGesture(ShapeHandle.START, 100f, 100f, arrow).applyTo(130f, 120f))
        assertEquals(moveShapeEndpoint(arrow, ShapeHandle.END, 130f, 120f), ActiveShapeGesture(ShapeHandle.END, 100f, 100f, arrow).applyTo(130f, 120f))
    }

    // --- инварианты ---------------------------------------------------------------------------------------

    @Test
    fun randomMovementsAlwaysKeepTheShapeOnTheSheetAndItsFrameValid() {
        val random = Random(20261105)
        repeat(60) { run ->
            val kind = Shape.Kind.entries[random.nextInt(Shape.Kind.entries.size)]
            var shape = insertShape(kind, (1 + random.nextInt(12)) * unit, 220f)
            val width = shape.width
            repeat(25) { step ->
                fun point() = random.nextFloat() * 1600f - 300f
                val before = shape
                var operation: String
                shape = if (kind.isLinear) {
                    when (random.nextInt(3)) {
                        0 -> moveShape(shape, point() - 500f, point() - 500f).also { operation = "move" }
                        1 -> moveShapeEndpoint(shape, ShapeHandle.START, point(), point()).also { operation = "start" }
                        else -> moveShapeEndpoint(shape, ShapeHandle.END, point(), point()).also { operation = "end" }
                    }
                } else {
                    when (random.nextInt(3)) {
                        0 -> moveShape(shape, point() - 500f, point() - 500f).also { operation = "move" }
                        1 -> resizeShape(shape, ShapeHandle.entries[random.nextInt(4)], point() - 500f, point() - 500f).also { operation = "resize" }
                        else -> rotateShape(shape, point(), point(), point(), point()).also { operation = "rotate" }
                    }
                }
                val where = "$run/$step ${kind.key} $operation из $before"
                assertTrue(canFitShape(shape), "$where не помещается: $shape")
                assertEquals(kind, shape.kind, where)
                assertEquals(width, shape.width, where)
                val box = boxOf(shape)
                assertTrue(shape.left < shape.right && shape.top < shape.bottom, where)
                if (kind.isLinear) {
                    assertTrue(abs(box.h - 80.0) < 0.01, "$where: высота линии ${box.h}")
                    assertTrue(box.w * 0.76 >= 64.0 - 0.01, "$where: ось короче 64: ${box.w * 0.76}")
                } else {
                    assertTrue(box.w >= 80.0 - 0.01 && box.h >= 80.0 - 0.01, "$where: рамка меньше 80: ${box.w} × ${box.h}")
                }
            }
        }
    }
}
