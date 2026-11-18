package app.deckly.domain.editor

import app.deckly.domain.model.StrokePoint
import kotlin.math.hypot

/*
 * Эталон ластика: перебор всех пар «отрезок контура — отрезок траектории» без отсечения. Так `Eraser.kt` считал до
 * прямоугольника досягаемости; арифметика расстояний повторена дословно. Править только вместе с `Eraser.kt`.
 */

/** Задета ли хоть одна из [polylines] траекторией [path] на расстоянии не больше [radius]: полный перебор пар. */
internal fun isTouchedByFullScan(polylines: List<List<StrokePoint>>, path: List<StrokePoint>, radius: Float): Boolean {
    val pathSegments = listScanSegments(path)
    return polylines.any { polyline ->
        listScanSegments(polyline).any { segment ->
            pathSegments.any { pathSegment -> computeScanDistance(segment, pathSegment) <= radius }
        }
    }
}

private data class ScanSegment(val ax: Double, val ay: Double, val bx: Double, val by: Double)

private fun listScanSegments(points: List<StrokePoint>): List<ScanSegment> = when (points.size) {
    0 -> emptyList()
    1 -> listOf(ScanSegment(points[0].x.toDouble(), points[0].y.toDouble(), points[0].x.toDouble(), points[0].y.toDouble()))
    else -> points.zipWithNext { a, b -> ScanSegment(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble()) }
}

private fun computeScanDistance(first: ScanSegment, second: ScanSegment): Double {
    if (isScanCrossing(first, second)) return 0.0
    return minOf(
        computeScanPointDistance(first.ax, first.ay, second),
        computeScanPointDistance(first.bx, first.by, second),
        computeScanPointDistance(second.ax, second.ay, first),
        computeScanPointDistance(second.bx, second.by, first),
    )
}

private fun isScanCrossing(first: ScanSegment, second: ScanSegment): Boolean {
    val firstStart = computeScanCross(second, first.ax, first.ay)
    val firstEnd = computeScanCross(second, first.bx, first.by)
    val secondStart = computeScanCross(first, second.ax, second.ay)
    val secondEnd = computeScanCross(first, second.bx, second.by)
    return hasScanOppositeSigns(firstStart, firstEnd) && hasScanOppositeSigns(secondStart, secondEnd)
}

private fun hasScanOppositeSigns(a: Double, b: Double): Boolean = (a > 0 && b < 0) || (a < 0 && b > 0)

private fun computeScanCross(segment: ScanSegment, x: Double, y: Double): Double =
    (segment.bx - segment.ax) * (y - segment.ay) - (segment.by - segment.ay) * (x - segment.ax)

private fun computeScanPointDistance(x: Double, y: Double, segment: ScanSegment): Double {
    val dx = segment.bx - segment.ax
    val dy = segment.by - segment.ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0.0) return hypot(x - segment.ax, y - segment.ay)
    val projection = (((x - segment.ax) * dx + (y - segment.ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(x - (segment.ax + projection * dx), y - (segment.ay + projection * dy))
}
