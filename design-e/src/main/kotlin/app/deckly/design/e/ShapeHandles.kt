package app.deckly.design.e

import app.deckly.ui.contract.ShapeFrame
import app.deckly.ui.contract.ShapeHandleKind
import kotlin.math.cos
import kotlin.math.sin

/** Где в системе координат листа стоит центр ручки и из какой фигуры она. Единицы — пиксели листа. */
internal data class HandlePlace(val handle: ShapeHandleKind, val x: Float, val y: Float)

/**
 * Ручки рамки в пикселях листа: центры целей касания. [scale] — пикселей в единице карточки (лист 1000 единиц в
 * ширину), [density] — пикселей в одном dp. Цели 64 dp, ручка поворота над рамкой на 48 dp выше её верхней кромки
 * (`top: -80px` у цели 64 dp в макете), у линии и стрелок вместо углов и поворота два конца оси на 12 и 88 % ширины.
 * Рамка может быть повёрнута: центры считаются в мире, цель остаётся квадратом в осях самой фигуры
 * ([findHandleAt]).
 */
internal fun listHandlePlaces(frame: ShapeFrame, scale: Float, density: Float): List<HandlePlace> {
    val width = (frame.right - frame.left) * scale
    val height = (frame.bottom - frame.top) * scale
    val centerX = (frame.left + frame.right) / 2f * scale
    val centerY = (frame.top + frame.bottom) / 2f * scale
    val radians = Math.toRadians(frame.angle.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    fun place(handle: ShapeHandleKind, localX: Float, localY: Float) =
        HandlePlace(handle, centerX + localX * cosine - localY * sine, centerY + localX * sine + localY * cosine)
    return if (frame.isLinear) {
        listOf(
            place(ShapeHandleKind.START, -width / 2f + Tokens.ShapeStartShare * width, 0f),
            place(ShapeHandleKind.END, -width / 2f + Tokens.ShapeEndShare * width, 0f),
        )
    } else {
        listOf(
            place(ShapeHandleKind.TOP_LEFT, -width / 2f, -height / 2f),
            place(ShapeHandleKind.TOP_RIGHT, width / 2f, -height / 2f),
            place(ShapeHandleKind.BOTTOM_LEFT, -width / 2f, height / 2f),
            place(ShapeHandleKind.BOTTOM_RIGHT, width / 2f, height / 2f),
            place(ShapeHandleKind.ROTATE, 0f, -height / 2f - (Tokens.ShapeRotateTop.value - Tokens.ShapeHandleTarget.value / 2f) * density),
        )
    }
}

/**
 * Ручка, в цель которой попало касание ([x], [y] в пикселях листа), или `null`: тогда касание — перенос за контур или
 * касание мимо, это решает модель. Цель — квадрат 64 dp в осях фигуры, поэтому у повёрнутой рамки касание сначала
 * поворачивается обратно. Если попали в несколько целей (рамка почти минимальная), берётся ближайшая по центру.
 */
internal fun findHandleAt(frame: ShapeFrame, x: Float, y: Float, scale: Float, density: Float): ShapeHandleKind? {
    val half = Tokens.ShapeHandleTarget.value * density / 2f
    val radians = Math.toRadians(frame.angle.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    return listHandlePlaces(frame, scale, density)
        .filter { place ->
            // Касание в осях фигуры относительно центра ручки: поворот обратно на угол рамки.
            val dx = x - place.x
            val dy = y - place.y
            val localX = dx * cosine + dy * sine
            val localY = -dx * sine + dy * cosine
            kotlin.math.abs(localX) <= half && kotlin.math.abs(localY) <= half
        }
        .minByOrNull { place -> (x - place.x) * (x - place.x) + (y - place.y) * (y - place.y) }
        ?.handle
}
