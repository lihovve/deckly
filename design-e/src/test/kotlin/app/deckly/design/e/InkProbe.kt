package app.deckly.design.e

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlin.math.ceil

/** Ширина и высота чернил значка в пикселях; при плотности mdpi 1 dp = 1 px. */
internal data class InkSize(val width: Float, val height: Float)

/** Рамка чернил (чёрных пикселей) в области [xs] × [ys] картинки или `null`, если в области пусто. */
internal fun measureInkBox(bitmap: Bitmap, xs: IntRange, ys: IntRange): InkSize? {
    fun isInk(x: Int, y: Int) = AndroidColor.red(bitmap.getPixel(x, y)) < 128
    val columns = xs.filter { x -> ys.any { y -> isInk(x, y) } }
    if (columns.isEmpty()) return null
    val rows = ys.filter { y -> xs.any { x -> isInk(x, y) } }
    return InkSize((columns.last() - columns.first() + 1).toFloat(), (rows.last() - rows.first() + 1).toFloat())
}

/**
 * Какого размера должны быть чернила значка [id] на листе [sizeDp] × [sizeDp]: габариты контура на сетке 24 плюс линия
 * [strokeWidth], если контур не залит. Значки разных контуров так различаются, а толщина линии заметна на больших значках.
 * Контур обходится по длине, а не по контрольным точкам: у кривых Безье `getBounds()` даёт охват точек, шире самой кривой.
 */
internal fun buildExpectedInk(id: IconId, sizeDp: Float, strokeWidth: Float): InkSize {
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (shape in id.shapes) {
        val inflate = if (shape.isFilled) 0f else strokeWidth / 2f
        val measure = PathMeasure(PathParser().parsePathString(shape.pathData).toPath().asAndroidPath(), false)
        val point = FloatArray(2)
        do {
            val length = measure.length
            var distance = 0f
            while (true) {
                if (measure.getPosTan(minOf(distance, length), point, null)) {
                    left = minOf(left, point[0] - inflate)
                    top = minOf(top, point[1] - inflate)
                    right = maxOf(right, point[0] + inflate)
                    bottom = maxOf(bottom, point[1] + inflate)
                }
                if (distance >= length) break
                distance += PATH_STEP
            }
        } while (measure.nextContour())
    }
    val scale = sizeDp / 24f
    return InkSize((right - left) * scale, (bottom - top) * scale)
}

// Шаг обхода контура в клетках сетки: точность габаритов намного выше пикселя.
private const val PATH_STEP = 0.05f

internal fun assertIconInk(message: String, id: IconId, sizeDp: Float, strokeWidth: Float, actual: InkSize?, tolerance: Float = 1.5f) {
    assertNotNull("$message: значка ${id.name} нет", actual)
    val expected = buildExpectedInk(id, sizeDp, strokeWidth)
    assertEquals("$message: ширина значка ${id.name}", expected.width, actual!!.width, tolerance)
    assertEquals("$message: высота значка ${id.name}", expected.height, actual.height, tolerance)
}

/**
 * Эталон значка: контуры [id] на сетке 24, нарисованные чёрным по белому на листе [sizeDp] с линией [strokeWidth],
 * круглые концы и углы, как в [DecklyIcons].
 */
internal fun renderExpectedIcon(id: IconId, sizeDp: Float, strokeWidth: Float): Bitmap {
    val side = ceil(sizeDp).toInt() + 4
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    canvas.translate(2f, 2f)
    canvas.scale(sizeDp / 24f, sizeDp / 24f)
    for (shape in id.shapes) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = AndroidColor.BLACK
        if (shape.isFilled) {
            paint.style = Paint.Style.FILL
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(PathParser().parsePathString(shape.pathData).toPath().asAndroidPath(), paint)
    }
    return bitmap
}

// Чёрные пиксели области, обрезанные по рамке чернил: маска [строка][столбец], или null, если в области пусто.
private fun cropInkMask(bitmap: Bitmap, xs: IntRange, ys: IntRange): Array<BooleanArray>? {
    val columns = xs.filter { x -> ys.any { y -> bitmap.isInkAt(x, y) } }
    val rows = ys.filter { y -> xs.any { x -> bitmap.isInkAt(x, y) } }
    if (columns.isEmpty()) return null
    return Array(rows.last() - rows.first() + 1) { row ->
        BooleanArray(columns.last() - columns.first() + 1) { column -> bitmap.isInkAt(columns.first() + column, rows.first() + row) }
    }
}

// Доля чёрных пикселей [from], рядом с которыми (в пределах [reach] по каждой оси) есть чёрный пиксель [to].
private fun measureInkOverlap(from: Array<BooleanArray>, to: Array<BooleanArray>, reach: Int): Float {
    var total = 0
    var matched = 0
    for (row in from.indices) for (column in from[row].indices) {
        if (!from[row][column]) continue
        total++
        val isNear = (row - reach..row + reach).any { y ->
            (column - reach..column + reach).any { x -> to.getOrNull(y)?.getOrNull(x) == true }
        }
        if (isNear) matched++
    }
    return if (total == 0) 0f else matched.toFloat() / total
}

/**
 * Значок в области [xs] × [ys] картинки того же размера и той же формы, что эталон [id]: габариты сходятся, а чернила
 * лежат на чернилах эталона и наоборот в пределах 2 px. Зеркальные и близкие по габаритам значки (стрелки влево и
 * вправо, перо и ластик) этим различаются, чего одни габариты не делают.
 */
internal fun assertIconShape(
    message: String,
    bitmap: Bitmap,
    xs: IntRange,
    ys: IntRange,
    id: IconId,
    sizeDp: Float,
    strokeWidth: Float,
    tolerance: Float = 1.5f,
) {
    val actual = cropInkMask(bitmap, xs, ys)
    assertNotNull("$message: значка ${id.name} нет", actual)
    val reference = renderExpectedIcon(id, sizeDp, strokeWidth)
    val expected = cropInkMask(reference, 0 until reference.width, 0 until reference.height)!!
    assertEquals("$message: ширина значка ${id.name}", expected[0].size.toFloat(), actual!![0].size.toFloat(), tolerance)
    assertEquals("$message: высота значка ${id.name}", expected.size.toFloat(), actual.size.toFloat(), tolerance)
    val onExpected = measureInkOverlap(actual, expected, 2)
    val onActual = measureInkOverlap(expected, actual, 2)
    assertTrue("$message: форма значка ${id.name} не совпадает с эталоном (на эталоне $onExpected, эталон на значке $onActual)", onExpected >= 0.9f && onActual >= 0.9f)
}

/** Стиль, которым набран текст узла: кегль, вес, интервал. Узел берётся из несклеенного дерева. */
internal fun readTextStyle(node: SemanticsNodeInteraction): TextStyle {
    val results = mutableListOf<TextLayoutResult>()
    val action = node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
    assertNotNull("у узла нет разметки текста", action)
    assertTrue(action!!.invoke(results))
    return results.single().layoutInput.style
}

/**
 * Значок слева от подписи кнопки или пункта меню: чернила левее текста, без рамки; поля отсекают рамку и линию пункта.
 * Узел с подписью [label] — вся кнопка, текст берётся из несклеенного дерева.
 */
internal fun SemanticsNodeInteractionsProvider.assertIconBeforeLabel(
    label: String,
    id: IconId,
    borderMargin: Int,
    topMargin: Int = borderMargin,
) {
    val labelBounds = onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val node = onNodeWithText(label)
    val nodeBounds = node.getUnclippedBoundsInRoot()
    val bitmap = node.captureToImage().asAndroidBitmap()
    val textLeft = (labelBounds.left.value - nodeBounds.left.value).toInt()
    assertIconShape(label, bitmap, borderMargin until textLeft - 4, topMargin until bitmap.height - topMargin, id, 26f, Tokens.IconStroke)
}

/** Кнопка без подписи: значок [sizeDp] посередине; поле [margin] отсекает рамку выбранной кнопки. */
internal fun assertIconInButton(message: String, button: SemanticsNodeInteraction, id: IconId, sizeDp: Float = 32f, margin: Int = 8) {
    val bitmap = button.captureToImage().asAndroidBitmap()
    assertIconShape(message, bitmap, margin until bitmap.width - margin, margin until bitmap.height - margin, id, sizeDp, Tokens.IconStroke)
}

/** Чёрный ли пиксель (красный канал меньше половины). */
internal fun Bitmap.isInkAt(x: Int, y: Int): Boolean = AndroidColor.red(getPixel(x, y)) < 128
