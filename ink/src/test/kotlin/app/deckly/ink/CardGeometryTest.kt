package app.deckly.ink

import org.junit.Assert.assertEquals
import org.junit.Test

class CardGeometryTest {
    // Числа замеров PEN_FOUNTAIN_CURVE из итогов прототипа: толщина пера 6 px на листе,
    // минимум 2 px при нулевом нажиме, максимум w + 3 = 9 px при полном.
    @Test
    fun widthFollowsFountainFormula() {
        val scale = 1.83f
        val width = 6f / scale

        assertEquals(2.00f, computePointWidthPx(width, 0f, scale), DELTA)
        assertEquals(9.00f, computePointWidthPx(width, 1f, scale), DELTA)
        assertEquals(3.75f, computePointWidthPx(width, 0.5f, scale), DELTA)
    }

    // Толстое перо (10 px): минимум от толщины не зависит, максимум w + 3 = 13 px.
    @Test
    fun minimumWidthDoesNotDependOnThickness() {
        val scale = 1.83f
        val width = 10f / scale

        assertEquals(2.00f, computePointWidthPx(width, 0f, scale), DELTA)
        assertEquals(13.00f, computePointWidthPx(width, 1f, scale), DELTA)
    }

    @Test
    fun unitsRoundTrip() {
        val scale = 1.83f

        for (pixels in listOf(0f, 3f, 123.45f, 1830f)) {
            assertEquals(pixels, CardGeometry.toPixels(CardGeometry.toCardUnits(pixels, scale), scale), DELTA)
        }
        assertEquals(1000f, CardGeometry.toCardUnits(1830f, scale), DELTA)
        assertEquals(1830f, CardGeometry.toPixels(1000f, scale), DELTA)
    }

    @Test
    fun scaleUsesCardWidth() {
        assertEquals(1.83f, CardGeometry.computeScale(1830f), 1e-6f)
        assertEquals(1f, CardGeometry.computeScale(CardGeometry.WIDTH), 1e-6f)
        // Высота листа — по пропорции листа макета 928 × 963.
        assertEquals(1000f * 963f / 928f, CardGeometry.HEIGHT, 1e-3f)
    }

    private companion object {
        const val DELTA = 1e-3f
    }
}
