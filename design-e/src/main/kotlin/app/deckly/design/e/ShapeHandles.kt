package app.deckly.design.e

import app.deckly.ui.contract.ShapeFrame
import app.deckly.ui.contract.ShapeHandleKind
import kotlin.math.cos
import kotlin.math.sin

/** Где в системе координат листа стоит центр ручки и из какой фигуры она. Единицы — пиксели листа. */
internal data class HandlePlace(val handle: ShapeHandleKind, val x: Float, val y: Float)

/**
 * Центры ручек в осях рамки до поворота, от её левого верхнего угла, в пикселях листа: по ним рисуются знаки, и они же
 * дают [listHandlePlaces]. [scale] — пикселей в единице карточки (лист 1000 единиц в ширину), [density] — пикселей в одном
 * dp. Углы стоят в углах рамки, ручка поворота по оси на 48 dp выше верхней стороны, у линии и стрелок вместо них два
 * конца оси на 12 и 88 % ширины.
 */
internal fun listLocalHandlePlaces(frame: ShapeFrame, scale: Float, density: Float): List<HandlePlace> {
    val width = (frame.right - frame.left) * scale
    val height = (frame.bottom - frame.top) * scale
    return if (frame.isLinear) {
        listOf(
            HandlePlace(ShapeHandleKind.START, Tokens.ShapeStartShare * width, height / 2f),
            HandlePlace(ShapeHandleKind.END, Tokens.ShapeEndShare * width, height / 2f),
        )
    } else {
        listOf(
            HandlePlace(ShapeHandleKind.TOP_LEFT, 0f, 0f),
            HandlePlace(ShapeHandleKind.TOP_RIGHT, width, 0f),
            HandlePlace(ShapeHandleKind.BOTTOM_LEFT, 0f, height),
            HandlePlace(ShapeHandleKind.BOTTOM_RIGHT, width, height),
            HandlePlace(ShapeHandleKind.ROTATE, width / 2f, -Tokens.ShapeRotateDistance.value * density),
        )
    }
}

/**
 * Ручки рамки в системе координат листа: центры целей касания. Рамка может быть повёрнута вокруг своего центра, поэтому
 * центры [listLocalHandlePlaces] поворачиваются так же; цель остаётся квадратом в осях самой фигуры ([findHandleAt]).
 */
internal fun listHandlePlaces(frame: ShapeFrame, scale: Float, density: Float): List<HandlePlace> {
    val halfWidth = (frame.right - frame.left) * scale / 2f
    val halfHeight = (frame.bottom - frame.top) * scale / 2f
    val centerX = (frame.left + frame.right) / 2f * scale
    val centerY = (frame.top + frame.bottom) / 2f * scale
    val radians = Math.toRadians(frame.angle.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    return listLocalHandlePlaces(frame, scale, density).map { local ->
        val offsetX = local.x - halfWidth
        val offsetY = local.y - halfHeight
        HandlePlace(local.handle, centerX + offsetX * cosine - offsetY * sine, centerY + offsetX * sine + offsetY * cosine)
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
