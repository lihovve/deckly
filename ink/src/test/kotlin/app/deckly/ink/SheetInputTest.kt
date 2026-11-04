package app.deckly.ink

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

        val bounds = computeChangedBounds(listOf(kept, removed), listOf(kept, added), 1f)!!

        assertTrue(bounds.left <= 195 && bounds.right >= 505)
        assertTrue(bounds.top <= 195 && bounds.bottom >= 405)
        // Неизменённый штрих в область не входит.
        assertTrue(bounds.left > 20 && bounds.top > 20)
    }

    @Test
    fun changedBoundsAreNullWhenListsAreEqual() {
        val stroke = Stroke(6f, listOf(StrokePoint(10f, 10f, 1f)))

        assertNull(computeChangedBounds(listOf(stroke), listOf(stroke.copy()), 1f))
        assertNull(computeChangedBounds(emptyList(), emptyList(), 1f))
    }

    @Test
    fun changedBoundsIgnoreOrderOfStrokes() {
        val first = Stroke(6f, listOf(StrokePoint(10f, 10f, 1f)))
        val second = Stroke(6f, listOf(StrokePoint(90f, 90f, 1f)))

        assertNull(computeChangedBounds(listOf(first, second), listOf(second, first), 1f))
    }

    private fun assertPoint(x: Float, y: Float, pressure: Float, actual: StrokePoint) {
        assertEquals(x, actual.x, 1e-3f)
        assertEquals(y, actual.y, 1e-3f)
        assertEquals(pressure, actual.pressure, 1e-6f)
    }
}
