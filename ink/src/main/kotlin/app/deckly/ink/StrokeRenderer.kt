package app.deckly.ink

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.deckly.domain.model.Stroke

/**
 * Рисует штрихи в пикселях [canvas]: [scale] — пикселей в единице карточки ([CardGeometry.computeScale]).
 * Штрих — цепочка отрезков с круглыми концами, толщина отрезка — среднее двух его точек
 * ([computePointWidthPx]). Штрих из одной точки — круг, штрих без точек не рисуется.
 *
 * Состояния нет: кисть своя на каждый вызов, поэтому функцию можно вызывать из любого потока, в том числе
 * из потока колбэков пера, пока поток интерфейса рисует ту же поверхность.
 */
fun drawStrokes(canvas: Canvas, strokes: List<Stroke>, scale: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (stroke in strokes) {
        drawStroke(canvas, stroke, scale, paint)
    }
}

private fun drawStroke(canvas: Canvas, stroke: Stroke, scale: Float, paint: Paint) {
    val points = stroke.points
    if (points.isEmpty()) return

    if (points.size == 1) {
        val point = points.first()
        val diameter = computePointWidthPx(stroke.width, point.pressure, scale)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(point.x * scale, point.y * scale, diameter / 2f, paint)
        return
    }

    paint.style = Paint.Style.STROKE
    for (index in 1 until points.size) {
        val from = points[index - 1]
        val to = points[index]
        val fromWidth = computePointWidthPx(stroke.width, from.pressure, scale)
        val toWidth = computePointWidthPx(stroke.width, to.pressure, scale)
        paint.strokeWidth = (fromWidth + toWidth) / 2f
        canvas.drawLine(from.x * scale, from.y * scale, to.x * scale, to.y * scale, paint)
    }
}
