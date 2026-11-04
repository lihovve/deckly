package app.deckly.domain.review

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeckState
import app.deckly.domain.findCard
import app.deckly.domain.model.Section
import app.deckly.domain.model.createDeck
import app.deckly.domain.model.findEpochSection
import app.deckly.domain.review.ReviewOutcome.ShowCard
import app.deckly.domain.review.ReviewOutcome.Transition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ReviewSimulationTest {

    private val maxActionsPerDay = 1000

    @Test
    fun cardReachesLearnedNotBeforeDay38() {
        var state = buildDeckState(createDeck(1, "Колода"), listOf(buildCard(1, Section.NEW)))
        val random = Random(1)
        val firstDayInSection = linkedMapOf<Section, Int>()

        fun observeCard(day: Int) {
            firstDayInSection.putIfAbsent(state.findCard(1).section, day)
        }

        for (day in 0..60) {
            val currentSection = findEpochSection(state.deck.currentEpoch)
            var result = ReviewEngine.applyAction(state, ReviewAction.OpenSection(currentSection), day, random)
            state = result.state
            observeCard(day)
            var actions = 0
            while ((result.outcome is Transition || result.outcome is ShowCard) && actions < maxActionsPerDay) {
                state = ReviewEngine.applyAction(state, ReviewAction.Flip, day, random).state
                result = ReviewEngine.applyAction(state, ReviewAction.Knew, day, random)
                state = result.state
                observeCard(day)
                actions += 2
            }
        }

        // Карточка не покидает эпоху 1 в день попадания и попадает в стек выученных не раньше дня 38.
        assertTrue(firstDayInSection.getValue(Section.EPOCH_2) > firstDayInSection.getValue(Section.EPOCH_1))
        assertEquals(38, firstDayInSection[Section.LEARNED])
        // Дни первого появления в секции (пул новых не виден: первое открытие в день 0 добирает карточку в эпоху 1).
        // Эпоха 2 — на следующий день; 4 прохода по одному в день дают выпуск в день 5; 4 прохода эпохи 3 через 3 дня —
        // выпуск в день 17; 3 прохода эпохи 4 через 7 дней — стек выученных в день 38.
        assertEquals(
            mapOf(
                Section.EPOCH_1 to 0,
                Section.EPOCH_2 to 1,
                Section.EPOCH_3 to 5,
                Section.EPOCH_4 to 17,
                Section.LEARNED to 38,
            ),
            firstDayInSection,
        )
    }
}
