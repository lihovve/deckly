package app.deckly.spike

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect

/** Штрих прототипа: точки подряд как x, y, нажим; координаты в пикселях холста. */
data class SpikeStroke(val points: FloatArray, val width: Float) {
    val pointCount: Int get() = points.size / VALUES_PER_POINT

    fun x(index: Int): Float = points[index * VALUES_PER_POINT]
    fun y(index: Int): Float = points[index * VALUES_PER_POINT + 1]

    companion object {
        const val VALUES_PER_POINT = 3
    }
}

/**
 * Штрихи листа, общие для обоих холстов (Onyx и Canvas). Пишет поток обратных вызовов пера, читает
 * поток интерфейса, поэтому все операции под одной блокировкой.
 */
object StrokeStore {
    private val strokes = mutableListOf<SpikeStroke>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    @Synchronized
    fun add(stroke: SpikeStroke) {
        strokes += stroke
    }

    @Synchronized
    fun count(): Int = strokes.size

    @Synchronized
    fun clear() {
        strokes.clear()
    }

    /**
     * Удаляет штрихи целиком, если любая их точка ближе [radius] к любой точке [path]. Возвращает
     * прямоугольник, который нужно перерисовать (границы удалённых штрихов), или null, если ничего
     * не удалено.
     */
    @Synchronized
    fun eraseAlong(path: List<PointF>, radius: Float): Rect? {
        if (path.isEmpty()) return null
        val radiusSquared = radius * radius
        var dirty: Rect? = null
        val iterator = strokes.iterator()
        while (iterator.hasNext()) {
            val stroke = iterator.next()
            if (isTouched(stroke, path, radiusSquared)) {
                iterator.remove()
                dirty = union(dirty, boundsOf(stroke))
            }
        }
        return dirty
    }

    @Synchronized
    fun drawTo(canvas: Canvas) {
        for (stroke in strokes) {
            drawStroke(canvas, stroke)
        }
    }

    /** Границы пути ластика с запасом [radius]: там прошивка оставляет след, который надо снять. */
    fun boundsOfPath(path: List<PointF>, radius: Float): Rect? {
        if (path.isEmpty()) return null
        val first = path.first()
        val bounds = Rect(first.x.toInt(), first.y.toInt(), first.x.toInt(), first.y.toInt())
        for (point in path) {
            bounds.union(point.x.toInt(), point.y.toInt())
        }
        bounds.inset(-radius.toInt(), -radius.toInt())
        return bounds
    }

    fun union(first: Rect?, second: Rect?): Rect? = when {
        first == null -> second?.let { Rect(it) }
        second == null -> Rect(first)
        else -> Rect(first).apply { union(second) }
    }

    private fun isTouched(stroke: SpikeStroke, path: List<PointF>, radiusSquared: Float): Boolean {
        for (index in 0 until stroke.pointCount) {
            val x = stroke.x(index)
            val y = stroke.y(index)
            for (point in path) {
                val dx = x - point.x
                val dy = y - point.y
                if (dx * dx + dy * dy < radiusSquared) return true
            }
        }
        return false
    }

    private fun boundsOf(stroke: SpikeStroke): Rect {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (index in 0 until stroke.pointCount) {
            left = minOf(left, stroke.x(index))
            top = minOf(top, stroke.y(index))
            right = maxOf(right, stroke.x(index))
            bottom = maxOf(bottom, stroke.y(index))
        }
        val margin = stroke.width.toInt() + 2
        return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()).apply {
            inset(-margin, -margin)
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: SpikeStroke) {
        if (stroke.pointCount == 0) return
        paint.strokeWidth = stroke.width
        if (stroke.pointCount == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(stroke.x(0), stroke.y(0), stroke.width / 2, paint)
            paint.style = Paint.Style.STROKE
            return
        }
        val path = Path().apply {
            moveTo(stroke.x(0), stroke.y(0))
            for (index in 1 until stroke.pointCount) {
                lineTo(stroke.x(index), stroke.y(index))
            }
        }
        canvas.drawPath(path, paint)
    }
}
