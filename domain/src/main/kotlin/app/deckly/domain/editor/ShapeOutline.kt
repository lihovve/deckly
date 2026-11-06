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

private const val GRID_SIZE = 100.0

private val GRID_OUTLINES: Map<Shape.Kind, List<List<GridPoint>>> by lazy {
    Shape.Kind.entries.associateWith { parseSvgOutlines(it.svg) }
}

/** Контуры значка вида в сетке 100 × 100, как в палитре. Считаются один раз на вид. */
fun Shape.Kind.listGridOutlines(): List<List<GridPoint>> = GRID_OUTLINES.getValue(this)

/**
 * Контуры фигуры на листе в единицах карточки: сетка вида растягивается на рамку независимо по осям, затем всё
 * поворачивается вокруг центра рамки. У линии и стрелок ось и острия считаются по размерам макета (остриё не растёт с
 * длиной), поэтому рамка у них только задаёт длину и положение.
 */
fun Shape.listPolylines(): List<List<StrokePoint>> {
    val local = if (kind.isLinear) listLinearOutlines() else listStretchedOutlines()
    val centerX = (left.toDouble() + right) / 2
    val centerY = (top.toDouble() + bottom) / 2
    val radians = Math.toRadians(angle.toDouble())
    val cosine = cos(radians)
    val sine = sin(radians)
    return local.map { line ->
        line.map { (x, y) ->
            val dx = x - centerX
            val dy = y - centerY
            StrokePoint((centerX + dx * cosine - dy * sine).toFloat(), (centerY + dx * sine + dy * cosine).toFloat(), 1f)
        }
    }
}

private fun Shape.listStretchedOutlines(): List<List<Pair<Double, Double>>> {
    val width = right.toDouble() - left
    val height = bottom.toDouble() - top
    return kind.listGridOutlines().map { line ->
        line.map { (gx, gy) -> (left + gx / GRID_SIZE * width) to (top + gy / GRID_SIZE * height) }
    }
}

private fun Shape.listLinearOutlines(): List<List<Pair<Double, Double>>> {
    val unit = CardUnits.PER_DESIGN_UNIT.toDouble()
    val designWidth = (right.toDouble() - left) / unit
    val designHeight = (bottom.toDouble() - top) / unit
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
    isPolylineTouched(shape.listPolylines(), path, radius)

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
