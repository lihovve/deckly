package app.deckly.ui.contract

/** Три толщины пера: тонкая, средняя, толстая. */
enum class Thickness { THIN, MEDIUM, THICK }

enum class EditorTool { PEN, ERASER }

/** Окна поверх редактора: отмена черновика, удаление карточки, выход из редактора. */
enum class EditorDialog { DISCARD_DRAFT, DELETE_CARD, SAVE_ON_BACK }

/**
 * Состояние редактора. [isNewCard] определяет левое действие шапки: «Отменить» у новой карточки,
 * «Удалить» у правки.
 */
data class EditorScreenState(
    val isNewCard: Boolean,
    val side: CardSideShown,
    val tool: EditorTool,
    val thickness: Thickness,
    val canUndo: Boolean,
    val dialog: EditorDialog?,
)

sealed interface EditorAction {
    data class SelectSide(val side: CardSideShown) : EditorAction

    data class SelectTool(val tool: EditorTool) : EditorAction

    data class SelectThickness(val thickness: Thickness) : EditorAction

    data object Undo : EditorAction

    data object Save : EditorAction

    /** Новая карточка: открывает [EditorDialog.DISCARD_DRAFT]. */
    data object Cancel : EditorAction

    /** Правка: открывает [EditorDialog.DELETE_CARD]. */
    data object Delete : EditorAction

    /** Системная «Назад»: открывает [EditorDialog.SAVE_ON_BACK]. */
    data object Back : EditorAction

    data object ConfirmDialog : EditorAction

    data object DismissDialog : EditorAction
}
