package app.deckly.ink

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.deckly.domain.editor.GridPoint
import app.deckly.domain.editor.listPolylines
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint

/**
 * Рисует сторону карточки: сначала фигуры, поверх них штрихи (письмо внутри пустой фигуры должно быть видно). Фигура
 * без заливки; штрихи и фигуры в пикселях [canvas], [scale] — пикселей в единице карточки.
 */
fun drawCardSide(canvas: Canvas, side: CardSide, scale: Float) {
    drawShapes(canvas, side.shapes, scale)
    drawStrokes(canvas, side.strokes, scale)
}

/**
 * Рисует контуры фигур. Толщина постоянная, `shape.width · scale`: круглые концы и стыки, без формулы нажима и без
 * зависимости от рамки (растянутая или повёрнутая фигура не становится толще). Состояния нет, как у [drawStrokes].
 */
fun drawShapes(canvas: Canvas, shapes: List<Shape>, scale: Float) {
    val paint = buildOutlinePaint()
    for (shape in shapes) {
        paint.strokeWidth = shape.width * scale
        for (line in shape.listPolylines()) canvas.drawPath(buildShapePath(line, scale), paint)
    }
}

/** Контуры значка вида в сетке 100 × 100: сетка растянута на область по осям [scaleX], [scaleY], толщина [widthPx] своя. */
internal fun drawGlyph(canvas: Canvas, outlines: List<List<GridPoint>>, scaleX: Float, scaleY: Float, widthPx: Float) {
    val paint = buildOutlinePaint().apply { strokeWidth = widthPx }
    for (line in outlines) canvas.drawPath(buildGlyphPath(line, scaleX, scaleY), paint)
}

private fun buildOutlinePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun buildShapePath(points: List<StrokePoint>, scale: Float): Path = Path().apply {
    moveTo(points.first().x * scale, points.first().y * scale)
    for (index in 1 until points.size) lineTo(points[index].x * scale, points[index].y * scale)
}

private fun buildGlyphPath(points: List<GridPoint>, scaleX: Float, scaleY: Float): Path = Path().apply {
    moveTo(points.first().x * scaleX, points.first().y * scaleY)
    for (index in 1 until points.size) lineTo(points[index].x * scaleX, points[index].y * scaleY)
}

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
