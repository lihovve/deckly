package app.deckly.editor

import app.deckly.deck.toShown
import app.deckly.domain.editor.EditorSession
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.Thickness

/** Состояние редактора для дизайна. Отмена доступна, пока есть что отменять. */
fun buildEditorScreenState(
    session: EditorSession,
    thickness: Thickness,
    dialog: EditorDialog?,
): EditorScreenState = EditorScreenState(
    isNewCard = session.draft.cardId == null,
    side = session.draft.visibleSide.toShown(),
    thickness = thickness,
    canUndo = session.history.isNotEmpty(),
    dialog = dialog,
)

/** Штрихи видимой стороны: их показывает лист. */
fun listVisibleStrokes(session: EditorSession): List<Stroke> =
    (if (session.draft.visibleSide == Side.FRONT) session.draft.front else session.draft.back).strokes

/** Толщина пера в пикселях листа (решение владельца 5 ноября: 3, 10 и 25). В единицы карточки её переводит `ink`. */
fun Thickness.toPenWidthPx(): Float = when (this) {
    Thickness.THIN -> 3f
    Thickness.MEDIUM -> 10f
    Thickness.THICK -> 25f
}

fun CardSideShown.toSide(): Side = when (this) {
    CardSideShown.FRONT -> Side.FRONT
    CardSideShown.BACK -> Side.BACK
}
