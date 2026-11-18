package app.deckly.domain.editor

import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Линейные фигуры, прижатые упором к краю листа: правило «помещается» не должно зависеть от шума перевода рамки в `Float`
 * (порядка 5e-5). Иначе у фигуры, стоящей у границы внутри шума, каждый кандидат помещается случайно, деление пополам
 * отдаёт случайную точку, и фигура дёргается.
 */
class ShapeTransformEdgeTest {
    private val unit = CardUnits.PER_DESIGN_UNIT.toDouble()

    // Толщина фигур теста 3 единицы карточки: полтолщины в единицах макета.
    private val halfOutline = 1.5 / unit

    private enum class Edge { BOTTOM, RIGHT }

    private fun buildShape(kind: Shape.Kind, x: Double, y: Double, w: Double, h: Double, angle: Double): Shape =
        Shape(kind, (x * unit).toFloat(), (y * unit).toFloat(), ((x + w) * unit).toFloat(), ((y + h) * unit).toFloat(), angle.toFloat(), 3f)

    // Точки контура в единицах макета, в `Double` из тех же `Float`-чисел рамки, что хранит фигура: ровно то, что видит правило.
    private fun listPoints(shape: Shape): List<Pair<Double, Double>> =
        listContourOutlines(shape.kind, shape.left.toDouble(), shape.top.toDouble(), shape.right.toDouble(), shape.bottom.toDouble(), shape.angle.toDouble())
            .flatten()
            .map { (x, y) -> x / unit to y / unit }

    // Насколько самая дальняя точка контура стоит внутри границы листа с отступом в полтолщины (меньше нуля — снаружи).
    private fun measureGap(shape: Shape, edge: Edge): Double {
        val points = listPoints(shape)
        return when (edge) {
            Edge.BOTTOM -> 963.0 - halfOutline - points.maxOf { it.second }
            Edge.RIGHT -> 928.0 - halfOutline - points.maxOf { it.first }
        }
    }

    // Зазор кандидата, сдвинутого на [dx] единиц карточки, после перевода рамки в `Float`, как считало прежнее правило
    // (`Frame` → `toShape`): шум округления делает его то больше, то меньше зазора самой фигуры, и знак может смениться.
    private fun measureGapAfterFloatRoundTrip(shape: Shape, dx: Float, edge: Edge): Double {
        val x = shape.left.toDouble() / unit + dx / unit
        val y = shape.top.toDouble() / unit
        val w = (shape.right.toDouble() - shape.left) / unit
        val h = (shape.bottom.toDouble() - shape.top) / unit
        val rounded = shape.copy(left = (x * unit).toFloat(), top = (y * unit).toFloat(), right = ((x + w) * unit).toFloat(), bottom = ((y + h) * unit).toFloat())
        return measureGap(rounded, edge)
    }

    private fun bump(value: Float, random: Random, reach: Int): Float = Float.fromBits(value.toRawBits() + random.nextInt(-reach, reach + 1))

    /**
     * Фигура вида [kind] под углом [angle], у которой дальняя точка контура стоит внутри границы [edge] на расстоянии из
     * ([gapLow], [gapHigh]]. `Float` рамки шагами в 6e-5 и 1e-4 не даёт задать такой зазор прямо, поэтому рамка ставится по
     * формуле в `Double`, а затем перебираются последние биты четырёх её чисел (до 150 шагов), пока зазор не попадёт в окно и
     * фигура не пройдёт [accept].
     */
    private fun findShapeRestingInside(
        kind: Shape.Kind,
        angle: Double,
        edge: Edge,
        gapLow: Double,
        gapHigh: Double,
        accept: (Shape) -> Boolean = { true },
    ): Shape {
        val probe = buildShape(kind, 300.0, 400.0, 300.0, 80.0, angle)
        val shift = measureGap(probe, edge) - (gapLow + gapHigh) / 2
        val base = if (edge == Edge.BOTTOM) buildShape(kind, 300.0, 400.0 + shift, 300.0, 80.0, angle) else buildShape(kind, 300.0 + shift, 400.0, 300.0, 80.0, angle)
        val random = Random(20261118)
        repeat(400_000) {
            val candidate = base.copy(
                left = bump(base.left, random, 150),
                top = bump(base.top, random, 150),
                right = bump(base.right, random, 150),
                bottom = bump(base.bottom, random, 150),
            )
            val gap = measureGap(candidate, edge)
            if (gap > gapLow && gap <= gapHigh && accept(candidate)) return candidate
        }
        error("Не нашлась фигура $kind при $angle° с зазором ($gapLow, $gapHigh] у $edge")
    }

    // --- перенос вдоль края ------------------------------------------------------------------------------

    @Test
    fun rotatedArrowRestingInsideTheBottomEdgeSlidesAlongItExactlyToTheCandidate() {
        for (angle in listOf(30.0, -35.0)) {
            for (gap in listOf(1e-5, 1e-6)) {
                // Дробные dx, как от настоящего пера: целое слагаемое складывается с `Float` без округления и шума не даёт.
                val offsets = listOf(-300.37f, -150.9f, -60.123f, -10.77f, -1.31f, 1.31f, 10.77f, 60.123f, 150.9f, 300.37f)
                // Берутся фигуры, у которых прежнее правило по шуму округления отвергло бы хотя бы одного из кандидатов.
                val shape = findShapeRestingInside(Shape.Kind.ARROW, angle, Edge.BOTTOM, gap / 10, gap) { candidate ->
                    offsets.any { measureGapAfterFloatRoundTrip(candidate, it, Edge.BOTTOM) <= 0.0 }
                }

                for (dx in offsets) {
                    val slid = moveShape(shape, dx, 0f)

                    assertEquals(shape.left + dx, slid.left, 0.005f, "$angle°, зазор $gap, dx $dx: сдвиг дошёл до кандидата")
                    assertEquals(shape.top, slid.top, 0.001f, "$angle°, зазор $gap, dx $dx: поперёк края не сдвинулась")
                }
            }
        }
    }

    @Test
    fun shapeFromAnOldFileAtTheEdgeIsReturnedWithAReserveAndThenSlidesAlongItExactly() {
        // По прежнему правилу (без толщины контура) дальняя точка стояла ровно на краю листа, то есть на полтолщины за границей
        // нового правила. Жест возвращает фигуру внутрь с запасом: от рамки ровно на границе кандидаты из-за погрешности
        // округления порядка 1e-13 помещались бы случайно, и деление пополам отдавало бы точку чуть короче кандидата.
        for (angle in listOf(30.0, -35.0, 0.0)) {
            val probe = buildShape(Shape.Kind.ARROW, 300.0, 400.0, 300.0, 80.0, angle)
            val legacy = buildShape(Shape.Kind.ARROW, 300.0, 400.0 + measureGap(probe, Edge.BOTTOM) + halfOutline, 300.0, 80.0, angle)

            for (dx in listOf(-300.37f, -150.9f, 150.9f, 300.37f)) {
                val slid = moveShape(legacy, dx, 0f)

                assertEquals(legacy.left + dx, slid.left, 1e-4f, "$angle°, dx $dx: дошла до кандидата, а не до точки чуть короче")
                assertTrue(canFitShape(slid), "$angle°, dx $dx: помещается")
            }
        }
    }

    // --- второй конец при неподвижном конце у границы ------------------------------------------------------

    @Test
    fun movedEndReachesThePointerWhileTheOtherEndRestsInsideTheRightEdge() {
        // Цели от острия влево и чуть вверх или вниз: ось не круче 25° к горизонтали, крыло острия остаётся на листе.
        val offsets = listOf(-400.0 to -100.0, -300.0 to 100.0, -500.0 to 0.0, -200.0 to -50.0, -350.0 to 150.0)
        for (angle in listOf(30.0, -35.0)) {
            for (gap in listOf(1e-5, 1e-6)) {
                val shape = findShapeRestingInside(Shape.Kind.ARROW, angle, Edge.RIGHT, gap / 10, gap)
                val tip = shape.listPolylines()[0][1]

                for ((offsetX, offsetY) in offsets) {
                    val targetX = tip.x + offsetX * unit
                    val targetY = tip.y + offsetY * unit

                    val moved = moveShapeEndpoint(shape, ShapeHandle.START, targetX.toFloat(), targetY.toFloat())

                    val axis = moved.listPolylines()[0]
                    assertEquals(targetX, axis[0].x.toDouble(), 0.02, "$angle°, зазор $gap, цель ($offsetX, $offsetY): начало по x")
                    assertEquals(targetY, axis[0].y.toDouble(), 0.02, "$angle°, зазор $gap, цель ($offsetX, $offsetY): начало по y")
                    assertEquals(tip.x.toDouble(), axis[1].x.toDouble(), 0.02, "$angle°, зазор $gap, цель ($offsetX, $offsetY): острие на месте")
                    assertTrue(canFitShape(moved), "$angle°, зазор $gap, цель ($offsetX, $offsetY): помещается")
                }
            }
        }
    }

    // --- фигура после настоящего упора ----------------------------------------------------------------------

    @Test
    fun shapePressedToTheEdgeByAGestureAnswersANewGestureAlongTheEdgeWithoutRandomness() {
        // Фигуру прижимает к нижнему краю настоящий упор: она стоит от края на [distance] и ведётся на 3 дальше, деление пополам
        // оставляет её внутри на 0…(путь · 2^-20), то есть внутри шума `Float`. Затем она записывается в `Float`, и новый жест
        // ведёт её вдоль края. Ответ по dx равен кандидату и монотонен: либо фигура помещается строго, либо жест сначала
        // возвращает её на лист с запасом.
        val offsets = listOf(-200.31f, -99.7f, -40.13f, -10.77f, -2.31f, -1.17f, 0f, 1.17f, 2.31f, 10.77f, 40.13f, 99.7f, 200.31f)
        for (kind in listOf(Shape.Kind.LINE, Shape.Kind.ARROW, Shape.Kind.DOUBLE_ARROW)) {
            for (angle in listOf(30.0, -35.0, 12.0, 80.0)) {
                for (distance in listOf(0.5, 1.7, 3.3, 7.9, 13.2, 21.0, 34.0)) {
                    val probe = buildShape(kind, 300.0, 400.0, 300.0, 80.0, angle)
                    val start = buildShape(kind, 300.0, 400.0 + measureGap(probe, Edge.BOTTOM) - distance, 300.0, 80.0, angle)
                    val pressed = moveShape(start, 0f, ((distance + 3.0) * unit).toFloat())
                    val label = "${kind.key} $angle°, до края $distance"

                    val results = offsets.map { dx -> moveShape(pressed, dx, 0f) }

                    for ((index, slid) in results.withIndex()) {
                        assertTrue(canFitShape(slid), "$label, dx ${offsets[index]}: помещается")
                        assertEquals(pressed.left + offsets[index], slid.left, 0.005f, "$label, dx ${offsets[index]}: дошла до кандидата")
                    }
                    assertTrue(results.zipWithNext().all { (a, b) -> b.left >= a.left - 1e-3f }, "$label: ответ монотонен по dx")
                }
            }
        }
    }
}
