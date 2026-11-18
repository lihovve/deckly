package app.deckly.domain.editor

import app.deckly.domain.model.Shape
import kotlin.math.cos
import kotlin.math.sin

/**
 * Фигура со старой рамкой → та же фигура с рамкой по границам контура. До формата версии 3 рамка обычного вида была
 * сеткой значка 100 × 100 с полями (прямоугольник стоял в ней как 14…86 × 20…80), и контур лежал внутри рамки. Теперь
 * рамка описана вокруг контура: на листе фигура та же, меняется только рамка. У несимметричных видов (треугольник,
 * сердце, облако, молния, луна, капля) центр контура не совпадает с центром сетки, поэтому новый центр — старый плюс
 * смещение в осях фигуры, повёрнутое на её угол. Угол и толщина прежние; линия и стрелки не менялись и возвращаются
 * как есть. Функция не идемпотентна: её применяют один раз, к фигурам файлов версии 2.
 */
fun Shape.migrateGridFrameToContour(): Shape {
    if (kind.isLinear) return this
    val bounds = kind.findGridBounds()
    val width = right.toDouble() - left
    val height = bottom.toDouble() - top
    val contourLeft = left + bounds.minX / GRID_SIZE * width
    val contourRight = left + bounds.maxX / GRID_SIZE * width
    val contourTop = top + bounds.minY / GRID_SIZE * height
    val contourBottom = top + bounds.maxY / GRID_SIZE * height
    val offsetX = (contourLeft + contourRight) / 2 - (left.toDouble() + right) / 2
    val offsetY = (contourTop + contourBottom) / 2 - (top.toDouble() + bottom) / 2
    val radians = Math.toRadians(angle.toDouble())
    val centerX = (left.toDouble() + right) / 2 + offsetX * cos(radians) - offsetY * sin(radians)
    val centerY = (top.toDouble() + bottom) / 2 + offsetX * sin(radians) + offsetY * cos(radians)
    val halfWidth = (contourRight - contourLeft) / 2
    val halfHeight = (contourBottom - contourTop) / 2
    return copy(
        left = (centerX - halfWidth).toFloat(),
        top = (centerY - halfHeight).toFloat(),
        right = (centerX + halfWidth).toFloat(),
        bottom = (centerY + halfHeight).toFloat(),
    )
}
