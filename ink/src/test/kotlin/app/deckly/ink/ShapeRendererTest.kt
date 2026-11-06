package app.deckly.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Фигуры на настоящем [Bitmap]: пиксель считается чернилами, если он закрашен больше чем наполовину. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeRendererTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val unit = CardUnits.PER_DESIGN_UNIT

    // Рамка 200 × 300: левый край контура x = 128, верхний y = 260, правый 272, нижний 440.
    private val rectangle = Shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, 0f, 6f)

    private fun drawSide(scale: Float, side: CardSide, size: Int = 1000): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawCardSide(canvas, side, scale)
        return bitmap
    }

    private fun drawShapes(scale: Float, vararg shapes: Shape): Bitmap = drawSide(scale, CardSide(shapes = shapes.toList()))

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = Color.red(getPixel(x, y)) < 128

    /** Сколько подряд идущих чернильных пикселей по горизонтали вокруг (x, y). */
    private fun Bitmap.measureRun(x: Int, y: Int, isVertical: Boolean): Int {
        var from = if (isVertical) y else x
        var to = from
        fun ink(v: Int) = if (isVertical) isInk(x, v) else isInk(v, y)
        while (ink(from - 1)) from--
        while (ink(to + 1)) to++
        return to - from + 1
    }

    // --- толщина контура ---------------------------------------------------------------------------

    @Test
    fun outlineHasTheWidthOfTheShapeAtScaleOne() {
        val bitmap = drawShapes(1f, rectangle)

        // Верхний край: толщина 6 px.
        assertEquals(6, bitmap.measureRun(200, 260, isVertical = true))
        assertEquals(6, bitmap.measureRun(128, 350, isVertical = false))
    }

    @Test
    fun outlineWidthIsMultipliedByTheScaleOfTheSheet() {
        val bitmap = drawShapes(2f, rectangle)

        assertEquals(12, bitmap.measureRun(400, 520, isVertical = true))
    }

    @Test
    fun stretchedFrameDoesNotThickenTheOutline() {
        // Одна фигура и та же рамка, растянутая по ширине в три раза: толщина контура прежняя, а не растянутая.
        val wide = rectangle.copy(left = 100f, right = 700f)
        val bitmap = drawShapes(1f, rectangle, wide.copy(top = 600f, bottom = 900f))

        assertEquals(6, bitmap.measureRun(200, 260, isVertical = true))
        assertEquals(6, bitmap.measureRun(400, 660, isVertical = true))
        // Левый край растянутой рамки: x = 100 + 14 % · 600 = 184.
        assertEquals(6, bitmap.measureRun(184, 750, isVertical = false))
    }

    @Test
    fun rotatedShapeKeepsTheOutlineWidth() {
        // Поворот на 90° вокруг центра (200, 350): левый край стал верхним (y = 278), верхний — правым (x = 290).
        val bitmap = drawShapes(1f, rectangle.copy(angle = 90f))

        assertEquals(6, bitmap.measureRun(200, 278, isVertical = true))
        assertEquals(6, bitmap.measureRun(290, 350, isVertical = false))
    }

    @Test
    fun outlineWidthDoesNotDependOnPressure() {
        val thin = drawShapes(1f, rectangle.copy(width = 2f))
        val thick = drawShapes(1f, rectangle.copy(width = 12f))

        assertEquals(2, thin.measureRun(200, 260, isVertical = true))
        assertEquals(12, thick.measureRun(200, 260, isVertical = true))
    }

    // --- вид ----------------------------------------------------------------------------------------

    @Test
    fun shapeHasNoFillAndAStrokeInsideIsVisible() {
        val inside = Stroke(6f, listOf(StrokePoint(200f, 350f, 1f), StrokePoint(240f, 350f, 1f)))
        val bitmap = drawSide(1f, CardSide(listOf(inside), listOf(rectangle)))

        assertTrue(bitmap.isInk(220, 350))
        assertFalse(drawShapes(1f, rectangle).isInk(200, 350))
        assertFalse(drawShapes(1f, rectangle).isInk(220, 400))
    }

    @Test
    fun outlineEndsAndCornersAreRound() {
        val line = Shape(Shape.Kind.LINE, 100f, 300f, 100f + 300f * unit, 300f + 80f * unit, 0f, 20f)
        val bitmap = drawShapes(1f, line)
        val end = 100f + 0.88f * 300f * unit
        val y = 300f + 40f * unit

        // Круглый конец выходит за конец оси на полтолщины, а в углу квадрата 10 × 10 у круга пикселя нет.
        assertTrue(bitmap.isInk((end + 8f).toInt(), y.toInt()))
        assertFalse(bitmap.isInk((end + 9f).toInt(), (y + 9f).toInt()))
    }

    @Test
    fun joinsAreRound() {
        // Остриё стрелки: угол между звеньями около 67°. Острый (miter) стык выступал бы за вершину в 1,8 раза дальше
        // полутолщины, круглый — ровно на неё.
        val arrow = Shape(Shape.Kind.ARROW, 20f, 400f, 20f + 320f * unit, 400f + 80f * unit, 0f, 20f)
        val bitmap = drawShapes(1f, arrow)
        val end = 20f + 0.88f * 320f * unit
        val axis = 400f + 40f * unit

        assertTrue(bitmap.isInk((end + 8f).toInt(), axis.toInt()))
        assertFalse(bitmap.isInk((end + 13f).toInt(), axis.toInt()))
    }

    @Test
    fun arrowHeadKeepsItsSizeWhateverTheLength() {
        // Остриё 24 вдоль оси и ±16 поперёк в единицах макета: середина верхней половины острия лежит в
        // (конец − 12, ось − 8) и у короткой, и у длинной стрелки.
        for (designWidth in listOf(320f, 1000f)) {
            val arrow = Shape(Shape.Kind.ARROW, 20f, 400f, 20f + designWidth * unit, 400f + 80f * unit, 0f, 6f)
            val bitmap = drawShapes(1f, arrow)
            val end = 20f + 0.88f * designWidth * unit
            val axis = 400f + 40f * unit

            assertTrue("середина острия при ширине $designWidth", bitmap.isInk((end - 12f * unit).toInt(), (axis - 8f * unit).toInt()))
            assertFalse("за остриём при ширине $designWidth", bitmap.isInk((end - 12f * unit).toInt(), (axis - 16f * unit).toInt()))
        }
    }

    @Test
    fun drawingShapesDoesNotChangeThem() {
        val shapes = listOf(rectangle, rectangle.copy(angle = 30f))
        val copy = shapes.toList()

        drawShapes(1f, *shapes.toTypedArray())

        assertEquals(copy, shapes)
    }

    @Test
    fun drawCardSideDrawsStrokesAndShapes() {
        val stroke = Stroke(6f, listOf(StrokePoint(600f, 700f, 1f)))
        val bitmap = drawSide(1f, CardSide(listOf(stroke), listOf(rectangle)))

        assertTrue(bitmap.isInk(600, 700))
        assertTrue(bitmap.isInk(200, 260))
    }

    @Test
    fun drawingTwiceGivesTheSameImage() {
        val first = drawShapes(1f, rectangle, rectangle.copy(kind = Shape.Kind.CLOUD, angle = 17f))
        val second = drawShapes(1f, rectangle, rectangle.copy(kind = Shape.Kind.CLOUD, angle = 17f))

        assertTrue(first.sameAs(second))
    }

    @Test
    fun shapeBoundsContainEveryDrawnPixel() {
        for (kind in Shape.Kind.entries) {
            val shape = Shape(kind, 200f, 300f, 600f, 560f, 33f, 14f)
            val bitmap = drawShapes(1f, shape)
            val bounds = computeShapeBounds(shape, 1f)

            val outside = (0 until bitmap.height).flatMap { y -> (0 until bitmap.width).map { x -> x to y } }
                .filter { (x, y) -> Color.red(bitmap.getPixel(x, y)) < 250 }
                .filter { (x, y) -> x < bounds.left || x >= bounds.right || y < bounds.top || y >= bounds.bottom }

            assertEquals("${kind.key}: чернила вне границ", emptyList<Pair<Int, Int>>(), outside)
        }
    }

    // --- значок палитры -----------------------------------------------------------------------------

    /** Несколько значков рядом в одной композиции: `setContent` вызывается один раз за тест. */
    private fun captureGlyphs(kind: Shape.Kind, outlines: List<Int>, sizeDp: Int): List<Bitmap> {
        composeRule.setContent {
            androidx.compose.foundation.layout.Row {
                outlines.forEachIndexed { index, outline ->
                    ShapeGlyph(kind, outline, Modifier.size(sizeDp.dp).testTag("glyph$index"))
                }
            }
        }
        return outlines.indices.map { composeRule.onNodeWithTag("glyph$it").captureToImage().asAndroidBitmap() }
    }

    private fun Bitmap.countInk(): Int = (0 until height).sumOf { y -> (0 until width).count { x -> isInk(x, y) } }

    @Test
    fun glyphWithOutlineTwelveIsThickerThanWithOutlineOne() {
        // Область 99 dp: верхний край прямоугольника на y = 19,8, линия не попадает на границу пикселей.
        val (thin, thick) = captureGlyphs(Shape.Kind.RECTANGLE, listOf(1, 12), 99)

        assertEquals(1, thin.measureRun(50, 19, isVertical = true))
        assertEquals(12, thick.measureRun(50, 19, isVertical = true))
        assertTrue(thick.countInk() > 8 * thin.countInk())
    }

    @Test
    fun glyphOutlineWidthIsInDesignUnitsWhateverTheGlyphSize() {
        val (small) = captureGlyphs(Shape.Kind.RECTANGLE, listOf(12), 56)

        // Контур значка не растягивается вместе с сеткой (`non-scaling-stroke` в макете): 12 dp при области 56 dp.
        assertEquals(12, small.measureRun(28, 11, isVertical = true))
    }

    @Test
    fun glyphStretchesTheGridOverTheWholeArea() {
        val (bitmap) = captureGlyphs(Shape.Kind.RECTANGLE, listOf(2), 100)

        // Сетка 100 × 100 на область 100 dp: верхний край прямоугольника на y = 20, левый на x = 14.
        assertTrue(bitmap.isInk(50, 20))
        assertTrue(bitmap.isInk(14, 50))
        assertFalse(bitmap.isInk(50, 50))
        assertFalse(bitmap.isInk(50, 5))
    }

    @Test
    fun glyphOfEveryKindDrawsSomething() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Row {
                for (kind in Shape.Kind.entries) ShapeGlyph(kind, 3, Modifier.size(60.dp).testTag("glyph-${kind.key}"))
            }
        }

        for (kind in Shape.Kind.entries) {
            val bitmap = composeRule.onNodeWithTag("glyph-${kind.key}").captureToImage().asAndroidBitmap()
            assertTrue(kind.key, bitmap.countInk() > 60)
        }
    }
}
