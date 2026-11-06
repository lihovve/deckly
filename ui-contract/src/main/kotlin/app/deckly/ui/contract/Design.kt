package app.deckly.ui.contract

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Дизайн приложения: по одной функции на экран. Получает состояние и обработчик действий, тексты и
 * разметку выбирает сам. Холсты приходят слотами [sheet]: дизайн решает, где и какого размера лист,
 * а `app` наполняет слот реализацией из `ink`. Значки видов фигур в палитре и на панели тоже приходят слотом
 * [EditorScreen]: вид (`ShapeKind`), толщина контура в единицах макета и модификатор с размером.
 */
interface Design {
    @Composable
    fun LoadingScreen()

    @Composable
    fun StartupScreen(state: StartupState, onAction: (StartupAction) -> Unit)

    @Composable
    fun DeckScreen(state: DeckScreenState, onAction: (DeckAction) -> Unit, sheet: @Composable (Modifier) -> Unit)

    @Composable
    fun EditorScreen(
        state: EditorScreenState,
        onAction: (EditorAction) -> Unit,
        sheet: @Composable (Modifier) -> Unit,
        shapeGlyph: @Composable (ShapeKind, Int, Modifier) -> Unit,
    )

    @Composable
    fun DeckListScreen(state: DeckListState, onAction: (DeckListAction) -> Unit)

    @Composable
    fun DeckNameScreen(state: DeckNameState, onAction: (DeckNameAction) -> Unit)
}
