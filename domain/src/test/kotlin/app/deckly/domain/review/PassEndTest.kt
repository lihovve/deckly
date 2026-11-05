package app.deckly.domain.review

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeck
import app.deckly.domain.buildDeckState
import app.deckly.domain.buildEpochCards
import app.deckly.domain.findCard
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.findEpochSection
import app.deckly.domain.model.listSectionCards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PassEndTest {

    @Test
    fun lastFiveShownCardsAreRememberedInOrder() {
        val state = buildDeckState(buildDeck(), buildEpochCards(2, 1, 2, 3, 4, 5, 6, 7))

        val after = finishPass(state, 2, today = 9)

        assertEquals(listOf(3L, 4L, 5L, 6L, 7L), after.deck.tail[2])
        assertEquals(emptyList<Long>(), after.deck.tail[1])
    }

    @Test
    fun shortPassRemembersAllItsCardsAndSkipsExcludedOnes() {
        val cards = listOf(
            buildCard(1, Section.EPOCH_3, position = 1),
            buildCard(2, Section.EPOCH_3, position = 0),
            buildCard(3, Section.EPOCH_3, position = 2, excludedFromPass = true),
        )

        val after = finishPass(buildDeckState(buildDeck(), cards), 3, today = 9)

        assertEquals(listOf(2L, 1L), after.deck.tail[3])
    }

    @Test
    fun failedCardsGoOneEpochDownWithThreeSuccesses() {
        for (epoch in 2..4) {
            val section = findEpochSection(epoch)
            val lower = findEpochSection(epoch - 1)
            val state = buildDeckState(
                buildDeck(),
                listOf(
                    buildCard(1, section, position = 0, successes = 2, failed = true, answerRevealed = true, postponed = true),
                    buildCard(2, section, position = 1, successes = 2),
                    buildCard(10, lower, position = 4),
                ),
            )

            val after = finishPass(state, epoch, today = 9)

            val demoted = after.findCard(1)
            assertEquals(lower, demoted.section, "эпоха $epoch")
            assertEquals(3, demoted.successes, "эпоха $epoch")
            assertEquals(9, demoted.sectionDay, "эпоха $epoch")
            assertFalse(demoted.failed)
            assertFalse(demoted.answerRevealed)
            assertFalse(demoted.postponed)
            assertTrue(demoted.position > 4, "в конец секции эпохи $epoch - 1")
            assertEquals(section, after.findCard(2).section)
            assertEquals(2, after.findCard(2).successes)
        }
    }

    @Test
    fun learnedCheckResultIsAppliedAtPassEnd() {
        val state = buildDeckState(
            buildDeck(),
            listOf(
                buildCard(1, Section.EPOCH_4, position = 0, checking = true, sectionDay = 5, successes = 0),
                buildCard(2, Section.EPOCH_4, position = 1, checking = true, sectionDay = 5, failed = true),
                buildCard(3, Section.EPOCH_4, position = 2, failed = true, successes = 1),
                buildCard(10, Section.LEARNED, position = 0, sectionDay = 1),
                buildCard(11, Section.LEARNED, position = 3, sectionDay = 2),
            ),
        )

        val after = finishPass(state, 4, today = 40)

        val knew = after.findCard(1)
        assertEquals(Section.LEARNED, knew.section)
        assertEquals(40, knew.sectionDay)
        assertEquals(0, knew.successes)
        assertFalse(knew.checking)
        assertEquals(1L, after.listSectionCards(Section.LEARNED).last().id)

        val didNotKnow = after.findCard(2)
        assertEquals(Section.EPOCH_4, didNotKnow.section)
        assertEquals(2, didNotKnow.successes)
        assertFalse(didNotKnow.checking)

        // Обычная карточка эпохи 4 с «не знал» спускается, а проверяемая не спускается.
        assertEquals(Section.EPOCH_3, after.findCard(3).section)
        assertEquals(3, after.findCard(3).successes)
    }

    @Test
    fun lastPassDayIsWrittenOnlyForNonEmptyPass() {
        val withCards = finishPass(buildDeckState(buildDeck(), buildEpochCards(3, 1, 2)), 3, today = 12)
        assertEquals(12, withCards.deck.lastPassDay[3])

        val earlier = buildDeck(lastPassDay = EpochValues<Int?>(null, null, 5, null))
        val empty = finishPass(buildDeckState(earlier, emptyList()), 3, today = 12)
        assertEquals(5, empty.deck.lastPassDay[3])

        val allDemoted = finishPass(
            buildDeckState(buildDeck(), listOf(buildCard(1, Section.EPOCH_3, failed = true))),
            3, today = 12,
        )
        assertEquals(12, allDemoted.deck.lastPassDay[3], "проход, где карточка спущена, всё равно непустой")
    }

    @Test
    fun thresholdOnEntryDayMovesCardToRestingAtPassEnd() {
        val state = buildDeckState(
            buildDeck(),
            buildCard(1, Section.EPOCH_1, position = 0, successes = 4, sectionDay = 9),
            buildCard(2, Section.EPOCH_1, position = 1, successes = 3, sectionDay = 9),
            buildCard(3, Section.EPOCH_1, position = 2, successes = 4, sectionDay = 9, postponed = true),
        )

        val after = finishPass(state, 1, today = 9)

        val resting = after.findCard(1)
        assertEquals(Section.RESTING, resting.section)
        assertEquals(0, resting.successes)
        assertEquals(9, resting.sectionDay)
        assertFalse(resting.excludedFromPass)
        assertEquals(Section.RESTING, after.findCard(3).section)
        assertEquals(listOf(0, 1), after.listSectionCards(Section.RESTING).map { it.position })
        assertEquals(listOf(2L), after.listSectionCards(Section.EPOCH_1).map { it.id })
        assertEquals(emptyList<Long>(), after.listSectionCards(Section.EPOCH_2).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), after.deck.tail[1])
    }

    @Test
    fun legacyCardOverThresholdRestsOnEntryDayAndNotAfterIt() {
        // Карточка «5/4» из прошлой сборки: счётчик выше порога.
        val legacy = buildCard(1, Section.EPOCH_1, successes = 5, sectionDay = 9)

        assertEquals(Section.RESTING, finishPass(buildDeckState(buildDeck(), legacy), 1, today = 9).findCard(1).section)
        assertEquals(Section.EPOCH_1, finishPass(buildDeckState(buildDeck(), legacy), 1, today = 10).findCard(1).section)
    }

    @Test
    fun passFinishedNextDayDoesNotRest() {
        val state = buildDeckState(buildDeck(), buildCard(1, Section.EPOCH_1, successes = 4, sectionDay = 9))

        val after = finishPass(state, 1, today = 10)

        assertEquals(Section.EPOCH_1, after.findCard(1).section)
        assertEquals(4, after.findCard(1).successes)
    }

    @Test
    fun belowThresholdCardDoesNotRest() {
        val state = buildDeckState(buildDeck(), buildCard(1, Section.EPOCH_1, successes = 3, sectionDay = 9))

        assertEquals(state.cards, finishPass(state, 1, today = 9).cards)
    }

    @Test
    fun restingIsOnlyForFirstEpoch() {
        for (epoch in 2..4) {
            val section = findEpochSection(epoch)
            val state = buildDeckState(buildDeck(), buildCard(1, section, successes = 4, sectionDay = 9))

            assertEquals(section, finishPass(state, epoch, today = 9).findCard(1).section, "эпоха $epoch")
        }
    }

    @Test
    fun firstEpochPassOnlyRemembersTail() {
        val deck = buildDeck(
            pausedEpoch = 4,
            pausedCursor = 40,
            passCount = EpochValues(3, 1, 2, 0),
            lastPassDay = EpochValues<Int?>(null, 2, 3, 4),
        )
        val state = buildDeckState(
            deck,
            buildEpochCards(1, 1, 2, 3).map { it.copy(failed = true, successes = 4) },
        )

        val after = finishPass(state, 1, today = 9)

        assertEquals(listOf(1L, 2L, 3L), after.deck.tail[1])
        assertEquals(state.cards, after.cards)
        assertEquals(deck.copy(tail = after.deck.tail), after.deck)
    }
}
