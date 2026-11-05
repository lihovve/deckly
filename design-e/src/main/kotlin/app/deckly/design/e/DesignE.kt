package app.deckly.design.e

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckNameAction
import app.deckly.ui.contract.DeckNameState
import app.deckly.ui.contract.DeckScreenState
import app.deckly.ui.contract.Design
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.StartupAction
import app.deckly.ui.contract.StartupState

/**
 * Вариант E «Ясный лист»: пять экранов контракта [Design]. Вызовы идут по полному имени: одноимённая функция пакета
 * внутри объекта затенена его методом, и без имени метод вызывал бы сам себя.
 */
object DesignE : Design {
    @Composable
    override fun StartupScreen(state: StartupState, onAction: (StartupAction) -> Unit) =
        app.deckly.design.e.StartupScreen(state, onAction)

    @Composable
    override fun DeckScreen(state: DeckScreenState, onAction: (DeckAction) -> Unit, sheet: @Composable (Modifier) -> Unit) =
        app.deckly.design.e.DeckScreen(state, onAction, sheet)

    @Composable
    override fun EditorScreen(state: EditorScreenState, onAction: (EditorAction) -> Unit, sheet: @Composable (Modifier) -> Unit) =
        app.deckly.design.e.EditorScreen(state, onAction, sheet)

    @Composable
    override fun DeckListScreen(state: DeckListState, onAction: (DeckListAction) -> Unit) =
        app.deckly.design.e.DeckListScreen(state, onAction)

    @Composable
    override fun DeckNameScreen(state: DeckNameState, onAction: (DeckNameAction) -> Unit) =
        app.deckly.design.e.DeckNameScreen(state, onAction)
}
