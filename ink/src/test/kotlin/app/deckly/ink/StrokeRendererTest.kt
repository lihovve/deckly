package app.deckly.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Рисует на настоящем [Bitmap] (нативная графика Robolectric) и проверяет пиксели. Пиксель считается чернилами, если закрашен больше
 * чем наполовину: границы штриха сглажены, поэтому точные значения на краю не проверяются, а
 * контрольные пиксели выбраны так, чтобы проверяемое правило решало, чёрный пиксель или белый.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrokeRendererTest {
    @Test
    fun singlePointStrokeIsRoundDot() {
        // Толщина пера 6, нажим 1: диаметр 2 + (6 + 1) = 9, радиус 4,5.
        val bitmap = drawOnWhite(1f, stroke(6f, point(100f, 100f, 1f)))

        assertTrue(bitmap.isInk(100, 100))
        assertTrue(bitmap.isInk(103, 100))
        assertFalse(bitmap.isInk(106, 100))
        // Угол квадрата 9 × 9 был бы чёрным, у круга он белый: центр пикселя в 4,95 от центра точки.
        assertFalse(bitmap.isInk(103, 103))
    }

    @Test
    fun segmentWidthIsAverageOfPointWidths() {
        // Ширины точек 2 (нажим 0) и 9 (нажим 1), среднее 5,5: линия занимает y от 97,25 до 102,75.
        // Ширина при среднем нажиме 0,5 была бы 3,75: пиксель в строке 97 остался бы белым.
        val stroke = stroke(6f, point(50f, 100f, 0f), point(150f, 100f, 1f))
        val bitmap = drawOnWhite(1f, stroke)

        assertTrue(bitmap.isInk(100, 97))
        assertTrue(bitmap.isInk(100, 102))
        assertFalse(bitmap.isInk(100, 96))
        assertFalse(bitmap.isInk(100, 103))
    }

    @Test
    fun segmentEndsAreRound() {
        // Конец отрезка шириной 5,5 в точке (150, 100): круглый колпачок радиусом 2,75.
        val bitmap = drawOnWhite(1f, stroke(6f, point(50f, 100f, 0f), point(150f, 100f, 1f)))

        assertTrue(bitmap.isInk(151, 100))
        // Квадратный колпачок закрасил бы и этот угол (в 3,5 от конца), круглый — нет.
        assertFalse(bitmap.isInk(152, 102))
        // Плоский колпачок не дошёл бы до пикселя за концом.
        assertTrue(bitmap.isInk(152, 100))
    }

    @Test
    fun everySegmentHasItsOwnWidth() {
        // Три точки с нажимом 1, 1, 0 и ширинами 9, 9, 2: у первого отрезка ширина 9, у второго
        // (9 + 2) / 2 = 5,5. Единая ширина на весь штрих не прошла бы ни одну из двух проверок.
        val stroke = stroke(6f, point(50f, 100f, 1f), point(150f, 100f, 1f), point(250f, 100f, 0f))
        val bitmap = drawOnWhite(1f, stroke)

        // Первый отрезок: ширина 9, y от 95,5 до 104,5.
        assertTrue(bitmap.isInk(100, 96))
        assertTrue(bitmap.isInk(100, 103))
        // Второй отрезок: ширина 5,5, y от 97,25 до 102,75.
        assertFalse(bitmap.isInk(200, 95))
        assertTrue(bitmap.isInk(200, 98))
        assertFalse(bitmap.isInk(200, 105))
    }

    @Test
    fun scaleMultipliesCoordinatesAndThickness() {
        // Масштаб 2, толщина штриха 3 единицы карточки = 6 px: диаметр точки при нажиме 1 равен 9 px,
        // центр — (50, 50) · 2 = (100, 100).
        val bitmap = drawOnWhite(2f, stroke(3f, point(50f, 50f, 1f)))

        assertTrue(bitmap.isInk(100, 100))
        assertTrue(bitmap.isInk(103, 100))
        assertFalse(bitmap.isInk(106, 100))
        assertFalse(bitmap.isInk(50, 50))
    }

    @Test
    fun scaleMultipliesSegmentEndpoints() {
        // Масштаб 2: отрезок (25, 50) → (75, 50) единиц карточки идёт по y = 100 от x = 50 до x = 150 px.
        val bitmap = drawOnWhite(2f, stroke(3f, point(25f, 50f, 1f), point(75f, 50f, 1f)))

        assertTrue(bitmap.isInk(100, 100))
        assertTrue(bitmap.isInk(140, 100))
        assertFalse(bitmap.isInk(100, 50))
        assertFalse(bitmap.isInk(40, 100))
    }

    @Test
    fun strokeWithoutPointsDrawsNothing() {
        val bitmap = drawOnWhite(1f, Stroke(6f, emptyList()))

        for (y in 0 until bitmap.height step 10) {
            for (x in 0 until bitmap.width step 10) {
                assertFalse("($x, $y)", bitmap.isInk(x, y))
            }
        }
    }

    @Test
    fun strokesAreDrawnInGivenOrder() {
        // Оба штриха рисуются, независимо друг от друга: у каждого свои толщина и нажим.
        val first = stroke(6f, point(50f, 50f, 1f))
        val second = stroke(6f, point(150f, 150f, 1f))
        val bitmap = drawOnWhite(1f, first, second)

        assertTrue(bitmap.isInk(50, 50))
        assertTrue(bitmap.isInk(150, 150))
        assertFalse(bitmap.isInk(100, 100))
    }

    @Test
    fun drawingDoesNotChangeStrokes() {
        val stroke = stroke(6f, point(50f, 100f, 0f), point(150f, 100f, 1f))
        val before = stroke.copy()

        drawOnWhite(1f, stroke)

        assertEquals(before, stroke)
    }

    @Test
    fun strokeLooksTheSameAsItsPiecesOfTwoPointsDrawnOneByOne() {
        // Линия при касании мимо фигуры показывается кусками из двух точек, на подъёме куски заменяет один штрих: картинка
        // не должна измениться. Штрих рисуется цепочкой независимых отрезков, поэтому совпадают все пиксели.
        val points = listOf(
            point(30f, 40f, 0.2f), point(90f, 60f, 0.9f), point(150f, 50f, 0.5f), point(210f, 120f, 1f), point(260f, 250f, 0.1f),
        )
        val whole = drawOnWhite(1.74f, Stroke(14f, points))

        val pieces = drawOnWhite(1.74f, *points.zipWithNext { from, to -> Stroke(14f, listOf(from, to)) }.toTypedArray())

        assertTrue(whole.sameAs(pieces))
    }

    private fun drawOnWhite(scale: Float, vararg strokes: Stroke): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawStrokes(canvas, strokes.toList(), scale)
        return bitmap
    }

    private fun stroke(width: Float, vararg points: StrokePoint) = Stroke(width, points.toList())

    private fun point(x: Float, y: Float, pressure: Float) = StrokePoint(x, y, pressure)

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = Color.red(getPixel(x, y)) < 128

    private companion object {
        const val SIZE = 300
    }
}
