package app.deckly.domain.editor

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeck
import app.deckly.domain.buildDeckState
import app.deckly.domain.buildStroke
import app.deckly.domain.findCard
import app.deckly.domain.model.Card
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DraftTest {

    private val front = listOf(buildStroke(0f to 0f, 10f to 0f))
    private val back = listOf(buildStroke(0f to 5f, 10f to 5f), buildStroke(1f to 1f))

    @Test
    fun savingNewDraftAppendsCardToPool() {
        val state = buildDeckState(
            buildDeck(),
            buildCard(1, Section.NEW, position = 0),
            buildCard(2, Section.NEW, position = 4),
            buildCard(3, Section.EPOCH_1, position = 9),
        )
        val draft = Draft(null, front, back, Side.BACK, Section.EPOCH_1)

        val (saved, content) = saveDraft(state, draft, newCardId = 77, today = 9, now = 1234L)

        val expected = Card(
            id = 77,
            section = Section.NEW,
            position = 5,
            successes = 0,
            sectionDay = 9,
            lastSuccessDay = null,
            createdAt = 1234L,
            postponed = false,
            answerRevealed = false,
            failed = false,
            checking = false,
            excludedFromPass = false,
        )
        assertEquals(expected, saved.findCard(77))
        assertEquals(CardContent(77, front, back), content)
        assertEquals(state.cards, saved.cards.dropLast(1))
        assertEquals(state.deck, saved.deck)
    }

    @Test
    fun savingNewDraftIntoEmptyPoolStartsAtZero() {
        val state = buildDeckState(buildDeck(), buildCard(3, Section.EPOCH_1, position = 9))

        val (saved, _) = saveDraft(state, Draft(null, front, back, Side.FRONT, Section.NEW), 77, today = 9, now = 1L)

        assertEquals(0, saved.findCard(77).position)
    }

    @Test
    fun savingEditKeepsCardPlace() {
        val card = buildCard(5, Section.NEW, position = 3, createdAt = 50L)
        val state = buildDeckState(buildDeck(), buildCard(1, Section.NEW, position = 0), card)
        val draft = Draft(5, front, back, Side.FRONT, Section.NEW)

        val (saved, content) = saveDraft(state, draft, newCardId = 999, today = 9, now = 1234L)

        assertEquals(card, saved.findCard(5))
        assertEquals(state, saved)
        assertEquals(CardContent(5, front, back), content)
    }

    @Test
    fun emptySidesAreAllowed() {
        val state = buildDeckState(buildDeck(), buildCard(1, Section.NEW))
        val draft = Draft(null, emptyList(), emptyList(), Side.FRONT, Section.NEW)

        val (saved, content) = saveDraft(state, draft, newCardId = 77, today = 9, now = 1234L)

        assertEquals(2, saved.cards.size)
        assertEquals(CardContent(77, emptyList(), emptyList()), content)
    }

    @Test
    fun savingEditOfMissingCardIsRejected() {
        val state = buildDeckState(buildDeck(), buildCard(1, Section.NEW))
        val draft = Draft(42, front, back, Side.FRONT, Section.NEW)

        assertThrows<IllegalArgumentException> { saveDraft(state, draft, newCardId = 77, today = 9, now = 1L) }
    }
}
