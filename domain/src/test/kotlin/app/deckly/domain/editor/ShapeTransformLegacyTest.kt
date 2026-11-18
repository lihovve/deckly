package app.deckly.domain.editor

import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Жесты над фигурами из файлов прежних версий: они лежат у края листа по прежнему правилу (без толщины контура) или
 * стали меньше 80 после перевода рамки на контур. Работать с ними должно так же, как с новыми.
 */
class ShapeTransformLegacyTest {
    private val unit = CardUnits.PER_DESIGN_UNIT

    // Контур толщиной 3 единицы карточки выступает за свои точки на 1,5, в единицах макета:
    private val halfOutline = 1.5 / unit

    private val corners = listOf(ShapeHandle.TOP_LEFT, ShapeHandle.TOP_RIGHT, ShapeHandle.BOTTOM_LEFT, ShapeHandle.BOTTOM_RIGHT)

    private fun buildShape(kind: Shape.Kind, x: Double, y: Double, w: Double, h: Double, angle: Double = 0.0, width: Float = 3f) =
        Shape(kind, (x * unit).toFloat(), (y * unit).toFloat(), ((x + w) * unit).toFloat(), ((y + h) * unit).toFloat(), angle.toFloat(), width)

    private fun measureWidth(shape: Shape): Double = (shape.right.toDouble() - shape.left) / unit

    private fun measureHeight(shape: Shape): Double = (shape.bottom.toDouble() - shape.top) / unit

    // Все точки контура в единицах макета: тот же код, что рисует фигуру.
    private fun listContour(shape: Shape): List<Pair<Double, Double>> =
        shape.listPolylines().flatten().map { it.x / unit.toDouble() to it.y / unit.toDouble() }

    // --- возврат стартовой рамки на лист -----------------------------------------------------------

    @Test
    fun arrowWhoseEndStandsOnTheEdgeMovesItsOtherEndAndFitsAfterwards() {
        // Острие стрелки точно на правом краю листа (x = 928): по прежнему правилу это «помещается», с толщиной контура нет.
        val arrow = buildShape(Shape.Kind.ARROW, 664.0, 500.0, 300.0, 80.0)
        assertFalse(canFitShape(arrow))
        val targetX = 400.0 * unit
        val targetY = 300.0 * unit

        val moved = moveShapeEndpoint(arrow, ShapeHandle.START, targetX.toFloat(), targetY.toFloat())

        assertTrue(canFitShape(moved))
        val axis = moved.listPolylines()[0]
        assertEquals(targetX, axis[0].x.toDouble(), 0.02, "начало оси пришло в указатель по x")
        assertEquals(targetY, axis[0].y.toDouble(), 0.02, "начало оси пришло в указатель по y")
        assertEquals(928.0 - halfOutline, axis[1].x / unit.toDouble(), 0.01, "острие встало в полтолщины от края")
    }

    @Test
    fun endOfAnArrowAtTheEdgeReachesTheMovedEndWhateverTheOvershootAndTheTarget() {
        // Острие выступает за границу нового правила на 1,39 (полтолщины: прежнее правило пускало его ровно на край, упор мог
        // остановить его на 0,001…0,005 внутри) и больше, указатель в разные стороны: начало оси приходит в указатель всякий
        // раз, а не останавливается, не дойдя (шум `Float` при пересчёте контура на неподвижном конце не мешает: сдвиг оставляет
        // запас).
        // Цели такие, что острие у края остаётся на листе: при оси круче 56° вниз его крыло торчало бы за край, и упор был бы прав.
        val targets = listOf(400.0 to 300.0, 600.0 to 300.0, 300.0 to 800.0, 650.0 to 540.0, 100.0 to 540.0)
        for (overshoot in listOf(halfOutline - 5e-3, halfOutline - 1e-3, halfOutline, 2.0, 5.0)) {
            val arrow = buildShape(Shape.Kind.ARROW, 664.0 - halfOutline + overshoot, 500.0, 300.0, 80.0)
            for ((targetX, targetY) in targets) {
                val moved = moveShapeEndpoint(arrow, ShapeHandle.START, (targetX * unit).toFloat(), (targetY * unit).toFloat())

                val axis = moved.listPolylines()[0]
                assertEquals(targetX * unit, axis[0].x.toDouble(), 0.05, "выступ $overshoot, указатель ($targetX, $targetY): x")
                assertEquals(targetY * unit, axis[0].y.toDouble(), 0.05, "выступ $overshoot, указатель ($targetX, $targetY): y")
                assertTrue(canFitShape(moved), "выступ $overshoot, указатель ($targetX, $targetY)")
            }
        }
    }

    @Test
    fun shapeThatStandsAgainstTheEdgeSlidesAlongIt() {
        // Ось линии на верхнем краю (y = 0): с толщиной 3 контур выступал бы за лист на 1,5.
        val line = buildShape(Shape.Kind.LINE, 100.0, -40.0, 300.0, 80.0)
        assertFalse(canFitShape(line))

        val slid = moveShape(line, 100f, 0f)

        assertTrue(canFitShape(slid))
        assertEquals(line.left + 100f, slid.left, 0.01f, "сдвиг вдоль края дошёл")
        assertEquals(halfOutline, listContour(slid).minOf { it.second }, 0.01, "ось в полтолщины от края")
        assertEquals(measureWidth(line), measureWidth(slid), 0.001, "размер прежний")
        assertEquals(measureHeight(line), measureHeight(slid), 0.001)
    }

    @Test
    fun everyMovementStartsFromTheShapeReturnedOntoTheSheet() {
        // Повёрнутый квадрат у верхнего левого угла: после поворота его углы выступают за лист по обеим осям.
        val rectangle = buildShape(Shape.Kind.RECTANGLE, 10.0, 10.0, 100.0, 100.0, angle = 45.0)
        assertFalse(canFitShape(rectangle))

        assertTrue(canFitShape(moveShape(rectangle, 0f, 0f)), "перенос")
        for (corner in corners) assertTrue(canFitShape(resizeShape(rectangle, corner, 1f, 1f)), "растяжение за $corner")
        assertTrue(canFitShape(rotateShape(rectangle, 500f, 500f, 500f, 500f)), "поворот")
        val arrow = buildShape(Shape.Kind.DOUBLE_ARROW, 664.0, 500.0, 300.0, 80.0)
        assertFalse(canFitShape(arrow))
        assertTrue(canFitShape(moveShapeEndpoint(arrow, ShapeHandle.START, 300f, 300f)), "конец линии")
    }

    @Test
    fun rotatedFrameWithCornersBeyondTheSheetIsShiftedInByTheOvershootOnEachAxis() {
        val rectangle = buildShape(Shape.Kind.RECTANGLE, 10.0, 10.0, 100.0, 100.0, angle = 45.0)

        val returned = moveShape(rectangle, 0f, 0f)

        // Углы квадрата 100 × 100 по 45° вокруг (60, 60) лежат на 70,71 от центра: до листа не хватает 10,71 + полтолщины по обеим осям.
        val expected = 10.0 + (70.7107 - 60.0) + halfOutline
        assertEquals(expected, returned.left / unit.toDouble(), 0.01, "сдвиг по x")
        assertEquals(expected, returned.top / unit.toDouble(), 0.01, "сдвиг по y")
        assertEquals(measureWidth(rectangle), measureWidth(returned), 0.001, "ширина прежняя")
        assertEquals(measureHeight(rectangle), measureHeight(returned), 0.001, "высота прежняя")
        assertEquals(45f, returned.angle, "угол прежний")
    }

    @Test
    fun shapeThatFitsDoesNotChangeByABitOnZeroMovement() {
        for (kind in Shape.Kind.entries) {
            for (angle in listOf(0.0, 30.0, -35.0, 90.0)) {
                val shape = if (kind.isLinear) buildShape(kind, 300.0, 400.0, 300.0, 80.0, angle) else buildShape(kind, 300.0, 400.0, 220.0, 150.0, angle)
                assertTrue(canFitShape(shape), "${kind.key} $angle°: контрольная фигура помещается")
                val centerX = (shape.left + shape.right) / 2
                val centerY = (shape.top + shape.bottom) / 2

                assertEquals(shape, moveShape(shape, 0f, 0f), "${kind.key} $angle°: перенос")
                assertEquals(shape, rotateShape(shape, centerX + 100f, centerY, centerX + 100f, centerY), "${kind.key} $angle°: поворот")
                if (!kind.isLinear) {
                    for (corner in corners) assertEquals(shape, resizeShape(shape, corner, 0f, 0f), "${kind.key} $angle°: растяжение за $corner")
                }
            }
        }
    }

    @Test
    fun shapeThatFitsByAHairIsNotShiftedEither() {
        // Левый и верхний углы на 1,5000001 от края: при толщине 3 помещается на волосок (следующее `Float` над 1,5). Сдвиг на запас
        // коснулся бы только того, что не помещается, а эта фигура помещается и остаётся прежней до бита.
        val shape = Shape(Shape.Kind.RECTANGLE, 1.5000001f, 1.5000001f, 240f, 180f, 0f, 3f)
        assertTrue(canFitShape(shape))

        assertEquals(shape, moveShape(shape, 0f, 0f), "перенос")
        assertEquals(shape, resizeShape(shape, ShapeHandle.BOTTOM_RIGHT, 0f, 0f), "растяжение")
        assertEquals(shape, rotateShape(shape, 100f, 100f, 100f, 100f), "поворот")
    }

    // --- растяжение фигуры меньше минимума ------------------------------------------------------------

    // Прямоугольник со старой рамкой 80 × 80 после перевода: по контуру 72 % и 60 % от неё, то есть 57,6 × 48.
    private val small = buildShape(Shape.Kind.RECTANGLE, 400.0, 400.0, 80.0, 80.0).migrateGridFrameToContour()

    @Test
    fun smallFrameComesFromTheMigrationOfTheOldEightyFrame() {
        assertEquals(57.6, measureWidth(small), 0.001)
        assertEquals(48.0, measureHeight(small), 0.001)
    }

    @Test
    fun frameSmallerThanEightyDoesNotJumpOnZeroResizeFromAnyCorner() {
        for (corner in corners) {
            val same = resizeShape(small, corner, 0f, 0f)

            assertEquals(small.left, same.left, 0.001f, "$corner: left")
            assertEquals(small.top, same.top, 0.001f, "$corner: top")
            assertEquals(small.right, same.right, 0.001f, "$corner: right")
            assertEquals(small.bottom, same.bottom, 0.001f, "$corner: bottom")
        }
    }

    @Test
    fun squeezingStopsAtTheCurrentSizeOfASmallFrame() {
        for (corner in corners) {
            val dx = if (corner == ShapeHandle.TOP_LEFT || corner == ShapeHandle.BOTTOM_LEFT) 5000f else -5000f
            val dy = if (corner == ShapeHandle.TOP_LEFT || corner == ShapeHandle.TOP_RIGHT) 5000f else -5000f

            val squeezed = resizeShape(small, corner, dx, dy)

            assertEquals(57.6, measureWidth(squeezed), 0.001, "$corner: ширина")
            assertEquals(48.0, measureHeight(squeezed), 0.001, "$corner: высота")
        }
    }

    @Test
    fun smallFrameCanStillGrow() {
        val grown = resizeShape(small, ShapeHandle.BOTTOM_RIGHT, 20f * unit, 10f * unit)

        assertEquals(77.6, measureWidth(grown), 0.001)
        assertEquals(58.0, measureHeight(grown), 0.001)
        assertEquals(small.left, grown.left, 0.001f, "противоположный угол на месте")
        assertEquals(small.top, grown.top, 0.001f)
    }

    @Test
    fun theMinimumIsPerSideSoOneShortSideDoesNotHoldTheOtherBack() {
        val narrow = buildShape(Shape.Kind.RECTANGLE, 400.0, 300.0, 57.6, 200.0)

        val squeezed = resizeShape(narrow, ShapeHandle.BOTTOM_RIGHT, -5000f, -5000f)

        assertEquals(57.6, measureWidth(squeezed), 0.001, "короткая сторона остаётся как есть")
        assertEquals(80.0, measureHeight(squeezed), 0.001, "длинная сжимается до 80")
    }

    @Test
    fun frameFromEightyUpKeepsTheOldMinimum() {
        val eighty = buildShape(Shape.Kind.RECTANGLE, 400.0, 400.0, 80.0, 120.0)

        val squeezed = resizeShape(eighty, ShapeHandle.BOTTOM_RIGHT, -5000f, -5000f)

        assertEquals(80.0, measureWidth(squeezed), 0.001)
        assertEquals(80.0, measureHeight(squeezed), 0.001)
    }
}
