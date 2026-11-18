package app.deckly.domain.editor

import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import app.deckly.domain.model.StrokePoint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Числа линий и стрелок из `buildLinearShape` в `design/editor-shapes.js`, в единицах макета.
private const val AXIS_START = 0.12
private const val AXIS_END = 0.88
private const val HEAD_LENGTH = 24.0
private const val HEAD_HALF_WIDTH = 16.0

/** Лента выбора шире контура на 8 единиц макета с каждой стороны (`stroke-width = width + 16` в демо). */
private const val SELECTION_MARGIN = 8f

internal const val GRID_SIZE = 100.0

private val GRID_OUTLINES: Map<Shape.Kind, List<List<GridPoint>>> by lazy {
    Shape.Kind.entries.associateWith { parseSvgOutlines(it.svg) }
}

/** Границы контура вида в сетке 100 × 100: минимум и максимум по точкам его ломаных. */
data class GridBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

private val GRID_BOUNDS: Map<Shape.Kind, GridBounds> by lazy {
    Shape.Kind.entries.associateWith { kind ->
        val points = kind.listGridOutlines().flatten()
        GridBounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
}

/** Контуры значка вида в сетке 100 × 100, как в палитре. Считаются один раз на вид. */
fun Shape.Kind.listGridOutlines(): List<List<GridPoint>> = GRID_OUTLINES.getValue(this)

/** Границы контура вида в сетке: значок стоит в ней с полями, а рамка фигуры идёт по контуру. Считаются один раз на вид. */
fun Shape.Kind.findGridBounds(): GridBounds = GRID_BOUNDS.getValue(this)

/**
 * Контуры фигуры на листе в единицах карточки, в `Double` без перевода в `Float`: контур вида растягивается на рамку
 * [left]…[right] × [top]…[bottom] от своих границ независимо по осям (его крайние точки стоят на сторонах рамки), затем всё
 * поворачивается на [angle] градусов вокруг центра рамки. У линии и стрелок ось и острия считаются по размерам макета
 * (остриё не растёт с длиной), поэтому рамка у них только задаёт длину и положение. Одна формула для рисунка
 * ([listPolylines]) и для правила «помещается» (`ShapeTransform.kt`): правило сравнивает точки с границей листа, и шум
 * перевода рамки в `Float` (порядка 5e-5) для фигуры у края сделал бы его случайным.
 */
internal fun listContourOutlines(
    kind: Shape.Kind,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
    angle: Double,
): List<List<Pair<Double, Double>>> {
    val local = if (kind.isLinear) listLinearOutlines(kind, left, top, right, bottom) else listStretchedOutlines(kind, left, top, right, bottom)
    val centerX = (left + right) / 2
    val centerY = (top + bottom) / 2
    val radians = Math.toRadians(angle)
    val cosine = cos(radians)
    val sine = sin(radians)
    return local.map { line ->
        line.map { (x, y) ->
            val dx = x - centerX
            val dy = y - centerY
            (centerX + dx * cosine - dy * sine) to (centerY + dx * sine + dy * cosine)
        }
    }
}

/** Контуры фигуры на листе в единицах карточки для рисования: [listContourOutlines], переведённые в `Float`. */
fun Shape.listPolylines(): List<List<StrokePoint>> =
    listContourOutlines(kind, left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble(), angle.toDouble())
        .map { line -> line.map { (x, y) -> StrokePoint(x.toFloat(), y.toFloat(), 1f) } }

private fun listStretchedOutlines(kind: Shape.Kind, left: Double, top: Double, right: Double, bottom: Double): List<List<Pair<Double, Double>>> {
    val width = right - left
    val height = bottom - top
    val bounds = kind.findGridBounds()
    val spanX = bounds.maxX.toDouble() - bounds.minX
    val spanY = bounds.maxY.toDouble() - bounds.minY
    return kind.listGridOutlines().map { line ->
        line.map { (gx, gy) -> (left + (gx - bounds.minX) / spanX * width) to (top + (gy - bounds.minY) / spanY * height) }
    }
}

private fun listLinearOutlines(kind: Shape.Kind, left: Double, top: Double, right: Double, bottom: Double): List<List<Pair<Double, Double>>> {
    val unit = CardUnits.PER_DESIGN_UNIT.toDouble()
    val designWidth = (right - left) / unit
    val designHeight = (bottom - top) / unit
    val start = designWidth * AXIS_START
    val end = designWidth * AXIS_END
    val y = designHeight / 2
    val head = min(HEAD_LENGTH, (end - start) / 3)
    val outlines = mutableListOf(listOf(start to y, end to y))
    if (kind != Shape.Kind.LINE) {
        outlines += listOf((end - head) to (y - HEAD_HALF_WIDTH), end to y, (end - head) to (y + HEAD_HALF_WIDTH))
    }
    if (kind == Shape.Kind.DOUBLE_ARROW) {
        outlines += listOf((start + head) to (y - HEAD_HALF_WIDTH), start to y, (start + head) to (y + HEAD_HALF_WIDTH))
    }
    return outlines.map { line -> line.map { (x, py) -> (left + x * unit) to (top + py * unit) } }
}

/** Задета ли фигура траекторией: любой отрезок контура ближе [radius] к отрезку траектории. Внутренность не задета. */
fun isShapeTouched(shape: Shape, path: List<StrokePoint>, radius: Float): Boolean =
    EraserTrace(path, radius).isTouchingShape(shape)

/**
 * Верхняя из фигур, чья лента выбора задета точкой: расстояние от неё до контура не больше `width / 2 + 8` единиц
 * макета. Внутренность пустой фигуры не выбирает, поэтому внутри можно писать. Если задеты несколько, выигрывает
 * последняя в списке (она нарисована выше). Нет такой — `null`.
 */
fun findShapeAt(shapes: List<Shape>, x: Float, y: Float): Int? {
    val point = listOf(StrokePoint(x, y, 1f))
    return shapes.indices.lastOrNull { index ->
        isShapeTouched(shapes[index], point, shapes[index].width / 2 + SELECTION_MARGIN * CardUnits.PER_DESIGN_UNIT)
    }
}
