package app.deckly.ink

import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetInputTest {
    @Test
    fun strokeIsBuiltInCardUnits() {
        val scale = 1.83f
        val points = listOf(RawPoint(183f, 366f, 0.5f), RawPoint(1830f, 183f, 1f))

        val stroke = buildStroke(points, 6f, scale)

        assertEquals(6f / scale, stroke!!.width, 1e-4f)
        assertEquals(2, stroke.points.size)
        assertPoint(100f, 200f, 0.5f, stroke.points[0])
        assertPoint(1000f, 100f, 1f, stroke.points[1])
    }

    @Test
    fun emptyPointListGivesNoStroke() {
        assertNull(buildStroke(emptyList(), 6f, 1.83f))
    }

    @Test
    fun pressureOutsideRangeIsClamped() {
        val stroke = buildStroke(listOf(RawPoint(10f, 10f, 1.2f), RawPoint(20f, 20f, -0.1f)), 6f, 1f)!!

        assertEquals(1f, stroke.points[0].pressure, 0f)
        assertEquals(0f, stroke.points[1].pressure, 0f)
    }

    @Test
    fun rawPressureIsNormalizedByDeviceMaximum() {
        assertEquals(0.5f, normalizePressure(2048f, 4096f), 1e-6f)
        assertEquals(1f, normalizePressure(5000f, 4096f), 0f)
        assertEquals(0f, normalizePressure(-3f, 4096f), 0f)
    }

    @Test
    fun erasePathIsInCardUnitsWithoutPressureMeaning() {
        val path = buildErasePath(listOf(RawPoint(183f, 366f, 0f), RawPoint(1830f, 183f, 1f)), 1.83f)

        assertEquals(2, path.size)
        assertEquals(100f, path[0].x, 1e-3f)
        assertEquals(200f, path[0].y, 1e-3f)
        assertEquals(1000f, path[1].x, 1e-3f)
        assertEquals(100f, path[1].y, 1e-3f)
    }

    @Test
    fun eraserRadiusIsSixteenPixelsInCardUnits() {
        assertEquals(16f / 1.83f, computeEraserRadius(1.83f), 1e-4f)
        assertEquals(16f, computeEraserRadius(1f), 1e-6f)
    }

    @Test
    fun strokeBoundsCoverPointsAndWidestPenMark() {
        // Перо 6 px: точка при полном нажиме шириной 9 px, радиус 4,5; запас с краем сглаживания.
        val stroke = Stroke(6f, listOf(StrokePoint(100f, 100f, 1f), StrokePoint(150f, 120f, 1f)))

        val bounds = computeStrokeBounds(stroke, 1f)!!

        assertTrue(bounds.left <= 95 && bounds.top <= 95)
        assertTrue(bounds.right >= 155 && bounds.bottom >= 125)
        // Запас не раздут: не больше 4 px сверх радиуса самой широкой точки.
        assertTrue(bounds.left >= 90 && bounds.top >= 90)
        assertTrue(bounds.right <= 160 && bounds.bottom <= 130)
    }

    @Test
    fun strokeBoundsFollowScale() {
        val stroke = Stroke(3f, listOf(StrokePoint(50f, 50f, 1f)))

        val bounds = computeStrokeBounds(stroke, 2f)!!

        // Центр (100, 100), перо 6 px: радиус 4,5.
        assertTrue(bounds.left in 90..95)
        assertTrue(bounds.right in 105..110)
    }

    @Test
    fun strokeWithoutPointsHasNoBounds() {
        assertNull(computeStrokeBounds(Stroke(6f, emptyList()), 1f))
    }

    @Test
    fun changedBoundsCoverRemovedAndAddedStrokes() {
        val kept = Stroke(6f, listOf(StrokePoint(10f, 10f, 1f)))
        val removed = Stroke(6f, listOf(StrokePoint(200f, 200f, 1f), StrokePoint(300f, 200f, 1f)))
        val added = Stroke(6f, listOf(StrokePoint(500f, 400f, 1f)))

        val bounds = computeChangedBounds(CardSide(listOf(kept, removed)), CardSide(listOf(kept, added)), 1f)!!

        assertTrue(bounds.left <= 195 && bounds.right >= 505)
        assertTrue(bounds.top <= 195 && bounds.bottom >= 405)
        // Неизменённый штрих в область не входит.
        assertTrue(bounds.left > 20 && bounds.top > 20)
    }

    @Test
    fun changedBoundsAreNullWhenListsAreEqual() {
        val stroke = Stroke(6f, listOf(StrokePoint(10f, 10f, 1f)))

        assertNull(computeChangedBounds(CardSide(listOf(stroke)), CardSide(listOf(stroke.copy())), 1f))
        assertNull(computeChangedBounds(CardSide(), CardSide(), 1f))
    }

    @Test
    fun changedBoundsIgnoreOrderOfStrokes() {
        val first = Stroke(6f, listOf(StrokePoint(10f, 10f, 1f)))
        val second = Stroke(6f, listOf(StrokePoint(90f, 90f, 1f)))

        assertNull(computeChangedBounds(CardSide(listOf(first, second)), CardSide(listOf(second, first)), 1f))
    }

    // --- фигуры -------------------------------------------------------------------------------------

    // Рамка идёт по контуру: от x = 128 до 272 и от y = 260 до 440.
    private val rectangle = Shape(Shape.Kind.RECTANGLE, 128f, 260f, 272f, 440f, 0f, 6f)

    @Test
    fun shapeBoundsCoverTheOutlineAndHalfItsWidth() {
        val bounds = computeShapeBounds(rectangle, 1f)

        assertTrue(bounds.left <= 125 && bounds.top <= 257)
        assertTrue(bounds.right >= 275 && bounds.bottom >= 443)
        // Не раздуто: полтолщины 3 и запас сглаживания.
        assertTrue(bounds.left >= 120 && bounds.top >= 252)
        assertTrue(bounds.right <= 280 && bounds.bottom <= 448)
    }

    @Test
    fun shapeBoundsFollowTheRotatedOutlineNotTheFrame() {
        // Поворот на 90° вокруг центра (200, 350): контур занимает x от 110 до 290 и y от 278 до 422.
        val bounds = computeShapeBounds(rectangle.copy(angle = 90f), 1f)

        assertTrue(bounds.left in 102..110 && bounds.right in 290..298)
        assertTrue(bounds.top in 272..278 && bounds.bottom in 422..428)
    }

    @Test
    fun shapeBoundsFollowScaleAndWidth() {
        val bounds = computeShapeBounds(rectangle.copy(width = 20f), 2f)

        // Контур в пикселях от x = 256, толщина 40 px: полтолщины 20.
        assertTrue(bounds.left <= 236 && bounds.left >= 230)
    }

    @Test
    fun changedBoundsCoverAMovedShapeAtBothPlaces() {
        val moved = rectangle.copy(left = 500f, right = 644f)

        val bounds = computeChangedBounds(CardSide(shapes = listOf(rectangle)), CardSide(shapes = listOf(moved)), 1f)!!

        // Старое место: левый край контура 128; новое: правый край контура 644.
        assertTrue(bounds.left <= 125 && bounds.right >= 644)
    }

    @Test
    fun changedBoundsIgnoreUnchangedShapes() {
        val other = Shape(Shape.Kind.OVAL, 800f, 800f, 900f, 900f, 0f, 3f)
        val moved = rectangle.copy(left = 500f, right = 644f)

        val bounds = computeChangedBounds(CardSide(shapes = listOf(other, rectangle)), CardSide(shapes = listOf(other, moved)), 1f)!!

        assertTrue(bounds.right < 800 && bounds.bottom < 800)
    }

    @Test
    fun changedBoundsAreNullWhenShapesAreEqualInAnyOrder() {
        val other = Shape(Shape.Kind.OVAL, 800f, 800f, 900f, 900f, 0f, 3f)

        assertNull(computeChangedBounds(CardSide(shapes = listOf(rectangle)), CardSide(shapes = listOf(rectangle.copy())), 1f))
        assertNull(computeChangedBounds(CardSide(shapes = listOf(rectangle, other)), CardSide(shapes = listOf(other, rectangle)), 1f))
    }

    @Test
    fun changedBoundsCoverStrokeAndShapeTogether() {
        val stroke = Stroke(6f, listOf(StrokePoint(900f, 100f, 1f)))

        val bounds = computeChangedBounds(CardSide(), CardSide(listOf(stroke), listOf(rectangle)), 1f)!!

        assertTrue(bounds.left <= 125 && bounds.right >= 905 && bounds.top <= 100)
    }

    @Test
    fun changedBoundsAreNullForTwoEmptySides() {
        assertNull(computeChangedBounds(CardSide(), CardSide(), 1f))
    }

    @Test
    fun traceBoundsCoverThePointsAndThePenMark() {
        val points = listOf(RawPoint(100f, 120f, 1f), RawPoint(130f, 110f, 1f))

        val bounds = computeTraceBounds(points, 10f)!!

        // Перо 10 px: самая широкая точка 13 px, радиус 6,5; запас с краем сглаживания.
        assertTrue(bounds.left <= 93 && bounds.top <= 103)
        assertTrue(bounds.right >= 137 && bounds.bottom >= 127)
        assertTrue(bounds.left >= 85 && bounds.top >= 95)
    }

    @Test
    fun traceBoundsOfNoPointsAreNull() {
        assertNull(computeTraceBounds(emptyList(), 10f))
    }

    // --- границы перерисовки не зависят от числа штрихов ----------------------------------------------

    private val manyStrokes = List(300) {
        val x = 20f + (it % 20) * 45f
        val y = 20f + (it / 20) * 60f
        Stroke(4f, listOf(StrokePoint(x, y, 0.5f), StrokePoint(x + 30f, y + 20f, 0.8f)))
    }

    @Test
    fun changedShapeWithTheSameStrokeListGivesOnlyTheShapeBoundsAndDoesNotReadTheStrokes() {
        val moved = rectangle.copy(left = 500f, right = 700f)
        val strokes = CountingList(manyStrokes)

        val bounds = computeChangedBounds(CardSide(strokes, listOf(rectangle)), CardSide(strokes, listOf(moved)), 1f)

        assertEquals(computeShapeBounds(rectangle, 1f).union(computeShapeBounds(moved, 1f)), bounds)
        assertEquals(0, strokes.reads)
    }

    @Test
    fun equalStrokeListsAreComparedInLinearTime() {
        val before = CountingList(manyStrokes)
        val after = CountingList(manyStrokes.map { it.copy() })

        assertNull(computeChangedBounds(CardSide(before), CardSide(after), 1f))

        assertTrue("чтений ${before.reads + after.reads}", before.reads + after.reads <= 4 * manyStrokes.size)
    }

    @Test
    fun differentStrokeListsAreComparedInLinearTime() {
        val removed = Stroke(6f, listOf(StrokePoint(900f, 900f, 1f)))
        val added = Stroke(6f, listOf(StrokePoint(950f, 940f, 1f)))
        val before = CountingList(manyStrokes + removed)
        val after = CountingList(manyStrokes + added)

        val bounds = computeChangedBounds(CardSide(before), CardSide(after), 1f)

        assertEquals(computeStrokeBounds(removed, 1f)!!.union(computeStrokeBounds(added, 1f)!!), bounds)
        assertTrue("чтений ${before.reads + after.reads}", before.reads + after.reads <= 8 * manyStrokes.size)
    }

    @Test
    fun removedAndAddedStrokeAmongManyUnchangedGiveExactlyTheirUnion() {
        val removed = Stroke(8f, listOf(StrokePoint(100f, 700f, 1f), StrokePoint(180f, 760f, 0.5f)))
        val added = Stroke(3f, listOf(StrokePoint(820f, 640f, 0.7f)))

        val bounds = computeChangedBounds(CardSide(manyStrokes + removed), CardSide(manyStrokes + added), 1f)

        assertEquals(computeStrokeBounds(removed, 1f)!!.union(computeStrokeBounds(added, 1f)!!), bounds)
    }

    @Test
    fun strokeRepeatedInOnlyOneSideIsNotAChange() {
        val repeated = Stroke(6f, listOf(StrokePoint(10f, 10f, 1f)))
        val other = Stroke(6f, listOf(StrokePoint(300f, 200f, 1f)))

        assertNull(computeChangedBounds(CardSide(listOf(repeated)), CardSide(listOf(repeated, repeated)), 1f))
        assertNull(computeChangedBounds(CardSide(listOf(repeated, repeated)), CardSide(listOf(repeated)), 1f))
        assertEquals(
            computeStrokeBounds(other, 1f),
            computeChangedBounds(CardSide(listOf(repeated)), CardSide(listOf(repeated, repeated, other)), 1f),
        )
    }

    @Test
    fun shapeRepeatedInOnlyOneSideIsNotAChange() {
        assertNull(computeChangedBounds(CardSide(shapes = listOf(rectangle)), CardSide(shapes = listOf(rectangle, rectangle)), 1f))
        assertNull(computeChangedBounds(CardSide(shapes = listOf(rectangle, rectangle)), CardSide(shapes = listOf(rectangle)), 1f))
    }

    @Test
    fun listsOfEqualSizeWithDifferentStrokesAreStillCompared() {
        val first = Stroke(6f, listOf(StrokePoint(10f, 10f, 1f)))
        val second = Stroke(6f, listOf(StrokePoint(90f, 90f, 1f)))

        assertEquals(computeStrokeBounds(first, 1f)!!.union(computeStrokeBounds(second, 1f)!!),
            computeChangedBounds(CardSide(listOf(first)), CardSide(listOf(second)), 1f))
    }

    private fun assertPoint(x: Float, y: Float, pressure: Float, actual: StrokePoint) {
        assertEquals(x, actual.x, 1e-3f)
        assertEquals(y, actual.y, 1e-3f)
        assertEquals(pressure, actual.pressure, 1e-6f)
    }
}

/** Список, который считает чтения элементов: по их числу видно, сколько работы делает сравнение сторон. */
private class CountingList<T>(private val items: List<T>) : AbstractList<T>() {
    var reads = 0
        private set

    override val size: Int get() = items.size

    override fun get(index: Int): T {
        reads++
        return items[index]
    }
}
