package app.deckly.design.e

import app.deckly.ui.contract.Thickness

/** Тексты редактора из `design/build.py`: функция `editor`, окна `dlg-discard`, `dlg-delete-edit`, `dlg-editor-back`. Интерфейс английский. */
internal object EditorTexts {
    const val TITLE_NEW = "New card"
    const val TITLE_EDIT = "Edit card"
    const val CANCEL = "Cancel"
    const val CANCEL_DESCRIPTION = "Cancel card creation"
    const val DELETE = "Delete"
    const val DELETE_DESCRIPTION = "Delete card"
    const val SAVE = "Save"
    const val SIDES = "Card side"
    const val FRONT = "Question"
    const val BACK = "Answer"
    const val CANVAS = "Canvas. Draw with the pen only"
    const val TOOLS = "Drawing tools"
    const val THICKNESS = "Pen width"
    const val THIN = "Thin"
    const val MEDIUM = "Medium"
    const val THICK = "Thick"
    const val LINE = "line"
    const val UNDO_STROKE = "Undo stroke"

    // --- окна ---
    const val KEEP_EDITING = "Keep editing"
    const val DISCARD_TITLE = "Discard this draft?"
    const val DISCARD_TEXT = "Your drawings on both sides won't be saved."
    const val DISCARD = "Discard draft"
    const val DELETE_CARD_TITLE = "Delete card?"
    const val DELETE_CARD_TEXT = "Both sides will be deleted. You can't undo this."
    const val SAVE_EXIT_TITLE = "Save and exit?"
    const val SAVE_EXIT = "Save and exit"

    /** «Thin line», «Medium line», «Thick line»: `aria-label="{name} line"`. */
    fun describeThickness(thickness: Thickness): String {
        val name = when (thickness) {
            Thickness.THIN -> THIN
            Thickness.MEDIUM -> MEDIUM
            Thickness.THICK -> THICK
        }
        return "$name $LINE"
    }
}

/** Тексты списка колод из `design/build.py`: `deck_header`, `deck_row`, `deck_actions`, `decks-empty`. */
internal object DeckListTexts {
    const val TITLE = "Decks"
    const val BACK_TO_CARDS = "Back to cards"
    const val NEW_DECK = "New deck"
    const val NO_DECKS = "No decks"
    const val LEARNED = "learned"
    const val SEPARATOR = "·"

    /** Номер строки с нулём впереди: `{number:02}`. */
    fun formatDeckNumber(number: Int): String = number.toString().padStart(2, '0')

    /** «245 cards ·»: подпись перед значком ▶ и эпохой. */
    fun formatCaptionBeforeEpoch(cardCount: Int): String = "${formatCardCount(cardCount)} $SEPARATOR"

    /** «18 cards · learned». */
    fun formatLearnedCaption(cardCount: Int): String = "${formatCardCount(cardCount)} $SEPARATOR $LEARNED"
}

/** Тексты формы новой колоды из `design/build.py` (`deck-new`). */
internal object DeckNameTexts {
    const val TITLE = "New deck"
    const val CANCEL = "Cancel"
    const val CREATE = "Create"
    const val FIELD_LABEL = "Deck name"
    const val PLACEHOLDER = "English A2"
}

/** Тексты экранов запуска из `design/build.py`: цикл `startup-*` (окна доступа, повреждённой и пропавшей базы). */
internal object StartupTexts {
    const val APP_NAME = "Deckly"
    const val ACCESS_TITLE = "We need file access"
    const val ACCESS_TEXT = "Your decks are in the Deckly folder. Allow file access to open them."
    const val OPEN_SETTINGS = "Open settings"
    const val DAMAGED_TITLE = "Database damaged"
    const val DAMAGED_TEXT = "Couldn't open Deckly/deckly.db. We haven't deleted anything."
    const val MISSING_TITLE = "Database not found"
    const val MISSING_TEXT = "We can't find deckly.db in the Deckly folder. Your drawings are still there."
    const val CLOSE = "Close"
}
