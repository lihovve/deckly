package app.deckly.ui

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
import app.deckly.editor.CanvasState
import app.deckly.ink.DrawingTool
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
        val canvas = CanvasState(emptyList(), DrawingTool.PEN, penWidthPx = 6f, isPenEnabled = true, uiRevision = 0)
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
}
