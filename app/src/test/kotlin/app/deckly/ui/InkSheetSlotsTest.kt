package app.deckly.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.size
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
import app.deckly.domain.model.Stroke
import app.deckly.editor.CanvasState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Настоящие листы в слотах дизайна: описание ставит дизайн через модификатор, лист его не затирает. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
class InkSheetSlotsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun deckSheetKeepsDescriptionOfDesign() {
        composeRule.setContent {
            InkSheetSlots.DeckSheet(emptyList(), Modifier.size(200.dp).semantics { contentDescription = "Вопрос" })
        }

        val descriptions = composeRule.onNodeWithContentDescription("Вопрос").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Вопрос"), descriptions)
    }

    @Test
    fun deckSheetAddsNoDescriptionOfItsOwn() {
        composeRule.setContent { InkSheetSlots.DeckSheet(emptyList(), Modifier.size(200.dp).testTag("sheet")) }

        val config = composeRule.onNodeWithTag("sheet").fetchSemanticsNode().config
        assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun editorSheetKeepsDescriptionOfDesign() {
        val canvas = CanvasState(emptyList(), penWidthPx = 10f, isPenEnabled = true, uiRevision = 0)
        composeRule.setContent {
            InkSheetSlots.EditorSheet(
                canvas = canvas,
                onStroke = {},
                onErase = { _, _ -> },
                modifier = Modifier.size(200.dp).semantics { contentDescription = "Холст. Рисование только пером" },
            )
        }

        composeRule.onNodeWithContentDescription("Холст. Рисование только пером").assertExists()
    }

    @Test
    fun editorSheetDrawsWithPenWidthOfCanvas() {
        // Толщина пера из состояния холста доходит до листа: штрих приходит в единицах карточки (лист 500 px, масштаб 0,5).
        val reported = mutableListOf<Stroke>()
        val canvas = CanvasState(emptyList(), penWidthPx = 25f, isPenEnabled = true, uiRevision = 0)
        composeRule.setContent {
            InkSheetSlots.EditorSheet(
                canvas = canvas,
                onStroke = { reported += it },
                onErase = { _, _ -> },
                modifier = Modifier.size(500.dp),
            )
        }
        composeRule.waitForIdle()

        performStylusGesture(listOf(Triple(50f, 100f, 0.5f), Triple(250f, 100f, 1f)))

        assertEquals(25f / 0.5f, reported.single().width, 1e-3f)
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
