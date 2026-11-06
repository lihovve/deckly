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
        assertTrue(DEMO_CASES.size >= 100, "таблица значений демо слишком короткая")
        for (case in DEMO_CASES) {
            assertBox(case.result, boxOf(run(case)), case.name)
        }
    }

    @Test
    fun referenceTableCoversEveryMovementAndTheirLimits() {
        val byOp = DEMO_CASES.groupingBy { it.op }.eachCount()

        // Перенос, четыре угла, поворот, оба конца стрелки; упор в поле у каждого; углы 0° и 30°.
        for (op in listOf("move", "resize", "rotate", "start", "end")) assertTrue((byOp[op] ?: 0) >= 10, op)
        for (corner in listOf("nw", "ne", "sw", "se")) assertTrue(DEMO_CASES.any { it.name.startsWith("resize $corner a30") }, corner)
        assertTrue(DEMO_CASES.any { it.name.startsWith("rotate a0 -170->170") })
        assertTrue(DEMO_CASES.any { it.name.startsWith("rotate a0 170->-170") })
        assertTrue(DEMO_CASES.any { it.name.startsWith("rotate limited") })
        assertTrue(DEMO_CASES.any { it.name.startsWith("endpoint end (336,540)") })
    }

    @Test
    fun fitIsTheSameAsTheDemoForEveryReferenceBox() {
        for (fit in DEMO_FITS) {
            assertEquals(fit.fits, canFitShape(shapeOf(fit.kind, fit.box)), "${fit.kind} ${fit.box}")
        }
    }

    @Test
    fun numbersAreTheOnesOfTheDemoFile() {
        val script = DesignSources.shapesScript

        // Поле 32, рамка не меньше 80, кратчайшая ось 64, ось 0,76 рамки, концы по 0,38, ручка на 48, 20 делений, вставка 220.
        assertTrue(script.contains("point.x >= 32 && point.x <= width - 32 && point.y >= 32 && point.y <= height - 32"))
        assertTrue(script.contains("left = Math.min(left + localX, right - 80)"))
        assertTrue(script.contains("right = Math.max(right + localX, left + 80)"))
        assertTrue(script.contains("top = Math.min(top + localY, bottom - 80)"))
        assertTrue(script.contains("bottom = Math.max(bottom + localY, top + 80)"))
        assertTrue(script.contains("if (length < 64)"))
        assertTrue(script.contains("dx = Math.cos(angle) * 64; dy = Math.sin(angle) * 64; length = 64;"))
        assertTrue(script.contains("const w = length / 0.76;"))
        assertTrue(script.contains("(endpoint === 'start' ? -0.38 : 0.38) * box.w"))
        assertTrue(script.contains("(endpoint === 'start' ? 0.38 : -0.38) * box.w"))
        assertTrue(script.contains("-box.h / 2 - 48"))
        assertTrue(script.contains("for (let i = 0; i < 20; i++)"))
        assertTrue(script.contains("w: 220, h: height"))
        assertTrue(script.contains("const height = isLinearShape(kind) ? 80 : 220;"))
        assertTrue(script.contains("y: fixed.y + dy / 2 - 40, w, h: 80"))
    }

    // --- вставка ----------------------------------------------------------------------------------------

    @Test
    fun insertedShapeIsAtTheCenterOfTheSheet() {
        for (kind in Shape.Kind.entries) {
            val shape = insertShape(kind, 3f)

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
    fun insertedShapeKeepsTheGivenOutlineWidth() {
        assertEquals(12f * unit, insertShape(Shape.Kind.HEART, 12f * unit).width, 0.0001f)
    }

    @Test
    fun sheetOfTheDemoIsTheSheetOfTheCard() {
        // 928 × 963 единиц макета — это 1000 × HEIGHT единиц карточки.
        assertEquals(CardUnits.WIDTH, 928f * unit, 0.01f)
        assertEquals(CardUnits.HEIGHT, 963f * unit, 0.01f)
    }

    @Test
    fun rotateHandleMustFitForOrdinaryShapesButNotForLines() {
        // Рамка у верхнего поля помещается, пока ручки нет над ней: у линии её нет, у остальных видов есть (на 48 выше).
        val frame = DemoBox(300.0, 32.0 + 10.0, 200.0, 100.0, 0.0)

        assertFalse(canFitShape(shapeOf("star", frame)))
        assertTrue(canFitShape(shapeOf("line", frame)))
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
    fun moveBeyondTheFieldStopsAtTheFieldSmoothly() {
        val shape = shapeOf("star", DemoBox(320.0, 410.0, 220.0, 220.0, 0.0))

        val far = boxOf(moveShape(shape, 5000f, 0f))
        val farther = boxOf(moveShape(shape, 50000f, 0f))

        // Правый край рамки у поля 32 от края листа, дальше тащить некуда. Двадцать делений пополам дают точность в
        // 2^-20 пути, поэтому для пути в 50000 единиц упор отличается от поля до 0,05, но поле не пересечено.
        assertEquals(928.0 - 32.0, far.x + far.w, 0.01)
        assertEquals(928.0 - 32.0, farther.x + farther.w, 0.1)
        assertTrue(farther.x + farther.w <= 928.0 - 32.0 + 0.001)
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
            var shape = insertShape(kind, (1 + random.nextInt(12)) * unit)
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
