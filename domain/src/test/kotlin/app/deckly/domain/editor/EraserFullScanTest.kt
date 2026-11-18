package app.deckly.domain.editor

import app.deckly.domain.model.CardSide
import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Section
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ластик даёт то же, что полный перебор пар отрезков (`FullScanEraser.kt`): на случайных данных с фиксированным зерном
 * по всему листу и на целочисленной сетке, где расстояния часто равны радиусу ровно.
 */
class EraserFullScanTest {

    private val radii = listOf(0.5f, 3f, 12f, 40f, 150f)
    private val gridRadii = listOf(0f, 1f, 2f, 3f, 5f, 8f)

    // --- штрихи на листе ---------------------------------------------------------------------------

    @Test
    fun strokeTouchEqualsFullScanOnRandomSheet() {
        val random = Random(SEED)
        val strokes = generateStrokes(random, 300)

        for ((traceIndex, path) in generateTraces(random).withIndex()) {
            for (radius in radii) {
                for ((index, stroke) in strokes.withIndex()) {
                    assertEquals(
                        isTouchedByFullScan(listOf(stroke.points), path, radius),
                        isStrokeTouched(stroke, path, radius),
                        "stroke $index, trace $traceIndex, radius $radius",
                    )
                }
            }
        }
    }

    @Test
    fun randomSheetHasNoticeableSharesOfTouchedAndUntouchedStrokes() {
        val random = Random(SEED)
        val strokes = generateStrokes(random, 300).filter { it.points.isNotEmpty() }
        var touched = 0
        var total = 0

        for (path in generateTraces(random).filter { it.isNotEmpty() }) {
            for (radius in radii) {
                touched += strokes.count { isTouchedByFullScan(listOf(it.points), path, radius) }
                total += strokes.size
            }
        }

        assertTrue(touched >= total / 10, "touched $touched of $total")
        assertTrue(total - touched >= total / 10, "touched $touched of $total")
    }

    @Test
    fun radiiDerivedFromBoxGapsEqualFullScan() {
        val random = Random(SEED + 1)
        val strokes = generateStrokes(random, 300).filter { it.points.isNotEmpty() }
        var checked = 0

        for (path in generateTraces(random).filter { it.isNotEmpty() }) {
            val pathBox = computeBox(path)
            for (stroke in strokes) {
                val strokeBox = computeBox(stroke.points)
                val gaps = listOf(
                    strokeBox.minX - pathBox.maxX,
                    pathBox.minX - strokeBox.maxX,
                    strokeBox.minY - pathBox.maxY,
                    pathBox.minY - strokeBox.maxY,
                ).filter { it > 0f }
                for (gap in gaps) {
                    for (radius in listOf(Math.nextDown(gap), gap, Math.nextUp(gap))) {
                        assertEquals(
                            isTouchedByFullScan(listOf(stroke.points), path, radius),
                            isStrokeTouched(stroke, path, radius),
                            "box gap $gap, radius $radius",
                        )
                        checked++
                    }
                }
            }
        }

        assertTrue(checked > 1000, "checked $checked")
    }

    // --- сетка с ничьими ---------------------------------------------------------------------------

    @Test
    fun strokeTouchEqualsFullScanOnIntegerGrid() {
        val random = Random(SEED + 2)
        val strokes = generateGridStrokes(random, 400)

        for ((traceIndex, path) in generateGridTraces(random).withIndex()) {
            for (radius in gridRadii) {
                for ((index, stroke) in strokes.withIndex()) {
                    assertEquals(
                        isTouchedByFullScan(listOf(stroke.points), path, radius),
                        isStrokeTouched(stroke, path, radius),
                        "grid stroke $index, trace $traceIndex, radius $radius",
                    )
                }
            }
        }
    }

    @Test
    fun integerGridHasStrokesExactlyAtTheRadius() {
        val random = Random(SEED + 2)
        val strokes = generateGridStrokes(random, 400)
        var atTheRadius = 0
        var touched = 0
        var total = 0

        for (path in generateGridTraces(random)) {
            for (radius in gridRadii) {
                for (stroke in strokes) {
                    val isTouched = isTouchedByFullScan(listOf(stroke.points), path, radius)
                    val isTouchedBelow = isTouchedByFullScan(listOf(stroke.points), path, Math.nextDown(radius))
                    if (isTouched && !isTouchedBelow) atTheRadius++
                    if (isTouched) touched++
                    total++
                }
            }
        }

        assertTrue(atTheRadius >= 100, "exactly at the radius: $atTheRadius")
        assertTrue(touched >= total / 10 && total - touched >= total / 10, "touched $touched of $total")
    }

    // --- eraseAlong и фигуры -----------------------------------------------------------------------

    @Test
    fun eraseAlongRemovesExactlyWhatFullScanSelects() {
        val random = Random(SEED + 3)
        val strokes = generateStrokes(random, 300)
        val shapes = generateShapes(random)
        val session = resumeDraft(Draft(null, CardSide(strokes, shapes), CardSide(), Side.FRONT, Section.NEW))
        var erasedSomething = 0
        var erasedNothing = 0

        for (path in generateTraces(random)) {
            for (radius in radii) {
                val expectedStrokes = strokes.withIndex()
                    .filter { isTouchedByFullScan(listOf(it.value.points), path, radius) }
                    .map { ErasedStroke(it.index, it.value) }
                val expectedShapes = shapes.withIndex()
                    .filter { isTouchedByFullScan(it.value.listPolylines(), path, radius) }
                    .map { ErasedShape(it.index, it.value) }

                val erased = session.eraseAlong(path, radius)

                if (expectedStrokes.isEmpty() && expectedShapes.isEmpty()) {
                    assertSame(session, erased)
                    erasedNothing++
                    continue
                }
                val step = erased.history.last() as EditorStep.ContentErased
                assertEquals(expectedStrokes, step.strokes)
                assertEquals(expectedShapes, step.shapes)
                assertEquals(strokes.filter { stroke -> expectedStrokes.none { it.stroke === stroke } }, erased.draft.front.strokes)
                assertEquals(shapes.filter { shape -> expectedShapes.none { it.shape === shape } }, erased.draft.front.shapes)
                erasedSomething++
            }
        }

        assertTrue(erasedSomething >= 10 && erasedNothing >= 3, "erased $erasedSomething passes, nothing in $erasedNothing")
    }

    @Test
    fun shapeTouchEqualsFullScan() {
        val random = Random(SEED + 4)
        val shapes = generateShapes(random)
        var touched = 0
        var total = 0

        for ((traceIndex, path) in generateTraces(random).withIndex()) {
            for (radius in radii) {
                for ((index, shape) in shapes.withIndex()) {
                    val expected = isTouchedByFullScan(shape.listPolylines(), path, radius)
                    assertEquals(expected, isShapeTouched(shape, path, radius), "shape $index, trace $traceIndex, radius $radius")
                    if (expected) touched++
                    total++
                }
            }
        }

        assertTrue(touched >= total / 10 && total - touched >= total / 10, "touched $touched of $total")
    }

    @Test
    fun findShapeAtEqualsFullScan() {
        val random = Random(SEED + 5)
        val shapes = generateShapes(random)
        var found = 0

        repeat(600) {
            val x = random.nextFloat() * CardUnits.WIDTH
            val y = random.nextFloat() * CardUnits.HEIGHT
            val point = listOf(StrokePoint(x, y, 1f))
            val expected = shapes.indices.lastOrNull { index ->
                isTouchedByFullScan(shapes[index].listPolylines(), point, shapes[index].width / 2 + 8f * CardUnits.PER_DESIGN_UNIT)
            }

            assertEquals(expected, findShapeAt(shapes, x, y), "point ($x, $y)")
            if (expected != null) found++
        }

        assertTrue(found in 60..540, "found $found of 600")
    }

    // --- траектории из нескольких далёких рук: отсев по парам отрезков -------------------------------

    @Test
    fun strokeAndShapeTouchEqualsFullScanForTracesWithFarArms() {
        val random = Random(SEED + 6)
        val strokes = generateStrokes(random, 300) + generateStrokesInside(random, 300)
        val shapes = generateShapes(random)

        for ((traceIndex, path) in generateArmedTraces().withIndex()) {
            for (radius in radii) {
                for ((index, stroke) in strokes.withIndex()) {
                    assertEquals(
                        isTouchedByFullScan(listOf(stroke.points), path, radius),
                        isStrokeTouched(stroke, path, radius),
                        "stroke $index, armed trace $traceIndex, radius $radius",
                    )
                }
                for ((index, shape) in shapes.withIndex()) {
                    assertEquals(
                        isTouchedByFullScan(shape.listPolylines(), path, radius),
                        isShapeTouched(shape, path, radius),
                        "shape $index, armed trace $traceIndex, radius $radius",
                    )
                }
            }
        }
    }

    @Test
    fun armedTracesHaveSegmentsThatOnlyThePairwiseRectanglesDiscard() {
        val random = Random(SEED + 6)
        val strokes = (generateStrokes(random, 300) + generateStrokesInside(random, 300)).filter { it.points.isNotEmpty() }
        var passingCommonRect = 0
        var discardedByPairs = 0
        var touched = 0
        var total = 0

        for (path in generateArmedTraces()) {
            for (radius in radii) {
                val pathBox = computeBox(path)
                for (stroke in strokes) {
                    for (segment in listSegmentsOf(stroke.points)) {
                        val isInCommonRect = isBoxWithinReach(segment, buildDiagonalOf(pathBox), radius)
                        val isNearSomeArm = listSegmentsOf(path).any { isBoxWithinReach(segment, it, radius) }
                        if (isInCommonRect) passingCommonRect++
                        if (isInCommonRect && !isNearSomeArm) discardedByPairs++
                    }
                    if (isTouchedByFullScan(listOf(stroke.points), path, radius)) touched++
                    total++
                }
            }
        }

        assertTrue(discardedByPairs >= passingCommonRect / 10, "discarded by pairs $discardedByPairs of $passingCommonRect")
        assertTrue(touched >= total / 20 && total - touched >= total / 2, "touched $touched of $total")
    }

    @Test
    fun integerGridHasTouchingPairsExactlyOnTheEdgeOfThePairwiseRectangle() {
        val random = Random(SEED + 2)
        val strokes = generateGridStrokes(random, 400)
        var onTheEdge = 0

        for (path in generateGridTraces(random)) {
            for (radius in gridRadii) {
                for (stroke in strokes) {
                    for (segment in listSegmentsOf(stroke.points)) {
                        for (arm in listSegmentsOf(path)) {
                            val isTouchingPair = isTouchedByFullScan(listOf(listOf(segment.first, segment.second)), listOf(arm.first, arm.second), radius)
                            if (isTouchingPair && isBoxOnTheEdgeOfReach(segment, arm, radius)) onTheEdge++
                        }
                    }
                }
            }
        }

        assertTrue(onTheEdge >= 100, "touching pairs on the edge: $onTheEdge")
    }

    // --- данные ------------------------------------------------------------------------------------

    private data class Box(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

    private fun computeBox(points: List<StrokePoint>) = Box(
        points.minOf { it.x },
        points.minOf { it.y },
        points.maxOf { it.x },
        points.maxOf { it.y },
    )

    /** Отрезки ломаной как пары точек; одна точка — вырожденный отрезок, пустая — нет отрезков. */
    private fun listSegmentsOf(points: List<StrokePoint>): List<Pair<StrokePoint, StrokePoint>> = when (points.size) {
        0 -> emptyList()
        1 -> listOf(points[0] to points[0])
        else -> points.zipWithNext()
    }

    /** Общий прямоугольник траектории как диагональ из двух углов: так он проверяется тем же [isBoxWithinReach]. */
    private fun buildDiagonalOf(box: Box): Pair<StrokePoint, StrokePoint> =
        StrokePoint(box.minX, box.minY, 0.5f) to StrokePoint(box.maxX, box.maxY, 0.5f)

    /** Пересекается ли прямоугольник отрезка [segment] с прямоугольником отрезка [arm], расширенным на [radius]. */
    private fun isBoxWithinReach(segment: Pair<StrokePoint, StrokePoint>, arm: Pair<StrokePoint, StrokePoint>, radius: Float): Boolean =
        min(segment.first.x, segment.second.x).toDouble() <= max(arm.first.x, arm.second.x).toDouble() + radius &&
            max(segment.first.x, segment.second.x).toDouble() >= min(arm.first.x, arm.second.x).toDouble() - radius &&
            min(segment.first.y, segment.second.y).toDouble() <= max(arm.first.y, arm.second.y).toDouble() + radius &&
            max(segment.first.y, segment.second.y).toDouble() >= min(arm.first.y, arm.second.y).toDouble() - radius

    /** Лежит ли край прямоугольника [segment] ровно на краю прямоугольника [arm], расширенного на [radius], при пересечении по другой оси. */
    private fun isBoxOnTheEdgeOfReach(segment: Pair<StrokePoint, StrokePoint>, arm: Pair<StrokePoint, StrokePoint>, radius: Float): Boolean {
        val sMinX = min(segment.first.x, segment.second.x).toDouble()
        val sMaxX = max(segment.first.x, segment.second.x).toDouble()
        val sMinY = min(segment.first.y, segment.second.y).toDouble()
        val sMaxY = max(segment.first.y, segment.second.y).toDouble()
        val aMinX = min(arm.first.x, arm.second.x).toDouble() - radius
        val aMaxX = max(arm.first.x, arm.second.x).toDouble() + radius
        val aMinY = min(arm.first.y, arm.second.y).toDouble() - radius
        val aMaxY = max(arm.first.y, arm.second.y).toDouble() + radius
        val isXOverlapping = sMinX <= aMaxX && sMaxX >= aMinX
        val isYOverlapping = sMinY <= aMaxY && sMaxY >= aMinY
        return (isYOverlapping && (sMinX == aMaxX || sMaxX == aMinX)) || (isXOverlapping && (sMinY == aMaxY || sMaxY == aMinY))
    }

    /** Штрихи письма внутри листа: начало в рамке 50..950, чтобы они лежали между руками траектории. */
    private fun generateStrokesInside(random: Random, count: Int): List<Stroke> = List(count) {
        var x = 50f + random.nextFloat() * 900f
        var y = 50f + random.nextFloat() * 900f
        Stroke(2f, List(random.nextInt(20, 61)) {
            x = (x + (random.nextFloat() * 2 - 1) * 8f).coerceIn(0f, CardUnits.WIDTH)
            y = (y + (random.nextFloat() * 2 - 1) * 8f).coerceIn(0f, CardUnits.HEIGHT)
            StrokePoint(x, y, 0.5f)
        })
    }

    /** Траектории из нескольких рук: Г, П, каракули в 150 × 80 посреди листа, гребёнка через весь лист, замкнутый квадрат. */
    private fun generateArmedTraces(): List<List<StrokePoint>> = listOf(
        listOf(StrokePoint(100f, 100f, 0.5f), StrokePoint(700f, 100f, 0.5f), StrokePoint(700f, 900f, 0.5f)),
        listOf(StrokePoint(50f, 900f, 0.5f), StrokePoint(50f, 100f, 0.5f), StrokePoint(900f, 100f, 0.5f), StrokePoint(900f, 900f, 0.5f)),
        List(300) { StrokePoint((500 + 75 * sin(it * 0.21)).toFloat(), (520 + 40 * sin(it * 0.77 + 1)).toFloat(), 0.5f) },
        List(60) { StrokePoint(it * 16f, if (it % 2 == 0) 100f else 900f, 0.5f) },
        listOf(
            StrokePoint(300f, 300f, 0.5f), StrokePoint(600f, 300f, 0.5f), StrokePoint(600f, 600f, 0.5f),
            StrokePoint(300f, 600f, 0.5f), StrokePoint(300f, 300f, 0.5f),
        ),
    )

    /** Блуждание из [count] точек от случайного места листа, шаг каждой координаты до [step]. */
    private fun generateWalk(random: Random, count: Int, step: Float): List<StrokePoint> {
        var x = random.nextFloat() * CardUnits.WIDTH
        var y = random.nextFloat() * CardUnits.HEIGHT
        return List(count) {
            x = (x + (random.nextFloat() * 2 - 1) * step).coerceIn(0f, CardUnits.WIDTH)
            y = (y + (random.nextFloat() * 2 - 1) * step).coerceIn(0f, CardUnits.HEIGHT)
            StrokePoint(x, y, 0.5f)
        }
    }

    /** Штрихи по всему листу: пустые, из одной точки, из двух-трёх далёких точек и письмо в 20–60 точек. */
    private fun generateStrokes(random: Random, count: Int): List<Stroke> = List(count) {
        val roll = random.nextInt(100)
        val points = when {
            roll < 4 -> emptyList()
            roll < 12 -> generateWalk(random, 1, 0f)
            roll < 20 -> generateWalk(random, random.nextInt(2, 4), CardUnits.WIDTH)
            else -> generateWalk(random, random.nextInt(20, 61), 8f)
        }
        Stroke(2f, points)
    }

    /** Траектории ластика: пустая, точка, касание, средний и два длинных росчерка, диагональ листа. */
    private fun generateTraces(random: Random): List<List<StrokePoint>> = listOf(
        emptyList(),
        generateWalk(random, 1, 0f),
        generateWalk(random, 2, 6f),
        generateWalk(random, 40, 20f),
        generateWalk(random, 300, 25f),
        generateWalk(random, 300, 60f),
        listOf(StrokePoint(0f, 0f, 0.5f), StrokePoint(CardUnits.WIDTH, CardUnits.HEIGHT, 0.5f)),
    )

    private fun generateGridPoint(random: Random) = StrokePoint(random.nextInt(61).toFloat(), random.nextInt(61).toFloat(), 0.5f)

    /** Штрихи на сетке 0..60: точки, горизонтали, вертикали, диагонали в 45° и ломаные. */
    private fun generateGridStrokes(random: Random, count: Int): List<Stroke> = List(count) {
        val points = when (random.nextInt(5)) {
            0 -> listOf(generateGridPoint(random))
            1 -> {
                val y = random.nextInt(61).toFloat()
                listOf(StrokePoint(random.nextInt(61).toFloat(), y, 0.5f), StrokePoint(random.nextInt(61).toFloat(), y, 0.5f))
            }
            2 -> {
                val x = random.nextInt(61).toFloat()
                listOf(StrokePoint(x, random.nextInt(61).toFloat(), 0.5f), StrokePoint(x, random.nextInt(61).toFloat(), 0.5f))
            }
            3 -> {
                val start = generateGridPoint(random)
                val length = random.nextInt(-20, 21).toFloat()
                listOf(start, StrokePoint(start.x + length, start.y + length, 0.5f))
            }
            else -> List(random.nextInt(2, 6)) { generateGridPoint(random) }
        }
        Stroke(2f, points)
    }

    /** Траектории на сетке: точка, горизонталь, вертикаль, диагональ, короткая и длинная ломаные. */
    private fun generateGridTraces(random: Random): List<List<StrokePoint>> = listOf(
        listOf(generateGridPoint(random)),
        listOf(StrokePoint(5f, 30f, 0.5f), StrokePoint(45f, 30f, 0.5f)),
        listOf(StrokePoint(30f, 5f, 0.5f), StrokePoint(30f, 45f, 0.5f)),
        listOf(StrokePoint(10f, 10f, 0.5f), StrokePoint(40f, 40f, 0.5f)),
        List(3) { generateGridPoint(random) },
        List(6) { generateGridPoint(random) },
        List(12) { generateGridPoint(random) },
        emptyList(),
    )

    /** По две фигуры каждого вида: случайная рамка, угол от 0 до 360 градусов. */
    private fun generateShapes(random: Random): List<Shape> = Shape.Kind.entries.flatMap { kind ->
        List(2) {
            val left = random.nextFloat() * (CardUnits.WIDTH - 200f)
            val top = random.nextFloat() * (CardUnits.HEIGHT - 200f)
            val width = 40f + random.nextFloat() * 160f
            val height = 40f + random.nextFloat() * 160f
            Shape(kind, left, top, left + width, top + height, random.nextFloat() * 360f, 3f)
        }
    }

    private companion object {
        const val SEED = 20261117
    }
}
