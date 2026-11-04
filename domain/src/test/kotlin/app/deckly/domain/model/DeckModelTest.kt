package app.deckly.domain.model

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeck
import app.deckly.domain.buildDeckState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DeckModelTest {

    @Test
    fun newDeckStartsAtPoolWithFirstEpochNotStarted() {
        val deck = createDeck(1, "Слова")

        assertEquals(1L, deck.id)
        assertEquals("Слова", deck.name)
        assertEquals(Section.NEW, deck.activeSection)
        assertEquals(1, deck.currentEpoch)
        assertFalse(deck.passStarted)
        assertEquals(EpochValues(0, 0, 0, 0), deck.passCount)
        assertEquals(0, deck.passCursor)
        assertEquals(0, deck.browseCursor)
        assertEquals(EpochValues<Int?>(null, null, null, null), deck.lastPassDay)
        assertEquals(0, deck.waitingEpoch)
        assertEquals(0, deck.pausedEpoch)
        assertEquals(0, deck.pausedCursor)
        assertEquals(EpochValues(emptyList<Long>(), emptyList(), emptyList(), emptyList()), deck.tail)
    }

    @Test
    fun epochValuesReplaceOnlyOneEpoch() {
        val values = EpochValues(1, 2, 3, 4)

        val replaced = values.replace(3, 7)

        assertEquals(1, replaced[1])
        assertEquals(2, replaced[2])
        assertEquals(7, replaced[3])
        assertEquals(4, replaced[4])
        assertEquals(3, values[3])
    }

    @Test
    fun epochValuesRejectEpochOutsideOneToFour() {
        val values = EpochValues(1, 2, 3, 4)

        assertThrows<IllegalArgumentException> { values[0] }
        assertThrows<IllegalArgumentException> { values[5] }
        assertThrows<IllegalArgumentException> { values.replace(0, 9) }
        assertThrows<IllegalArgumentException> { values.replace(5, 9) }
    }

    @Test
    fun epochSectionsConvertBothWays() {
        assertEquals(Section.EPOCH_1, findEpochSection(1))
        assertEquals(Section.EPOCH_2, findEpochSection(2))
        assertEquals(Section.EPOCH_3, findEpochSection(3))
        assertEquals(Section.EPOCH_4, findEpochSection(4))
        assertEquals(1, Section.EPOCH_1.epochNumber)
        assertEquals(2, Section.EPOCH_2.epochNumber)
        assertEquals(3, Section.EPOCH_3.epochNumber)
        assertEquals(4, Section.EPOCH_4.epochNumber)
        assertNull(Section.NEW.epochNumber)
        assertNull(Section.LEARNED.epochNumber)
        assertThrows<IllegalArgumentException> { findEpochSection(0) }
        assertThrows<IllegalArgumentException> { findEpochSection(5) }
    }

    @Test
    fun sectionCardsAreFilteredAndOrderedByPosition() {
        val state = buildDeckState(
            buildDeck(),
            buildCard(1, Section.EPOCH_2, position = 2),
            buildCard(2, Section.EPOCH_1, position = 0),
            buildCard(3, Section.EPOCH_2, position = 0),
            buildCard(4, Section.EPOCH_2, position = 1),
        )

        assertEquals(listOf(3L, 4L, 1L), state.listSectionCards(Section.EPOCH_2).map { it.id })
        assertEquals(emptyList<Card>(), state.listSectionCards(Section.LEARNED))
    }

    @Test
    fun changesListChangedNewAndDeletedCards() {
        val deck = buildDeck()
        val kept = buildCard(1, Section.EPOCH_1, position = 0)
        val changedBefore = buildCard(2, Section.EPOCH_1, position = 1, successes = 1)
        val removed = buildCard(3, Section.EPOCH_1, position = 2)
        val before = buildDeckState(deck, kept, changedBefore, removed)

        val changedAfter = changedBefore.copy(successes = 2)
        val added = buildCard(4, Section.NEW, position = 0)
        val deckAfter = deck.copy(passCursor = 1)
        val after = buildDeckState(deckAfter, kept, changedAfter, added)

        val changes = collectDeckChanges(before, after)

        assertEquals(deckAfter, changes.deck)
        assertEquals(listOf(changedAfter, added), changes.upsertedCards)
        assertEquals(setOf(3L), changes.deletedCardIds)
    }
}
