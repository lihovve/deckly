package app.deckly.editor

import app.deckly.deck.toShown
import app.deckly.domain.editor.EditorSession
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.ink.DrawingTool
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.EditorTool
import app.deckly.ui.contract.Thickness

/** Состояние редактора для дизайна. Отмена доступна, пока есть что отменять. */
fun buildEditorScreenState(
    session: EditorSession,
    tool: EditorTool,
    thickness: Thickness,
    dialog: EditorDialog?,
): EditorScreenState = EditorScreenState(
    isNewCard = session.draft.cardId == null,
    side = session.draft.visibleSide.toShown(),
    tool = tool,
    thickness = thickness,
    canUndo = session.history.isNotEmpty(),
    dialog = dialog,
)

/** Штрихи видимой стороны: их показывает лист. */
fun listVisibleStrokes(session: EditorSession): List<Stroke> =
    if (session.draft.visibleSide == Side.FRONT) session.draft.front else session.draft.back

/** Толщина пера в пикселях листа (значения, проверенные владельцем 4 ноября). В единицы карточки её переводит `ink`. */
fun Thickness.toPenWidthPx(): Float = when (this) {
    Thickness.THIN -> 3f
    Thickness.MEDIUM -> 6f
    Thickness.THICK -> 10f
}

fun EditorTool.toDrawingTool(): DrawingTool = when (this) {
    EditorTool.PEN -> DrawingTool.PEN
    EditorTool.ERASER -> DrawingTool.ERASER
}

fun CardSideShown.toSide(): Side = when (this) {
    CardSideShown.FRONT -> Side.FRONT
    CardSideShown.BACK -> Side.BACK
}
