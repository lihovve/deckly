package app.deckly.domain.editor

import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Задет ли штрих ластиком: любой отрезок штриха проходит от любого отрезка траектории ластика не
 * дальше [radius]. Расстояние меряется между отрезками, а не между точками: быстрый штрих с редкими
 * точками иначе проскакивал бы мимо ластика. Штрих и траектория из одной точки — вырожденные
 * отрезки, пустые — ничего не задевают.
 */
fun isStrokeTouched(stroke: Stroke, path: List<StrokePoint>, radius: Float): Boolean =
    EraserTrace(path, radius).isTouchingStroke(stroke)

/**
 * Запас прямоугольника досягаемости со всех сторон, в единицах карточки. Расстояние считается в double, и при округлении
 * штрих, который геометрически чуть дальше радиуса, засчитывается касанием: граница без запаса отбросила бы его и
 * изменила итог. Запас только оставляет лишние отрезки на точный расчёт, на результат он не влияет.
 */
private const val REACH_SLACK = 1e-6

/**
 * След ластика за один проход: отрезки траектории и прямоугольник досягаемости, то есть границы траектории, расширенные
 * на радиус. Отрезок штриха или контура вне прямоугольника не достаёт ни до одного отрезка траектории, и расстояния для
 * него не считаются; для остальных расчёт полный. Строится один раз и проверяет любое число штрихов и фигур.
 */
internal class EraserTrace(path: List<StrokePoint>, private val radius: Float) {
    private val segments = listSegments(path)
    private val reachMinX: Double
    private val reachMinY: Double
    private val reachMaxX: Double
    private val reachMaxY: Double

    init {
        val margin = radius.toDouble() + REACH_SLACK
        reachMinX = (path.minOfOrNull { it.x.toDouble() } ?: 0.0) - margin
        reachMinY = (path.minOfOrNull { it.y.toDouble() } ?: 0.0) - margin
        reachMaxX = (path.maxOfOrNull { it.x.toDouble() } ?: 0.0) + margin
        reachMaxY = (path.maxOfOrNull { it.y.toDouble() } ?: 0.0) + margin
    }

    fun isTouchingStroke(stroke: Stroke): Boolean = isTouchingPolyline(stroke.points)

    /** Задета, если задет хоть один контур. */
    fun isTouchingShape(shape: Shape): Boolean = shape.listPolylines().any { isTouchingPolyline(it) }

    private fun isTouchingPolyline(points: List<StrokePoint>): Boolean {
        if (segments.isEmpty()) return false
        if (points.size == 1) return isTouchingSegment(points[0], points[0])
        for (index in 1 until points.size) {
            if (isTouchingSegment(points[index - 1], points[index])) return true
        }
        return false
    }

    private fun isTouchingSegment(start: StrokePoint, end: StrokePoint): Boolean {
        val ax = start.x.toDouble()
        val ay = start.y.toDouble()
        val bx = end.x.toDouble()
        val by = end.y.toDouble()
        val isOutsideReach = max(ax, bx) < reachMinX || min(ax, bx) > reachMaxX ||
            max(ay, by) < reachMinY || min(ay, by) > reachMaxY
        if (isOutsideReach) return false
        val segment = Segment(ax, ay, bx, by)
        return segments.any { computeSegmentDistance(segment, it) <= radius }
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
