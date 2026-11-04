package app.deckly.domain.review

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeck
import app.deckly.domain.buildDeckState
import app.deckly.domain.buildEpochCards
import app.deckly.domain.buildPassDeck
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.review.Availability.Available
import app.deckly.domain.review.Availability.Unavailable
import app.deckly.domain.review.CardAction.BROWSE
import app.deckly.domain.review.CardAction.CREATE
import app.deckly.domain.review.CardAction.DELETE
import app.deckly.domain.review.CardAction.DID_NOT_KNOW
import app.deckly.domain.review.CardAction.EDIT
import app.deckly.domain.review.CardAction.FLIP
import app.deckly.domain.review.CardAction.KNEW
import app.deckly.domain.review.CardAction.POSTPONE
import app.deckly.domain.review.CardAction.RETURN_TO_NEW
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardActionsTest {

    private fun buildPassOfThree(passCursor: Int = 0): DeckState =
        buildDeckState(buildPassDeck(2, passCursor), buildEpochCards(2, 1, 2, 3))

    @Test
    fun postponeAvailableBeforeReveal() {
        val actions = computeCardActions(buildPassOfThree(), Side.FRONT)

        assertEquals(Available, actions[POSTPONE])
    }

    @Test
    fun revealBlocksPostponeEvenOnFront() {
        val revealed = revealAnswer(buildPassOfThree())

        val onFront = computeCardActions(revealed, Side.FRONT)
        assertEquals(Unavailable(UnavailableReason.ANSWER_REVEALED), onFront[POSTPONE])
        assertEquals(Unavailable(UnavailableReason.FRONT_VISIBLE), onFront[KNEW])
        assertEquals(Unavailable(UnavailableReason.FRONT_VISIBLE), onFront[DID_NOT_KNOW])

        val onBack = computeCardActions(revealed, Side.BACK)
        assertEquals(Available, onBack[KNEW])
        assertEquals(Available, onBack[DID_NOT_KNOW])
        assertEquals(Unavailable(UnavailableReason.ANSWER_REVEALED), onBack[POSTPONE])
    }

    @Test
    fun postponeUnavailableForLastAndPostponed() {
        val last = buildPassOfThree(passCursor = 2)
        assertEquals(Unavailable(UnavailableReason.LAST_IN_PASS), computeCardActions(last, Side.FRONT)[POSTPONE])

        val alreadyPostponed = buildDeckState(
            buildPassDeck(2),
            buildEpochCards(2, 1, 2, 3).map { if (it.id == 1L) it.copy(postponed = true) else it },
        )
        assertEquals(
            Unavailable(UnavailableReason.ALREADY_POSTPONED),
            computeCardActions(alreadyPostponed, Side.FRONT)[POSTPONE],
        )
    }

    @Test
    fun postponedCardShownLastIsReportedAsAlreadyPostponed() {
        val postponedLast = buildDeckState(
            buildPassDeck(2, passCursor = 2),
            buildEpochCards(2, 1, 2, 3).map { if (it.id == 3L) it.copy(postponed = true) else it },
        )

        assertEquals(
            Unavailable(UnavailableReason.ALREADY_POSTPONED),
            computeCardActions(postponedLast, Side.FRONT)[POSTPONE],
        )
    }

    @Test
    fun revealedPostponedLastCardIsReportedAsAnswerRevealed() {
        val state = buildDeckState(
            buildPassDeck(2, passCursor = 2),
            buildEpochCards(2, 1, 2, 3).map { if (it.id == 3L) it.copy(postponed = true, answerRevealed = true) else it },
        )

        assertEquals(
            Unavailable(UnavailableReason.ANSWER_REVEALED),
            computeCardActions(state, Side.BACK)[POSTPONE],
        )
    }

    @Test
    fun sectionsOfferTheirActions() {
        val activePass = computeCardActions(buildPassOfThree(), Side.FRONT)
        assertEquals(setOf(DELETE, RETURN_TO_NEW, KNEW, DID_NOT_KNOW, POSTPONE, FLIP), activePass.keys)

        val pool = buildDeckState(buildDeck(activeSection = Section.NEW), listOf(buildCard(1, Section.NEW)))
        val poolActions = computeCardActions(pool, Side.FRONT)
        assertEquals(setOf(DELETE, EDIT, CREATE, BROWSE, FLIP), poolActions.keys)
        assertTrue(poolActions.values.all { it == Available })

        val emptyPool = buildDeckState(buildDeck(activeSection = Section.NEW), emptyList())
        assertEquals(setOf(CREATE), computeCardActions(emptyPool, Side.FRONT).keys)

        val inactiveEpoch = buildDeckState(
            buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1, passStarted = true),
            buildEpochCards(3, 1, 2),
        )
        val inactiveActions = computeCardActions(inactiveEpoch, Side.FRONT)
        assertEquals(setOf(DELETE, RETURN_TO_NEW, BROWSE, FLIP), inactiveActions.keys)
        assertTrue(inactiveActions.values.all { it == Available })

        val learned = buildDeckState(
            buildDeck(activeSection = Section.LEARNED),
            listOf(buildCard(1, Section.LEARNED)),
        )
        assertEquals(setOf(DELETE, BROWSE, FLIP), computeCardActions(learned, Side.FRONT).keys)

        val emptyInactive = buildDeckState(
            buildDeck(activeSection = Section.EPOCH_4, currentEpoch = 1, passStarted = true),
            buildEpochCards(1, 1, 2),
        )
        assertEquals(emptyMap<CardAction, Availability>(), computeCardActions(emptyInactive, Side.FRONT))
    }

    @Test
    fun pausedEpochOffersNoRating() {
        val paused = buildDeckState(
            buildDeck(
                activeSection = Section.EPOCH_4,
                currentEpoch = 1,
                passStarted = true,
                pausedEpoch = 4,
                pausedCursor = 1,
            ),
            buildEpochCards(4, 1, 2, 3) + buildCard(9, Section.EPOCH_1),
        )

        assertEquals(setOf(DELETE, RETURN_TO_NEW, BROWSE, FLIP), computeCardActions(paused, Side.BACK).keys)
    }
}
