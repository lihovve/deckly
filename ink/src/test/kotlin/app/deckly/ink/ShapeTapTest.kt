package app.deckly.ink

import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Касание пером у контура выделяет фигуру, а штрих рисуется во всех остальных случаях. Лист 1000 px: масштаб 1, пиксели
 * совпадают с единицами карточки; другие масштабы проверяются отдельно.
 */
class ShapeTapTest {
    // Рамка 200 × 300: левый край контура x = 100 + 14 % · 200 = 128, верхний y = 200 + 20 % · 300 = 260.
    private val rectangle = Shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, 0f, 6f)

    private fun path(vararg xy: Pair<Float, Float>) = xy.map { RawPoint(it.first, it.second, 0.5f) }

    @Test
    fun tapNearOutlineSelectsShape() {
        assertEquals(0, findTappedShape(path(130f to 350f), listOf(rectangle), 1f))
        assertEquals(0, findTappedShape(path(128f to 350f), listOf(rectangle), 1f))
        assertEquals(0, findTappedShape(path(200f to 262f), listOf(rectangle), 1f))
    }

    @Test
    fun tapWithSmallJitterIsStillATap() {
        val jitter = path(130f to 350f, 133f to 352f, 128f to 349f, 131f to 351f)

        assertEquals(0, findTappedShape(jitter, listOf(rectangle), 1f))
    }

    @Test
    fun dotInsideShapeIsAStroke() {
        assertNull(findTappedShape(path(200f to 350f), listOf(rectangle), 1f))
        assertNull(findTappedShape(path(200f to 350f, 203f to 352f), listOf(rectangle), 1f))
    }

    @Test
    fun longStrokeAcrossOutlineIsAStroke() {
        // Штрих пересекает контур, но он длиннее касания: это письмо, а не выбор.
        assertNull(findTappedShape(path(100f to 350f, 130f to 350f, 160f to 350f), listOf(rectangle), 1f))
    }

    @Test
    fun strokeStartingAtOutlineButLongerThanTwelvePixelsIsAStroke() {
        assertNull(findTappedShape(path(128f to 350f, 128f to 365f), listOf(rectangle), 1f))
    }

    @Test
    fun tapNeverSpreadsMoreThanTwelvePixelsOfTheSheetWhateverTheScale() {
        val onOutline = 128f * 0.5f
        val near = path(onOutline to 175f, onOutline + 11.9f to 175f)
        val far = path(onOutline to 175f, onOutline + 12.1f to 175f)

        // Порог — 12 px листа, а не единиц карточки: при масштабе 0,5 это 24 единицы.
        assertEquals(0, findTappedShape(near, listOf(rectangle), 0.5f))
        assertNull(findTappedShape(far, listOf(rectangle), 0.5f))
    }

    @Test
    fun pointsAreReadInPixelsOfTheSheet() {
        // Масштаб 2: контур в пикселях лежит в 256, а не в 128.
        assertEquals(0, findTappedShape(path(258f to 700f), listOf(rectangle), 2f))
        assertNull(findTappedShape(path(130f to 700f), listOf(rectangle), 2f))
    }

    @Test
    fun ribbonIsHalfOfOutlineWidthPlusEightDesignUnits() {
        val ribbon = 3f + 8f * CardUnits.PER_DESIGN_UNIT

        assertEquals(0, findTappedShape(path(128f - (ribbon - 0.1f) to 350f), listOf(rectangle), 1f))
        assertNull(findTappedShape(path(128f - (ribbon + 0.1f) to 350f), listOf(rectangle), 1f))
    }

    @Test
    fun ofTwoTouchedShapesTheLastOneWins() {
        val outer = Shape(Shape.Kind.RECTANGLE, 110f, 210f, 310f, 510f, 0f, 6f)

        assertEquals(1, findTappedShape(path(129f to 350f), listOf(rectangle, outer), 1f))
    }

    @Test
    fun noShapesAndNoPointsGiveNothing() {
        assertNull(findTappedShape(path(130f to 350f), emptyList(), 1f))
        assertNull(findTappedShape(emptyList(), listOf(rectangle), 1f))
    }
}
