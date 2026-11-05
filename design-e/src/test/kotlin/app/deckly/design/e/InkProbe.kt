package app.deckly.design.e

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

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

/** Стиль, которым набран текст узла: кегль, вес, интервал. Узел берётся из несклеенного дерева. */
internal fun readTextStyle(node: SemanticsNodeInteraction): TextStyle {
    val results = mutableListOf<TextLayoutResult>()
    val action = node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
    assertNotNull("у узла нет разметки текста", action)
    assertTrue(action!!.invoke(results))
    return results.single().layoutInput.style
}
