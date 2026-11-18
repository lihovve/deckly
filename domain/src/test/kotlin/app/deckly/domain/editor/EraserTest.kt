package app.deckly.domain.editor

import app.deckly.domain.buildPath
import app.deckly.domain.buildStroke
import app.deckly.domain.model.Stroke
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EraserTest {

    private val diagonalPath = buildPath(10f to 10f, 60f to 40f)

    @Test
    fun fastStrokeWithSparsePointsIsErased() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertTrue(isStrokeTouched(stroke, buildPath(50f to 3f), radius = 5f))
    }

    @Test
    fun tapErasesWithinRadius() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertTrue(isStrokeTouched(stroke, buildPath(50f to 4f), radius = 5f))
        assertFalse(isStrokeTouched(stroke, buildPath(50f to 6f), radius = 5f))
    }

    @Test
    fun touchAtExactRadiusCounts() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertTrue(isStrokeTouched(stroke, buildPath(50f to 5f), radius = 5f))
    }

    @Test
    fun strokeEndsAreNotExtendedIntoLines() {
        val stroke = buildStroke(0f to 0f, 10f to 0f)

        assertFalse(isStrokeTouched(stroke, buildPath(20f to 3f), radius = 5f))
        assertTrue(isStrokeTouched(stroke, buildPath(13f to 3f), radius = 5f))
    }

    @Test
    fun crossingPathTouchesStrokeEvenWhenNoPointIsNear() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)
        val crossing = buildPath(50f to -10f, 50f to 10f)

        assertTrue(isStrokeTouched(stroke, crossing, radius = 0.5f))
    }

    @Test
    fun parallelPathBeyondRadiusDoesNotTouch() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertFalse(isStrokeTouched(stroke, buildPath(0f to 10f, 100f to 10f), radius = 5f))
        assertTrue(isStrokeTouched(stroke, buildPath(0f to 4f, 100f to 4f), radius = 5f))
    }

    @Test
    fun anySegmentOfTheStrokeCounts() {
        val stroke = buildStroke(0f to 0f, 10f to 0f, 10f to 100f)

        assertTrue(isStrokeTouched(stroke, buildPath(14f to 50f), radius = 5f))
        assertFalse(isStrokeTouched(stroke, buildPath(14f to 50f), radius = 3f))
    }

    @Test
    fun singlePointStrokeIsTouchedByNearbyTap() {
        val dot = buildStroke(10f to 10f)

        assertTrue(isStrokeTouched(dot, buildPath(12f to 10f), radius = 3f))
        assertFalse(isStrokeTouched(dot, buildPath(12f to 10f), radius = 1f))
    }

    @Test
    fun emptyStrokeOrEmptyPathTouchesNothing() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertFalse(isStrokeTouched(Stroke(2f, emptyList()), buildPath(0f to 0f), radius = 50f))
        assertFalse(isStrokeTouched(stroke, emptyList(), radius = 50f))
    }

    @Test
    fun negativeRadiusTouchesNothingEvenWhenStrokeCrossesThePath() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertFalse(isStrokeTouched(stroke, buildPath(50f to 0f), radius = -1f))
        assertFalse(isStrokeTouched(stroke, buildPath(50f to -10f, 50f to 10f), radius = -1f))
    }

    // --- край прямоугольника досягаемости: границы траектории, расширенные на радиус -----------------

    // Траектория (10, 10) – (60, 40) с радиусом 5 достаёт до x 5..65 и y 5..45. Штрих лежит ровно на краю
    // и касается ровно на радиусе; на волос дальше уже не касается. Края проверяются все четыре: у каждого
    // свой конец траектории и своя ось.

    @Test
    fun strokeOnTheRightEdgeOfReachCountsAndAHairFurtherDoesNot() {
        assertTrue(isStrokeTouched(buildStroke(65f to 0f, 65f to 100f), diagonalPath, radius = 5f))
        assertFalse(isStrokeTouched(buildStroke(Math.nextUp(65f) to 0f, Math.nextUp(65f) to 100f), diagonalPath, radius = 5f))
    }

    @Test
    fun strokeOnTheLeftEdgeOfReachCountsAndAHairFurtherDoesNot() {
        assertTrue(isStrokeTouched(buildStroke(5f to 0f, 5f to 100f), diagonalPath, radius = 5f))
        assertFalse(isStrokeTouched(buildStroke(Math.nextDown(5f) to 0f, Math.nextDown(5f) to 100f), diagonalPath, radius = 5f))
    }

    @Test
    fun strokeOnTheBottomEdgeOfReachCountsAndAHairFurtherDoesNot() {
        assertTrue(isStrokeTouched(buildStroke(0f to 45f, 100f to 45f), diagonalPath, radius = 5f))
        assertFalse(isStrokeTouched(buildStroke(0f to Math.nextUp(45f), 100f to Math.nextUp(45f)), diagonalPath, radius = 5f))
    }

    @Test
    fun strokeOnTheTopEdgeOfReachCountsAndAHairFurtherDoesNot() {
        assertTrue(isStrokeTouched(buildStroke(0f to 5f, 100f to 5f), diagonalPath, radius = 5f))
        assertFalse(isStrokeTouched(buildStroke(0f to Math.nextDown(5f), 100f to Math.nextDown(5f)), diagonalPath, radius = 5f))
    }

    @Test
    fun strokePointOnTheCornerOfReachIsNotTouchedBecauseTheCornerIsFartherThanTheRadius() {
        // Прямоугольник досягаемости точки (95, 95) с радиусом 5 кончается углом (100, 100), до него 5·√2 > 5.
        val tap = buildPath(95f to 95f)

        assertFalse(isStrokeTouched(buildStroke(100f to 100f), tap, radius = 5f))
        assertFalse(isStrokeTouched(buildStroke(100f to 100f, 110f to 110f), tap, radius = 5f))
        assertFalse(isStrokeTouched(buildStroke(105f to 95f, 95f to 105f), tap, radius = 5f))
        assertTrue(isStrokeTouched(buildStroke(99f to 97f, 110f to 110f), tap, radius = 5f))
    }

    @Test
    fun touchAtExactRadiusAlongTheDiagonalCountsAndAHairFurtherDoesNot() {
        // Расстояние от (97, 96) до (100, 100) — ровно 5 (3-4-5).
        val tap = buildPath(97f to 96f)
        val dot = buildStroke(100f to 100f)

        assertTrue(isStrokeTouched(dot, tap, radius = 5f))
        assertFalse(isStrokeTouched(dot, tap, radius = Math.nextDown(5f)))
    }

    @Test
    fun strokeThatReachesTheTraceOnlyWithItsLastSegmentIsTouched() {
        val wandering = List(200) { it * 5f to 900f }
        val stroke = buildStroke(*wandering.toTypedArray(), 995f to 905f, 995f to 1000f)

        assertTrue(isStrokeTouched(stroke, buildPath(1000f to 960f), radius = 6f))
        assertFalse(isStrokeTouched(stroke, buildPath(1000f to 960f), radius = 4f))
    }

    @Test
    fun longStrokeAcrossTheSheetIsTouchedByOneNearSegmentOnly() {
        val across = buildStroke(0f to 0f, 1000f to 1000f)

        assertTrue(isStrokeTouched(across, buildPath(500f to 504f), radius = 3f))
        assertFalse(isStrokeTouched(across, buildPath(500f to 504f), radius = 2f))
        assertFalse(isStrokeTouched(across, buildPath(800f to 100f, 900f to 100f), radius = 3f))
    }

    @Test
    fun touchThatRoundingAcceptsJustBeyondTheReachIsKept() {
        // Штрих на x = 1e-14, траектория — точка x = -500, радиус 500: геометрически расстояние на 1e-14 больше радиуса,
        // но в double -500 - 1e-14 округляется до -500, и расчёт засчитывает касание. Отсечение по границе этот исход
        // менять не должно: запас на округление оставляет такой штрих на расчёте.
        val stroke = buildStroke(1e-14f to -1f, 1e-14f to 1f)

        assertTrue(isStrokeTouched(stroke, buildPath(-500f to 0f), radius = 500f))
    }
}
