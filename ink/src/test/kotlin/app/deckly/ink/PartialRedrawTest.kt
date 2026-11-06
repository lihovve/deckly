package app.deckly.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.random.Random

/**
 * Перерисовка только изменившейся области (`OnyxDrawingView.commitReplacement`): к старой картинке дорисовывается область
 * [computeChangedBounds], и получается то же изображение, что при перерисовке всего листа, с точностью до сглаживания
 * краёв. Если запас границ мал, на листе остаётся хвост от старого положения фигуры или обрезанный край нового: на JVM это
 * видно по пикселям, а сам `OnyxDrawingView` на JVM не проверить. Масштаб как на планшете: 1740 пикселей листа на 1000 единиц карточки.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartialRedrawTest {
    private val scale = 1.74f
    private val sheetSize = 1740

    // Штрихи через весь лист: фигуры при переносе скользят по ним, и пересечения контура со штрихом попадают в границы.
    private val strokes = listOf(
        Stroke(4f, listOf(StrokePoint(50f, 100f, 0.4f), StrokePoint(500f, 300f, 0.9f), StrokePoint(950f, 520f, 0.6f))),
        Stroke(9f, listOf(StrokePoint(100f, 900f, 1f), StrokePoint(480f, 520f, 0.5f), StrokePoint(900f, 80f, 0.8f))),
        Stroke(14f, listOf(StrokePoint(60f, 500f, 0.7f), StrokePoint(940f, 500f, 0.7f))),
        Stroke(2f, listOf(StrokePoint(500f, 40f, 0.2f), StrokePoint(520f, 960f, 1f))),
    )

    private val startShapes = Shape.Kind.entries.mapIndexed { index, kind ->
        val left = 80f + (index % 5) * 170f
        val top = 120f + (index / 5) * 270f
        Shape(kind, left, top, left + 150f + index * 4f, top + 130f + index * 3f, index * 11f, 2f + index % 6 * 2f)
    }

    @Test
    fun redrawingTheChangedAreaGivesTheSameImageAsRedrawingTheWholeSheet() {
        val random = Random(20261106)
        var shown = CardSide(strokes, startShapes)
        val surface = renderSide(shown)

        repeat(40) { step ->
            val next = shown.copy(shapes = shown.shapes.toMutableList().also { shapes ->
                val index = random.nextInt(shapes.size)
                shapes[index] = changeShape(shapes[index], step % 4, random)
            })

            val area = redrawChangedArea(surface, shown, next)

            assertSameImage("шаг $step", surface, renderSide(next), area)
            shown = next
        }
    }

    @Test
    fun redrawingTheChangedAreaAfterRemovingAndReturningStrokesGivesTheSameImage() {
        val full = CardSide(strokes, startShapes)
        val surface = renderSide(full)
        var shown = full

        for (variant in listOf(full.copy(strokes = strokes.drop(1)), full, full.copy(strokes = strokes.take(2)), full.copy(strokes = emptyList()), full)) {
            val area = redrawChangedArea(surface, shown, variant)

            assertSameImage("штрихов ${variant.strokes.size}", surface, renderSide(variant), area)
            shown = variant
        }
    }

    @Test
    fun redrawingTheChangedAreaWhenTheSideIsReplacedByAnotherGivesTheSameImage() {
        val front = CardSide(strokes, startShapes.take(6))
        val back = CardSide(strokes.reversed().take(2), startShapes.drop(8))
        val surface = renderSide(front)

        val toBack = redrawChangedArea(surface, front, back)
        assertSameImage("лицо → оборот", surface, renderSide(back), toBack)

        val toFront = redrawChangedArea(surface, back, front)
        assertSameImage("оборот → лицо", surface, renderSide(front), toFront)
    }

    // Изменение: 0 — перенос, 1 — поворот, 2 — растяжение, 3 — другая толщина и вид. Рамка остаётся в пределах листа.
    private fun changeShape(shape: Shape, change: Int, random: Random): Shape =
        when (change) {
            0 -> {
                val dx = random.nextFloat() * 300f - 150f
                val dy = random.nextFloat() * 300f - 150f
                val shiftX = dx.coerceIn(-shape.left, 1000f - shape.right)
                val shiftY = dy.coerceIn(-shape.top, 1000f - shape.bottom)
                shape.copy(left = shape.left + shiftX, right = shape.right + shiftX, top = shape.top + shiftY, bottom = shape.bottom + shiftY)
            }
            1 -> shape.copy(angle = shape.angle + 10f + random.nextFloat() * 160f)
            2 -> shape.copy(
                right = (shape.right + random.nextFloat() * 160f - 80f).coerceIn(shape.left + 40f, 1000f),
                bottom = (shape.bottom + random.nextFloat() * 160f - 80f).coerceIn(shape.top + 40f, 1000f),
            )
            else -> shape.copy(
                width = 1f + random.nextInt(12),
                kind = Shape.Kind.entries[random.nextInt(Shape.Kind.entries.size)],
            )
        }

    private fun renderSide(side: CardSide): Bitmap {
        val bitmap = Bitmap.createBitmap(sheetSize, sheetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawCardSide(canvas, side, scale)
        return bitmap
    }

    // То же, что делает лист: холст ограничен областью (`lockCanvas(dirty)` оставляет остальное от прошлого кадра), в ней
    // фон и вся сторона.
    private fun redrawChangedArea(surface: Bitmap, before: CardSide, after: CardSide): PixelBounds {
        val area = computeChangedBounds(before, after, scale)
        assertNotNull("стороны разные, а области нет", area)
        val canvas = Canvas(surface)
        canvas.clipRect(
            area!!.left.coerceAtLeast(0),
            area.top.coerceAtLeast(0),
            area.right.coerceAtMost(sheetSize),
            area.bottom.coerceAtMost(sheetSize),
        )
        canvas.drawColor(Color.WHITE)
        drawCardSide(canvas, after, scale)
        return area
    }

    // Совпадение с допуском. Растеризатор обрезает контур по границе области и разбивает кривые на отрезки иначе, чем у
    // целого листа, поэтому сглаженные края кривых расходятся на десятки уровней серого (по 40 шагам не больше 59 из 255).
    // Остаток старой фигуры или обрезанный край дают на ядре линии 175 и больше, так что допуск 96 их не пропускает.
    private fun assertSameImage(name: String, actual: Bitmap, expected: Bitmap, area: PixelBounds) {
        val actualPixels = IntArray(sheetSize * sheetSize).also { actual.getPixels(it, 0, sheetSize, 0, 0, sheetSize, sheetSize) }
        val expectedPixels = IntArray(sheetSize * sheetSize).also { expected.getPixels(it, 0, sheetSize, 0, 0, sheetSize, sheetSize) }
        val worst = actualPixels.indices.maxBy { measureDifference(actualPixels[it], expectedPixels[it]) }
        val largest = measureDifference(actualPixels[worst], expectedPixels[worst])

        assertTrue("$name: расхождение $largest в (${worst % sheetSize}, ${worst / sheetSize}), область $area", largest <= TOLERANCE)
    }

    private fun measureDifference(first: Int, second: Int): Int =
        maxOf(abs(Color.red(first) - Color.red(second)), abs(Color.green(first) - Color.green(second)), abs(Color.blue(first) - Color.blue(second)))

    private companion object {
        const val TOLERANCE = 96
    }
}
