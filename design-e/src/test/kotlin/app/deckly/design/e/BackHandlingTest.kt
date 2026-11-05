package app.deckly.design.e

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.DeckBody
import app.deckly.ui.contract.DeckFooter
import app.deckly.ui.contract.DeckHeader
import app.deckly.ui.contract.DeckScreenState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Системная «Назад» и меню: открытое меню ловит её само, потому что открыто ли оно, знает только дизайн. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackHandlingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<DeckAction>()

    @Test
    fun backClosesOpenCardMenuWithoutActionsAndStaysInApp() {
        showDeckScreen()
        composeRule.onNode(hasContentDescription("Действия с карточкой") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Удалить карточку").assertExists()

        pressBack()

        composeRule.onNodeWithText("Удалить карточку").assertDoesNotExist()
        assertTrue("меню закрывается без действий", actions.isEmpty())
        assertFalse("приложение не уходит", composeRule.activity.isFinishing)
    }

    @Test
    fun backWithoutMenuIsLeftToTheSystem() {
        showDeckScreen()

        pressBack()

        // Экран колоды сам «Назад» не ловит: её обрабатывает приложение, а без него это системное поведение.
        assertTrue(composeRule.activity.isFinishing)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun backAfterMenuIsClosedIsLeftToTheSystem() {
        showDeckScreen()
        composeRule.onNode(hasContentDescription("Действия с карточкой") and hasClickAction()).performClick()
        pressBack()
        assertFalse(composeRule.activity.isFinishing)

        pressBack()

        assertTrue("обработчик меню снят вместе с меню", composeRule.activity.isFinishing)
    }

    private fun pressBack() {
        // Обработчик меню регистрируется после кадра, в котором меню появилось: «Назад» идёт, когда экран устоялся.
        composeRule.waitForIdle()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun showDeckScreen() {
        val state = DeckScreenState(
            header = DeckHeader("Немецкий A2", null, null, canCreateCard = false, cardMenu = listOf(CardMenuItem.DELETE)),
            tabs = emptyList(),
            body = DeckBody.Card(CardSideShown.FRONT, isContentDamaged = false),
            footer = DeckFooter.None,
            dialog = null,
        )
        composeRule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(Color.White).testTag("frame")) {
                DeckScreen(state, { actions += it }) { modifier -> Box(modifier) }
            }
        }
    }
}
