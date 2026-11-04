package app.deckly.domain.editor

import app.deckly.domain.buildCard
import app.deckly.domain.buildPath
import app.deckly.domain.buildStroke
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EditorSessionTest {

    private val firstStroke = buildStroke(0f to 0f, 10f to 0f)

    @Test
    fun newCardStartsEmptyOnFrontSide() {
        val session = startNewCard(Section.EPOCH_2)

        assertEquals(Draft(null, emptyList(), emptyList(), Side.FRONT, Section.EPOCH_2), session.draft)
        assertEquals(emptyList<EditorStep>(), session.history)
    }

    @Test
    fun strokeAddedAndUndone() {
        val session = startNewCard(Section.NEW).addStroke(firstStroke)
        assertEquals(listOf(firstStroke), session.draft.front)

        val undone = session.undoLastStep()

        assertEquals(emptyList<Stroke>(), undone.draft.front)
        assertEquals(emptyList<EditorStep>(), undone.history)
    }

    @Test
    fun strokesGoToTheVisibleSide() {
        val session = startNewCard(Section.NEW).switchSide(Side.BACK).addStroke(firstStroke)

        assertEquals(emptyList<Stroke>(), session.draft.front)
        assertEquals(listOf(firstStroke), session.draft.back)
    }

    @Test
    fun eraseRemovesWholeTouchedStrokes() {
        val top = buildStroke(0f to 0f, 10f to 0f)
        val far = buildStroke(200f to 200f, 210f to 200f)
        val bottom = buildStroke(0f to 40f, 10f to 40f)
        val session = startNewCard(Section.NEW).addStroke(top).addStroke(far).addStroke(bottom)

        val erased = session.eraseAlong(buildPath(5f to -3f, 5f to 43f), radius = 2f)

        assertEquals(listOf(far), erased.draft.front)

        // Одна отмена возвращает оба штриха на прежние места.
        val undone = erased.undoLastStep()
        assertEquals(listOf(top, far, bottom), undone.draft.front)
        assertEquals(3, undone.history.size)
    }

    @Test
    fun eraseMissingEverythingAddsNoStep() {
        val session = startNewCard(Section.NEW).addStroke(firstStroke)

        val erased = session.eraseAlong(buildPath(500f to 500f), radius = 5f)

        assertEquals(session, erased)
    }

    @Test
    fun eraseWorksOnlyOnTheVisibleSide() {
        val session = startNewCard(Section.NEW)
            .addStroke(firstStroke)
            .switchSide(Side.BACK)
            .addStroke(firstStroke)

        val erased = session.eraseAlong(buildPath(5f to 0f), radius = 2f)

        assertEquals(listOf(firstStroke), erased.draft.front)
        assertEquals(emptyList<Stroke>(), erased.draft.back)
    }

    @Test
    fun undoReachesTwentyFiveStepsAndCapsAtHundred() {
        // 25 действий вперемешку: добавление штриха и стирание предыдущего.
        var mixed = startNewCard(Section.NEW)
        for (step in 1..25) {
            val y = step * 100f
            mixed = if (step % 2 == 1) {
                mixed.addStroke(buildStroke(0f to y, 10f to y))
            } else {
                mixed.eraseAlong(buildPath(5f to y - 100f), radius = 3f)
            }
        }
        assertEquals(25, mixed.history.size)
        repeat(25) { mixed = mixed.undoLastStep() }
        assertEquals(emptyList<Stroke>(), mixed.draft.front)
        assertEquals(emptyList<EditorStep>(), mixed.history)

        // После 101 действия снимаются 100, 101-я отмена ничего не меняет.
        var long = startNewCard(Section.NEW)
        repeat(101) { long = long.addStroke(buildStroke(0f to it * 10f, 5f to it * 10f)) }
        assertEquals(UNDO_DEPTH, long.history.size)
        repeat(UNDO_DEPTH) { long = long.undoLastStep() }
        assertEquals(1, long.draft.front.size)
        assertEquals(long, long.undoLastStep())
    }

    @Test
    fun undoOnOtherSideShowsThatSide() {
        val session = startNewCard(Section.NEW).addStroke(firstStroke).switchSide(Side.BACK)

        val undone = session.undoLastStep()

        assertEquals(emptyList<Stroke>(), undone.draft.front)
        assertEquals(Side.FRONT, undone.draft.visibleSide)
    }

    @Test
    fun switchingSideIsNotAStep() {
        val session = startNewCard(Section.NEW).addStroke(firstStroke)

        val switched = session.switchSide(Side.BACK).switchSide(Side.FRONT)

        assertEquals(session, switched)
    }

    @Test
    fun undoWithoutHistoryChangesNothing() {
        val session = startNewCard(Section.NEW)

        assertEquals(session, session.undoLastStep())
    }

    @Test
    fun resumedDraftKeepsSideAndStartsWithoutHistory() {
        val draft = Draft(7, listOf(firstStroke), emptyList(), Side.BACK, Section.EPOCH_3)

        val session = resumeDraft(draft)

        assertEquals(draft, session.draft)
        assertEquals(emptyList<EditorStep>(), session.history)
    }

    @Test
    fun editingStartsFromCardContentAndKeepsReturnSection() {
        val card = buildCard(5, Section.NEW)
        val content = CardContent(5, listOf(firstStroke), emptyList())

        val session = startEditing(card, content, Section.EPOCH_2)

        assertEquals(Draft(5, listOf(firstStroke), emptyList(), Side.FRONT, Section.EPOCH_2), session.draft)
        assertEquals(emptyList<EditorStep>(), session.history)
    }

    @Test
    fun editingOnlyFromPool() {
        val content = CardContent(5, emptyList(), emptyList())

        assertThrows<IllegalArgumentException> { startEditing(buildCard(5, Section.EPOCH_1), content, Section.NEW) }
        assertThrows<IllegalArgumentException> { startEditing(buildCard(5, Section.LEARNED), content, Section.NEW) }
    }

    @Test
    fun editingRejectsContentOfAnotherCard() {
        val content = CardContent(6, emptyList(), emptyList())

        assertThrows<IllegalArgumentException> { startEditing(buildCard(5, Section.NEW), content, Section.NEW) }
    }
}
