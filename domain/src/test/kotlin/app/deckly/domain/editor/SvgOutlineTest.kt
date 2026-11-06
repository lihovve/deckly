package app.deckly.domain.editor

import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SvgOutlineTest {
    private fun parse(svg: String) = parseSvgOutlines(svg)

    private fun points(vararg xy: Pair<Float, Float>) = xy.map { GridPoint(it.first, it.second) }

    private fun assertPoints(expected: List<GridPoint>, actual: List<GridPoint>) {
        assertEquals(expected.size, actual.size, "число точек: $actual")
        expected.zip(actual).forEachIndexed { index, (want, got) ->
            assertEquals(want.x, got.x, 0.001f, "x точки $index")
            assertEquals(want.y, got.y, 0.001f, "y точки $index")
        }
    }

    private fun longestSegment(outlines: List<List<GridPoint>>): Float =
        outlines.maxOf { line -> line.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.maxOrNull() ?: 0f }

    @Test
    fun absoluteCommandsBuildPolylinesWithHorizontalAndVerticalMoves() {
        val outlines = parse("""<path d="M10 20H50V60L80 90"/>""")

        assertEquals(1, outlines.size)
        assertPoints(points(10f to 20f, 50f to 20f, 50f to 60f, 80f to 90f), outlines[0])
    }

    @Test
    fun relativeCommandsMoveFromTheCurrentPoint() {
        val outlines = parse("""<path d="m10 20h40v40l30 30l-5-5"/>""")

        // l30 30 абсолютный L80 90? Нет: относительный, от (50, 60): (80, 90); затем l-5-5 даёт (75, 85).
        assertPoints(points(10f to 20f, 50f to 20f, 50f to 60f, 80f to 90f, 75f to 85f), outlines[0])
    }

    @Test
    fun implicitRepeatsContinueTheCommandAndNumbersMayBeGluedByMinus() {
        // `M88 50 68 70` — второй отрезок без команды; `m50 10 12 27 29 3-22 20` — относительные повторы со знаком минус.
        assertPoints(points(88f to 50f, 68f to 70f), parse("""<path d="M88 50 68 70"/>""")[0])
        assertPoints(
            points(50f to 10f, 62f to 37f, 91f to 40f, 69f to 60f),
            parse("""<path d="m50 10 12 27 29 3-22 20"/>""")[0],
        )
    }

    @Test
    fun closePathReturnsToTheStartAndRelativeMovesRestartFromIt() {
        val outlines = parse("""<path d="M10 10H30V30Z m5 5 10 0"/>""")

        assertEquals(2, outlines.size)
        assertPoints(points(10f to 10f, 30f to 10f, 30f to 30f, 10f to 10f), outlines[0])
        // После Z текущая точка — начало подконтура (10, 10), относительный `m5 5` даёт (15, 15), затем (25, 15).
        assertPoints(points(15f to 15f, 25f to 15f), outlines[1])
    }

    @Test
    fun everyMoveStartsANewPolyline() {
        val outlines = parse("""<path d="M0 0L10 0M20 0L30 0M40 0 50 10"/>""")

        assertEquals(3, outlines.size)
        assertPoints(points(40f to 0f, 50f to 10f), outlines[2])
    }

    @Test
    fun rectangleEllipseAndCircleElementsAreRead() {
        val rect = parse("""<rect x="14" y="20" width="72" height="60"/>""")
        assertPoints(points(14f to 20f, 86f to 20f, 86f to 80f, 14f to 80f, 14f to 20f), rect[0])

        val ellipse = parse("""<ellipse cx="50" cy="50" rx="38" ry="30"/>""")[0]
        val circle = parse("""<circle cx="50" cy="50" r="23"/>""")[0]
        assertEquals(ellipse.first(), ellipse.last())
        assertEquals(circle.first(), circle.last())
        // Крайние точки на осях: ellipse от 12 до 88 по x и от 20 до 80 по y.
        for (extreme in points(12f to 50f, 88f to 50f, 50f to 20f, 50f to 80f)) {
            assertTrue(ellipse.any { hypot(it.x - extreme.x, it.y - extreme.y) < 0.01f }, "нет крайней точки $extreme")
        }
        assertTrue(circle.all { Math.abs(hypot(it.x - 50f, it.y - 50f) - 23f) < 0.01f })
    }

    @Test
    fun severalElementsGiveSeveralOutlinesInOrder() {
        val outlines = parse("""<circle cx="50" cy="50" r="23"/><path d="M50 7V16M50 84V93"/>""")

        assertEquals(3, outlines.size)
        assertPoints(points(50f to 7f, 50f to 16f), outlines[1])
    }

    @Test
    fun arcFollowsTheCircleByTheSweepFlagAndStaysWithinTwoUnits() {
        // Полукруг радиуса 40 с центром (50, 50) по часовой стрелке: из (10, 50) через верх (50, 10) в (90, 50).
        val outline = parse("""<path d="M10 50A40 40 0 0 1 90 50"/>""")

        assertPoints(points(10f to 50f), outline[0].take(1))
        assertPoints(points(90f to 50f), outline[0].takeLast(1))
        assertTrue(outline[0].all { Math.abs(hypot(it.x - 50f, it.y - 50f) - 40f) < 0.05f }, "точка не на окружности")
        assertTrue(outline[0].any { hypot(it.x - 50f, it.y - 10f) < 1f }, "дуга не прошла через верх")
        assertTrue(longestSegment(outline) <= 2.0001f)
    }

    @Test
    fun oppositeSweepFlagGoesThroughTheBottom() {
        val outline = parse("""<path d="M10 50A40 40 0 0 0 90 50"/>""")

        assertTrue(outline[0].any { hypot(it.x - 50f, it.y - 90f) < 1f })
        assertTrue(outline[0].none { it.y < 49.9f })
    }

    @Test
    fun largeArcFlagChoosesTheLongerWay() {
        // Радиус 50 и хорда 80: центров два, флаги выбирают дугу больше или меньше полукруга.
        val small = parse("""<path d="M10 50A50 50 0 0 1 90 50"/>""")[0]
        val large = parse("""<path d="M10 50A50 50 0 1 1 90 50"/>""")[0]

        assertTrue(large.size > small.size)
    }

    @Test
    fun tooSmallRadiiAreScaledUpLikeSvgDoes() {
        // Хорда 80, радиус 10: по спецификации радиус растёт до 40 и получается полукруг.
        val outline = parse("""<path d="M10 50A10 10 0 0 1 90 50"/>""")[0]

        assertTrue(outline.all { Math.abs(hypot(it.x - 50f, it.y - 50f) - 40f) < 0.05f })
    }

    @Test
    fun relativeArcUsesTheCurrentPointAsStart() {
        val absolute = parse("""<path d="M20 20A30 30 0 0 1 80 20"/>""")[0]
        val relative = parse("""<path d="M20 20a30 30 0 0 1 60 0"/>""")[0]

        assertPoints(absolute, relative)
    }

    @Test
    fun arcWithZeroRadiusIsAStraightLineToItsEnd() {
        // По правилам SVG дуга с нулевым радиусом по любой оси превращается в отрезок.
        assertPoints(points(10f to 10f, 50f to 30f), parse("""<path d="M10 10A0 5 0 0 1 50 30"/>""")[0])
        assertPoints(points(10f to 10f, 50f to 30f), parse("""<path d="M10 10A5 0 0 0 1 50 30"/>""")[0])
    }

    @Test
    fun arcToTheCurrentPointStaysAtThatPoint() {
        val line = parse("""<path d="M10 10A5 5 0 0 1 10 10"/>""")[0]

        assertEquals(10f, line.last().x, 0.001f)
        assertEquals(10f, line.last().y, 0.001f)
        assertTrue(line.all { it.x == 10f && it.y == 10f })
    }

    @Test
    fun cubicCurveReachesItsEndsAndStaysWithinTwoUnits() {
        val outline = parse("""<path d="M0 0C0 50 100 50 100 0"/>""")

        assertPoints(points(0f to 0f), outline[0].take(1))
        assertPoints(points(100f to 0f), outline[0].takeLast(1))
        assertTrue(longestSegment(outline) <= 2.0001f)
        // Середина кривой: B(0,5) = (50, 37,5).
        assertTrue(outline[0].any { hypot(it.x - 50f, it.y - 37.5f) < 1f })
    }

    @Test
    fun relativeCubicIsOffsetFromTheCurrentPoint() {
        val absolute = parse("""<path d="M10 10C10 60 110 60 110 10"/>""")[0]
        val relative = parse("""<path d="M10 10c0 50 100 50 100 0"/>""")[0]

        assertPoints(absolute, relative)
    }

    @Test
    fun unsupportedCommandOrElementIsRejected() {
        assertThrows<IllegalArgumentException> { parse("""<path d="M0 0S10 10 20 20"/>""") }
        assertThrows<IllegalArgumentException> { parse("""<polygon points="0,0 10,10"/>""") }
        assertThrows<IllegalArgumentException> { parse("""<path d="L10 10"/>""") }
    }
}
