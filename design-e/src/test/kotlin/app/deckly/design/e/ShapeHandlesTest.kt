package app.deckly.design.e

import app.deckly.ui.contract.ShapeFrame
import app.deckly.ui.contract.ShapeHandleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Центры ручек и попадание касания в цель 64 dp: чистая геометрия, без Android. */
class ShapeHandlesTest {
    // Лист 1000 единиц на 1000 пикселей и один пиксель на dp: числа читаются как единицы карточки.
    private val scale = 1f
    private val density = 1f
    private val plain = ShapeFrame(left = 100f, top = 100f, right = 300f, bottom = 200f, angle = 0f, isLinear = false)

    @Test
    fun plainFrameHasFourCornersAndRotateHandleFortyEightAboveTop() {
        val places = listHandlePlaces(plain, scale, density).associate { it.handle to (it.x to it.y) }

        assertEquals(
            mapOf(
                ShapeHandleKind.TOP_LEFT to (100f to 100f),
                ShapeHandleKind.TOP_RIGHT to (300f to 100f),
                ShapeHandleKind.BOTTOM_LEFT to (100f to 200f),
                ShapeHandleKind.BOTTOM_RIGHT to (300f to 200f),
                // Цель 64 стоит верхом на 80 выше рамки, значит её центр на 48 выше верхней кромки.
                ShapeHandleKind.ROTATE to (200f to 52f),
            ),
            places,
        )
    }

    @Test
    fun linearFrameHasTwoEndsAtTwelveAndEightyEightPercentOfTheAxis() {
        val line = plain.copy(isLinear = true)

        val places = listHandlePlaces(line, scale, density).associate { it.handle to (it.x to it.y) }

        assertEquals(setOf(ShapeHandleKind.START, ShapeHandleKind.END), places.keys)
        assertEquals(124f, places.getValue(ShapeHandleKind.START).first, 0.001f)
        assertEquals(276f, places.getValue(ShapeHandleKind.END).first, 0.001f)
        assertEquals(150f, places.getValue(ShapeHandleKind.START).second, 0.001f)
        assertEquals(150f, places.getValue(ShapeHandleKind.END).second, 0.001f)
    }

    @Test
    fun placesFollowScaleAndDensity() {
        // Лист в 2 пикселя на единицу, 3 пикселя на dp: углы масштабируются, расстояние до поворота считается в dp.
        val places = listHandlePlaces(plain, 2f, 3f).associate { it.handle to (it.x to it.y) }

        assertEquals(200f to 200f, places.getValue(ShapeHandleKind.TOP_LEFT))
        assertEquals(600f to 400f, places.getValue(ShapeHandleKind.BOTTOM_RIGHT))
        assertEquals(400f to 200f - 48f * 3f, places.getValue(ShapeHandleKind.ROTATE))
    }

    @Test
    fun rotatedFrameTurnsHandlePlacesAroundTheCenter() {
        val turned = plain.copy(angle = 90f)

        val places = listHandlePlaces(turned, scale, density).associate { it.handle to (it.x to it.y) }

        // Центр (200, 150); верхний левый угол (−100, −50) по часовой стрелке на 90° уходит в (250, 50).
        assertEquals(250f, places.getValue(ShapeHandleKind.TOP_LEFT).first, 0.001f)
        assertEquals(50f, places.getValue(ShapeHandleKind.TOP_LEFT).second, 0.001f)
        // Ручка поворота была над рамкой, после поворота она справа от неё: (200 + 50 + 48, 150).
        assertEquals(298f, places.getValue(ShapeHandleKind.ROTATE).first, 0.001f)
        assertEquals(150f, places.getValue(ShapeHandleKind.ROTATE).second, 0.001f)
    }

    @Test
    fun touchInsideTargetFindsTheHandleAndTheTargetIsSixtyFourWide() {
        assertEquals(ShapeHandleKind.TOP_LEFT, findHandleAt(plain, 100f + 31f, 100f - 31f, scale, density))
        assertEquals(ShapeHandleKind.TOP_LEFT, findHandleAt(plain, 100f - 32f, 100f + 32f, scale, density))
        assertEquals(ShapeHandleKind.BOTTOM_RIGHT, findHandleAt(plain, 300f + 20f, 200f + 20f, scale, density))
        assertEquals(ShapeHandleKind.ROTATE, findHandleAt(plain, 200f, 52f - 30f, scale, density))
        assertNull("за краем цели 64", findHandleAt(plain, 100f + 33f, 100f, scale, density))
        assertNull("за краем цели 64", findHandleAt(plain, 100f, 100f - 33f, scale, density))
    }

    @Test
    fun touchInsideTheFrameButOutsideAnyTargetIsNotAHandle() {
        assertNull(findHandleAt(plain, 200f, 150f, scale, density))
    }

    @Test
    fun targetSizeFollowsDensity() {
        // При 2 пикселях на dp цель 128 пикселей: полуширина 64.
        assertEquals(ShapeHandleKind.TOP_LEFT, findHandleAt(plain, 100f + 63f, 100f, 1f, 2f))
        assertNull(findHandleAt(plain, 100f + 65f, 100f, 1f, 2f))
    }

    @Test
    fun overlappingTargetsGoToTheNearestCenter() {
        // Рамка 80 × 80: центр ручки поворота (40, 352) в 48 над верхними углами (0, 400) и (80, 400), их цели по 64 пересекаются.
        val small = ShapeFrame(left = 0f, top = 400f, right = 80f, bottom = 480f, angle = 0f, isLinear = false)

        // Точка (20, 380) в обеих целях, до угла ближе.
        assertEquals(ShapeHandleKind.TOP_LEFT, findHandleAt(small, 20f, 380f, scale, density))
        // Точка (30, 370) в обеих целях, до ручки поворота ближе.
        assertEquals(ShapeHandleKind.ROTATE, findHandleAt(small, 30f, 370f, scale, density))
        assertEquals(ShapeHandleKind.TOP_RIGHT, findHandleAt(small, 60f, 380f, scale, density))
    }

    @Test
    fun rotatedTargetIsASquareInTheAxesOfTheShape() {
        val turned = plain.copy(angle = 45f)
        val corner = listHandlePlaces(turned, scale, density).first { it.handle == ShapeHandleKind.TOP_LEFT }

        // Сдвиг на 40 по горизонтали листа в осях фигуры (28,3; −28,3): внутри цели, хотя по осям листа 40 > 32.
        assertEquals(ShapeHandleKind.TOP_LEFT, findHandleAt(turned, corner.x + 40f, corner.y, scale, density))
        assertEquals(ShapeHandleKind.TOP_LEFT, findHandleAt(turned, corner.x, corner.y + 40f, scale, density))
        // Сдвиг на (30, 30) в осях фигуры (42,4; 0): за краем цели, хотя по осям листа 30 < 32.
        assertNull(findHandleAt(turned, corner.x + 30f, corner.y + 30f, scale, density))
    }

    @Test
    fun linearEndTargetsWorkAndNothingElseIsFound() {
        val line = plain.copy(isLinear = true)

        assertEquals(ShapeHandleKind.START, findHandleAt(line, 124f - 20f, 150f + 20f, scale, density))
        assertEquals(ShapeHandleKind.END, findHandleAt(line, 276f + 20f, 150f - 20f, scale, density))
        // Углов рамки у линии нет: касание у левого верхнего угла не находит ручку (до начала 24 + 32 = 56 слева).
        assertNull(findHandleAt(line, 100f, 100f, scale, density))
        assertNull(findHandleAt(line, 200f, 52f, scale, density))
    }
}
