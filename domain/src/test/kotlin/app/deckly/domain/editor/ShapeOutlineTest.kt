package app.deckly.domain.editor

import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import app.deckly.domain.model.StrokePoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShapeOutlineTest {
    private val unit = CardUnits.PER_DESIGN_UNIT

    private fun grid(kind: Shape.Kind) = kind.listGridOutlines()

    private fun gridPoints(vararg xy: Pair<Float, Float>) = xy.map { GridPoint(it.first, it.second) }

    private fun assertGrid(expected: List<GridPoint>, actual: List<GridPoint>) {
        assertEquals(expected.size, actual.size, "число точек: $actual")
        expected.zip(actual).forEach { (want, got) ->
            assertEquals(want.x, got.x, 0.001f)
            assertEquals(want.y, got.y, 0.001f)
        }
    }

    private fun shape(kind: Shape.Kind, left: Float, top: Float, right: Float, bottom: Float, angle: Float = 0f, width: Float = 6f) =
        Shape(kind, left, top, right, bottom, angle, width)

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: StrokePoint, delta: Float = 0.01f) {
        assertEquals(expectedX, actual.x, delta, "x")
        assertEquals(expectedY, actual.y, delta, "y")
    }

    // --- сетка 100 × 100 -------------------------------------------------------------------------

    @Test
    fun lineIsTwoPointsOnTheMiddle() {
        assertEquals(1, grid(Shape.Kind.LINE).size)
        assertGrid(gridPoints(12f to 50f, 88f to 50f), grid(Shape.Kind.LINE)[0])
    }

    @Test
    fun arrowIsAxisAndOneHead() {
        val outlines = grid(Shape.Kind.ARROW)

        assertEquals(2, outlines.size)
        assertGrid(gridPoints(12f to 50f, 88f to 50f, 68f to 30f), outlines[0])
        assertGrid(gridPoints(88f to 50f, 68f to 70f), outlines[1])
    }

    @Test
    fun doubleArrowHasAxisAndTwoHeads() {
        val outlines = grid(Shape.Kind.DOUBLE_ARROW)

        assertEquals(3, outlines.size)
        assertGrid(gridPoints(12f to 50f, 88f to 50f), outlines[0])
        assertGrid(gridPoints(32f to 30f, 12f to 50f, 32f to 70f), outlines[1])
        assertGrid(gridPoints(68f to 30f, 88f to 50f, 68f to 70f), outlines[2])
    }

    @Test
    fun polygonsHaveTheirVerticesAndAreClosed() {
        assertGrid(gridPoints(14f to 20f, 86f to 20f, 86f to 80f, 14f to 80f, 14f to 20f), grid(Shape.Kind.RECTANGLE)[0])
        assertGrid(gridPoints(50f to 12f, 89f to 85f, 11f to 85f, 50f to 12f), grid(Shape.Kind.TRIANGLE)[0])
        assertGrid(gridPoints(50f to 10f, 86f to 50f, 50f to 90f, 14f to 50f, 50f to 10f), grid(Shape.Kind.DIAMOND)[0])
        assertGrid(
            gridPoints(30f to 15f, 70f to 15f, 90f to 50f, 70f to 85f, 30f to 85f, 10f to 50f, 30f to 15f),
            grid(Shape.Kind.HEXAGON)[0],
        )
    }

    @Test
    fun starKeepsTheRelativeVerticesOfItsPath() {
        // m50 10 12 27 29 3-22 20 6 30-25-15-25 15 6-30L9 40l29-3Z
        assertGrid(
            gridPoints(50f to 10f, 62f to 37f, 91f to 40f, 69f to 60f, 75f to 90f, 50f to 75f, 25f to 90f, 31f to 60f, 9f to 40f, 38f to 37f, 50f to 10f),
            grid(Shape.Kind.STAR)[0],
        )
    }

    @Test
    fun ovalHasItsExtremePoints() {
        val oval = grid(Shape.Kind.OVAL)[0]

        for (extreme in gridPoints(12f to 50f, 88f to 50f, 50f to 20f, 50f to 80f)) {
            assertTrue(oval.any { hypot(it.x - extreme.x, it.y - extreme.y) < 0.01f }, "нет $extreme")
        }
    }

    @Test
    fun moonAndDropPassThroughTheirAnchorPoints() {
        val moon = grid(Shape.Kind.MOON)[0]
        for (anchor in gridPoints(65f to 12f, 87f to 70f)) {
            assertTrue(moon.any { hypot(it.x - anchor.x, it.y - anchor.y) < 0.01f }, "луна без $anchor")
        }
        val drop = grid(Shape.Kind.DROP)[0]
        for (anchor in gridPoints(50f to 10f, 20f to 62f, 80f to 62f)) {
            assertTrue(drop.any { hypot(it.x - anchor.x, it.y - anchor.y) < 0.01f }, "капля без $anchor")
        }
    }

    @Test
    fun sunIsCircleAndEightRays() {
        val outlines = grid(Shape.Kind.SUN)

        // Круг, затем восемь лучей из одного `path`: по подконтуру на луч.
        assertEquals(9, outlines.size)
        assertGrid(gridPoints(50f to 7f, 50f to 16f), outlines[1])
    }

    @Test
    fun everyKindStaysInsideTheHundredBySquareAndIsNotEmpty() {
        for (kind in Shape.Kind.entries) {
            val outlines = grid(kind)
            assertTrue(outlines.isNotEmpty() && outlines.all { it.size >= 2 }, kind.key)
            for (point in outlines.flatten()) {
                assertTrue(point.x in -0.001f..100.001f && point.y in -0.001f..100.001f, "${kind.key}: ($point)")
            }
        }
    }

    @Test
    fun curvedKindsAreSmoothEnoughAndOutlinesAreCached() {
        for (kind in listOf(Shape.Kind.OVAL, Shape.Kind.MOON, Shape.Kind.HEART, Shape.Kind.CLOUD, Shape.Kind.DROP)) {
            val points = grid(kind).flatten()
            assertTrue(points.size > 40, "${kind.key}: слишком мало точек ${points.size}")
        }
        // Сетка считается один раз на вид.
        assertTrue(grid(Shape.Kind.CLOUD) === grid(Shape.Kind.CLOUD))
    }

    // --- контуры на листе -------------------------------------------------------------------------

    @Test
    fun gridIsStretchedOverTheFrameIndependentlyByAxes() {
        val rectangle = shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f)

        val outline = rectangle.listPolylines()[0]

        // Рамка 200 × 300: x = 100 + gx · 2, y = 200 + gy · 3.
        assertPoint(128f, 260f, outline[0])
        assertPoint(272f, 260f, outline[1])
        assertPoint(272f, 440f, outline[2])
        assertPoint(128f, 440f, outline[3])
        assertPoint(128f, 260f, outline[4])
    }

    @Test
    fun rotationTurnsTheOutlineAroundTheFrameCenter() {
        val rectangle = shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, angle = 90f)

        val outline = rectangle.listPolylines()[0]

        // Центр (200, 350). Угол (128, 260) лежит в (−72, −90) от него; поворот по часовой на 90°: (x, y) → (−y, x).
        assertPoint(200f + 90f, 350f - 72f, outline[0])
        assertPoint(200f + 90f, 350f + 72f, outline[1])
    }

    @Test
    fun rotationByAnyAngleKeepsDistancesToTheCenter() {
        val star = shape(Shape.Kind.STAR, 100f, 100f, 400f, 300f)
        val plain = star.listPolylines()[0]
        val turned = star.copy(angle = 37f).listPolylines()[0]

        assertEquals(plain.size, turned.size)
        val cx = 250f
        val cy = 200f
        plain.zip(turned).forEach { (a, b) ->
            assertEquals(hypot(a.x - cx, a.y - cy), hypot(b.x - cx, b.y - cy), 0.01f)
            val radians = Math.toRadians(37.0)
            val rx = a.x - cx
            val ry = a.y - cy
            assertEquals(cx + rx * cos(radians).toFloat() - ry * sin(radians).toFloat(), b.x, 0.01f)
            assertEquals(cy + rx * sin(radians).toFloat() + ry * cos(radians).toFloat(), b.y, 0.01f)
        }
    }

    private fun linear(kind: Shape.Kind, designWidth: Float, angle: Float = 0f, left: Float = 100f, top: Float = 300f) =
        shape(kind, left, top, left + designWidth * unit, top + 80f * unit, angle)

    @Test
    fun lineAxisRunsFromTwelveToEightyEightPercentOfWidthOnTheMiddle() {
        val line = linear(Shape.Kind.LINE, 500f)

        val outlines = line.listPolylines()

        assertEquals(1, outlines.size)
        val y = 300f + 40f * unit
        assertPoint(100f + 0.12f * 500f * unit, y, outlines[0][0])
        assertPoint(100f + 0.88f * 500f * unit, y, outlines[0][1])
    }

    @Test
    fun arrowHeadKeepsItsSizeWhateverTheLength() {
        // Остриё 24 вдоль оси и ±16 поперёк в единицах макета: у стрелки шириной 320 и 1000 оно одинаковое.
        for (designWidth in listOf(320f, 1000f)) {
            val arrow = linear(Shape.Kind.ARROW, designWidth)

            val outlines = arrow.listPolylines()

            assertEquals(2, outlines.size)
            val head = outlines[1]
            val tip = head[1]
            assertEquals(24f * unit, tip.x - head[0].x, 0.01f, "длина острия при ширине $designWidth")
            assertEquals(16f * unit, tip.y - head[0].y, 0.01f)
            assertEquals(16f * unit, head[2].y - tip.y, 0.01f)
            assertEquals(head[0].x, head[2].x, 0.01f)
        }
    }

    @Test
    fun shortArrowHeadIsOneThirdOfTheAxis() {
        // Ширина 60: ось 0,76 · 60 = 45,6, треть — 15,2, это меньше 24.
        val arrow = linear(Shape.Kind.ARROW, 60f)

        val head = arrow.listPolylines()[1]

        assertEquals(15.2f * unit, head[1].x - head[0].x, 0.01f)
    }

    @Test
    fun doubleArrowHasAHeadOnEachEndAndLineHasNone() {
        assertEquals(1, linear(Shape.Kind.LINE, 400f).listPolylines().size)
        val double = linear(Shape.Kind.DOUBLE_ARROW, 400f).listPolylines()

        assertEquals(3, double.size)
        val axis = double[0]
        val startHead = double[2]
        val endHead = double[1]
        assertEquals(axis[1].x, endHead[1].x, 0.01f)
        assertEquals(axis[0].x, startHead[1].x, 0.01f)
        assertEquals(24f * unit, startHead[0].x - startHead[1].x, 0.01f)
        assertEquals(24f * unit, endHead[1].x - endHead[0].x, 0.01f)
    }

    @Test
    fun linearShapeIsRotatedAroundTheCenterOfItsFrame() {
        val line = linear(Shape.Kind.LINE, 500f, angle = 90f)

        val axis = line.listPolylines()[0]

        // Рамка 500 × 80 единиц макета, центр (100 + 250·u, 300 + 40·u). Ось после поворота на 90° вертикальна.
        val cx = 100f + 250f * unit
        val cy = 300f + 40f * unit
        assertEquals(cx, axis[0].x, 0.01f)
        assertEquals(cx, axis[1].x, 0.01f)
        assertEquals(cy - 0.38f * 500f * unit, axis[0].y, 0.01f)
        assertEquals(cy + 0.38f * 500f * unit, axis[1].y, 0.01f)
    }

    @Test
    fun linearNumbersComeFromTheMockupDemo() {
        val script = app.deckly.domain.DesignSources.shapesScript

        // buildLinearShape: ось от 12 % до 88 %, остриё min(24, длина оси / 3), поперёк ±16, лента выбора width + 16.
        assertTrue(script.contains("const start = box.w * 0.12, end = box.w * 0.88, y = box.h / 2;"))
        assertTrue(script.contains("const head = Math.min(24, (end - start) / 3);"))
        assertTrue(script.contains("y - 16"))
        assertTrue(script.contains("y + 16"))
        assertTrue(script.contains("hit.setAttribute('stroke-width', item.width + 16);"))
    }

    // --- касание ----------------------------------------------------------------------------------

    private fun path(vararg xy: Pair<Float, Float>) = xy.map { StrokePoint(it.first, it.second, 0.5f) }

    // Прямоугольник на рамке 200 × 300 с левым краем x = 128 (14 % от 200 = 28 от левого края 100).
    private val frame = shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, width = 6f)

    private val ribbon = 3f + 8f * unit

    @Test
    fun touchNearTheOutlineSelectsTheShape() {
        assertEquals(0, findShapeAt(listOf(frame), 128f, 350f))
        assertEquals(0, findShapeAt(listOf(frame), 128f - 5f, 350f))
        assertEquals(0, findShapeAt(listOf(frame), 128f + 5f, 350f))
    }

    @Test
    fun ribbonEndsAtHalfWidthPlusEightDesignUnits() {
        assertEquals(0, findShapeAt(listOf(frame), 128f - (ribbon - 0.05f), 350f))
        assertNull(findShapeAt(listOf(frame), 128f - (ribbon + 0.05f), 350f))
    }

    @Test
    fun centerOfAnEmptyShapeSelectsNothing() {
        assertNull(findShapeAt(listOf(frame), 200f, 350f))
        assertNull(findShapeAt(emptyList(), 200f, 350f))
    }

    @Test
    fun ofTwoTouchedShapesTheLastOneWins() {
        val inner = shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, width = 6f)
        val outer = shape(Shape.Kind.RECTANGLE, 110f, 210f, 310f, 510f, width = 6f)

        // x = 129 в одном пикселе от левого края внутренней (128) и в девяти от внешней (138): лента задевает обе,
        // выигрывает последняя в списке, а не ближайшая.
        assertEquals(1, findShapeAt(listOf(inner, outer), 129f, 350f))
        assertEquals(1, findShapeAt(listOf(outer, inner), 129f, 350f))
        assertEquals(0, findShapeAt(listOf(inner, outer), 128f - 10f, 350f))
    }

    @Test
    fun rotatedShapeIsTouchedAtTheRotatedOutline() {
        val turned = frame.copy(angle = 90f)

        // Поворот на 90° по часовой вокруг центра (200, 350): левый край стал верхним (y = 278), верхний — правым (x = 290).
        assertNull(findShapeAt(listOf(turned), 128f, 350f))
        assertEquals(0, findShapeAt(listOf(turned), 200f + 90f, 350f))
    }

    @Test
    fun shapeIsTouchedByPathSegmentsNotOnlyPoints() {
        // Штрих через контур с редкими точками: отрезок пересекает левый край, точки далеко от него.
        assertTrue(isShapeTouched(frame, path(100f to 350f, 160f to 350f), 1f))
        assertFalse(isShapeTouched(frame, path(100f to 350f, 110f to 350f), 1f))
    }

    @Test
    fun dotInsideAnEmptyShapeDoesNotTouchIt() {
        assertFalse(isShapeTouched(frame, path(200f to 350f), 5f))
        assertTrue(isShapeTouched(frame, path(128f to 350f), 1f))
        assertFalse(isShapeTouched(frame, emptyList(), 1000f))
    }

    @Test
    fun touchRadiusAddsToTheOutline() {
        assertFalse(isShapeTouched(frame, path(140f to 350f), 5f))
        assertTrue(isShapeTouched(frame, path(140f to 350f), 13f))
    }
}
