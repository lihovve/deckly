package app.deckly.design.e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.DeckBody
import app.deckly.ui.contract.DeckFooter
import app.deckly.ui.contract.DeckHeader
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckNameState
import app.deckly.ui.contract.DeckScreenState
import app.deckly.ui.contract.Design
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.StartupState
import app.deckly.ui.contract.Thickness
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** `DesignE` отдаёт приложению все пять экранов контракта; каждый вызов приходит на свой экран, а не сам в себя. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignETest {
    @get:Rule
    val composeRule = createComposeRule()

    private val design: Design = DesignE

    @Test
    fun designIsTheObjectOfVariantE() {
        assertSame(DesignE, design)
    }

    @Test
    fun loadingScreenShowsBrand() {
        showFrame { design.LoadingScreen() }

        composeRule.onNodeWithText("Deckly").assertExists()
    }

    @Test
    fun startupScreenIsStartupDialog() {
        showFrame { design.StartupScreen(StartupState.DATABASE_MISSING) {} }

        composeRule.onNodeWithText("Database not found").assertExists()
    }

    @Test
    fun deckScreenIsDeckScreen() {
        val state = DeckScreenState(
            header = DeckHeader("English A2", null, null, canCreateCard = false, cardMenu = emptyList()),
            tabs = emptyList(),
            body = DeckBody.Card(CardSideShown.FRONT, isContentDamaged = false),
            footer = DeckFooter.None,
            dialog = null,
        )
        showFrame { design.DeckScreen(state, {}) { Box(it.testTag("slot")) } }

        composeRule.onNodeWithText("English A2").assertExists()
        // Лист карточки приложения встал в слот.
        composeRule.onNodeWithTag("slot").assertExists()
    }

    @Test
    fun editorScreenIsEditor() {
        val state = EditorScreenState(true, CardSideShown.FRONT, Thickness.MEDIUM, canUndo = false, dialog = null)
        showFrame { design.EditorScreen(state, {}, { Box(it.testTag("slot")) }, { _, _, modifier -> Box(modifier) }) }

        composeRule.onNodeWithText("New card").assertExists()
        // Холст приложения встал в слот листа редактора.
        composeRule.onNodeWithTag("slot").assertExists()
    }

    @Test
    fun deckListScreenIsDeckList() {
        showFrame { design.DeckListScreen(DeckListState(emptyList(), canGoBack = false)) {} }

        composeRule.onNodeWithText("No decks").assertExists()
    }

    @Test
    fun deckNameScreenIsDeckNameForm() {
        showFrame { design.DeckNameScreen(DeckNameState("", canSave = false)) {} }

        composeRule.onNodeWithText("Deck name").assertExists()
    }

    private fun showFrame(screen: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(Color.White).testTag("frame")) { screen() }
        }
    }
}
