package app.deckly.domain.review

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeck
import app.deckly.domain.buildDeckState
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.findEpochSection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScheduleTest {

    @Test
    fun scheduleMatchesReferenceSequence() {
        val passCounts = IntArray(4)
        val visitedEpochs = mutableListOf<Int>()
        var epoch = 1

        repeat(39) {
            visitedEpochs += epoch
            passCounts[epoch - 1]++
            epoch = chooseNextEpoch(epoch, passCounts[epoch - 1], waitingEpoch = 0)
        }

        val reference = "1 1 1 1 1 2 1 1 1 1 1 2 1 1 1 1 1 2 3 1 1 1 1 1 2 1 1 1 1 1 2 1 1 1 1 1 2 3 4"
        assertEquals(reference.split(" ").map { it.toInt() }, visitedEpochs)
    }

    @Test
    fun waitingEpochGivesTurnToFirstEpoch() {
        assertEquals(1, chooseNextEpoch(1, 5, waitingEpoch = 2))
    }

    @Test
    fun unknownFinishedEpochIsRejected() {
        assertThrows<IllegalArgumentException> { chooseNextEpoch(0, 1, waitingEpoch = 0) }
        assertThrows<IllegalArgumentException> { chooseNextEpoch(5, 1, waitingEpoch = 0) }
    }

    @Test
    fun secondEpochMaturesNextDay() {
        // Прошлый проход закончился в день 10; границы: эпоха 2 — 1 день, 3 — 3 дня, 4 — 7 дней.
        assertMaturesOnlyFrom(epoch = 2, lastPassDay = 10, firstMatureDay = 11)
        assertMaturesOnlyFrom(epoch = 3, lastPassDay = 10, firstMatureDay = 13)
        assertMaturesOnlyFrom(epoch = 4, lastPassDay = 10, firstMatureDay = 17)
    }

    @Test
    fun firstEpochIsAlwaysMature() {
        val state = buildDeckState(
            buildDeck(lastPassDay = EpochValues<Int?>(5, null, null, null)),
            buildCard(1, Section.EPOCH_1),
        )

        assertTrue(isEpochMature(state, 1, today = 5))
    }

    @Test
    fun epochWithoutFinishedPassIsMature() {
        val state = buildDeckState(buildDeck(), buildCard(1, Section.EPOCH_3))

        assertTrue(isEpochMature(state, 3, today = 0))
    }

    @Test
    fun emptyEpochDoesNotWait() {
        val today = 10
        val state = buildDeckState(
            buildDeck(lastPassDay = EpochValues<Int?>(null, today, null, null)),
            buildCard(1, Section.EPOCH_1),
        )

        assertTrue(isEpochEmpty(state, 2, today))
        assertTrue(isEpochMature(state, 2, today))
    }

    @Test
    fun epochWithItsOwnCardIsNotEmpty() {
        val state = buildDeckState(
            buildDeck(),
            buildCard(1, Section.EPOCH_2),
            buildCard(2, Section.NEW),
        )

        assertFalse(isEpochEmpty(state, 2, today = 10))
        assertTrue(isEpochEmpty(state, 3, today = 10))
    }

    @Test
    fun fourthEpochWithReadyLearnedIsNotEmpty() {
        val today = 100

        val oddPass = buildFourthEpochStateWithReadyLearned(pass4 = 1, today = today)
        assertFalse(isEpochEmpty(oddPass, 4, today))
        assertFalse(isEpochMature(oddPass, 4, today))

        val evenPass = buildFourthEpochStateWithReadyLearned(pass4 = 2, today = today)
        assertTrue(isEpochEmpty(evenPass, 4, today))
        assertTrue(isEpochMature(evenPass, 4, today))
    }

    @Test
    fun dateMovedBackKeepsEpochWaiting() {
        val state = buildDeckState(
            buildDeck(lastPassDay = EpochValues<Int?>(null, null, 20, null)),
            buildCard(1, Section.EPOCH_3),
        )

        assertFalse(isEpochMature(state, 3, today = 15))
    }

    @Test
    fun learnedCardReadyAfterThirtyDays() {
        val learned = buildCard(1, Section.LEARNED, sectionDay = 100)

        assertFalse(isReadyForCheck(learned, today = 129))
        assertTrue(isReadyForCheck(learned, today = 130))
        assertFalse(isReadyForCheck(learned, today = 99))
    }

    @Test
    fun onlyLearnedCardsCanBeReadyForCheck() {
        val inFourthEpoch = buildCard(1, Section.EPOCH_4, sectionDay = 0)

        assertFalse(isReadyForCheck(inFourthEpoch, today = 100))
    }

    @Test
    fun learnedCheckComesOnEverySecondFourthEpochPass() {
        val results = (0..4).map { pass4 ->
            isLearnedCheckPass(buildDeck(passCount = EpochValues(0, 0, 0, pass4)))
        }

        assertEquals(listOf(false, true, false, true, false), results)
    }

    private fun assertMaturesOnlyFrom(epoch: Int, lastPassDay: Int, firstMatureDay: Int) {
        val state = buildDeckState(
            buildDeck(lastPassDay = EpochValues<Int?>(null, null, null, null).replace(epoch, lastPassDay)),
            buildCard(1, findEpochSection(epoch)),
        )

        for (today in lastPassDay until firstMatureDay) {
            assertFalse(isEpochMature(state, epoch, today), "эпоха $epoch в день $today ещё ждёт")
        }
        assertTrue(isEpochMature(state, epoch, firstMatureDay), "эпоха $epoch в день $firstMatureDay созрела")
    }

    private fun buildFourthEpochStateWithReadyLearned(pass4: Int, today: Int): DeckState = buildDeckState(
        buildDeck(
            passCount = EpochValues(0, 0, 0, pass4),
            lastPassDay = EpochValues<Int?>(null, null, null, today),
        ),
        buildCard(1, Section.LEARNED, sectionDay = today - 30),
    )
}
