package app.deckly.domain.editor

import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import kotlin.math.hypot

/**
 * Задет ли штрих ластиком: любой отрезок штриха проходит от любого отрезка траектории ластика не
 * дальше [radius]. Расстояние меряется между отрезками, а не между точками: быстрый штрих с редкими
 * точками иначе проскакивал бы мимо ластика. Штрих и траектория из одной точки — вырожденные
 * отрезки, пустые — ничего не задевают.
 */
fun isStrokeTouched(stroke: Stroke, path: List<StrokePoint>, radius: Float): Boolean {
    val strokeSegments = listSegments(stroke.points)
    val pathSegments = listSegments(path)
    return strokeSegments.any { strokeSegment ->
        pathSegments.any { pathSegment -> computeSegmentDistance(strokeSegment, pathSegment) <= radius }
    }
}

private data class Segment(val ax: Double, val ay: Double, val bx: Double, val by: Double)

private fun listSegments(points: List<StrokePoint>): List<Segment> = when (points.size) {
    0 -> emptyList()
    1 -> listOf(Segment(points[0].x.toDouble(), points[0].y.toDouble(), points[0].x.toDouble(), points[0].y.toDouble()))
    else -> points.zipWithNext { a, b -> Segment(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble()) }
}

/** Ноль, если отрезки пересекаются, иначе меньшее из четырёх расстояний «конец отрезка — другой отрезок». */
private fun computeSegmentDistance(first: Segment, second: Segment): Double {
    if (isCrossing(first, second)) return 0.0
    return minOf(
        computePointDistance(first.ax, first.ay, second),
        computePointDistance(first.bx, first.by, second),
        computePointDistance(second.ax, second.ay, first),
        computePointDistance(second.bx, second.by, first),
    )
}

/** Собственное пересечение: концы каждого отрезка лежат строго по разные стороны от другого. */
private fun isCrossing(first: Segment, second: Segment): Boolean {
    val firstStart = computeCross(second, first.ax, first.ay)
    val firstEnd = computeCross(second, first.bx, first.by)
    val secondStart = computeCross(first, second.ax, second.ay)
    val secondEnd = computeCross(first, second.bx, second.by)
    return hasOppositeSigns(firstStart, firstEnd) && hasOppositeSigns(secondStart, secondEnd)
}

private fun hasOppositeSigns(a: Double, b: Double): Boolean = (a > 0 && b < 0) || (a < 0 && b > 0)

/** Знак показывает, с какой стороны от прямой через [segment] лежит точка. */
private fun computeCross(segment: Segment, x: Double, y: Double): Double =
    (segment.bx - segment.ax) * (y - segment.ay) - (segment.by - segment.ay) * (x - segment.ax)

private fun computePointDistance(x: Double, y: Double, segment: Segment): Double {
    val dx = segment.bx - segment.ax
    val dy = segment.by - segment.ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0.0) return hypot(x - segment.ax, y - segment.ay)
    val projection = (((x - segment.ax) * dx + (y - segment.ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(x - (segment.ax + projection * dx), y - (segment.ay + projection * dy))
}
