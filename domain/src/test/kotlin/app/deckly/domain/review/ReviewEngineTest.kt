package app.deckly.domain.review

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeck
import app.deckly.domain.buildDeckState
import app.deckly.domain.buildEpochCards
import app.deckly.domain.buildEpochRange
import app.deckly.domain.buildPassDeck
import app.deckly.domain.findCard
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.createDeck
import app.deckly.domain.model.findEpochSection
import app.deckly.domain.model.listSectionCards
import app.deckly.domain.review.Availability.Available
import app.deckly.domain.review.Availability.Unavailable
import app.deckly.domain.review.CardAction.DID_NOT_KNOW
import app.deckly.domain.review.CardAction.KNEW
import app.deckly.domain.review.ReviewAction.Browse
import app.deckly.domain.review.ReviewAction.DeleteCard
import app.deckly.domain.review.ReviewAction.DidNotKnow
import app.deckly.domain.review.ReviewAction.Flip
import app.deckly.domain.review.ReviewAction.Knew
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
import app.deckly.domain.review.UnavailableReason.FRONT_VISIBLE
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
        val revealed = act(state, ReviewAction.Flip, day)
        return act(revealed.state, ReviewAction.Knew, day)
    }

    private fun playDidNotKnow(state: DeckState, day: Int = today): ReviewResult {
        val revealed = act(state, ReviewAction.Flip, day)
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
    fun demotedCardKnewOnDemotionDayRestsThenReleases() {
        val state = buildDeckState(buildPassDeck(2), listOf(buildCard(1, Section.EPOCH_2, successes = 2)))

        val afterFail = playDidNotKnow(state)
        assertEquals(Section.EPOCH_1, afterFail.state.findCard(1).section)
        assertEquals(3, afterFail.state.findCard(1).successes)
        assertEquals(Transition(1, 1, REGULAR), afterFail.outcome)

        // «Знал» в день спуска: четвёртый успех получен в день попадания, карточка уходит в ожидание.
        val sameDay = playKnew(afterFail.state)
        assertEquals(Section.RESTING, sameDay.state.findCard(1).section)
        assertEquals(0, sameDay.state.findCard(1).successes)
        assertEquals(DoneForToday, sameDay.outcome)

        // Первый старт прохода эпохи 1 на следующий день выпускает её без нового «знал».
        val nextDay = act(sameDay.state, OpenSection(Section.EPOCH_1), day = today + 1)
        assertEquals(Section.EPOCH_2, nextDay.state.findCard(1).section)
        assertEquals(0, nextDay.state.findCard(1).successes)
        assertEquals(today + 1, nextDay.state.findCard(1).sectionDay)
        assertEquals(Transition(2, 1, REGULAR), nextDay.outcome)
    }

    @Test
    fun demotedCardKnewNextDayIsReleasedAtNextStartSameDay() {
        val afterFail = playDidNotKnow(buildDeckState(buildPassDeck(2), listOf(buildCard(1, Section.EPOCH_2, successes = 2))))

        val result = playKnew(afterFail.state, today + 1)

        assertEquals(Section.EPOCH_2, result.state.findCard(1).section)
        assertEquals(0, result.state.findCard(1).successes)
        assertEquals(Transition(2, 1, REGULAR), result.outcome)
    }

    @Test
    fun thresholdOnEntryDayMovesCardToRestingAtPassEnd() {
        val state = buildDeckState(
            buildPassDeck(1),
            listOf(buildCard(1, Section.EPOCH_1, successes = 3, sectionDay = today)),
        )

        val result = playKnew(state)

        assertEquals(DoneForToday, result.outcome)
        assertEquals(Section.RESTING, result.state.findCard(1).section)
        assertEquals(0, result.state.findCard(1).successes)
        assertEquals(emptyList<Long>(), result.state.listSectionCards(Section.EPOCH_1).map { it.id })
        assertEquals(emptyList<Long>(), result.state.listSectionCards(Section.EPOCH_2).map { it.id })
        assertEquals(1, result.state.deck.passCount[1])
    }

    @Test
    fun countersNeverExceedThresholdOnEntryDay() {
        // Сценарий владельца: четыре новые карточки, по кругу «Знал» на всех.
        val pool = (1L..4L).map { buildCard(it, Section.NEW, position = it.toInt()) }
        var result = act(buildDeckState(createDeck(1, "Слова"), pool), OpenSection(Section.EPOCH_1))
        var highestSuccesses = 0
        var ratings = 0

        while ((result.outcome is Transition || result.outcome is ShowCard) && ratings < 40) {
            result = playKnew(result.state)
            ratings++
            highestSuccesses = maxOf(highestSuccesses, result.state.cards.maxOf { it.successes })
        }

        assertEquals(16, ratings)
        assertEquals(4, highestSuccesses)
        assertEquals(DoneForToday, result.outcome)
        assertTrue(result.state.cards.all { it.section == Section.RESTING })
    }

    @Test
    fun restingCardWaitsForNightAcrossRepeatedOpenings() {
        val state = buildDeckState(buildPassDeck(1), listOf(buildCard(1, Section.EPOCH_1, successes = 3, sectionDay = today)))
        var result = playKnew(state)

        repeat(3) {
            result = act(result.state, OpenSection(Section.EPOCH_1))
            assertEquals(DoneForToday, result.outcome)
            assertEquals(Section.RESTING, result.state.findCard(1).section)
        }

        assertEquals(1, result.state.deck.passCount[1])
    }

    @Test
    fun thresholdAfterNightIsReleasedAtNextStartSameDay() {
        // Три «знал» в день попадания, четвёртое на следующий день: ожидания нет.
        val state = buildDeckState(
            buildPassDeck(1),
            listOf(buildCard(1, Section.EPOCH_1, successes = 3, sectionDay = today)),
        )

        val result = playKnew(state, today + 1)

        assertEquals(Transition(2, 1, REGULAR), result.outcome)
        val released = result.state.findCard(1)
        assertEquals(Section.EPOCH_2, released.section)
        assertEquals(0, released.successes)
        assertEquals(today + 1, released.sectionDay)
    }

    @Test
    fun passFinishedNextDayDoesNotRest() {
        val state = buildDeckState(
            buildPassDeck(1),
            listOf(
                buildCard(1, Section.EPOCH_1, position = 0, successes = 3, sectionDay = today),
                buildCard(2, Section.EPOCH_1, position = 1, sectionDay = today),
            ),
        )

        // Четвёртый успех получен в день попадания, но проход не закончен.
        val afterFirst = playKnew(state)
        assertEquals(ShowCard(2), afterFirst.outcome)
        assertEquals(Section.EPOCH_1, afterFirst.state.findCard(1).section)
        assertEquals(4, afterFirst.state.findCard(1).successes)

        // Проход закончился после ночи: ожидания нет, выпуск на ближайшем старте.
        val afterSecond = playKnew(afterFirst.state, today + 1)

        assertEquals(Section.EPOCH_2, afterSecond.state.findCard(1).section)
        assertTrue(afterSecond.state.cards.none { it.section == Section.RESTING })
        assertEquals(Transition(1, 1, REGULAR), afterSecond.outcome)
    }

    @Test
    fun legacyCardOverThresholdLeavesEpochByNewRule() {
        // Карточка «5/4» из прошлой сборки, колода открывается без миграции.
        val legacy = buildCard(1, Section.EPOCH_1, position = 0, successes = 5, sectionDay = today)
        val other = buildCard(2, Section.EPOCH_1, position = 1)

        val midPass = buildDeckState(buildPassDeck(1, passCursor = 1), listOf(legacy, other))
        val sameDay = playKnew(midPass)
        assertEquals(Section.RESTING, sameDay.state.findCard(1).section)
        assertEquals(Transition(1, 1, REGULAR), sameDay.outcome)

        val atStart = buildDeckState(buildDeck(currentEpoch = 1), listOf(legacy, other))
        val nextDay = act(atStart, OpenSection(Section.EPOCH_1), day = today + 1)
        assertEquals(Section.EPOCH_2, nextDay.state.findCard(1).section)
        assertEquals(Transition(1, 1, REGULAR), nextDay.outcome)
    }

    @Test
    fun onlyRestingCardsGiveDoneForToday() {
        val resting = buildDeckState(createDeck(1, "Слова"), listOf(buildCard(1, Section.RESTING, sectionDay = today)))

        for (epoch in 1..4) {
            val state = resting.copy(deck = resting.deck.copy(currentEpoch = epoch))
            val section = findEpochSection(epoch)

            val first = act(state, OpenSection(section))

            assertEquals(DoneForToday, first.outcome, "эпоха $epoch")
            assertEquals(Section.NEW, first.state.deck.activeSection, "эпоха $epoch")
            assertFalse(first.state.deck.passStarted, "эпоха $epoch")
            assertEquals(EpochValues(0, 0, 0, 0), first.state.deck.passCount, "эпоха $epoch")
            assertEquals(first, act(first.state, OpenSection(section)), "повторное открытие, эпоха $epoch")
        }
    }

    @Test
    fun restingCardsWithYoungLearnedStillGiveDoneForToday() {
        val state = buildDeckState(
            createDeck(1, "Слова"),
            listOf(
                buildCard(1, Section.RESTING, sectionDay = today),
                buildCard(2, Section.LEARNED, sectionDay = today - 29),
            ),
        )

        assertEquals(DoneForToday, act(state, OpenSection(Section.EPOCH_1)).outcome)
    }

    @Test
    fun dueRestingCardsReachSecondEpochThroughChain() {
        val state = buildDeckState(createDeck(1, "Слова"), listOf(buildCard(1, Section.RESTING, sectionDay = today - 1)))

        val result = act(state, OpenSection(Section.EPOCH_1))

        assertEquals(Transition(2, 1, REGULAR), result.outcome)
        assertEquals(Section.EPOCH_2, result.state.findCard(1).section)
        assertEquals(5, result.state.deck.passCount[1])
    }

    @Test
    fun dueRestingCardsAreReleasedWhileWaitingEpochIsImmature() {
        val deck = createDeck(1, "Слова").copy(
            passCount = EpochValues(4, 0, 0, 0),
            waitingEpoch = 3,
            lastPassDay = EpochValues<Int?>(null, null, today, null),
        )
        val state = buildDeckState(
            deck,
            listOf(buildCard(50, Section.EPOCH_3), buildCard(1, Section.RESTING, sectionDay = today - 1)),
        )

        val result = act(state, OpenSection(Section.EPOCH_1))

        assertEquals(DoneForToday, result.outcome)
        assertEquals(Section.EPOCH_2, result.state.findCard(1).section)
        assertEquals(3, result.state.deck.waitingEpoch)
    }

    @Test
    fun restingCardsWithoutDueDoNotTriggerInsertedPass() {
        val resting = (201L..202L).map { buildCard(it, Section.RESTING, sectionDay = today) }
        val deck = buildPassDeck(3).copy(tail = EpochValues(listOf(901L, 902L), emptyList(), emptyList(), emptyList()))
        val state = buildDeckState(deck, buildEpochRange(3, 1, 45) + resting)

        val result = playKnewTimes(state, 40)

        assertEquals(ShowCard(41), result.outcome)
        assertEquals(0, result.state.deck.pausedEpoch)
        assertEquals(0, result.state.deck.portionRatedCount)
        assertEquals(40, result.state.deck.passCursor)
        // Пустой вставной проход, даже пропущенный молча, стёр бы tail эпохи 1: здесь он не стартует вовсе.
        assertEquals(listOf(901L, 902L), result.state.deck.tail[1])
        assertEquals(setOf(Section.RESTING), result.state.cards.filter { it.id > 200 }.map { it.section }.toSet())
    }

    @Test
    fun dueRestingCardsAreReleasedByInsertedPassCheck() {
        val resting = (201L..202L).map { buildCard(it, Section.RESTING, sectionDay = today - 1) }
        val state = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 45) + resting)

        val result = playKnewTimes(state, 40)

        // Вставной проход пуст и пропущен молча, но ждущие карточки он выпустил.
        assertEquals(ShowCard(41), result.outcome)
        assertEquals(0, result.state.deck.pausedEpoch)
        assertEquals(setOf(Section.EPOCH_2), result.state.cards.filter { it.id > 200 }.map { it.section }.toSet())
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
        val releasable = buildCard(201, Section.EPOCH_1, successes = 4, sectionDay = 1)
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
        val promoted = buildCard(201, Section.EPOCH_1, position = 0, successes = 4, sectionDay = 1)
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

        // Удаление показывает следующую карточку прохода, возврат открывает пул на возвращённой.
        for ((action, expected) in listOf(DeleteCard(1) to ShowCard(2), ReturnToNew(1) to ShowCard(1))) {
            val result = act(fresh, action)

            assertEquals(expected, result.outcome, "$action")
            assertEquals(0, result.state.deck.pausedEpoch)
            assertEquals(3, result.state.deck.currentEpoch)
            assertEquals(0, result.state.deck.passCursor)
            // Вставного прохода нет, и проход эпохи 3 продолжается с карточки 2.
            assertEquals(ShowCard(2), act(result.state, OpenSection(Section.EPOCH_3)).outcome, "$action")
        }

        val longPass = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 45) + buildEpochRange(1, 201, 2))
        val afterInsert = playKnewTimes(playKnewTimes(longPass, 40).state, 2)
        assertEquals(Transition(3, 5, RESUMED), afterInsert.outcome)

        for ((action, expected) in listOf(DeleteCard(41) to ShowCard(42), ReturnToNew(41) to ShowCard(41))) {
            val result = act(afterInsert.state, action)

            assertEquals(expected, result.outcome, "$action")
            assertEquals(0, result.state.deck.pausedEpoch)
            assertEquals(3, result.state.deck.currentEpoch)
            assertEquals(40, result.state.deck.passCursor)
            assertEquals(ShowCard(42), act(result.state, OpenSection(Section.EPOCH_3)).outcome, "$action")
        }
    }

    @Test
    fun returnOfOnlyCardKeepsItInPool() {
        // Сценарий владельца: в колоде одна карточка, она в эпохе 1.
        val state = buildDeckState(buildPassDeck(1), listOf(buildCard(1, Section.EPOCH_1)))

        val result = act(state, ReturnToNew(1))

        assertEquals(ShowCard(1), result.outcome)
        assertEquals(Section.NEW, result.state.findCard(1).section)
        assertEquals(Section.NEW, result.state.deck.activeSection)
        assertFalse(result.state.deck.passStarted)
        assertEquals(1, result.state.deck.passCount[1])

        // Проход завершён, но следующий не стартовал: добор забирает карточку, только когда владелец откроет эпоху.
        val opened = act(result.state, OpenSection(Section.EPOCH_1))
        assertEquals(Transition(1, 1, REGULAR), opened.outcome)
        assertEquals(Section.EPOCH_1, opened.state.findCard(1).section)
        assertEquals(1, opened.state.deck.passCount[1])
    }

    @Test
    fun returnOpensPoolOnReturnedCard() {
        val deck = buildPassDeck(1, passCursor = 1, visibleSide = Side.BACK)
        val cards = buildEpochRange(1, 1, 3).map { if (it.id == 2L) it.copy(successes = 3, answerRevealed = true) else it } +
            (101L..102L).map { buildCard(it, Section.NEW, position = (it - 101).toInt()) }

        val result = act(buildDeckState(deck, cards), ReturnToNew(2))

        assertEquals(ShowCard(2), result.outcome)
        assertEquals(listOf(101L, 102L, 2L), result.state.listSectionCards(Section.NEW).map { it.id })
        assertEquals(0, result.state.findCard(2).successes)
        assertEquals(Section.NEW, result.state.deck.activeSection)
        assertEquals(2, result.state.deck.browseCursor)
        assertEquals(Side.FRONT, result.state.deck.visibleSide)
    }

    @Test
    fun returnInMiddleOfPassKeepsPassPosition() {
        val state = buildDeckState(buildPassDeck(2, passCursor = 2), buildEpochRange(2, 1, 5))

        val returned = act(state, ReturnToNew(3))

        assertEquals(ShowCard(3), returned.outcome)
        assertTrue(returned.state.deck.passStarted)
        assertEquals(2, returned.state.deck.passCursor)
        assertEquals(EpochValues(0, 0, 0, 0), returned.state.deck.passCount)

        // Активная эпоха открывается без экрана перехода, со следующей карточки.
        val reopened = act(returned.state, OpenSection(Section.EPOCH_2))
        assertEquals(ShowCard(4), reopened.outcome)
        assertEquals(2, reopened.state.deck.passCursor)
    }

    @Test
    fun returnFromInactiveEpochOpensPool() {
        val deck = buildPassDeck(1, passCursor = 1).copy(activeSection = Section.EPOCH_3)
        val state = buildDeckState(deck, buildEpochRange(1, 1, 4) + buildEpochRange(3, 11, 3))

        val result = act(state, ReturnToNew(12))

        assertEquals(ShowCard(12), result.outcome)
        assertEquals(Section.NEW, result.state.deck.activeSection)
        // Проход активной эпохи не тронут.
        assertTrue(result.state.deck.passStarted)
        assertEquals(1, result.state.deck.currentEpoch)
        assertEquals(1, result.state.deck.passCursor)
        assertEquals(EpochValues(0, 0, 0, 0), result.state.deck.passCount)
        assertEquals(ShowCard(2), act(result.state, OpenSection(Section.EPOCH_1)).outcome)
    }

    @Test
    fun returnEndingSecondEpochPassFinishesIt() {
        val cards = listOf(
            buildCard(1, Section.EPOCH_2, position = 0, successes = 2, failed = true),
            buildCard(2, Section.EPOCH_2, position = 1),
        )

        val result = act(buildDeckState(buildPassDeck(2, passCursor = 1), cards), ReturnToNew(2))

        assertEquals(ShowCard(2), result.outcome)
        val after = result.state
        assertEquals(Section.EPOCH_1, after.findCard(1).section)
        assertEquals(3, after.findCard(1).successes)
        assertEquals(today, after.deck.lastPassDay[2])
        assertEquals(1, after.deck.passCount[2])
        // Следующая эпоха выбрана, но не начата.
        assertEquals(1, after.deck.currentEpoch)
        assertFalse(after.deck.passStarted)
        assertEquals(Section.NEW, after.deck.activeSection)
        assertEquals(Section.NEW, after.findCard(2).section)
    }

    @Test
    fun returnEndingInsertedPassResumesPausedPassOnOpen() {
        val state = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 45) + buildCard(201, Section.EPOCH_1))
        var result = playKnewTimes(state, 40)
        assertEquals(Transition(1, 1, INSERTED), result.outcome)

        result = act(result.state, ReturnToNew(201))

        assertEquals(ShowCard(201), result.outcome)
        val deck = result.state.deck
        assertEquals(Section.NEW, deck.activeSection)
        assertEquals(3, deck.currentEpoch)
        assertEquals(0, deck.pausedEpoch)
        assertTrue(deck.passStarted)
        assertEquals(40, deck.passCursor)
        assertEquals(0, deck.passCount[1])

        // Прерванная эпоха продолжается при открытии: без экрана перехода, со следующей карточки.
        val reopened = act(result.state, OpenSection(Section.EPOCH_3))
        assertEquals(ShowCard(41), reopened.outcome)
        assertEquals(0, reopened.state.deck.passCount[1])
    }

    @Test
    fun returnEndingInsertedPassFinishesItsFirstEpochPass() {
        // Вставной проход: карточка 201 набрала порог в день попадания, карточку 202 владелец возвращает в пул.
        val deck = buildPassDeck(1, passCursor = 1).copy(pausedEpoch = 3, pausedCursor = 40)
        val cards = buildEpochRange(3, 1, 45) +
            buildCard(201, Section.EPOCH_1, position = 0, successes = 4, sectionDay = today) +
            buildCard(202, Section.EPOCH_1, position = 1)

        val result = act(buildDeckState(deck, cards), ReturnToNew(202))

        assertEquals(ShowCard(202), result.outcome)
        // Конец вставного прохода — это `finishPass` эпохи 1: ожидание ночи и `tail`, как и при оценке «знал».
        assertEquals(Section.RESTING, result.state.findCard(201).section)
        assertEquals(listOf(201L), result.state.deck.tail[1])
        assertEquals(0, result.state.deck.pausedEpoch)
        assertEquals(3, result.state.deck.currentEpoch)
        assertEquals(40, result.state.deck.passCursor)
        assertEquals(EpochValues(0, 0, 0, 0), result.state.deck.passCount)
    }

    @Test
    fun returnEndingInsertedPassCompletesExhaustedPausedPass() {
        val state = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 45) + buildCard(201, Section.EPOCH_1))
        var result = playKnewTimes(state, 40)
        // Во время вставки владелец убирает все оставшиеся карточки прерванной эпохи.
        result = act(result.state, OpenSection(Section.EPOCH_3))
        for (id in 41L..45L) result = act(result.state, DeleteCard(id))
        result = act(result.state, OpenSection(Section.EPOCH_1))

        result = act(result.state, ReturnToNew(201))

        assertEquals(ShowCard(201), result.outcome)
        val deck = result.state.deck
        assertEquals(0, deck.pausedEpoch)
        assertFalse(deck.passStarted)
        assertEquals(1, deck.passCount[3])
        assertEquals(0, deck.passCount[1])
        assertEquals(today, deck.lastPassDay[3])
        assertEquals(Section.NEW, deck.activeSection)
        assertEquals(Section.NEW, result.state.findCard(201).section)
    }

    @Test
    fun deletingRatedCardDuringInsertKeepsPortionOfForty() {
        checkPortionOfFortySurvivesRemovalDuringInsert(::DeleteCard, expectedInsertSize = 2)
    }

    @Test
    fun returningRatedCardToNewDuringInsertKeepsPortionOfForty() {
        // Возвращённая карточка лежит в пуле, и следующий вставной проход подтянет её в эпоху 1.
        checkPortionOfFortySurvivesRemovalDuringInsert(::ReturnToNew, expectedInsertSize = 3)
    }

    @Test
    fun emptyInsertStartsNextPortionOfForty() {
        // Эпоха 1 и пул пусты: на границе 40 вставки нет, следующая порция идёт сразу.
        val state = buildDeckState(buildPassDeck(4), buildEpochRange(4, 1, 100))
        var result = ReviewResult(state, ReviewEngine.computeCurrentOutcome(state))

        for (rating in 1..79) {
            // После 50-й оценки в пуле появилась карточка (например, владелец дописал её).
            if (rating == 51) {
                result = result.copy(state = result.state.copy(cards = result.state.cards + buildCard(201, Section.NEW)))
            }
            result = playKnew(result.state)
            assertTrue(result.outcome is ShowCard, "оценка $rating: ${result.outcome}")
        }
        result = playKnew(result.state)

        assertEquals(Transition(1, 1, INSERTED), result.outcome)
    }

    @Test
    fun onlyRatingsOfEpochsTwoToFourCountTowardPortion() {
        val third = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 5))
        val thirdRated = playDidNotKnow(playKnew(third).state)
        assertEquals(2, thirdRated.state.deck.portionRatedCount)

        val first = buildDeckState(buildPassDeck(1), buildEpochRange(1, 1, 5))
        assertEquals(0, playKnewTimes(first, 2).state.deck.portionRatedCount)
    }

    @Test
    fun newPassStartsWithEmptyPortion() {
        val deck = buildDeck(currentEpoch = 4, portionRatedCount = 17)
        val state = buildDeckState(deck, buildEpochRange(4, 1, 3))

        val result = act(state, OpenSection(Section.EPOCH_4))

        assertEquals(Transition(4, 3, REGULAR), result.outcome)
        assertEquals(0, result.state.deck.portionRatedCount)
    }

    /**
     * Во время вставки владелец открывает прерванную эпоху 4 и убирает уже оценённую карточку 1
     * ([removal]). Порция в 40 оценок от этого не сдвигается: следующая вставка приходит на 40-й
     * оценке после возврата, а не раньше.
     */
    private fun checkPortionOfFortySurvivesRemovalDuringInsert(
        removal: (Long) -> ReviewAction,
        expectedInsertSize: Int,
    ) {
        val state = buildDeckState(buildPassDeck(4), buildEpochRange(4, 1, 100) + buildEpochRange(1, 201, 2))
        var result = playKnewTimes(state, 40)
        assertEquals(Transition(1, 2, INSERTED), result.outcome)

        result = act(result.state, OpenSection(Section.EPOCH_4))
        result = act(result.state, removal(1))
        result = act(result.state, OpenSection(Section.EPOCH_1))
        result = playKnewTimes(result.state, 2)
        assertEquals(Transition(4, 60, RESUMED), result.outcome)

        repeat(39) {
            result = playKnew(result.state)
            assertTrue(result.outcome is ShowCard, "оценка ${it + 1} после возврата: ${result.outcome}")
        }
        result = playKnew(result.state)

        assertEquals(Transition(1, expectedInsertSize, INSERTED), result.outcome)
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

    @Test
    fun flipShowsBackAndRevealsAnswer() {
        val state = buildDeckState(buildPassDeck(2), buildEpochCards(2, 1, 2, 3))

        val result = act(state, Flip)

        assertEquals(Side.BACK, result.state.deck.visibleSide)
        assertEquals(listOf(true, false, false), listOf(1L, 2L, 3L).map { result.state.findCard(it).answerRevealed })
        assertEquals(ShowCard(1), result.outcome)
    }

    @Test
    fun flippingBackToFrontBlocksRating() {
        val state = buildDeckState(buildPassDeck(2), buildEpochCards(2, 1, 2, 3))
        val onBack = act(state, Flip).state
        assertEquals(Available, computeCardActions(onBack)[KNEW])
        assertEquals(Available, computeCardActions(onBack)[DID_NOT_KNOW])

        val onFront = act(onBack, Flip).state

        assertEquals(Side.FRONT, onFront.deck.visibleSide)
        assertEquals(Unavailable(FRONT_VISIBLE), computeCardActions(onFront)[KNEW])
        assertEquals(Unavailable(FRONT_VISIBLE), computeCardActions(onFront)[DID_NOT_KNOW])
        assertThrows<IllegalArgumentException> { act(onFront, Knew) }
        assertThrows<IllegalArgumentException> { act(onFront, DidNotKnow) }
        // Ответ уже открывали, поэтому перенос остаётся закрытым.
        assertTrue(onFront.findCard(1).answerRevealed)
    }

    @Test
    fun nextCardStartsOnFront() {
        val pass = buildDeckState(buildPassDeck(2), buildEpochCards(2, 1, 2, 3))
        val browsing = buildDeckState(buildDeck(activeSection = Section.EPOCH_3), buildEpochCards(3, 11, 12))
        // Перенос доступен только на лице; сторону BACK задаём руками, чтобы проверить сброс.
        val backWithoutReveal = pass.copy(deck = pass.deck.copy(visibleSide = Side.BACK))
        val scenarios = listOf<Triple<String, DeckState, ReviewAction>>(
            Triple("Knew", act(pass, Flip).state, Knew),
            Triple("DidNotKnow", act(pass, Flip).state, DidNotKnow),
            Triple("Postpone", backWithoutReveal, Postpone),
            Triple("Browse", act(browsing, Flip).state, Browse(+1)),
            Triple("OpenSection", act(pass, Flip).state, OpenSection(Section.EPOCH_3)),
            Triple("DeleteCard", act(pass, Flip).state, DeleteCard(2)),
            Triple("ReturnToNew", act(pass, Flip).state, ReturnToNew(2)),
        )

        for ((name, state, action) in scenarios) {
            assertEquals(Side.BACK, state.deck.visibleSide, "$name: до действия")

            val result = act(state, action)

            assertEquals(Side.FRONT, result.state.deck.visibleSide, "$name: после действия")
        }
    }

    @Test
    fun transitionsAndResumeStartOnFront() {
        val state = buildDeckState(buildPassDeck(3), buildEpochRange(3, 1, 45) + buildEpochRange(1, 201, 2))
        var result = ReviewResult(state, ReviewEngine.computeCurrentOutcome(state))
        val kinds = mutableListOf<TransitionKind>()

        repeat(42) {
            val flipped = act(result.state, Flip)
            assertEquals(Side.BACK, flipped.state.deck.visibleSide)
            result = act(flipped.state, Knew)
            (result.outcome as? Transition)?.let { kinds += it.kind }
            assertEquals(Side.FRONT, result.state.deck.visibleSide, "после оценки ${it + 1}: ${result.outcome}")
        }

        assertEquals(listOf(INSERTED, RESUMED), kinds)
    }

    @Test
    fun flipInInactiveSectionDoesNotRevealAnswer() {
        val browsing = buildDeckState(
            buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1, passStarted = true),
            buildEpochCards(3, 1, 2),
        )
        val notStarted = buildDeckState(buildPassDeck(2).copy(passStarted = false), buildEpochCards(2, 1, 2))

        for (state in listOf(browsing, notStarted)) {
            val result = act(state, Flip)

            assertEquals(Side.BACK, result.state.deck.visibleSide)
            assertEquals(state.cards, result.state.cards)
        }
    }

    @Test
    fun flipWithoutCurrentCardChangesNothing() {
        val state = buildDeckState(buildDeck(activeSection = Section.EPOCH_3))

        val result = act(state, Flip)

        assertEquals(state, result.state)
        assertEquals(EmptySection, result.outcome)
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

    // --- фокус на карточке при открытии секции -------------------------------------------------

    /** Пул: id 10, 20, 30 стоят на позициях 2, 0, 1, поэтому порядок секции — 20, 30, 10. */
    private fun buildShuffledPool(browseCursor: Int = 0): DeckState {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1, browseCursor = browseCursor)
        return buildDeckState(
            deck,
            listOf(
                buildCard(10, Section.NEW, position = 2),
                buildCard(20, Section.NEW, position = 0),
                buildCard(30, Section.NEW, position = 1),
            ),
        )
    }

    @Test
    fun openSectionFocusesGivenCard() {
        val result = act(buildShuffledPool(), OpenSection(Section.NEW, focusCardId = 10))

        // Индекс считается по порядку секции, а не по id и не по списку карточек колоды.
        assertEquals(2, result.state.deck.browseCursor)
        assertEquals(ShowCard(10), result.outcome)
        assertEquals(Section.NEW, result.state.deck.activeSection)
    }

    @Test
    fun openSectionWithoutFocusStartsFromFirstCard() {
        val result = act(buildShuffledPool(browseCursor = 2), OpenSection(Section.NEW))

        assertEquals(0, result.state.deck.browseCursor)
        assertEquals(ShowCard(20), result.outcome)
    }

    @Test
    fun focusOnMissingCardFallsBackToFirst() {
        val missing = act(buildShuffledPool(browseCursor = 2), OpenSection(Section.NEW, focusCardId = 999))
        // Карточка есть в колоде, но в другой секции: в этой её нет.
        val elsewhere = act(buildShuffledPool(browseCursor = 2), OpenSection(Section.EPOCH_4, focusCardId = 10))

        assertEquals(0, missing.state.deck.browseCursor)
        assertEquals(ShowCard(20), missing.outcome)
        assertEquals(0, elsewhere.state.deck.browseCursor)
        assertEquals(EmptySection, elsewhere.outcome)
    }

    @Test
    fun focusIgnoredForActivePass() {
        // Начатый проход эпохи 1, курсор на третьей карточке: просмотр здесь не листается, фокус не нужен.
        val state = buildDeckState(buildPassDeck(1, passCursor = 2), buildEpochRange(1, 1, 5))

        val result = act(state, OpenSection(Section.EPOCH_1, focusCardId = 5))

        assertEquals(ShowCard(3), result.outcome)
        assertEquals(2, result.state.deck.passCursor)
        assertEquals(0, result.state.deck.browseCursor)
    }
}
