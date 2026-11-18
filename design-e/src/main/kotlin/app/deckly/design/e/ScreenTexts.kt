package app.deckly.design.e

import app.deckly.ui.contract.ShapeHandleKind
import app.deckly.ui.contract.ShapeKind
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

    // --- фигуры ---
    const val SHAPES = "Shapes"
    const val CLOSE_SHAPES = "Close shape menu"
    const val OUTLINE_WIDTH = "Outline width"
    const val SHAPE_SIZE = "Default shape size"
    const val CANCEL_SHAPE = "Cancel shape insertion or changes"
    const val SHAPE_AND_OUTLINE = "Shape and outline width"
    const val APPLY_SHAPE = "Apply shape"
    const val ROTATE_SHAPE = "Rotate shape"
    const val RESIZE_TOP_LEFT = "Resize: top left corner"
    const val RESIZE_TOP_RIGHT = "Resize: top right corner"
    const val RESIZE_BOTTOM_LEFT = "Resize: bottom left corner"
    const val RESIZE_BOTTOM_RIGHT = "Resize: bottom right corner"

    // Части «Move start point» и «Move end point»: в макете это `Move {endpoint} point`.
    const val MOVE = "Move"
    const val POINT = "point"
    const val START = "start"
    const val END = "end"

    /** «Resize: top left corner» и остальные углы: `aria-label` ручки угла. */
    fun describeCorner(handle: ShapeHandleKind): String = when (handle) {
        ShapeHandleKind.TOP_LEFT -> RESIZE_TOP_LEFT
        ShapeHandleKind.TOP_RIGHT -> RESIZE_TOP_RIGHT
        ShapeHandleKind.BOTTOM_LEFT -> RESIZE_BOTTOM_LEFT
        ShapeHandleKind.BOTTOM_RIGHT -> RESIZE_BOTTOM_RIGHT
        else -> error("Не угол: $handle")
    }

    /** «Move start point» или «Move end point»: `aria-label` ручки конца линии. */
    fun describeEndpoint(handle: ShapeHandleKind): String =
        "$MOVE ${if (handle == ShapeHandleKind.START) START else END} $POINT"

    /** Имя вида: подпись значка в палитре и название фигуры на листе (`SHAPES` в макете). */
    fun describeShape(kind: ShapeKind): String = when (kind) {
        ShapeKind.LINE -> "Line"
        ShapeKind.ARROW -> "Arrow"
        ShapeKind.DOUBLE_ARROW -> "Double arrow"
        ShapeKind.RECTANGLE -> "Rectangle"
        ShapeKind.OVAL -> "Oval"
        ShapeKind.TRIANGLE -> "Triangle"
        ShapeKind.DIAMOND -> "Diamond"
        ShapeKind.HEXAGON -> "Hexagon"
        ShapeKind.STAR -> "Star"
        ShapeKind.MOON -> "Moon"
        ShapeKind.SUN -> "Sun"
        ShapeKind.HEART -> "Heart"
        ShapeKind.CLOUD -> "Cloud"
        ShapeKind.BOLT -> "Lightning"
        ShapeKind.DROP -> "Drop"
    }

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
    const val RENAME = "Rename"
    const val EXPORT_FILE = "Export to file"
    const val DELETE_DECK = "Delete deck"
    const val IMPORT_FILE = "Import from file"
    const val IMPORT_ERROR_TITLE = "Couldn't open file"
    const val IMPORT_ERROR_TEXT = "This file is damaged or uses a format we can't open."
    const val CLOSE = "Close"

    /** «Deck menu: English A2»: подпись кнопки «⋯» у строки для диктора. */
    fun formatMenuLabel(name: String): String = "Deck menu: $name"

    /** «Delete “English A2”?»: заголовок окна удаления. */
    fun formatDeleteTitle(name: String): String = "Delete “$name”?"

    /** «All 245 cards and their progress will be deleted. You can't undo this.» */
    fun formatDeleteText(cardCount: Int): String = "All ${formatCardCount(cardCount)} and their progress will be deleted. You can't undo this."

    /** «245 cards ·»: подпись перед значком ▶ и эпохой. */
    fun formatCaptionBeforeEpoch(cardCount: Int): String = "${formatCardCount(cardCount)} $SEPARATOR"

    /** «18 cards · learned». */
    fun formatLearnedCaption(cardCount: Int): String = "${formatCardCount(cardCount)} $SEPARATOR $LEARNED"
}

/** Тексты формы названия колоды из `design/build.py`: `deck-new` и `deck-rename`. */
internal object DeckNameTexts {
    const val TITLE = "New deck"
    const val RENAME_TITLE = "Rename"
    const val CANCEL = "Cancel"
    const val CREATE = "Create"
    const val SAVE = "Save"
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
