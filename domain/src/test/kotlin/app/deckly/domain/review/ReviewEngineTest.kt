package app.deckly.domain.review

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeckState
import app.deckly.domain.buildEpochRange
import app.deckly.domain.buildPassDeck
import app.deckly.domain.findCard
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.createDeck
import app.deckly.domain.model.listSectionCards
import app.deckly.domain.review.ReviewAction.Browse
import app.deckly.domain.review.ReviewAction.DeleteCard
import app.deckly.domain.review.ReviewAction.OpenSection
import app.deckly.domain.review.ReviewAction.Postpone
import app.deckly.domain.review.ReviewAction.ReturnToNew
import app.deckly.domain.review.ReviewOutcome.DoneForToday
import app.deckly.domain.review.ReviewOutcome.EmptySection
import app.deckly.domain.review.ReviewOutcome.NeedNewCard
import app.deckly.domain.review.ReviewOutcome.ShowCard
import app.deckly.domain.review.ReviewOutcome.Transition
import app.deckly.domain.review.TransitionKind.INSERTED
import app.deckly.domain.review.TransitionKind.REGULAR
import app.deckly.domain.review.TransitionKind.RESUMED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class ReviewEngineTest {

    private val today = 10
    private val random = Random(7)

    private fun act(state: DeckState, action: ReviewAction, day: Int = today): ReviewResult =
        ReviewEngine.applyAction(state, action, day, random)

    /** Открыть ответ текущей карточки и оценить «знал». */
    private fun playKnew(state: DeckState, day: Int = today): ReviewResult {
        val revealed = act(state, ReviewAction.RevealAnswer, day)
        return act(revealed.state, ReviewAction.Knew, day)
    }

    private fun playDidNotKnow(state: DeckState, day: Int = today): ReviewResult {
        val revealed = act(state, ReviewAction.RevealAnswer, day)
        return act(revealed.state, ReviewAction.DidNotKnow, day)
    }

    private fun playKnewTimes(state: DeckState, count: Int, day: Int = today): ReviewResult {
        var result = ReviewResult(state, ReviewEngine.computeCurrentOutcome(state))
        repeat(count) { result = playKnew(result.state, day) }
        return result
    }

    @Test
    fun emptySecondEpochPassesSilently() {
        val deck = buildPassDeck(1).copy(passCount = EpochValues(4, 0, 0, 0))
        val state = buildDeckState(deck, buildEpochRange(1, 1, 3))

        val result = playKnewTimes(state, 3)

        assertEquals(Transition(1, 3, REGULAR), result.outcome)
        assertEquals(EpochValues(5, 1, 0, 0), result.state.deck.passCount)
        assertNull(result.state.deck.lastPassDay[2])
        assertEquals(Section.EPOCH_1, result.state.deck.activeSection)
    }

    @Test
    fun emptyDeckAsksForNewCard() {
        val empty = act(buildDeckState(createDeck(1, "Слова"), emptyList()), OpenSection(Section.EPOCH_1))

        assertEquals(NeedNewCard, empty.outcome)
        assertEquals(Section.NEW, empty.state.deck.activeSection)
        assertFalse(empty.state.deck.passStarted)

        val youngLearned = buildDeckState(
            createDeck(1, "Слова"),
            listOf(buildCard(1, Section.LEARNED, sectionDay = today - 29)),
        )
        val result = act(youngLearned, OpenSection(Section.EPOCH_1))

        assertEquals(NeedNewCard, result.outcome)
        assertEquals(Section.NEW, result.state.deck.activeSection)
        assertFalse(result.state.deck.passStarted)
    }

    @Test
    fun onlyFourthEpochReachedWithinThirtyPasses() {
        val cards = buildEpochRange(4, 1, 3)

        val mature = act(buildDeckState(createDeck(1, "Слова"), cards), OpenSection(Section.EPOCH_1))

        assertEquals(Transition(4, 3, REGULAR), mature.outcome)
        assertTrue(mature.state.deck.passCount[1] <= 30, "pass1 = ${mature.state.deck.passCount[1]}")

        val deckNotMature = createDeck(1, "Слова").copy(lastPassDay = EpochValues<Int?>(null, null, null, today))
        val waiting = act(buildDeckState(deckNotMature, cards), OpenSection(Section.EPOCH_1))

        assertEquals(DoneForToday, waiting.outcome)
        assertEquals(4, waiting.state.deck.waitingEpoch)
    }

    @Test
    fun immatureEpochWaitsAndFirstEpochPlays() {
        val deck = buildPassDeck(1).copy(
            passCount = EpochValues(4, 0, 0, 0),
            lastPassDay = EpochValues<Int?>(null, today, null, null),
        )
        val secondEpochCard = buildCard(50, Section.EPOCH_2)

        // Расписание выбирает эпоху 2, она не созрела, эпоха 1 непустая: ход у эпохи 1.
        val withFirstEpoch = playKnewTimes(buildDeckState(deck, buildEpochRange(1, 1, 2) + secondEpochCard), 2)
        assertEquals(Transition(1, 2, REGULAR), withFirstEpoch.outcome)
        assertEquals(2, withFirstEpoch.state.deck.waitingEpoch)
        assertEquals(1, withFirstEpoch.state.deck.currentEpoch)

        // Эпоха 1 и пул пусты: «На сегодня всё», открыт пул, активной считается ожидающая.
        val idleDeck = createDeck(1, "Слова").copy(
            passCount = EpochValues(4, 0, 0, 0),
            lastPassDay = EpochValues<Int?>(null, today, null, null),
        )
        val idle = act(buildDeckState(idleDeck, listOf(secondEpochCard)), OpenSection(Section.EPOCH_1))
        assertEquals(DoneForToday, idle.outcome)
        assertEquals(2, idle.state.deck.currentEpoch)
        assertEquals(Section.NEW, idle.state.deck.activeSection)
        assertEquals(2, idle.state.deck.waitingEpoch)
        assertFalse(idle.state.deck.passStarted)
        // Пустой проход эпохи 1 засчитан один раз; выбор несозревшей эпохи 2 не плодит лишних проходов.
        assertEquals(EpochValues(5, 0, 0, 0), idle.state.deck.passCount)

        // В тот же день повторное открытие снова даёт «На сегодня всё».
        assertEquals(DoneForToday, act(idle.state, OpenSection(Section.EPOCH_2)).outcome)

        // На следующий день ожидавшая эпоха получает ход.
        val nextDay = act(idle.state, OpenSection(Section.EPOCH_2), day = today + 1)
        assertEquals(Transition(2, 1, REGULAR), nextDay.outcome)
        assertEquals(0, nextDay.state.deck.waitingEpoch)
        assertEquals(2, nextDay.state.deck.currentEpoch)
    }

    @Test
    fun waitingEpochCheckedAfterEachFirstEpochPass() {
        fun buildState(thirdEpochLastPassDay: Int): DeckState = buildDeckState(
            buildPassDeck(1).copy(
                passCount = EpochValues(4, 0, 0, 0),
                waitingEpoch = 3,
                lastPassDay = EpochValues<Int?>(null, null, thirdEpochLastPassDay, null),
            ),
            buildEpochRange(1, 1, 2) + buildCard(50, Section.EPOCH_2) + buildCard(60, Section.EPOCH_3),
        )

        // pass1 станет 5, но правило pass1 % 5 при ожидающей эпохе не действует: ход у созревшей эпохи 3.
        val mature = playKnewTimes(buildState(thirdEpochLastPassDay = today - 5), 2)
        assertEquals(Transition(3, 1, REGULAR), mature.outcome)
        assertEquals(0, mature.state.deck.waitingEpoch)
        assertEquals(5, mature.state.deck.passCount[1])
        assertEquals(Section.EPOCH_2, mature.state.findCard(50).section)

        // Эпоха 3 ещё не созрела: снова играет эпоха 1, эпоха 2 по pass1 % 5 не выбирается.
        val immature = playKnewTimes(buildState(thirdEpochLastPassDay = today), 2)
        assertEquals(Transition(1, 2, REGULAR), immature.outcome)
        assertEquals(3, immature.state.deck.waitingEpoch)
        assertEquals(5, immature.state.deck.passCount[1])
    }

    @Test
    fun demotedThirdEpochCardReturnsAfterOneKnew() {
        val state = buildDeckState(buildPassDeck(3), listOf(buildCard(1, Section.EPOCH_3, successes = 2)))

        val afterFail = playDidNotKnow(state)

        val demoted = afterFail.state.findCard(1)
        assertEquals(Section.EPOCH_2, demoted.section)
        assertEquals(3, demoted.successes)
        assertEquals(Transition(2, 1, REGULAR), afterFail.outcome)

        val afterKnew = playKnew(afterFail.state)
        assertEquals(Section.EPOCH_2, afterKnew.state.findCard(1).section)
        assertEquals(4, afterKnew.state.findCard(1).successes)

        val nextDay = act(afterKnew.state, OpenSection(Section.EPOCH_2), day = today + 1)
        assertEquals(Section.EPOCH_3, nextDay.state.findCard(1).section)
        assertEquals(0, nextDay.state.findCard(1).successes)
    }

    @Test
    fun demotedSecondEpochCardWaitsForNight() {
        val state = buildDeckState(buildPassDeck(2), listOf(buildCard(1, Section.EPOCH_2, successes = 2)))

        val afterFail = playDidNotKnow(state)
        assertEquals(Section.EPOCH_1, afterFail.state.findCard(1).section)
        assertEquals(3, afterFail.state.findCard(1).successes)
        assertEquals(Transition(1, 1, REGULAR), afterFail.outcome)

        // «Знал» в день спуска карточку не выпускает: день последнего «знал» равен дню попадания.
        val sameDay = playKnew(afterFail.state)
        assertEquals(Section.EPOCH_1, sameDay.state.findCard(1).section)
        assertEquals(4, sameDay.state.findCard(1).successes)

        // «Знал» на следующий день выпускает на ближайшем старте прохода первой эпохи.
        val nextDay = playKnew(act(sameDay.state, OpenSection(Section.EPOCH_1), day = today + 1).state, today + 1)
        assertEquals(Section.EPOCH_2, nextDay.state.findCard(1).section)
        assertEquals(0, nextDay.state.findCard(1).successes)
    }

    @Test
    fun demotionIntoFullFirstEpochDoesNotRefill() {
        val cards = buildEpochRange(1, 101, 40) +
            buildCard(1, Section.EPOCH_2, successes = 2) +
            (1L..5L).map { buildCard(200 + it, Section.NEW, position = it.toInt()) }

        val result = playDidNotKnow(buildDeckState(buildPassDeck(2), cards))

        assertEquals(41, result.state.listSectionCards(Section.EPOCH_1).size)
        assertEquals(5, result.state.listSectionCards(Section.NEW).size)
        assertEquals(Transition(1, 41, REGULAR), result.outcome)
    }

    @Test
    fun longFourthEpochPassGoesInPortions() {
        val epoch4 = buildEpochRange(4, 1, 100)
        val epoch1 = buildEpochRange(1, 201, 3)
        var state = buildDeckState(buildPassDeck(4), epoch4 + epoch1)
        // Первая карточка откладывается в первой порции и должна показаться в последней.
        state = act(state, Postpone).state

        val seen = mutableListOf<ReviewOutcome>(ReviewEngine.computeCurrentOutcome(state))
        val statesAfterRating = mutableListOf<DeckState>()
        repeat(105) {
            val result = playKnew(state)
            state = result.state
            statesAfterRating += state
            seen += result.outcome
            // Экран перехода закрыт: показывается первая карточка прохода.
            if (result.outcome is Transition) seen += ReviewEngine.computeCurrentOutcome(state)
        }

        assertEquals(
            listOf<Any>(
                "ShowCard×40", Transition(1, 3, INSERTED),
                "ShowCard×3", Transition(4, 60, RESUMED),
                "ShowCard×40", Transition(1, 3, INSERTED),
                "ShowCard×3", Transition(4, 20, RESUMED),
                "ShowCard×20",
            ),
            summarizeOutcomes(seen),
        )

        val afterFirstPortion = statesAfterRating[39].deck
        assertEquals(4, afterFirstPortion.pausedEpoch)
        assertEquals(40, afterFirstPortion.pausedCursor)
        assertEquals(1, afterFirstPortion.currentEpoch)
        for (index in listOf(42, 85)) {
            val deck = statesAfterRating[index].deck
            assertEquals(EpochValues(0, 0, 0, 0), deck.passCount, "после оценки ${index + 1}")
            assertNull(deck.lastPassDay[4], "после оценки ${index + 1}")
            assertEquals(0, deck.pausedEpoch)
            assertEquals(4, deck.currentEpoch)
            assertEquals(3, deck.tail[1].size, "вставной проход запомнил свои карточки в tail эпохи 1")
        }

        val epoch4Ids = seen.filterIsInstance<ShowCard>().map { it.cardId }.filter { it <= 100 }
        assertEquals((1L..100L).toSet(), epoch4Ids.toSet())
        assertEquals(100, epoch4Ids.size)
        assertEquals(1L, epoch4Ids.last())

        val last = playKnew(state)
        assertEquals(EpochValues(0, 0, 0, 1), last.state.deck.passCount)
        assertEquals(today, last.state.deck.lastPassDay[4])
        assertEquals(Transition(1, 3, REGULAR), last.outcome)
    }

    @Test
    fun learnedCheckOutcome() {
        val state = buildDeckState(
            buildPassDeck(4),
            listOf(
                buildCard(1, Section.EPOCH_4, position = 0, checking = true),
                buildCard(2, Section.EPOCH_4, position = 1, checking = true),
                buildCard(3, Section.EPOCH_4, position = 2),
                buildCard(10, Section.LEARNED, position = 0, sectionDay = 1),
            ),
        )

        val afterKnew = playKnew(state)
        val afterDidNotKnow = playDidNotKnow(afterKnew.state)
        val result = playKnew(afterDidNotKnow.state)

        val knew = result.state.findCard(1)
        assertEquals(Section.LEARNED, knew.section)
        assertEquals(today, knew.sectionDay)
        assertEquals(0, knew.successes)
        assertEquals(1L, result.state.listSectionCards(Section.LEARNED).last().id)

        val didNotKnow = result.state.findCard(2)
        assertEquals(Section.EPOCH_4, didNotKnow.section)
        assertEquals(2, didNotKnow.successes)
        assertFalse(didNotKnow.checking)

        assertEquals(Section.EPOCH_4, result.state.findCard(3).section)
        assertEquals(1, result.state.findCard(3).successes)
    }

    @Test
    fun onlyLearnedReachCheckWithinSeventyEightPasses() {
        val ready = (1L..12L).map { buildCard(it, Section.LEARNED, position = it.toInt(), sectionDay = today - 40) }
        val fresh = (13L..14L).map { buildCard(it, Section.LEARNED, position = it.toInt(), sectionDay = today - 5) }

        val result = act(buildDeckState(createDeck(1, "Слова"), ready + fresh), OpenSection(Section.EPOCH_1))

        assertEquals(Transition(4, 10, REGULAR), result.outcome)
        assertTrue(result.state.deck.passCount[1] <= 78, "pass1 = ${result.state.deck.passCount[1]}")
    }

    @Test
    fun tailRememberedAtPassEnd() {
        val state = buildDeckState(buildPassDeck(2), buildEpochRange(2, 1, 7))

        val result = playKnewTimes(state, 7)

        assertEquals(listOf(3L, 4L, 5L, 6L, 7L), result.state.deck.tail[2])
    }

    @Test
    fun deletingLastUnshownCardFinishesPass() {
        val state = buildDeckState(buildPassDeck(1, passCursor = 2), buildEpochRange(1, 1, 3))

        val result = act(state, DeleteCard(3))

        assertEquals(Transition(1, 2, REGULAR), result.outcome)
        assertEquals(1, result.state.deck.passCount[1])
    }

    @Test
    fun insertSkippedWhenFirstEpochEmptiesOnStart() {
        val releasable = buildCard(201, Section.EPOCH_1, successes = 4, sectionDay = 1, lastSuccessDay = 3)
        val state = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 41) + releasable)

        val result = playKnewTimes(state, 40)

        assertEquals(ShowCard(41), result.outcome)
        assertEquals(0, result.state.deck.pausedEpoch)
        assertEquals(3, result.state.deck.currentEpoch)
        assertEquals(40, result.state.deck.passCursor)
        assertEquals(Section.EPOCH_2, result.state.findCard(201).section)
    }

    @Test
    fun pausedPassWithNothingLeftFinishesOnResume() {
        val state = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 45) + buildEpochRange(1, 201, 2))
        var result = playKnewTimes(state, 40)
        assertEquals(Transition(1, 2, INSERTED), result.outcome)

        // Во время вставки пользователь смотрит прерванную эпоху и удаляет оставшиеся карточки.
        result = act(result.state, OpenSection(Section.EPOCH_3))
        for (id in 41L..45L) result = act(result.state, DeleteCard(id))
        result = act(result.state, OpenSection(Section.EPOCH_1))
        // Порядок вставного прохода перемешан, поэтому первая карточка — одна из двух.
        assertTrue((result.outcome as ShowCard).cardId in setOf(201L, 202L))

        result = playKnewTimes(result.state, 2)

        assertEquals(Transition(1, 2, REGULAR), result.outcome)
        assertEquals(0, result.state.deck.pausedEpoch)
        assertEquals(EpochValues(0, 0, 1, 0), result.state.deck.passCount)
        assertEquals(today, result.state.deck.lastPassDay[3])
    }

    @Test
    fun promotedIntoPausedEpochWaitsForItsPass() {
        val promoted = buildCard(201, Section.EPOCH_1, position = 0, successes = 4, sectionDay = 1, lastSuccessDay = 3)
        val staying = buildCard(202, Section.EPOCH_1, position = 1)
        val state = buildDeckState(buildPassDeck(2), buildEpochRange(2, 1, 45) + promoted + staying)

        var result = playKnewTimes(state, 40)
        assertEquals(Transition(1, 1, INSERTED), result.outcome)
        assertEquals(Section.EPOCH_2, result.state.findCard(201).section)
        assertTrue(result.state.findCard(201).excludedFromPass)

        result = playKnewTimes(result.state, 1)
        assertEquals(Transition(2, 5, RESUMED), result.outcome)
        assertFalse(201L in listPassCards(result.state, 2).map { it.id })

        result = playKnewTimes(result.state, 5)
        assertEquals(1, result.state.deck.passCount[2])

        val nextPass = startPass(result.state, 2, today + 1, Random(1))
        assertTrue(201L in listPassCards(nextPass, 2).map { it.id })
    }

    @Test
    fun openingInactiveSectionDoesNotMovePass() {
        val state = buildDeckState(buildPassDeck(1), buildEpochRange(1, 1, 4) + buildEpochRange(3, 11, 3))
        val afterRating = playKnew(state)
        assertEquals(ShowCard(2), afterRating.outcome)

        var result = act(afterRating.state, OpenSection(Section.EPOCH_3))
        assertEquals(ShowCard(11), result.outcome)
        result = act(result.state, Browse(+1))
        assertEquals(ShowCard(12), result.outcome)
        result = act(result.state, Browse(+1))
        result = act(result.state, Browse(+1))
        assertEquals(ShowCard(11), result.outcome)

        assertEquals(EmptySection, act(result.state, OpenSection(Section.EPOCH_4)).outcome)

        result = act(result.state, OpenSection(Section.EPOCH_1))
        assertEquals(ShowCard(2), result.outcome)
        assertEquals(1, result.state.deck.passCursor)
        assertEquals(EpochValues(0, 0, 0, 0), result.state.deck.passCount)
    }

    @Test
    fun restartShowsCurrentCard() {
        val state = buildDeckState(buildPassDeck(1, passCursor = 1), buildEpochRange(1, 1, 3))

        assertEquals(ShowCard(2), ReviewEngine.computeCurrentOutcome(state))

        val browsingEmpty = state.copy(deck = state.deck.copy(activeSection = Section.EPOCH_4))
        assertEquals(EmptySection, ReviewEngine.computeCurrentOutcome(browsingEmpty))
    }

    @Test
    fun deletingFirstCardOfFreshPassDoesNotInsertFirstEpochPass() {
        val fresh = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 5) + buildEpochRange(1, 201, 2))

        for (action in listOf(DeleteCard(1), ReturnToNew(1))) {
            val result = act(fresh, action)

            assertEquals(ShowCard(2), result.outcome, "$action")
            assertEquals(0, result.state.deck.pausedEpoch)
            assertEquals(3, result.state.deck.currentEpoch)
            assertEquals(0, result.state.deck.passCursor)
        }

        val longPass = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 45) + buildEpochRange(1, 201, 2))
        val afterInsert = playKnewTimes(playKnewTimes(longPass, 40).state, 2)
        assertEquals(Transition(3, 5, RESUMED), afterInsert.outcome)

        for (action in listOf(DeleteCard(41), ReturnToNew(41))) {
            val result = act(afterInsert.state, action)

            assertEquals(ShowCard(42), result.outcome, "$action")
            assertEquals(0, result.state.deck.pausedEpoch)
            assertEquals(3, result.state.deck.currentEpoch)
            assertEquals(40, result.state.deck.passCursor)
        }
    }

    @Test
    fun firstEpochPassIsNotSplitIntoPortions() {
        val state = buildDeckState(buildPassDeck(1), buildEpochRange(1, 1, 45))

        val result = playKnewTimes(state, 40)

        assertEquals(ShowCard(41), result.outcome)
        assertEquals(0, result.state.deck.pausedEpoch)
        assertEquals(1, result.state.deck.currentEpoch)
    }

    @Test
    fun browsingInActivePassIsRejected() {
        val state = buildDeckState(buildPassDeck(1), buildEpochRange(1, 1, 3))

        assertThrows<IllegalArgumentException> { act(state, Browse(+1)) }
    }

    @Test
    fun startChainStopsAfterIterationLimit() {
        val state = buildDeckState(createDeck(1, "Слова"), buildEpochRange(4, 1, 3))

        assertThrows<IllegalStateException> { runStartChain(state, today, Random(1), maxIterations = 10) }
        assertEquals(Transition(4, 3, REGULAR), runStartChain(state, today, Random(1)).outcome)
    }

    @Test
    fun postponeKeepsTheCursorAndShowsTheNextCard() {
        val state = buildDeckState(buildPassDeck(2), buildEpochRange(2, 1, 3))

        val result = act(state, Postpone)

        assertEquals(ShowCard(2), result.outcome)
        assertEquals(0, result.state.deck.passCursor)
    }

    /** Сворачивает подряд идущие `ShowCard` в «ShowCard×N»; переходы остаются как есть. */
    private fun summarizeOutcomes(outcomes: List<ReviewOutcome>): List<Any> {
        val summary = mutableListOf<Any>()
        var cardRun = 0
        for (outcome in outcomes) {
            if (outcome is ShowCard) {
                cardRun++
                continue
            }
            if (cardRun > 0) summary += "ShowCard×$cardRun"
            cardRun = 0
            summary += outcome
        }
        if (cardRun > 0) summary += "ShowCard×$cardRun"
        return summary
    }
}
