package app.deckly.domain.editor

import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import app.deckly.domain.model.StrokePoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShapeOutlineTest {
    private val unit = CardUnits.PER_DESIGN_UNIT

    private fun grid(kind: Shape.Kind) = kind.listGridOutlines()

    private fun gridPoints(vararg xy: Pair<Float, Float>) = xy.map { GridPoint(it.first, it.second) }

    private fun assertGrid(expected: List<GridPoint>, actual: List<GridPoint>) {
        assertEquals(expected.size, actual.size, "число точек: $actual")
        expected.zip(actual).forEach { (want, got) ->
            assertEquals(want.x, got.x, 0.001f)
            assertEquals(want.y, got.y, 0.001f)
        }
    }

    private fun shape(kind: Shape.Kind, left: Float, top: Float, right: Float, bottom: Float, angle: Float = 0f, width: Float = 6f) =
        Shape(kind, left, top, right, bottom, angle, width)

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: StrokePoint, delta: Float = 0.01f) {
        assertEquals(expectedX, actual.x, delta, "x")
        assertEquals(expectedY, actual.y, delta, "y")
    }

    // --- сетка 100 × 100 -------------------------------------------------------------------------

    @Test
    fun lineIsTwoPointsOnTheMiddle() {
        assertEquals(1, grid(Shape.Kind.LINE).size)
        assertGrid(gridPoints(12f to 50f, 88f to 50f), grid(Shape.Kind.LINE)[0])
    }

    @Test
    fun arrowIsAxisAndOneHead() {
        val outlines = grid(Shape.Kind.ARROW)

        assertEquals(2, outlines.size)
        assertGrid(gridPoints(12f to 50f, 88f to 50f, 68f to 30f), outlines[0])
        assertGrid(gridPoints(88f to 50f, 68f to 70f), outlines[1])
    }

    @Test
    fun doubleArrowHasAxisAndTwoHeads() {
        val outlines = grid(Shape.Kind.DOUBLE_ARROW)

        assertEquals(3, outlines.size)
        assertGrid(gridPoints(12f to 50f, 88f to 50f), outlines[0])
        assertGrid(gridPoints(32f to 30f, 12f to 50f, 32f to 70f), outlines[1])
        assertGrid(gridPoints(68f to 30f, 88f to 50f, 68f to 70f), outlines[2])
    }

    @Test
    fun polygonsHaveTheirVerticesAndAreClosed() {
        assertGrid(gridPoints(14f to 20f, 86f to 20f, 86f to 80f, 14f to 80f, 14f to 20f), grid(Shape.Kind.RECTANGLE)[0])
        assertGrid(gridPoints(50f to 12f, 89f to 85f, 11f to 85f, 50f to 12f), grid(Shape.Kind.TRIANGLE)[0])
        assertGrid(gridPoints(50f to 10f, 86f to 50f, 50f to 90f, 14f to 50f, 50f to 10f), grid(Shape.Kind.DIAMOND)[0])
        assertGrid(
            gridPoints(30f to 15f, 70f to 15f, 90f to 50f, 70f to 85f, 30f to 85f, 10f to 50f, 30f to 15f),
            grid(Shape.Kind.HEXAGON)[0],
        )
    }

    @Test
    fun starKeepsTheRelativeVerticesOfItsPath() {
        // m50 10 12 27 29 3-22 20 6 30-25-15-25 15 6-30L9 40l29-3Z
        assertGrid(
            gridPoints(50f to 10f, 62f to 37f, 91f to 40f, 69f to 60f, 75f to 90f, 50f to 75f, 25f to 90f, 31f to 60f, 9f to 40f, 38f to 37f, 50f to 10f),
            grid(Shape.Kind.STAR)[0],
        )
    }

    @Test
    fun ovalHasItsExtremePoints() {
        val oval = grid(Shape.Kind.OVAL)[0]

        for (extreme in gridPoints(12f to 50f, 88f to 50f, 50f to 20f, 50f to 80f)) {
            assertTrue(oval.any { hypot(it.x - extreme.x, it.y - extreme.y) < 0.01f }, "нет $extreme")
        }
    }

    @Test
    fun moonAndDropPassThroughTheirAnchorPoints() {
        val moon = grid(Shape.Kind.MOON)[0]
        for (anchor in gridPoints(65f to 12f, 87f to 70f)) {
            assertTrue(moon.any { hypot(it.x - anchor.x, it.y - anchor.y) < 0.01f }, "луна без $anchor")
        }
        val drop = grid(Shape.Kind.DROP)[0]
        for (anchor in gridPoints(50f to 10f, 20f to 62f, 80f to 62f)) {
            assertTrue(drop.any { hypot(it.x - anchor.x, it.y - anchor.y) < 0.01f }, "капля без $anchor")
        }
    }

    @Test
    fun sunIsCircleAndEightRays() {
        val outlines = grid(Shape.Kind.SUN)

        // Круг, затем восемь лучей из одного `path`: по подконтуру на луч.
        assertEquals(9, outlines.size)
        assertGrid(gridPoints(50f to 7f, 50f to 16f), outlines[1])
    }

    @Test
    fun everyKindStaysInsideTheHundredBySquareAndIsNotEmpty() {
        for (kind in Shape.Kind.entries) {
            val outlines = grid(kind)
            assertTrue(outlines.isNotEmpty() && outlines.all { it.size >= 2 }, kind.key)
            for (point in outlines.flatten()) {
                assertTrue(point.x in -0.001f..100.001f && point.y in -0.001f..100.001f, "${kind.key}: ($point)")
            }
        }
    }

    @Test
    fun curvedKindsAreSmoothEnoughAndOutlinesAreCached() {
        for (kind in listOf(Shape.Kind.OVAL, Shape.Kind.MOON, Shape.Kind.HEART, Shape.Kind.CLOUD, Shape.Kind.DROP)) {
            val points = grid(kind).flatten()
            assertTrue(points.size > 40, "${kind.key}: слишком мало точек ${points.size}")
        }
        // Сетка считается один раз на вид.
        assertTrue(grid(Shape.Kind.CLOUD) === grid(Shape.Kind.CLOUD))
    }

    // --- границы контура ---------------------------------------------------------------------------

    private fun assertGridBounds(expected: GridBounds, kind: Shape.Kind) {
        val actual = kind.findGridBounds()
        assertEquals(expected.minX, actual.minX, 0.001f, "${kind.key}: minX")
        assertEquals(expected.minY, actual.minY, 0.001f, "${kind.key}: minY")
        assertEquals(expected.maxX, actual.maxX, 0.001f, "${kind.key}: maxX")
        assertEquals(expected.maxY, actual.maxY, 0.001f, "${kind.key}: maxY")
    }

    @Test
    fun gridBoundsAreTheExtremesOfTheIconOutline() {
        // Значок стоит в сетке с полями: у прямоугольника 14…86 × 20…80, у звезды 9…91 × 10…90, у треугольника 11…89 × 12…85.
        assertGridBounds(GridBounds(14f, 20f, 86f, 80f), Shape.Kind.RECTANGLE)
        assertGridBounds(GridBounds(9f, 10f, 91f, 90f), Shape.Kind.STAR)
        assertGridBounds(GridBounds(11f, 12f, 89f, 85f), Shape.Kind.TRIANGLE)
        for (kind in Shape.Kind.entries) {
            val points = grid(kind).flatten()
            assertGridBounds(GridBounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }), kind)
        }
    }

    @Test
    fun gridBoundsAreCountedOncePerKind() {
        assertTrue(Shape.Kind.CLOUD.findGridBounds() === Shape.Kind.CLOUD.findGridBounds())
    }

    @Test
    fun contourOfEveryOrdinaryKindTouchesAllFourSidesOfItsFrame() {
        val frames = listOf(
            listOf(100f, 200f, 300f, 500f),
            listOf(0f, 0f, 80f, 80f),
            listOf(333.3f, 40.5f, 910f, 160.25f),
        )
        for (kind in Shape.Kind.entries.filterNot { it.isLinear }) {
            for ((left, top, right, bottom) in frames) {
                val points = shape(kind, left, top, right, bottom).listPolylines().flatten()

                assertEquals(left, points.minOf { it.x }, 0.01f, "${kind.key} $left…$right: левая сторона")
                assertEquals(right, points.maxOf { it.x }, 0.01f, "${kind.key} $left…$right: правая сторона")
                assertEquals(top, points.minOf { it.y }, 0.01f, "${kind.key} $top…$bottom: верхняя сторона")
                assertEquals(bottom, points.maxOf { it.y }, 0.01f, "${kind.key} $top…$bottom: нижняя сторона")
            }
        }
    }

    // --- контуры на листе -------------------------------------------------------------------------

    @Test
    fun contourIsStretchedOverTheFrameFromItsOwnBoundsIndependentlyByAxes() {
        val rectangle = shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f)

        val outline = rectangle.listPolylines()[0]

        // Контур прямоугольника — сама рамка 200 × 300: ни полей значка (14 и 20 %), ни растяжения сетки 100 × 100.
        assertPoint(100f, 200f, outline[0])
        assertPoint(300f, 200f, outline[1])
        assertPoint(300f, 500f, outline[2])
        assertPoint(100f, 500f, outline[3])
        assertPoint(100f, 200f, outline[4])
    }

    @Test
    fun rotationTurnsTheOutlineAroundTheFrameCenter() {
        val rectangle = shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, angle = 90f)

        val outline = rectangle.listPolylines()[0]

        // Центр (200, 350). Угол (100, 200) лежит в (−100, −150) от него; поворот по часовой на 90°: (x, y) → (−y, x).
        assertPoint(200f + 150f, 350f - 100f, outline[0])
        assertPoint(200f + 150f, 350f + 100f, outline[1])
    }

    @Test
    fun rotationByAnyAngleKeepsDistancesToTheCenter() {
        val star = shape(Shape.Kind.STAR, 100f, 100f, 400f, 300f)
        val plain = star.listPolylines()[0]
        val turned = star.copy(angle = 37f).listPolylines()[0]

        assertEquals(plain.size, turned.size)
        val cx = 250f
        val cy = 200f
        plain.zip(turned).forEach { (a, b) ->
            assertEquals(hypot(a.x - cx, a.y - cy), hypot(b.x - cx, b.y - cy), 0.01f)
            val radians = Math.toRadians(37.0)
            val rx = a.x - cx
            val ry = a.y - cy
            assertEquals(cx + rx * cos(radians).toFloat() - ry * sin(radians).toFloat(), b.x, 0.01f)
            assertEquals(cy + rx * sin(radians).toFloat() + ry * cos(radians).toFloat(), b.y, 0.01f)
        }
    }

    private fun linear(kind: Shape.Kind, designWidth: Float, angle: Float = 0f, left: Float = 100f, top: Float = 300f) =
        shape(kind, left, top, left + designWidth * unit, top + 80f * unit, angle)

    @Test
    fun lineAxisRunsFromTwelveToEightyEightPercentOfWidthOnTheMiddle() {
        val line = linear(Shape.Kind.LINE, 500f)

        val outlines = line.listPolylines()

        assertEquals(1, outlines.size)
        val y = 300f + 40f * unit
        assertPoint(100f + 0.12f * 500f * unit, y, outlines[0][0])
        assertPoint(100f + 0.88f * 500f * unit, y, outlines[0][1])
    }

    @Test
    fun arrowHeadKeepsItsSizeWhateverTheLength() {
        // Остриё 24 вдоль оси и ±16 поперёк в единицах макета: у стрелки шириной 320 и 1000 оно одинаковое.
        for (designWidth in listOf(320f, 1000f)) {
            val arrow = linear(Shape.Kind.ARROW, designWidth)

            val outlines = arrow.listPolylines()

            assertEquals(2, outlines.size)
            val head = outlines[1]
            val tip = head[1]
            assertEquals(24f * unit, tip.x - head[0].x, 0.01f, "длина острия при ширине $designWidth")
            assertEquals(16f * unit, tip.y - head[0].y, 0.01f)
            assertEquals(16f * unit, head[2].y - tip.y, 0.01f)
            assertEquals(head[0].x, head[2].x, 0.01f)
        }
    }

    @Test
    fun shortArrowHeadIsOneThirdOfTheAxis() {
        // Ширина 60: ось 0,76 · 60 = 45,6, треть — 15,2, это меньше 24.
        val arrow = linear(Shape.Kind.ARROW, 60f)

        val head = arrow.listPolylines()[1]

        assertEquals(15.2f * unit, head[1].x - head[0].x, 0.01f)
    }

    @Test
    fun doubleArrowHasAHeadOnEachEndAndLineHasNone() {
        assertEquals(1, linear(Shape.Kind.LINE, 400f).listPolylines().size)
        val double = linear(Shape.Kind.DOUBLE_ARROW, 400f).listPolylines()

        assertEquals(3, double.size)
        val axis = double[0]
        val startHead = double[2]
        val endHead = double[1]
        assertEquals(axis[1].x, endHead[1].x, 0.01f)
        assertEquals(axis[0].x, startHead[1].x, 0.01f)
        assertEquals(24f * unit, startHead[0].x - startHead[1].x, 0.01f)
        assertEquals(24f * unit, endHead[1].x - endHead[0].x, 0.01f)
    }

    @Test
    fun linearShapeIsRotatedAroundTheCenterOfItsFrame() {
        val line = linear(Shape.Kind.LINE, 500f, angle = 90f)

        val axis = line.listPolylines()[0]

        // Рамка 500 × 80 единиц макета, центр (100 + 250·u, 300 + 40·u). Ось после поворота на 90° вертикальна.
        val cx = 100f + 250f * unit
        val cy = 300f + 40f * unit
        assertEquals(cx, axis[0].x, 0.01f)
        assertEquals(cx, axis[1].x, 0.01f)
        assertEquals(cy - 0.38f * 500f * unit, axis[0].y, 0.01f)
        assertEquals(cy + 0.38f * 500f * unit, axis[1].y, 0.01f)
    }

    @Test
    fun linearNumbersComeFromTheMockupDemo() {
        val script = app.deckly.domain.DesignSources.shapesScript

        // buildLinearShape: ось от 12 % до 88 %, остриё min(24, длина оси / 3), поперёк ±16, лента выбора width + 16.
        assertTrue(script.contains("const start = box.w * 0.12, end = box.w * 0.88, y = box.h / 2;"))
        assertTrue(script.contains("const head = Math.min(24, (end - start) / 3);"))
        assertTrue(script.contains("y - 16"))
        assertTrue(script.contains("y + 16"))
        assertTrue(script.contains("hit.setAttribute('stroke-width', item.width + 16);"))
    }

    // Хеши по битам `x` и `y` всех точек `listPolylines()` трёх рамок и углов у каждого вида. Сняты до того, как формула контура
    // выделена в функцию на `Double`: рефакторинг не должен менять рисунок ни на бит. Менять их стоит только вместе с осознанной
    // правкой формулы контура.
    private val polylineHashes = mapOf(
        Shape.Kind.LINE to longArrayOf(-1026831138848339871L, -8016943413030240553L, -5210225879204924700L),
        Shape.Kind.ARROW to longArrayOf(-5235162840465835591L, -5226939618332248447L, -604144581446694754L),
        Shape.Kind.DOUBLE_ARROW to longArrayOf(2539060755609918353L, 4037971848725878491L, 5501794797341402564L),
        Shape.Kind.RECTANGLE to longArrayOf(7309690382727302329L, -1546394730552270523L, -4442083826050364538L),
        Shape.Kind.OVAL to longArrayOf(-8846121065003976483L, -1251250345519788362L, 6056617431178486455L),
        Shape.Kind.TRIANGLE to longArrayOf(-6970578524172740175L, -487774579151698442L, 5864053552285135590L),
        Shape.Kind.DIAMOND to longArrayOf(7548985975795272889L, 4908648804472393815L, -1956244810168725408L),
        Shape.Kind.HEXAGON to longArrayOf(-675571636215496311L, -4203331879671421867L, 8653894238612103128L),
        Shape.Kind.STAR to longArrayOf(3385874259322322774L, 2693910103147328754L, -3604312307406387515L),
        Shape.Kind.MOON to longArrayOf(4179732716574444143L, -8281802763794453784L, -2768718079615437997L),
        Shape.Kind.SUN to longArrayOf(-8424241321237253701L, 3820730684575960507L, 1493857613745871164L),
        Shape.Kind.HEART to longArrayOf(142876396446023267L, -2112962482988163173L, -5258879653902087767L),
        Shape.Kind.CLOUD to longArrayOf(2319626549392518422L, -3833840364071054178L, 3468311427583586880L),
        Shape.Kind.BOLT to longArrayOf(-2346628432577116740L, 9108804524815000409L, -3085865864327319457L),
        Shape.Kind.DROP to longArrayOf(4936155314996197850L, 4799369850438500741L, 6311152057860361175L),
    )

    private val hashedFrames = listOf(
        listOf(100f, 200f, 300f, 500f, 0f),
        listOf(333.3f, 40.5f, 910f, 160.25f, 30f),
        listOf(120f, 80f, 520f, 380f, -35f),
    )

    @Test
    fun polylinesAreBitIdenticalToTheOnesRecordedBeforeTheContourFormulaMovedToDouble() {
        for (kind in Shape.Kind.entries) {
            for ((index, frame) in hashedFrames.withIndex()) {
                val points = shape(kind, frame[0], frame[1], frame[2], frame[3], frame[4]).listPolylines().flatten()

                val hash = points.fold(17L) { acc, point -> (acc * 1_000_003L + point.x.toRawBits()) * 1_000_003L + point.y.toRawBits() }

                assertEquals(polylineHashes.getValue(kind)[index], hash, "${kind.key}, рамка $index")
            }
        }
    }

    // --- касание ----------------------------------------------------------------------------------

    private fun path(vararg xy: Pair<Float, Float>) = xy.map { StrokePoint(it.first, it.second, 0.5f) }

    // Прямоугольник с левым краем x = 128: рамка по контуру 128…272 × 260…440 (по сетке 100 × 100 это была рамка 100…300 × 200…500).
    private val frame = shape(Shape.Kind.RECTANGLE, 128f, 260f, 272f, 440f, width = 6f)

    private val ribbon = 3f + 8f * unit

    @Test
    fun touchNearTheOutlineSelectsTheShape() {
        assertEquals(0, findShapeAt(listOf(frame), 128f, 350f))
        assertEquals(0, findShapeAt(listOf(frame), 128f - 5f, 350f))
        assertEquals(0, findShapeAt(listOf(frame), 128f + 5f, 350f))
    }

    @Test
    fun ribbonEndsAtHalfWidthPlusEightDesignUnits() {
        assertEquals(0, findShapeAt(listOf(frame), 128f - (ribbon - 0.05f), 350f))
        assertNull(findShapeAt(listOf(frame), 128f - (ribbon + 0.05f), 350f))
    }

    @Test
    fun centerOfAnEmptyShapeSelectsNothing() {
        assertNull(findShapeAt(listOf(frame), 200f, 350f))
        assertNull(findShapeAt(emptyList(), 200f, 350f))
    }

    @Test
    fun ofTwoTouchedShapesTheLastOneWins() {
        val inner = shape(Shape.Kind.RECTANGLE, 128f, 260f, 272f, 440f, width = 6f)
        val outer = shape(Shape.Kind.RECTANGLE, 138f, 270f, 282f, 450f, width = 6f)

        // x = 129 в одном пикселе от левого края внутренней (128) и в девяти от внешней (138): лента задевает обе,
        // выигрывает последняя в списке, а не ближайшая.
        assertEquals(1, findShapeAt(listOf(inner, outer), 129f, 350f))
        assertEquals(1, findShapeAt(listOf(outer, inner), 129f, 350f))
        assertEquals(0, findShapeAt(listOf(inner, outer), 128f - 10f, 350f))
    }

    @Test
    fun rotatedShapeIsTouchedAtTheRotatedOutline() {
        val turned = frame.copy(angle = 90f)

        // Поворот на 90° по часовой вокруг центра (200, 350): левый край стал верхним (y = 278), верхний — правым (x = 290).
        assertNull(findShapeAt(listOf(turned), 128f, 350f))
        assertEquals(0, findShapeAt(listOf(turned), 200f + 90f, 350f))
    }

    @Test
    fun shapeIsTouchedByPathSegmentsNotOnlyPoints() {
        // Штрих через контур с редкими точками: отрезок пересекает левый край, точки далеко от него.
        assertTrue(isShapeTouched(frame, path(100f to 350f, 160f to 350f), 1f))
        assertFalse(isShapeTouched(frame, path(100f to 350f, 110f to 350f), 1f))
    }

    @Test
    fun dotInsideAnEmptyShapeDoesNotTouchIt() {
        assertFalse(isShapeTouched(frame, path(200f to 350f), 5f))
        assertTrue(isShapeTouched(frame, path(128f to 350f), 1f))
        assertFalse(isShapeTouched(frame, emptyList(), 1000f))
    }

    @Test
    fun touchRadiusAddsToTheOutline() {
        assertFalse(isShapeTouched(frame, path(140f to 350f), 5f))
        assertTrue(isShapeTouched(frame, path(140f to 350f), 13f))
    }
}
