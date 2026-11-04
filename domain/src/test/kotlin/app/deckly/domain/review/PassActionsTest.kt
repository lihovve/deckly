package app.deckly.domain.review

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeck
import app.deckly.domain.buildDeckState
import app.deckly.domain.buildEpochCards
import app.deckly.domain.buildPassDeck
import app.deckly.domain.findCard
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.findEpochSection
import app.deckly.domain.model.listSectionCards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PassActionsTest {

    @Test
    fun knewAddsSuccessAndMovesOn() {
        val state = buildDeckState(
            buildPassDeck(2, visibleSide = Side.BACK),
            listOf(
                buildCard(1, Section.EPOCH_2, position = 0, successes = 1, answerRevealed = true),
                buildCard(2, Section.EPOCH_2, position = 1),
            ),
        )

        val after = rateKnew(state, today = 7)

        assertEquals(2, after.findCard(1).successes)
        assertEquals(7, after.findCard(1).lastSuccessDay)
        assertEquals(1, after.deck.passCursor)
        assertEquals(2L, findCurrentCard(after)?.id)
    }

    @Test
    fun didNotKnowKeepsZeroInFirstEpoch() {
        fun rateInEpoch(epoch: Int, successes: Int): DeckState {
            val section = findEpochSection(epoch)
            val state = buildDeckState(
                buildPassDeck(epoch, visibleSide = Side.BACK),
                listOf(
                    buildCard(1, section, position = 0, successes = successes, answerRevealed = true),
                    buildCard(2, section, position = 1),
                ),
            )
            return rateDidNotKnow(state)
        }

        val zero = rateInEpoch(1, successes = 0)
        assertEquals(0, zero.findCard(1).successes)
        assertFalse(zero.findCard(1).failed)
        assertEquals(1, zero.deck.passCursor)

        assertEquals(1, rateInEpoch(1, successes = 2).findCard(1).successes)

        val third = rateInEpoch(3, successes = 2)
        assertTrue(third.findCard(1).failed)
        assertEquals(2, third.findCard(1).successes)
        assertEquals(1, third.deck.passCursor)
    }

    @Test
    fun postponedCardGoesToEndOfPass() {
        val state = buildDeckState(
            buildPassDeck(2),
            buildEpochCards(2, 1, 2, 3).map { if (it.id == 1L) it.copy(successes = 2) else it },
        )

        val after = postponeCard(state)

        assertEquals(listOf(2L, 3L, 1L), listPassCards(after, 2).map { it.id })
        assertEquals(2L, findCurrentCard(after)?.id)
        assertEquals(0, after.deck.passCursor)
        assertTrue(after.findCard(1).postponed)
        assertEquals(2, after.findCard(1).successes)
    }

    @Test
    fun returnToNewPutsCardAtEndOfPoolWithZero() {
        val state = buildDeckState(
            buildPassDeck(3),
            listOf(
                buildCard(
                    1, Section.EPOCH_3, successes = 2, sectionDay = 3,
                    postponed = true, answerRevealed = true, failed = true, checking = true, excludedFromPass = true,
                ),
                buildCard(10, Section.NEW, position = 0),
                buildCard(11, Section.NEW, position = 4),
            ),
        )

        val after = returnToNew(state, 1, today = 9)

        val returned = after.findCard(1)
        assertEquals(Section.NEW, returned.section)
        assertEquals(listOf(10L, 11L, 1L), after.listSectionCards(Section.NEW).map { it.id })
        assertEquals(0, returned.successes)
        assertFalse(returned.postponed)
        assertFalse(returned.answerRevealed)
        assertFalse(returned.failed)
        assertFalse(returned.checking)
        assertFalse(returned.excludedFromPass)
        assertEquals(9, returned.sectionDay)
    }

    @Test
    fun removalBeforeCursorKeepsNextCard() {
        val state = buildDeckState(buildPassDeck(2, passCursor = 2), buildEpochCards(2, 1, 2, 3, 4))
        assertEquals(3L, findCurrentCard(state)?.id)

        val afterDelete = deleteCard(state, 1)
        assertEquals(3L, findCurrentCard(afterDelete)?.id)
        assertEquals(listOf(2L, 3L, 4L), listPassCards(afterDelete, 2).map { it.id })

        val afterReturn = returnToNew(state, 3, today = 9)
        assertEquals(4L, findCurrentCard(afterReturn)?.id)
        assertEquals(listOf(1L, 2L, 4L), listPassCards(afterReturn, 2).map { it.id })

        val afterBoth = returnToNew(afterDelete, 3, today = 9)
        assertEquals(4L, findCurrentCard(afterBoth)?.id)
        assertEquals(listOf(2L, 4L), listPassCards(afterBoth, 2).map { it.id })
    }

    @Test
    fun removalAfterCursorKeepsCursor() {
        val state = buildDeckState(buildPassDeck(2, passCursor = 1), buildEpochCards(2, 1, 2, 3, 4))

        val after = deleteCard(state, 4)

        assertEquals(1, after.deck.passCursor)
        assertEquals(2L, findCurrentCard(after)?.id)
    }

    @Test
    fun removingLastUnshownCardExhaustsPass() {
        val state = buildDeckState(buildPassDeck(2, passCursor = 2), buildEpochCards(2, 1, 2, 3))

        val after = deleteCard(state, 3)

        assertEquals(2, after.deck.passCursor)
        assertNull(findCurrentCard(after))
    }

    @Test
    fun removalInPausedPassKeepsItsPosition() {
        val insertedPass = buildCard(5, Section.EPOCH_1)
        val state = buildDeckState(
            buildDeck(
                activeSection = Section.EPOCH_1,
                currentEpoch = 1,
                passStarted = true,
                pausedEpoch = 4,
                pausedCursor = 2,
            ),
            buildEpochCards(4, 1, 2, 3, 4) + insertedPass,
        )
        fun findNextPausedCard(after: DeckState): Long? =
            listPassCards(after, 4).getOrNull(after.deck.pausedCursor)?.id

        assertEquals(3L, findNextPausedCard(state))

        val afterDeleteBefore = deleteCard(state, 2)
        assertEquals(1, afterDeleteBefore.deck.pausedCursor)
        assertEquals(3L, findNextPausedCard(afterDeleteBefore))

        val afterReturnBefore = returnToNew(state, 1, today = 9)
        assertEquals(1, afterReturnBefore.deck.pausedCursor)
        assertEquals(3L, findNextPausedCard(afterReturnBefore))

        val afterDeleteNext = deleteCard(state, 3)
        assertEquals(2, afterDeleteNext.deck.pausedCursor)
        assertEquals(4L, findNextPausedCard(afterDeleteNext))

        val afterDeleteAfter = deleteCard(state, 4)
        assertEquals(2, afterDeleteAfter.deck.pausedCursor)
        assertEquals(3L, findNextPausedCard(afterDeleteAfter))

        assertEquals(0, afterDeleteBefore.deck.passCursor)
    }

    @Test
    fun browsingWrapsAround() {
        val deck = buildDeck(activeSection = Section.EPOCH_2, currentEpoch = 1, browseCursor = 2)
        val state = buildDeckState(deck, buildEpochCards(2, 1, 2, 3))

        assertEquals(0, browseSection(state, +1).deck.browseCursor)
        assertEquals(2, browseSection(state.copy(deck = deck.copy(browseCursor = 0)), -1).deck.browseCursor)
        assertEquals(3L, findCurrentCard(state)?.id)
    }

    @Test
    fun browsingEmptySectionStaysAtZero() {
        val state = buildDeckState(buildDeck(activeSection = Section.EPOCH_2, currentEpoch = 1), emptyList())

        assertEquals(0, browseSection(state, +1).deck.browseCursor)
        assertNull(findCurrentCard(state))
    }

    @Test
    fun removalInBrowsedSectionKeepsCurrentCard() {
        val deck = buildDeck(activeSection = Section.EPOCH_2, currentEpoch = 1, browseCursor = 1)
        val state = buildDeckState(deck, buildEpochCards(2, 1, 2, 3))

        val afterDeleteBefore = deleteCard(state, 1)
        assertEquals(2L, findCurrentCard(afterDeleteBefore)?.id)

        val afterDeleteCurrent = deleteCard(state, 2)
        assertEquals(3L, findCurrentCard(afterDeleteCurrent)?.id)

        val atLast = state.copy(deck = deck.copy(browseCursor = 2))
        val afterDeleteLast = deleteCard(atLast, 3)
        assertEquals(0, afterDeleteLast.deck.browseCursor)
        assertEquals(1L, findCurrentCard(afterDeleteLast)?.id)
    }

    @Test
    fun flipMarksOnlyCurrentCardOfActivePass() {
        val state = buildDeckState(buildPassDeck(2, passCursor = 1), buildEpochCards(2, 1, 2, 3))

        val after = flipCard(state)

        assertEquals(Side.BACK, after.deck.visibleSide)
        assertEquals(listOf(false, true, false), listOf(1L, 2L, 3L).map { after.findCard(it).answerRevealed })
    }

    @Test
    fun flippingBackKeepsAnswerRevealed() {
        val state = buildDeckState(buildPassDeck(2), buildEpochCards(2, 1, 2, 3))

        val onFront = flipCard(flipCard(state))

        assertEquals(Side.FRONT, onFront.deck.visibleSide)
        assertTrue(onFront.findCard(1).answerRevealed)
    }

    @Test
    fun flipOutsideActivePassOnlyTurnsTheSide() {
        val browsing = buildDeckState(
            buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1, passStarted = true),
            buildEpochCards(3, 1, 2),
        )
        assertEquals(browsing.copy(deck = browsing.deck.copy(visibleSide = Side.BACK)), flipCard(browsing))

        val notStarted = buildDeckState(
            buildPassDeck(2).copy(passStarted = false),
            buildEpochCards(2, 1, 2),
        )
        assertEquals(notStarted.copy(deck = notStarted.deck.copy(visibleSide = Side.BACK)), flipCard(notStarted))
    }

    @Test
    fun currentCardFollowsPassCursorAndSkipsExcludedCards() {
        val cards = listOf(
            buildCard(1, Section.EPOCH_2, position = 0),
            buildCard(2, Section.EPOCH_2, position = 1, excludedFromPass = true),
            buildCard(3, Section.EPOCH_2, position = 2),
        )

        assertEquals(1L, findCurrentCard(buildDeckState(buildPassDeck(2, passCursor = 0), cards))?.id)
        assertEquals(3L, findCurrentCard(buildDeckState(buildPassDeck(2, passCursor = 1), cards))?.id)
        assertNull(findCurrentCard(buildDeckState(buildPassDeck(2, passCursor = 2), cards)))
    }

    @Test
    fun ratingAndPostponingOutsideStartedPassAreRejected() {
        val browsing = buildDeckState(
            buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1, passStarted = true),
            buildEpochCards(3, 1, 2).map { it.copy(answerRevealed = true) },
        )

        assertThrows<IllegalArgumentException> { rateKnew(browsing, today = 5) }
        assertThrows<IllegalArgumentException> { rateDidNotKnow(browsing) }
        assertThrows<IllegalArgumentException> { postponeCard(browsing) }
    }

    @Test
    fun ratingBeforeAnswerRevealIsRejected() {
        val state = buildDeckState(buildPassDeck(2), buildEpochCards(2, 1, 2))

        assertThrows<IllegalArgumentException> { rateKnew(state, today = 5) }
        assertThrows<IllegalArgumentException> { rateDidNotKnow(state) }
    }

    @Test
    fun ratingOnFrontAfterRevealIsRejected() {
        val state = buildDeckState(
            buildPassDeck(2, visibleSide = Side.FRONT),
            buildEpochCards(2, 1, 2).map { it.copy(answerRevealed = true) },
        )

        assertThrows<IllegalArgumentException> { rateKnew(state, today = 5) }
        assertThrows<IllegalArgumentException> { rateDidNotKnow(state) }
    }

    @Test
    fun postponingUnavailableCardIsRejected() {
        val revealed = buildDeckState(
            buildPassDeck(2),
            buildEpochCards(2, 1, 2).map { it.copy(answerRevealed = true) },
        )
        val alreadyPostponed = buildDeckState(
            buildPassDeck(2),
            buildEpochCards(2, 1, 2).map { it.copy(postponed = true) },
        )
        val last = buildDeckState(buildPassDeck(2, passCursor = 1), buildEpochCards(2, 1, 2))

        assertThrows<IllegalArgumentException> { postponeCard(revealed) }
        assertThrows<IllegalArgumentException> { postponeCard(alreadyPostponed) }
        assertThrows<IllegalArgumentException> { postponeCard(last) }
    }

    @Test
    fun onlyEpochCardsReturnToNew() {
        val state = buildDeckState(
            buildDeck(),
            listOf(buildCard(1, Section.NEW), buildCard(2, Section.LEARNED)),
        )

        assertThrows<IllegalArgumentException> { returnToNew(state, 1, today = 5) }
        assertThrows<IllegalArgumentException> { returnToNew(state, 2, today = 5) }
    }

    @Test
    fun removingUnknownCardIsRejected() {
        val state = buildDeckState(buildDeck(), listOf(buildCard(1, Section.NEW)))

        assertThrows<IllegalArgumentException> { deleteCard(state, 99) }
        assertThrows<IllegalArgumentException> { returnToNew(state, 99, today = 5) }
    }
}
