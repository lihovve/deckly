package app.deckly.editor

import app.deckly.domain.editor.Draft
import app.deckly.domain.editor.EditorSession
import app.deckly.domain.editor.EditorStep
import app.deckly.domain.editor.addStroke
import app.deckly.domain.editor.resumeDraft
import app.deckly.domain.editor.startNewCard
import app.deckly.domain.editor.switchSide
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.fixtures.buildStroke
import app.deckly.ink.DrawingTool
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.EditorTool
import app.deckly.ui.contract.Thickness
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorScreenMapperTest {
    private val front = buildStroke(1f to 1f, 5f to 5f)
    private val back = buildStroke(2f to 2f, 6f to 6f)

    @Test
    fun newCardHasNoCardId() {
        val session = startNewCard(Section.NEW)

        assertEquals(
            EditorScreenState(true, CardSideShown.FRONT, EditorTool.PEN, Thickness.MEDIUM, canUndo = false, dialog = null),
            buildEditorScreenState(session, EditorTool.PEN, Thickness.MEDIUM, null),
        )
    }

    @Test
    fun editedCardIsNotNew() {
        val session = resumeDraft(Draft(7, emptyList(), emptyList(), Side.FRONT, Section.NEW))

        assertEquals(false, buildEditorScreenState(session, EditorTool.PEN, Thickness.MEDIUM, null).isNewCard)
    }

    @Test
    fun undoIsAvailableOnlyWithHistory() {
        val empty = startNewCard(Section.NEW)
        val drawn = empty.addStroke(front)

        assertEquals(false, buildEditorScreenState(empty, EditorTool.PEN, Thickness.MEDIUM, null).canUndo)
        assertEquals(true, buildEditorScreenState(drawn, EditorTool.PEN, Thickness.MEDIUM, null).canUndo)
        assertEquals(listOf<EditorStep>(EditorStep.StrokeAdded(Side.FRONT)), drawn.history)
    }

    @Test
    fun sideToolThicknessAndDialogArePassedThrough() {
        val session = startNewCard(Section.NEW).switchSide(Side.BACK)

        val state = buildEditorScreenState(session, EditorTool.ERASER, Thickness.THICK, EditorDialog.SAVE_ON_BACK)

        assertEquals(CardSideShown.BACK, state.side)
        assertEquals(EditorTool.ERASER, state.tool)
        assertEquals(Thickness.THICK, state.thickness)
        assertEquals(EditorDialog.SAVE_ON_BACK, state.dialog)
    }

    @Test
    fun visibleStrokesAreOfVisibleSide() {
        val session = EditorSession(Draft(null, listOf(front), listOf(back), Side.FRONT, Section.NEW), emptyList())

        assertEquals(listOf(front), listVisibleStrokes(session))
        assertEquals(listOf(back), listVisibleStrokes(session.switchSide(Side.BACK)))
    }

    @Test
    fun thicknessesAreThreeSixAndTenPixels() {
        assertEquals(3f, Thickness.THIN.toPenWidthPx(), 0f)
        assertEquals(6f, Thickness.MEDIUM.toPenWidthPx(), 0f)
        assertEquals(10f, Thickness.THICK.toPenWidthPx(), 0f)
    }

    @Test
    fun toolsMapToDrawingTools() {
        assertEquals(DrawingTool.PEN, EditorTool.PEN.toDrawingTool())
        assertEquals(DrawingTool.ERASER, EditorTool.ERASER.toDrawingTool())
    }
}
