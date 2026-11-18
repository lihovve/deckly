package app.deckly.domain.editor

import app.deckly.domain.model.Shape
import app.deckly.domain.model.StrokePoint
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Перевод фигур со старой рамкой (рамка = сетка значка 100 × 100) на рамку по границам контура. */
class ShapeMigrationTest {
    private val angles = listOf(0f, 30f, -35f, 90f, 180f)

    // Рамка 400 × 300 не квадратная и не у начала координат: ошибка осей или сдвига не прячется.
    private fun buildOldShape(kind: Shape.Kind, angle: Float, width: Float = 3f) =
        Shape(kind, 120f, 80f, 520f, 380f, angle, width)

    /**
     * Прежнее отображение, оставленное эталоном: вся сетка 100 × 100 растянута на рамку независимо по осям, затем всё
     * повёрнуто вокруг центра рамки. Линия и стрелки считались и считаются не по сетке.
     */
    private fun listLegacyPolylines(shape: Shape): List<List<StrokePoint>> {
        if (shape.kind.isLinear) return shape.listPolylines()
        val width = shape.right.toDouble() - shape.left
        val height = shape.bottom.toDouble() - shape.top
        val centerX = (shape.left.toDouble() + shape.right) / 2
        val centerY = (shape.top.toDouble() + shape.bottom) / 2
        val radians = Math.toRadians(shape.angle.toDouble())
        return shape.kind.listGridOutlines().map { line ->
            line.map { (gx, gy) ->
                val dx = shape.left + gx / 100.0 * width - centerX
                val dy = shape.top + gy / 100.0 * height - centerY
                StrokePoint(
                    (centerX + dx * cos(radians) - dy * sin(radians)).toFloat(),
                    (centerY + dx * sin(radians) + dy * cos(radians)).toFloat(),
                    1f,
                )
            }
        }
    }

    private fun assertSameLines(expected: List<List<StrokePoint>>, actual: List<List<StrokePoint>>, message: String) {
        assertEquals(expected.size, actual.size, "$message: число линий")
        for ((index, line) in expected.withIndex()) {
            assertEquals(line.size, actual[index].size, "$message: число точек линии $index")
            for ((pointIndex, want) in line.withIndex()) {
                assertEquals(want.x, actual[index][pointIndex].x, 0.01f, "$message: x линии $index точки $pointIndex")
                assertEquals(want.y, actual[index][pointIndex].y, 0.01f, "$message: y линии $index точки $pointIndex")
            }
        }
    }

    private fun assertFrame(expected: List<Float>, actual: Shape, message: String) {
        assertEquals(expected[0], actual.left, 0.001f, "$message: left")
        assertEquals(expected[1], actual.top, 0.001f, "$message: top")
        assertEquals(expected[2], actual.right, 0.001f, "$message: right")
        assertEquals(expected[3], actual.bottom, 0.001f, "$message: bottom")
    }

    @Test
    fun convertedShapeDrawsTheSameLinesAsTheOldOneForEveryKindAndAngle() {
        for (kind in Shape.Kind.entries) {
            for (angle in angles) {
                val old = buildOldShape(kind, angle)

                val converted = old.migrateGridFrameToContour()

                assertSameLines(listLegacyPolylines(old), converted.listPolylines(), "${kind.key} $angle°")
            }
        }
    }

    @Test
    fun linearShapesAreReturnedUnchanged() {
        for (kind in Shape.Kind.entries.filter { it.isLinear }) {
            for (angle in angles) {
                val old = Shape(kind, 100f, 300f, 700f, 380f, angle, 3f)

                assertEquals(old, old.migrateGridFrameToContour(), "${kind.key} $angle°")
            }
        }
    }

    @Test
    fun symmetricKindShrinksAroundTheSameCenter() {
        // Прямоугольник занимает в сетке 14…86 × 20…80: новая рамка — 72 % ширины и 60 % высоты старой вокруг её центра.
        val converted = Shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, 0f, 3f).migrateGridFrameToContour()

        assertFrame(listOf(128f, 260f, 272f, 440f), converted, "прямоугольник")
    }

    @Test
    fun asymmetricKindMovesItsCenterAlongTheOffsetTurnedByTheAngle() {
        // Треугольник занимает в сетке 11…89 × 12…85, то есть новая рамка 156 × 219 от старой 200 × 300 с центром (200, 350).
        // Его центр в осях фигуры на 4,5 выше центра старой рамки: смещение (0, −4,5), и поворот на угол фигуры его поворачивает.
        val triangle = Shape(Shape.Kind.TRIANGLE, 100f, 200f, 300f, 500f, 0f, 3f)

        assertFrame(listOf(122f, 236f, 278f, 455f), triangle.migrateGridFrameToContour(), "без поворота: центр (200, 345,5)")
        assertFrame(listOf(126.5f, 240.5f, 282.5f, 459.5f), triangle.copy(angle = 90f).migrateGridFrameToContour(), "90°: центр (204,5, 350)")
        assertFrame(listOf(122f, 245f, 278f, 464f), triangle.copy(angle = 180f).migrateGridFrameToContour(), "180°: центр (200, 354,5)")
    }

    @Test
    fun angleAndOutlineWidthStayTheSame() {
        val converted = buildOldShape(Shape.Kind.HEART, -35f, width = 7.5f).migrateGridFrameToContour()

        assertEquals(Shape.Kind.HEART, converted.kind)
        assertEquals(-35f, converted.angle)
        assertEquals(7.5f, converted.width)
    }
}
