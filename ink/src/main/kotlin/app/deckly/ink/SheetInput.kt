package app.deckly.ink

import app.deckly.domain.editor.findShapeAt
import app.deckly.domain.editor.listPolylines
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import kotlin.math.ceil
import kotlin.math.hypot

/** Радиус ластика в пикселях листа; наружу уходит в единицах карточки ([computeEraserRadius]). */
internal const val ERASER_RADIUS_PX = 16f

/**
 * Касание пером, все точки которого в пределах 12 px листа от первой, может быть выбором фигуры, а не штрихом. Тот же радиус
 * у касания мимо фигуры в руках: пока перо не вышло за него, штриха нет, фигура лишь применена (поэтому значение открыто
 * редактору).
 */
const val TAP_RADIUS_PX = 12f

/** Запас вокруг следа пера, который рисует прошивка: расчёт её толщины не проверен на устройстве. */
private const val TRACE_MARGIN_PX = 4

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
 * Прямоугольник, в который попадает контур фигуры: ломаные на листе (с поворотом) плюс половина толщины контура и запас
 * на сглаживание. У фигуры всегда есть контур, поэтому прямоугольник есть всегда.
 */
internal fun computeShapeBounds(shape: Shape, scale: Float): PixelBounds {
    val margin = ceil(shape.width * scale / 2f + 2.5f).toInt()
    val points = shape.listPolylines().flatten()
    return PixelBounds(
        points.minOf { CardGeometry.toPixels(it.x, scale) }.toInt() - margin,
        points.minOf { CardGeometry.toPixels(it.y, scale) }.toInt() - margin,
        ceil(points.maxOf { CardGeometry.toPixels(it.x, scale) }).toInt() + margin,
        ceil(points.maxOf { CardGeometry.toPixels(it.y, scale) }).toInt() + margin,
    )
}

/**
 * Где на листе стороны различаются: объединение прямоугольников штрихов и фигур, которые есть только в одной из них.
 * Порядок и повторы значения не имеют: одинаковые штрихи и фигуры закрашивают те же пиксели. Одинаковые стороны дают
 * `null`.
 */
internal fun computeChangedBounds(before: CardSide, after: CardSide, scale: Float): PixelBounds? {
    val strokeSet = (before.strokes + after.strokes).filter { (it in before.strokes) != (it in after.strokes) }
    val shapeSet = (before.shapes + after.shapes).filter { (it in before.shapes) != (it in after.shapes) }
    return (strokeSet.mapNotNull { computeStrokeBounds(it, scale) } + shapeSet.map { computeShapeBounds(it, scale) })
        .reduceOrNull { first, second -> first.union(second) }
}

/**
 * Прямоугольник, в котором прошивка рисует след касания пера: точки плюс половина самой широкой точки пера [penWidthPx]
 * и запас. Без точек следа нет.
 */
internal fun computeTraceBounds(points: List<RawPoint>, penWidthPx: Float): PixelBounds? {
    if (points.isEmpty()) return null
    val margin = ceil(penWidthPx / 2f + 2.5f).toInt() + TRACE_MARGIN_PX
    return PixelBounds(
        points.minOf { it.x }.toInt() - margin,
        points.minOf { it.y }.toInt() - margin,
        ceil(points.maxOf { it.x }).toInt() + margin,
        ceil(points.maxOf { it.y }).toInt() + margin,
    )
}

/**
 * Какую фигуру выбрало касание пером или `null`, если это штрих. Касание — все точки в пределах [TAP_RADIUS_PX] листа от
 * первой; выбирает фигуру только касание у её контура (лента `width / 2 + 8` единиц макета), внутренность пустой фигуры
 * нет, поэтому точка внутри остаётся точкой штриха, а длинный штрих через контур остаётся штрихом. [points] в пикселях
 * листа, [scale] — пикселей в единице карточки.
 */
internal fun findTappedShape(points: List<RawPoint>, shapes: List<Shape>, scale: Float): Int? {
    val first = points.firstOrNull() ?: return null
    if (shapes.isEmpty() || points.any { hypot(it.x - first.x, it.y - first.y) > TAP_RADIUS_PX }) return null
    return findShapeAt(shapes, CardGeometry.toCardUnits(first.x, scale), CardGeometry.toCardUnits(first.y, scale))
}
