package app.deckly.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.editor.CanvasState
import app.deckly.ui.contract.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Настоящие листы в слотах дизайна: описание ставит дизайн через модификатор, лист его не затирает. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkSheetSlotsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun deckSheetKeepsDescriptionOfDesign() {
        composeRule.setContent {
            InkSheetSlots.DeckSheet(CardSide(), Modifier.size(200.dp).semantics { contentDescription = "Question" })
        }

        val descriptions = composeRule.onNodeWithContentDescription("Question").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Question"), descriptions)
    }

    @Test
    fun deckSheetAddsNoDescriptionOfItsOwn() {
        composeRule.setContent { InkSheetSlots.DeckSheet(CardSide(), Modifier.size(200.dp).testTag("sheet")) }

        val config = composeRule.onNodeWithTag("sheet").fetchSemanticsNode().config
        assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun editorSheetKeepsDescriptionOfDesign() {
        val canvas = CanvasState(CardSide(), penWidthPx = 10f, isPenEnabled = true, uiRevision = 0)
        composeRule.setContent {
            InkSheetSlots.EditorSheet(
                canvas = canvas,
                onStroke = {},
                onErase = { _, _ -> },
                onShapeTap = {},
                modifier = Modifier.size(200.dp).semantics { contentDescription = "Canvas. Draw with the pen only" },
            )
        }

        composeRule.onNodeWithContentDescription("Canvas. Draw with the pen only").assertExists()
    }

    @Test
    fun editorSheetDrawsWithPenWidthOfCanvas() {
        // Толщина пера из состояния холста доходит до листа: штрих приходит в единицах карточки (лист 500 px, масштаб 0,5).
        val reported = mutableListOf<Stroke>()
        val canvas = CanvasState(CardSide(), penWidthPx = 25f, isPenEnabled = true, uiRevision = 0)
        composeRule.setContent {
            InkSheetSlots.EditorSheet(
                canvas = canvas,
                onStroke = { reported += it },
                onErase = { _, _ -> },
                onShapeTap = {},
                modifier = Modifier.size(500.dp),
            )
        }
        composeRule.waitForIdle()

        performStylusGesture(listOf(Triple(50f, 100f, 0.5f), Triple(250f, 100f, 1f)))

        assertEquals(25f / 0.5f, reported.single().width, 1e-3f)
    }

    @Test
    fun editorSheetReportsTheShapeThatAShortTapAtItsOutlineHit() {
        // Прямоугольник 100…400 × 100…300 единиц карточки на листе 500 dp (масштаб 0,5): левая кромка на 14 % ширины, то есть
        // x = 142 единицы, 71 px; середина высоты y = 200 единиц, 100 px.
        val shape = Shape(Shape.Kind.RECTANGLE, 100f, 100f, 400f, 300f, 0f, 3f)
        val tapped = mutableListOf<Int>()
        val strokes = mutableListOf<Stroke>()
        composeRule.setContent {
            InkSheetSlots.EditorSheet(
                canvas = CanvasState(CardSide(shapes = listOf(shape)), penWidthPx = 10f, isPenEnabled = true, uiRevision = 0),
                onStroke = { strokes += it },
                onErase = { _, _ -> },
                onShapeTap = { tapped += it },
                modifier = Modifier.size(500.dp),
            )
        }
        composeRule.waitForIdle()

        performStylusGesture(listOf(Triple(71f, 100f, 0.5f)))

        assertEquals(listOf(0), tapped)
        assertEquals("касание у контура штрихом не стало", emptyList<Stroke>(), strokes)
    }

    @Test
    fun editorSheetKeepsTapInsideHollowShapeAsStroke() {
        val shape = Shape(Shape.Kind.RECTANGLE, 100f, 100f, 400f, 300f, 0f, 3f)
        val tapped = mutableListOf<Int>()
        val strokes = mutableListOf<Stroke>()
        composeRule.setContent {
            InkSheetSlots.EditorSheet(
                canvas = CanvasState(CardSide(shapes = listOf(shape)), penWidthPx = 10f, isPenEnabled = true, uiRevision = 0),
                onStroke = { strokes += it },
                onErase = { _, _ -> },
                onShapeTap = { tapped += it },
                modifier = Modifier.size(500.dp),
            )
        }
        composeRule.waitForIdle()

        // Центр фигуры (125, 100 px) далеко от контура: точка внутри пустой фигуры остаётся точкой.
        performStylusGesture(listOf(Triple(125f, 100f, 0.5f)))

        assertEquals(emptyList<Int>(), tapped)
        assertEquals(1, strokes.size)
    }

    @Test
    fun deckSheetDrawsTheShapesOfTheSide() {
        val shape = Shape(Shape.Kind.RECTANGLE, 100f, 100f, 400f, 300f, 0f, 6f)
        composeRule.setContent {
            Box(Modifier.size(500.dp).background(Color.White).testTag("sheet")) {
                InkSheetSlots.DeckSheet(CardSide(shapes = listOf(shape)), Modifier.size(500.dp))
            }
        }
        composeRule.waitForIdle()

        val image = composeRule.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()

        // Левая кромка прямоугольника на (71, 100) px чёрная, середина фигуры (125, 100) пустая.
        assertTrue("контур нарисован", image.getPixel(71, 100) and 0xFF < 128)
        assertTrue("внутри пусто", image.getPixel(125, 100) and 0xFF > 200)
    }

    @Test
    fun shapeGlyphDrawsTheOutlineOfItsKindWithTheGivenWidth() {
        composeRule.setContent {
            Box(Modifier.size(56.dp).background(Color.White).testTag("glyph")) {
                InkSheetSlots.ShapeGlyph(ShapeKind.RECTANGLE, 3, Modifier.size(56.dp))
            }
        }
        composeRule.waitForIdle()

        val image = composeRule.onNodeWithTag("glyph").captureToImage().asAndroidBitmap()

        // Прямоугольник сетки 100: левая кромка на 14 % ширины, то есть на 8 px, середина высоты на 28 px; внутри пусто.
        assertTrue("контур нарисован", image.getPixel(8, 28) and 0xFF < 128)
        assertTrue("внутри пусто", image.getPixel(28, 28) and 0xFF > 200)
    }

    // Холст листа внутренний для `ink`, поэтому его ищут по имени класса.
    private fun findSheetView(root: View): View? {
        if (root.javaClass.simpleName == "CanvasDrawingView") return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findSheetView(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun performStylusGesture(samples: List<Triple<Float, Float, Float>>) {
        val view = checkNotNull(findSheetView(composeRule.activity.window.decorView))
        val downTime = SystemClock.uptimeMillis()
        samples.forEachIndexed { index, (x, y, pressure) ->
            val action = if (index == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE
            view.dispatchTouchEvent(obtainStylusEvent(downTime, action, x, y, pressure))
        }
        val (x, y, pressure) = samples.last()
        view.dispatchTouchEvent(obtainStylusEvent(downTime, MotionEvent.ACTION_UP, x, y, pressure))
    }

    private fun obtainStylusEvent(downTime: Long, action: Int, x: Float, y: Float, pressure: Float): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            this.pressure = pressure
            size = 1f
        }
        return MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, 1,
            arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
    }
}
