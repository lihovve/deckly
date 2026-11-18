package app.deckly.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.random.Random

/**
 * Штрих целиком вне области отсечения холста не рисуется: перерисовка небольшой области стоит столько, сколько в ней
 * штрихов, а не сколько их на карточке. Счётчики вызовов показывают, что штрихи вне области до холста не доходят, а
 * пиксели области совпадают с теми, что дал бы холст, не знающий отсечения (рисует всё и оставляет отбрасывание Skia).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrokeClippingTest {

    // --- что доходит до холста ----------------------------------------------------------------------

    @Test
    fun strokesEntirelyOutsideTheClipAreNotDrawn() {
        val random = Random(20261117)
        val outside = List(100) { scatterStroke(random, 300f, 900f) }
        val inside = stroke(6f, 1f, 110f to 110f, 120f to 130f, 130f to 110f, 140f to 150f, 150f to 120f)
        val canvas = CountingCanvas(1000)
        canvas.clipRect(CLIP)

        drawStrokes(canvas, outside + inside, 1f)

        assertEquals(4, canvas.lines)
        assertEquals(0, canvas.circles)
    }

    @Test
    fun strokeThatStartsOutsideAndEntersTheClipIsDrawn() {
        val canvas = CountingCanvas(1000)
        canvas.clipRect(CLIP)

        drawStrokes(canvas, listOf(stroke(4f, 1f, 20f to 150f, 80f to 150f, 150f to 150f)), 1f)

        assertEquals(2, canvas.lines)
    }

    @Test
    fun strokeThatReachesTheClipOnlyWithItsEdgeIsDrawn() {
        // Толщина 20, нажим 1: ширина 2 + 21 = 23, половина 11,5. Ось в 8 px левее области, край заходит на 3,5 px.
        val canvas = CountingCanvas(1000)
        canvas.clipRect(CLIP)

        drawStrokes(canvas, listOf(stroke(20f, 1f, 92f to 120f, 92f to 180f)), 1f)
        assertEquals(1, canvas.lines)

        // Одна точка: круг радиусом 11,5 с центром в 5 px левее области.
        drawStrokes(canvas, listOf(stroke(20f, 1f, 95f to 150f)), 1f)
        assertEquals(1, canvas.circles)
    }

    @Test
    fun strokeCloseOutsideTheClipBeyondTheMarginIsNotDrawn() {
        // Ось в 20 px левее области: край ширины 23 доходит до x = 91,5, прямоугольник штриха с запасом — до 93.
        val canvas = CountingCanvas(1000)
        canvas.clipRect(CLIP)

        drawStrokes(canvas, listOf(stroke(20f, 1f, 80f to 120f, 80f to 180f), stroke(20f, 1f, 80f to 150f)), 1f)

        assertEquals(0, canvas.lines)
        assertEquals(0, canvas.circles)
    }

    @Test
    fun everyStrokeIsDrawnWhenTheClipIsTheWholeSheet() {
        val random = Random(20261118)
        val strokes = List(50) { scatterStroke(random, 20f, 980f) } + stroke(4f, 0.5f, 500f to 500f)
        val canvas = CountingCanvas(1000)

        drawStrokes(canvas, strokes, 1f)

        assertEquals(strokes.sumOf { (it.points.size - 1).coerceAtLeast(0) }, canvas.lines)
        assertEquals(1, canvas.circles)
    }

    @Test
    fun strokeWithoutPointsIsNeitherDrawnNorFailsTheClipCheck() {
        val canvas = CountingCanvas(1000)
        canvas.clipRect(CLIP)

        drawStrokes(canvas, listOf(Stroke(6f, emptyList())), 1f)

        assertEquals(0, canvas.lines + canvas.circles)
    }

    // --- пиксели ------------------------------------------------------------------------------------

    @Test
    fun clippedAreaHasTheSamePixelsAsACanvasThatDoesNotKnowTheClip() {
        val scale = 1.74f
        val area = Rect(500, 500, 900, 800)
        val strokes = boundaryStrokes(scale, area) + List(60) { walkStroke(Random(it)) }

        val culled = renderThroughClip(Bitmap.createBitmap(SHEET, SHEET, Bitmap.Config.ARGB_8888), area, strokes, scale, knowsClip = true)
        val everything = renderThroughClip(Bitmap.createBitmap(SHEET, SHEET, Bitmap.Config.ARGB_8888), area, strokes, scale, knowsClip = false)

        assertSamePixels(culled, everything)
        // Область не залита целиком: иначе потерянный штрих был бы не виден.
        val ink = countInk(culled, area)
        assertTrue("чернил $ink из ${area.width() * area.height()}", ink in 8000..70000)
    }

    @Test
    fun clippedAreaMatchesTheSamePlaceOfTheWholeSheetDrawnAtOnce() {
        val scale = 1.74f
        val area = Rect(500, 500, 900, 800)
        val strokes = boundaryStrokes(scale, area) + List(60) { walkStroke(Random(it)) }
        val whole = Bitmap.createBitmap(SHEET, SHEET, Bitmap.Config.ARGB_8888)
        Canvas(whole).also {
            it.drawColor(Color.WHITE)
            drawStrokes(it, strokes, scale)
        }

        val culled = renderThroughClip(Bitmap.createBitmap(SHEET, SHEET, Bitmap.Config.ARGB_8888), area, strokes, scale, knowsClip = true)

        // Растеризатор обрезает штрих по границе области и сглаживает края иначе, чем у целого листа, поэтому допуск, как в
        // `PartialRedrawTest`: потерянный край штриха даёт на ядре линии 175 и больше.
        val worst = (area.top until area.bottom).flatMap { y -> (area.left until area.right).map { x -> x to y } }
            .maxBy { (x, y) -> measureDifference(whole.getPixel(x, y), culled.getPixel(x, y)) }
        val largest = measureDifference(whole.getPixel(worst.first, worst.second), culled.getPixel(worst.first, worst.second))
        assertTrue("расхождение $largest в $worst", largest <= TOLERANCE)
    }

    @Test
    fun strokeCloseOutsideTheClipLeavesNoTraceInsideIt() {
        val scale = 1.74f
        val area = Rect(500, 500, 900, 800)
        // Ось в 7 px левее области, толщина пера 2: край в 1,5 px от оси, запас до области не доходит.
        val outside = Stroke(2f / scale, listOf(StrokePoint(493f / scale, 600f / scale, 0.3f), StrokePoint(493f / scale, 700f / scale, 0.3f)))

        val bitmap = renderThroughClip(Bitmap.createBitmap(SHEET, SHEET, Bitmap.Config.ARGB_8888), area, listOf(outside), scale, knowsClip = true)

        assertEquals(0, countInk(bitmap, area))
    }

    @Test
    fun strokeWhoseEdgeEntersTheClipByHalfAPixelIsStillDrawn() {
        val scale = 1.74f
        val area = Rect(500, 500, 900, 800)
        // Толщина 14 при масштабе 1,74 и нажиме 1: ширина 27,4, радиус 13,7 px. Ось в 13,2 px от области (13,3 с другой
        // стороны): край заходит в неё на полпикселя, и виден он только благодаря запасу на сглаживание. Штрих один:
        // рядом с другими его след в области закрыт.
        val edgeStrokes = listOf(
            vertical((area.left - 13.2f) / scale, 540f / scale, 760f / scale, 14f, 1f),
            vertical((area.right + 13.3f) / scale, 540f / scale, 760f / scale, 14f, 1f),
            horizontal((area.top - 13.2f) / scale, 540f / scale, 860f / scale, 14f, 1f),
            horizontal((area.bottom + 13.3f) / scale, 540f / scale, 860f / scale, 14f, 1f),
        )

        for ((index, edgeStroke) in edgeStrokes.withIndex()) {
            val culled = renderThroughClip(Bitmap.createBitmap(SHEET, SHEET, Bitmap.Config.ARGB_8888), area, listOf(edgeStroke), scale, knowsClip = true)
            val everything = renderThroughClip(Bitmap.createBitmap(SHEET, SHEET, Bitmap.Config.ARGB_8888), area, listOf(edgeStroke), scale, knowsClip = false)

            assertSamePixels(culled, everything)
            assertTrue("у штриха $index в области нет следа", countNonWhite(culled, area) > 100)
        }
    }

    // Штрихи у самой границы области с обеих сторон: толстая ось вне области, а край внутри; точка у границы; вплотную
    // снаружи; пересекающие границу; разные толщины и нажим.
    private fun boundaryStrokes(scale: Float, area: Rect): List<Stroke> {
        fun toCard(pixels: Int): Float = pixels / scale
        val left = area.left
        val right = area.right
        val top = area.top
        val bottom = area.bottom
        return listOf(
            vertical(toCard(left - 5), toCard(top + 40), toCard(bottom - 40), 14f, 1f),
            vertical(toCard(right + 5), toCard(top + 40), toCard(bottom - 40), 14f, 1f),
            vertical(toCard(left - 7), toCard(top + 40), toCard(bottom - 40), 2f, 0.3f),
            horizontal(toCard(top - 5), toCard(left + 40), toCard(right - 40), 14f, 1f),
            horizontal(toCard(bottom + 5), toCard(left + 40), toCard(right - 40), 14f, 0.9f),
            horizontal(toCard(bottom + 9), toCard(left + 40), toCard(right - 40), 6f, 0.5f),
            stroke(14f, 1f, toCard(left - 4) to toCard(650)),
            stroke(14f, 1f, toCard(right + 4) to toCard(650)),
            stroke(3f, 0.6f, toCard(left - 30) to toCard(top - 30), toCard(left + 60) to toCard(top + 60), toCard(left + 120) to toCard(top - 40)),
            stroke(9f, 0.8f, toCard(right - 50) to toCard(bottom - 20), toCard(right + 50) to toCard(bottom + 60)),
            stroke(5f, 1f, toCard(left - 100) to toCard(600), toCard(right + 100) to toCard(620)),
        )
    }

    private fun vertical(x: Float, fromY: Float, toY: Float, width: Float, pressure: Float) =
        stroke(width, pressure, x to fromY, x to toY)

    private fun horizontal(y: Float, fromX: Float, toX: Float, width: Float, pressure: Float) =
        stroke(width, pressure, fromX to y, toX to y)

    // Холст ограничен областью, как у листа с `lockCanvas(dirty)`: в области фон и штрихи, вне её лист остаётся белым.
    private fun renderThroughClip(bitmap: Bitmap, area: Rect, strokes: List<Stroke>, scale: Float, knowsClip: Boolean): Bitmap {
        val canvas = if (knowsClip) Canvas(bitmap) else ClipBlindCanvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.clipRect(area)
        drawStrokes(canvas, strokes, scale)
        return bitmap
    }

    private fun assertSamePixels(actual: Bitmap, expected: Bitmap) {
        val actualPixels = IntArray(SHEET * SHEET).also { actual.getPixels(it, 0, SHEET, 0, 0, SHEET, SHEET) }
        val expectedPixels = IntArray(SHEET * SHEET).also { expected.getPixels(it, 0, SHEET, 0, 0, SHEET, SHEET) }
        val first = actualPixels.indices.firstOrNull { actualPixels[it] != expectedPixels[it] }
        assertTrue("первое расхождение в (${first?.rem(SHEET)}, ${first?.div(SHEET)})", first == null)
    }

    private fun measureDifference(first: Int, second: Int): Int =
        maxOf(abs(Color.red(first) - Color.red(second)), abs(Color.green(first) - Color.green(second)), abs(Color.blue(first) - Color.blue(second)))

    private fun countNonWhite(bitmap: Bitmap, area: Rect): Int =
        (area.top until area.bottom).sumOf { y -> (area.left until area.right).count { x -> bitmap.getPixel(x, y) != Color.WHITE } }

    private fun countInk(bitmap: Bitmap, area: Rect): Int =
        (area.top until area.bottom).sumOf { y -> (area.left until area.right).count { x -> Color.red(bitmap.getPixel(x, y)) < 128 } }

    private fun stroke(width: Float, pressure: Float, vararg points: Pair<Float, Float>): Stroke =
        Stroke(width, points.map { StrokePoint(it.first, it.second, pressure) })

    // Штрих из 20 точек в квадрате [from, to] с нажимом по зерну.
    private fun scatterStroke(random: Random, from: Float, to: Float): Stroke =
        Stroke(
            4f,
            List(20) { StrokePoint(from + random.nextFloat() * (to - from), from + random.nextFloat() * (to - from), random.nextFloat()) },
        )

    // Письмо: 25 точек блуждания по листу, толщина 2..7 и нажим по зерну.
    private fun walkStroke(random: Random): Stroke {
        var x = random.nextFloat() * 1000f
        var y = random.nextFloat() * 1000f
        return Stroke(
            2f + random.nextInt(6),
            List(25) {
                x = (x + (random.nextFloat() * 2 - 1) * 25f).coerceIn(0f, 1000f)
                y = (y + (random.nextFloat() * 2 - 1) * 25f).coerceIn(0f, 1000f)
                StrokePoint(x, y, random.nextFloat())
            },
        )
    }

    /** Холст над [Bitmap], который считает вызовы, дошедшие до рисования отрезков и кругов. */
    private class CountingCanvas(size: Int) : Canvas(Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)) {
        var lines = 0
            private set
        var circles = 0
            private set

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            lines++
            super.drawLine(startX, startY, stopX, stopY, paint)
        }

        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
            circles++
            super.drawCircle(cx, cy, radius, paint)
        }
    }

    /** Холст, который отсечения не знает: `quickReject` никогда не отбрасывает, и рисуется всё, как до отсева. */
    private class ClipBlindCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        override fun quickReject(left: Float, top: Float, right: Float, bottom: Float): Boolean = false
    }

    private companion object {
        const val SHEET = 1740
        const val TOLERANCE = 96
        val CLIP = Rect(100, 100, 200, 200)
    }
}
