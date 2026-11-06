package app.deckly.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Лист в Compose на запасном холсте: на JVM нет Onyx SDK, поэтому `DrawingSheet` выбирает `Canvas`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawingSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var side by mutableStateOf(CardSide())
    private var penWidthPx by mutableStateOf(6f)
    private var isPenEnabled by mutableStateOf(true)
    private var onStroke by mutableStateOf<(Stroke) -> Unit>({})
    private var onErase by mutableStateOf<(List<StrokePoint>, Float) -> Unit>({ _, _ -> })
    private var onShapeTap by mutableStateOf<(Int) -> Unit>({})

    @Test
    fun withoutOnyxSdkSheetIsCanvasView() {
        showSheet()

        assertNotNull(findSheetView())
    }

    @Test
    fun strokesFromComposeAreDrawn() {
        showSheet()

        side = CardSide(listOf(Stroke(12f, listOf(StrokePoint(500f, 500f, 1f)))))
        composeRule.waitForIdle()

        // Лист 500 px: масштаб 0,5, точка в (500, 500) единиц карточки стоит в (250, 250) px.
        val bitmap = drawSheet()
        assertTrue(bitmap.isInk(250, 250))
        assertFalse(bitmap.isInk(20, 20))
    }

    @Test
    fun strokeOfStylusReachesCallbackInCardUnits() {
        val reported = mutableListOf<Stroke>()
        onStroke = { reported += it }
        showSheet()

        performStylusGesture(listOf(Triple(50f, 100f, 0.5f), Triple(250f, 100f, 1f)))

        val stroke = reported.single()
        assertEquals(6f / 0.5f, stroke.width, 1e-3f)
        assertEquals(100f, stroke.points[0].x, 1e-2f)
        assertEquals(200f, stroke.points[0].y, 1e-2f)
        assertEquals(500f, stroke.points[1].x, 1e-2f)
    }

    @Test
    fun replacedCallbacksAreUsed() {
        val first = mutableListOf<Stroke>()
        val second = mutableListOf<Stroke>()
        onStroke = { first += it }
        showSheet()

        onStroke = { second += it }
        composeRule.waitForIdle()
        performStylusGesture(listOf(Triple(50f, 100f, 0.5f), Triple(250f, 100f, 1f)))

        assertTrue(first.isEmpty())
        assertEquals(1, second.size)
    }

    @Test
    fun penWidthFromComposeSetsStrokeWidth() {
        val reported = mutableListOf<Stroke>()
        onStroke = { reported += it }
        showSheet()

        penWidthPx = 10f
        composeRule.waitForIdle()
        performStylusGesture(listOf(Triple(50f, 100f, 0.5f), Triple(250f, 100f, 1f)))

        assertEquals(10f / 0.5f, reported.single().width, 1e-3f)
    }

    @Test
    fun disabledPenFromComposeIgnoresStylus() {
        val reported = mutableListOf<Stroke>()
        onStroke = { reported += it }
        showSheet()

        isPenEnabled = false
        composeRule.waitForIdle()
        performStylusGesture(listOf(Triple(50f, 100f, 0.5f), Triple(250f, 100f, 1f)))

        assertTrue(reported.isEmpty())
    }

    @Test
    fun shapeTapOnTheSheetReachesTheHostWithItsIndex() {
        val taps = mutableListOf<Int>()
        val reported = mutableListOf<Stroke>()
        onShapeTap = { taps += it }
        onStroke = { reported += it }
        showSheet()

        side = CardSide(shapes = listOf(Shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, 0f, 6f)))
        composeRule.waitForIdle()
        // Лист 500 px, масштаб 0,5: левый край контура x = 128 единиц лежит в 64 px, середина по высоте — 175 px.
        performStylusGesture(listOf(Triple(66f, 175f, 0.5f)))

        assertEquals(listOf(0), taps)
        assertTrue(reported.isEmpty())
        assertTrue(drawSheet().isInk(64, 175))
    }

    @Test
    fun shapesFromComposeAreDrawn() {
        showSheet()

        side = CardSide(shapes = listOf(Shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, 0f, 6f)))
        composeRule.waitForIdle()

        val bitmap = drawSheet()
        assertTrue(bitmap.isInk(64, 175))
        assertFalse(bitmap.isInk(100, 175))
    }

    private fun showSheet() {
        composeRule.setContent {
            DrawingSheet(
                side = side,
                penWidthPx = penWidthPx,
                isPenEnabled = isPenEnabled,
                uiRevision = 0,
                onStroke = onStroke,
                onErase = onErase,
                onShapeTap = onShapeTap,
                modifier = Modifier.size(SHEET_DP.dp),
            )
        }
        composeRule.waitForIdle()
    }

    private fun findSheetView(): CanvasDrawingView? = findView(composeRule.activity.window.decorView)

    private fun findView(root: View): CanvasDrawingView? {
        if (root is CanvasDrawingView) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findView(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun drawSheet(): Bitmap {
        val view = checkNotNull(findSheetView())
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun performStylusGesture(samples: List<Triple<Float, Float, Float>>) {
        val view = checkNotNull(findSheetView())
        val downTime = SystemClock.uptimeMillis()
        samples.forEachIndexed { index, (x, y, pressure) ->
            val action = if (index == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE
            view.dispatchTouchEvent(obtain(downTime, action, x, y, pressure))
        }
        val (x, y, pressure) = samples.last()
        view.dispatchTouchEvent(obtain(downTime, MotionEvent.ACTION_UP, x, y, pressure))
    }

    private fun obtain(downTime: Long, action: Int, x: Float, y: Float, pressure: Float): MotionEvent {
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

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = Color.red(getPixel(x, y)) < 128

    private companion object {
        const val SHEET_DP = 500
    }
}
