package app.deckly.ink

import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import kotlin.math.ceil

/** Радиус ластика в пикселях листа; наружу уходит в единицах карточки ([computeEraserRadius]). */
internal const val ERASER_RADIUS_PX = 16f

/** Точка пера в пикселях листа; нажим от устройства, 0..1 (за диапазон выходит редко, см. [buildStroke]). */
internal data class RawPoint(val x: Float, val y: Float, val pressure: Float)

/** Прямоугольник в пикселях листа; правый и нижний края не входят в закрашенное. */
internal data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun union(other: PixelBounds): PixelBounds =
        PixelBounds(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
}

/** Нажим сырого пера (до максимума устройства) в диапазон 0..1. */
internal fun normalizePressure(rawPressure: Float, maxPressure: Float): Float =
    (rawPressure / maxPressure).coerceIn(0f, 1f)

/**
 * Штрих из точек пера в единицах карточки; толщина пера [penWidthPx] приходит в пикселях листа. Нажим вне
 * 0..1 (устройство может отдать чуть больше единицы) прижимается к границам: [StrokePoint] другого не
 * принимает. Без точек штриха нет.
 */
internal fun buildStroke(points: List<RawPoint>, penWidthPx: Float, scale: Float): Stroke? {
    if (points.isEmpty()) return null
    return Stroke(
        CardGeometry.toCardUnits(penWidthPx, scale),
        points.map { point ->
            StrokePoint(
                CardGeometry.toCardUnits(point.x, scale),
                CardGeometry.toCardUnits(point.y, scale),
                point.pressure.coerceIn(0f, 1f),
            )
        },
    )
}

/** Траектория ластика в единицах карточки; нажим для стирания значения не имеет. */
internal fun buildErasePath(points: List<RawPoint>, scale: Float): List<StrokePoint> =
    points.map { point ->
        StrokePoint(CardGeometry.toCardUnits(point.x, scale), CardGeometry.toCardUnits(point.y, scale), 0f)
    }

internal fun computeEraserRadius(scale: Float): Float = CardGeometry.toCardUnits(ERASER_RADIUS_PX, scale)

/**
 * Прямоугольник, в который попадает всё нарисованное штрихом: точки плюс половина самой широкой точки
 * (`w + 3` px при полном нажиме, см. [computePointWidthPx]) и запас на сглаживание. У штриха без точек
 * прямоугольника нет.
 */
internal fun computeStrokeBounds(stroke: Stroke, scale: Float): PixelBounds? {
    if (stroke.points.isEmpty()) return null
    val margin = ceil(stroke.width * scale / 2f + 2.5f).toInt()
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (point in stroke.points) {
        val x = CardGeometry.toPixels(point.x, scale)
        val y = CardGeometry.toPixels(point.y, scale)
        left = minOf(left, x)
        top = minOf(top, y)
        right = maxOf(right, x)
        bottom = maxOf(bottom, y)
    }
    return PixelBounds(
        left.toInt() - margin,
        top.toInt() - margin,
        ceil(right).toInt() + margin,
        ceil(bottom).toInt() + margin,
    )
}

/**
 * Где на листе списки различаются: объединение прямоугольников штрихов, которые есть только в одном из них.
 * Порядок штрихов и повторы значения не имеют: одинаковые штрихи закрашивают те же пиксели.
 * Одинаковые списки дают `null`.
 */
internal fun computeChangedBounds(before: List<Stroke>, after: List<Stroke>, scale: Float): PixelBounds? {
    val beforeSet = before.toHashSet()
    val afterSet = after.toHashSet()
    val changed = before.filter { it !in afterSet } + after.filter { it !in beforeSet }
    return changed
        .mapNotNull { computeStrokeBounds(it, scale) }
        .reduceOrNull { first, second -> first.union(second) }
}
