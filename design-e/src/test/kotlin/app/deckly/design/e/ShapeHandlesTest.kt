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
    fun localPlacesAreMeasuredFromTheTopLeftCornerInTheAxesOfTheFrame() {
        val places = listLocalHandlePlaces(plain, scale, density).associate { it.handle to (it.x to it.y) }

        // Рамка 200 × 100: углы в углах, ручка поворота по оси на 48 выше верхней стороны.
        assertEquals(
            mapOf(
                ShapeHandleKind.TOP_LEFT to (0f to 0f),
                ShapeHandleKind.TOP_RIGHT to (200f to 0f),
                ShapeHandleKind.BOTTOM_LEFT to (0f to 100f),
                ShapeHandleKind.BOTTOM_RIGHT to (200f to 100f),
                ShapeHandleKind.ROTATE to (100f to -48f),
            ),
            places,
        )
    }

    @Test
    fun localPlacesOfALineAreOnTheAxisAtTwelveAndEightyEightPercent() {
        val places = listLocalHandlePlaces(plain.copy(isLinear = true), scale, density).associate { it.handle to (it.x to it.y) }

        assertEquals(setOf(ShapeHandleKind.START, ShapeHandleKind.END), places.keys)
        assertEquals(24f, places.getValue(ShapeHandleKind.START).first, 0.001f)
        assertEquals(176f, places.getValue(ShapeHandleKind.END).first, 0.001f)
        assertEquals(50f, places.getValue(ShapeHandleKind.START).second, 0.001f)
        assertEquals(50f, places.getValue(ShapeHandleKind.END).second, 0.001f)
    }

    @Test
    fun localPlacesFollowScaleAndDensity() {
        val places = listLocalHandlePlaces(plain, 2f, 3f).associate { it.handle to (it.x to it.y) }

        assertEquals(400f to 200f, places.getValue(ShapeHandleKind.BOTTOM_RIGHT))
        assertEquals(200f to -48f * 3f, places.getValue(ShapeHandleKind.ROTATE))
    }

    @Test
    fun worldPlacesOfAFrameTurnedByThirtyDegreesAreTheLocalOnesTurnedAroundTheCenter() {
        val turned = plain.copy(angle = 30f)

        val places = listHandlePlaces(turned, scale, density).associate { it.handle to (it.x to it.y) }

        // Центр (200, 150). Верхний левый угол (−100, −50) по часовой на 30°: (−100·cos + 50·sin, −100·sin − 50·cos).
        assertEquals(138.4f, places.getValue(ShapeHandleKind.TOP_LEFT).first, 0.05f)
        assertEquals(56.7f, places.getValue(ShapeHandleKind.TOP_LEFT).second, 0.05f)
        // Ручка поворота (0, −98): (49, −84,9).
        assertEquals(249f, places.getValue(ShapeHandleKind.ROTATE).first, 0.05f)
        assertEquals(65.1f, places.getValue(ShapeHandleKind.ROTATE).second, 0.05f)
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

    // --- ручка поворота под рамкой ---------------------------------------------------------------

    @Test
    fun rotateHandleBelowStandsFortyEightUnderTheMiddleOfTheBottomSide() {
        val local = listLocalHandlePlaces(plain, scale, density, RotateSide.BELOW).first { it.handle == ShapeHandleKind.ROTATE }
        val world = listHandlePlaces(plain, scale, density, RotateSide.BELOW).first { it.handle == ShapeHandleKind.ROTATE }

        // Рамка 200 × 100 с левым верхним углом (100, 100): низ на 200, центр ручки на 48 ниже.
        assertEquals(100f to 148f, local.x to local.y)
        assertEquals(200f to 248f, world.x to world.y)
    }

    @Test
    fun rotateHandleBelowFollowsScaleDensityAndTurn() {
        val scaled = listLocalHandlePlaces(plain, 2f, 3f, RotateSide.BELOW).first { it.handle == ShapeHandleKind.ROTATE }
        assertEquals(200f to 200f + 48f * 3f, scaled.x to scaled.y)

        // Под рамкой, повёрнутой на 90°, ручка уходит влево: центр (200, 150), вниз на 50 + 48 в осях фигуры.
        val turned = listHandlePlaces(plain.copy(angle = 90f), scale, density, RotateSide.BELOW).first { it.handle == ShapeHandleKind.ROTATE }
        assertEquals(102f, turned.x, 0.001f)
        assertEquals(150f, turned.y, 0.001f)
    }

    @Test
    fun otherHandlesDoNotDependOnTheSideOfTheRotateHandle() {
        val above = listHandlePlaces(plain, scale, density, RotateSide.ABOVE).filter { it.handle != ShapeHandleKind.ROTATE }
        val below = listHandlePlaces(plain, scale, density, RotateSide.BELOW).filter { it.handle != ShapeHandleKind.ROTATE }

        assertEquals(above, below)
    }

    @Test
    fun touchFindsTheRotateHandleOnTheSideItStandsOn() {
        assertEquals(ShapeHandleKind.ROTATE, findHandleAt(plain, 200f, 248f, scale, density, RotateSide.BELOW))
        assertNull("над рамкой её уже нет", findHandleAt(plain, 200f, 52f, scale, density, RotateSide.BELOW))
        assertEquals(ShapeHandleKind.ROTATE, findHandleAt(plain, 200f, 52f, scale, density, RotateSide.ABOVE))
        assertNull("под рамкой её ещё нет", findHandleAt(plain, 200f, 248f, scale, density, RotateSide.ABOVE))
    }

    // Лист 1000 × 1000 пикселей, 1 пиксель на dp и на единицу карточки; цель 64, центр ручки на 48 от рамки.
    private fun choose(current: RotateSide, frame: ShapeFrame) = chooseRotateSide(current, frame, scale, density, 1000f, 1000f)

    @Test
    fun handleStaysAboveWhenItsTargetFitsThere() {
        // Верх рамки на 90: центр цели на 42, цель 64 занимает 10…74: помещается.
        val frame = ShapeFrame(left = 400f, top = 90f, right = 600f, bottom = 300f, angle = 0f, isLinear = false)

        assertEquals(RotateSide.ABOVE, choose(RotateSide.ABOVE, frame))
    }

    @Test
    fun handleGoesBelowWhenTheTargetDoesNotFitAboveButFitsBelow() {
        val frame = ShapeFrame(left = 400f, top = 60f, right = 600f, bottom = 300f, angle = 0f, isLinear = false)

        assertEquals(RotateSide.BELOW, choose(RotateSide.ABOVE, frame))
    }

    @Test
    fun sideIsStickyAndChangesOnlyWhenTheCurrentOneDoesNotFit() {
        // Рамка посередине: цель помещается и над, и под. Какая сторона была, та и остаётся.
        val middle = ShapeFrame(left = 400f, top = 400f, right = 600f, bottom = 600f, angle = 0f, isLinear = false)
        assertEquals(RotateSide.BELOW, choose(RotateSide.BELOW, middle))
        assertEquals(RotateSide.ABOVE, choose(RotateSide.ABOVE, middle))
        // Под рамкой не помещается (низ на 960: центр на 1008), а над помещается: возвращается наверх.
        val low = ShapeFrame(left = 400f, top = 700f, right = 600f, bottom = 960f, angle = 0f, isLinear = false)
        assertEquals(RotateSide.ABOVE, choose(RotateSide.BELOW, low))
    }

    @Test
    fun sideStaysWhereItIsWhenItFitsNowhere() {
        // Рамка во весь лист: ни над, ни под цель не помещается.
        val whole = ShapeFrame(left = 0f, top = 0f, right = 1000f, bottom = 1000f, angle = 0f, isLinear = false)

        assertEquals(RotateSide.ABOVE, choose(RotateSide.ABOVE, whole))
        assertEquals(RotateSide.BELOW, choose(RotateSide.BELOW, whole))
    }

    @Test
    fun rotatedTargetIsCheckedByItsRotatedCorners() {
        // Рамка 200 × 100 по центру (500, 100): над ней на 0° цель с углами от y = 100 − 50 − 48 − 32 = −30: не помещается.
        // Повёрнутая на 90° ручка уходит вправо, и цель по горизонтали помещается; повёрнутая на 45° её угол выходит за верх.
        val top = ShapeFrame(left = 400f, top = 50f, right = 600f, bottom = 150f, angle = 0f, isLinear = false)
        assertEquals(RotateSide.BELOW, choose(RotateSide.ABOVE, top))
        assertEquals(RotateSide.ABOVE, choose(RotateSide.ABOVE, top.copy(angle = 90f)))
        // Рамка по центру (500, 110), на 45°: центр ручки над ней в (569,3; 40,7), по осям цель 8,7…72,7 помещается, но её
        // повёрнутый угол выше центра на 32·√2 = 45,3 и уходит за верх листа на 4,6.
        val corner = ShapeFrame(left = 400f, top = 60f, right = 600f, bottom = 160f, angle = 45f, isLinear = false)
        assertEquals(RotateSide.BELOW, choose(RotateSide.ABOVE, corner))
    }

    @Test
    fun targetCannotStickOutAtTheSideOfTheSheetEither() {
        // Рамка у правого края: на 90° ручка над рамкой уходит вправо и вылезает за край листа, под рамкой она слева.
        val right = ShapeFrame(left = 850f, top = 450f, right = 950f, bottom = 550f, angle = 90f, isLinear = false)

        assertEquals(RotateSide.BELOW, choose(RotateSide.ABOVE, right))
    }

    @Test
    fun lineHasNoRotateHandleAndKeepsTheSide() {
        val line = ShapeFrame(left = 100f, top = 10f, right = 300f, bottom = 90f, angle = 0f, isLinear = true)

        assertEquals(RotateSide.ABOVE, choose(RotateSide.ABOVE, line))
        assertEquals(RotateSide.BELOW, choose(RotateSide.BELOW, line))
    }
}
