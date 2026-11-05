package app.deckly.data.repository

import app.deckly.data.SCENARIO_DAY
import app.deckly.data.Scenario
import app.deckly.data.TestRepositories
import app.deckly.data.assertSuspendThrows
import app.deckly.data.buildEpochCard
import app.deckly.data.buildFullDeck
import app.deckly.data.buildInterruptedPassScenario
import app.deckly.data.buildPlainCard
import app.deckly.data.buildSeedContentFile
import app.deckly.data.buildWaitingEpochScenario
import app.deckly.data.createTestStorage
import app.deckly.data.hasStrokeFile
import app.deckly.data.listStrokeFileNames
import app.deckly.data.seedDeck
import app.deckly.data.seedDraft
import app.deckly.data.sortCardsById
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.collectDeckChanges
import app.deckly.domain.model.createDeck
import app.deckly.domain.port.DeckSummary
import app.deckly.domain.review.ReviewAction
import app.deckly.domain.review.ReviewEngine
import app.deckly.domain.review.finishPass
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomDeckRepositoryTest {

    private val repos = TestRepositories(createTestStorage())

    @After
    fun closeRepositories() {
        repos.close()
    }

    @Test
    fun createdDecksAreListedInCreationOrder() = runTest {
        val second = repos.decks.createDeck("Б")
        val first = repos.decks.createDeck("А")

        assertTrue(second.id < first.id)
        assertEquals(
            listOf(DeckSummary(second.id, "Б"), DeckSummary(first.id, "А")),
            repos.decks.listDecks(),
        )
    }

    @Test
    fun createdDeckStartsInInitialState() = runTest {
        val deck = repos.decks.createDeck("Новая")

        assertEquals(createDeck(deck.id, "Новая"), deck)
        assertEquals(DeckState(deck, emptyList()), repos.decks.loadDeckState(deck.id))
    }

    @Test
    fun renameChangesOnlyName() = runTest {
        val state = DeckState(buildFullDeck(), listOf(buildPlainCard(1)))
        seedDeck(repos, state)

        repos.decks.renameDeck(state.deck.id, "Новое имя")

        assertEquals(
            state.copy(deck = state.deck.copy(name = "Новое имя")),
            repos.decks.loadDeckState(state.deck.id),
        )
        assertEquals(listOf(DeckSummary(state.deck.id, "Новое имя")), repos.decks.listDecks())
    }

    @Test
    fun stateSurvivesReopening() = runTest {
        val scenario = buildInterruptedPassScenario()
        assertInterruptedPassShape(scenario)
        seedDeck(repos, scenario.before)

        repos.decks.saveDeckChanges(collectDeckChanges(scenario.before, scenario.after))
        repos.reopen()

        assertEquals(scenario.after, repos.decks.loadDeckState(scenario.after.deck.id))
    }

    @Test
    fun restingCardSurvivesDatabaseRoundTrip() = runTest {
        val before = DeckState(
            buildFullDeck(),
            listOf(
                buildEpochCard(1, Section.EPOCH_1, position = 0, successes = 4),
                buildEpochCard(2, Section.EPOCH_1, position = 1, successes = 1),
            ),
        )
        // sectionDay карточек из buildEpochCard — 150: четвёртый успех получен в день попадания.
        val after = finishPass(before, 1, today = 150)
        assertEquals(Section.RESTING, after.cards.single { it.id == 1L }.section)
        seedDeck(repos, before)

        repos.decks.saveDeckChanges(collectDeckChanges(before, after))
        repos.reopen()

        assertEquals(after, repos.decks.loadDeckState(after.deck.id))
    }

    @Test
    fun waitingEpochAndTailSurviveReopening() = runTest {
        val scenario = buildWaitingEpochScenario()
        assertWaitingEpochShape(scenario)
        seedDeck(repos, scenario.before)

        repos.decks.saveDeckChanges(collectDeckChanges(scenario.before, scenario.after))
        repos.reopen()

        assertEquals(scenario.after, repos.decks.loadDeckState(scenario.after.deck.id))
    }

    @Test
    fun successSurvivesKillRightAfterKnew() = runTest {
        val deck = createDeck(1, "Проход").copy(
            activeSection = Section.EPOCH_2,
            currentEpoch = 2,
            passStarted = true,
        )
        val cards = (1L..3L).map { buildEpochCard(it, Section.EPOCH_2, (it - 1).toInt(), successes = 1) }
        val before = DeckState(deck, cards)
        seedDeck(repos, before)
        val random = Random(3)
        val flipped = ReviewEngine.applyAction(before, ReviewAction.Flip, SCENARIO_DAY, random).state
        val after = ReviewEngine.applyAction(flipped, ReviewAction.Knew, SCENARIO_DAY, random).state
        val ratedId = before.cards.single { it.position == 0 }.id

        repos.decks.saveDeckChanges(collectDeckChanges(before, after))
        repos.reopen()

        val loaded = repos.decks.loadDeckState(deck.id)
        val rated = loaded.cards.single { it.id == ratedId }
        assertEquals(2, rated.successes)
        assertEquals(SCENARIO_DAY, rated.lastSuccessDay)
        assertEquals(after.deck.passCursor, loaded.deck.passCursor)
    }

    @Test
    fun failedSaveWritesNothing() = runTest {
        val scenario = buildWaitingEpochScenario()
        seedDeck(repos, scenario.before)
        val namesBefore = listStrokeFileNames(repos.storage)
        val changes = DeckChanges(
            deck = scenario.after.deck,
            upsertedCards = listOf(scenario.before.cards.first().copy(successes = 3), buildPlainCard(999)),
            deletedCardIds = setOf(scenario.before.cards.last().id),
        )

        assertSuspendThrows<IllegalStateException> { repos.decks.saveDeckChanges(changes) }

        assertEquals(scenario.before, repos.decks.loadDeckState(scenario.before.deck.id))
        assertEquals("файлы удалены при откате", namesBefore, listStrokeFileNames(repos.storage))
    }

    @Test
    fun operationsOnMissingDeckFail() = runTest {
        val deck = createDeck(55, "Нет такой")

        assertSuspendThrows<IllegalStateException> { repos.decks.loadDeckState(deck.id) }
        assertSuspendThrows<IllegalStateException> { repos.decks.renameDeck(deck.id, "Имя") }
        assertSuspendThrows<IllegalStateException> {
            repos.decks.saveDeckChanges(DeckChanges(deck, emptyList(), emptySet()))
        }
    }

    @Test
    fun deletedCardsLoseTheirFiles() = runTest {
        val state = DeckState(buildFullDeck(), listOf(buildPlainCard(1), buildPlainCard(2, 1), buildPlainCard(3, 2)))
        seedDeck(repos, state)

        repos.decks.saveDeckChanges(DeckChanges(state.deck, emptyList(), setOf(1L, 3L)))

        assertEquals(setOf(buildSeedContentFile(2)), listStrokeFileNames(repos.storage))
        assertEquals(listOf(2L), repos.decks.loadDeckState(state.deck.id).cards.map { it.id })
    }

    @Test
    fun deletingDeckClearsItsDraftAndCurrentChoice() = runTest {
        val state = DeckState(buildFullDeck(id = 1), listOf(buildPlainCard(1), buildPlainCard(2, 1)))
        val other = DeckState(buildFullDeck(id = 2), listOf(buildPlainCard(3)))
        seedDeck(repos, state)
        seedDeck(repos, other)
        seedDraft(repos, deckId = 1, contentFile = "draft-aaaa.dks")
        repos.appState.saveCurrentDeckId(1)

        repos.decks.deleteDeck(1)

        assertNull(repos.database.draftDao().findDraft())
        assertNull(repos.appState.loadCurrentDeckId())
        assertEquals(setOf(buildSeedContentFile(3)), listStrokeFileNames(repos.storage))
        assertEquals(listOf(DeckSummary(2, other.deck.name)), repos.decks.listDecks())
        assertEquals(other.sortCardsById(), repos.decks.loadDeckState(2))
    }

    @Test
    fun deletingDeckKeepsDraftAndCurrentChoiceOfAnotherDeck() = runTest {
        val state = DeckState(buildFullDeck(id = 1), listOf(buildPlainCard(1)))
        val other = DeckState(buildFullDeck(id = 2), listOf(buildPlainCard(3)))
        seedDeck(repos, state)
        seedDeck(repos, other)
        seedDraft(repos, deckId = 2, contentFile = "draft-bbbb.dks")
        repos.appState.saveCurrentDeckId(2)

        repos.decks.deleteDeck(1)

        assertNotNull(repos.database.draftDao().findDraft())
        assertEquals(2L, repos.database.draftDao().findDraft()!!.deckId)
        assertEquals(2L, repos.appState.loadCurrentDeckId())
        assertFalse(hasStrokeFile(repos.storage, buildSeedContentFile(1)))
        assertTrue(hasStrokeFile(repos.storage, "draft-bbbb.dks"))
        assertTrue(hasStrokeFile(repos.storage, buildSeedContentFile(3)))
    }

    private fun assertInterruptedPassShape(scenario: Scenario) {
        val deck = scenario.after.deck
        assertEquals(4, deck.pausedEpoch)
        assertEquals(40, deck.pausedCursor)
        assertEquals(1, deck.currentEpoch)
        assertEquals(1, deck.passCursor)
        assertEquals(Side.BACK, deck.visibleSide)
        assertTrue(scenario.after.cards.any { it.postponed })
        assertTrue(scenario.after.cards.any { it.answerRevealed })
    }

    private fun assertWaitingEpochShape(scenario: Scenario) {
        val deck = scenario.after.deck
        assertEquals(2, deck.waitingEpoch)
        assertEquals(3, deck.tail.epoch1.size)
        assertEquals(3, deck.passCount.epoch1)
        assertTrue(deck.passStarted)
    }
}
