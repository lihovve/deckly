package app.deckly.data.repository

import app.deckly.data.SCENARIO_DAY
import app.deckly.data.Scenario
import app.deckly.data.assertSuspendThrows
import app.deckly.data.buildEpochCard
import app.deckly.data.buildFullDeck
import app.deckly.data.buildInterruptedPassScenario
import app.deckly.data.buildPlainCard
import app.deckly.data.buildSeedContentFile
import app.deckly.data.buildWaitingEpochScenario
import app.deckly.data.createTestStorage
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.openTestDatabase
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
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomDeckRepositoryTest {

    private val storage = createTestStorage()
    private val deletedFiles = mutableListOf<String>()
    private lateinit var database: DecklyDatabase
    private lateinit var repository: RoomDeckRepository
    private lateinit var appState: RoomAppStateRepository

    @Before
    fun openRepositories() {
        database = openTestDatabase(storage)
        repository = RoomDeckRepository(database) { deletedFiles += it }
        appState = RoomAppStateRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    /** Закрывает базу и открывает её заново: как после перезапуска приложения. */
    private fun reopenRepositories() {
        database.close()
        openRepositories()
    }

    @Test
    fun createdDecksAreListedInCreationOrder() = runTest {
        val second = repository.createDeck("Б")
        val first = repository.createDeck("А")

        assertTrue(second.id < first.id)
        assertEquals(listOf(DeckSummary(second.id, "Б"), DeckSummary(first.id, "А")), repository.listDecks())
    }

    @Test
    fun createdDeckStartsInInitialState() = runTest {
        val deck = repository.createDeck("Новая")

        assertEquals(createDeck(deck.id, "Новая"), deck)
        assertEquals(DeckState(deck, emptyList()), repository.loadDeckState(deck.id))
    }

    @Test
    fun renameChangesOnlyName() = runTest {
        val state = DeckState(buildFullDeck(), listOf(buildPlainCard(1)))
        seedDeck(database, state)

        repository.renameDeck(state.deck.id, "Новое имя")

        assertEquals(
            state.copy(deck = state.deck.copy(name = "Новое имя")),
            repository.loadDeckState(state.deck.id),
        )
        assertEquals(listOf(DeckSummary(state.deck.id, "Новое имя")), repository.listDecks())
    }

    @Test
    fun stateSurvivesReopening() = runTest {
        val scenario = buildInterruptedPassScenario()
        assertInterruptedPassShape(scenario)
        seedDeck(database, scenario.before)

        repository.saveDeckChanges(collectDeckChanges(scenario.before, scenario.after))
        reopenRepositories()

        assertEquals(scenario.after, repository.loadDeckState(scenario.after.deck.id))
    }

    @Test
    fun waitingEpochAndTailSurviveReopening() = runTest {
        val scenario = buildWaitingEpochScenario()
        assertWaitingEpochShape(scenario)
        seedDeck(database, scenario.before)

        repository.saveDeckChanges(collectDeckChanges(scenario.before, scenario.after))
        reopenRepositories()

        assertEquals(scenario.after, repository.loadDeckState(scenario.after.deck.id))
    }

    @Test
    fun successSurvivesKillRightAfterKnew() = runTest {
        val deck = createDeck(1, "Проход").copy(
            activeSection = Section.EPOCH_2,
            currentEpoch = 2,
            passStarted = true,
        )
        val before = DeckState(deck, (1L..3L).map { buildEpochCard(it, Section.EPOCH_2, (it - 1).toInt(), successes = 1) })
        seedDeck(database, before)
        val random = Random(3)
        val flipped = ReviewEngine.applyAction(before, ReviewAction.Flip, SCENARIO_DAY, random).state
        val after = ReviewEngine.applyAction(flipped, ReviewAction.Knew, SCENARIO_DAY, random).state
        val ratedId = before.cards.single { it.successes == 1 && it.position == 0 }.id

        repository.saveDeckChanges(collectDeckChanges(before, after))
        reopenRepositories()

        val rated = repository.loadDeckState(deck.id).cards.single { it.id == ratedId }
        assertEquals(2, rated.successes)
        assertEquals(SCENARIO_DAY, rated.lastSuccessDay)
        assertEquals(after.deck.passCursor, repository.loadDeckState(deck.id).deck.passCursor)
    }

    @Test
    fun failedSaveWritesNothing() = runTest {
        val scenario = buildWaitingEpochScenario()
        seedDeck(database, scenario.before)
        val existing = scenario.before.cards.first()
        val changes = DeckChanges(
            deck = scenario.after.deck,
            upsertedCards = listOf(existing.copy(successes = 3), buildPlainCard(999)),
            deletedCardIds = setOf(scenario.before.cards.last().id),
        )

        assertSuspendThrows<IllegalStateException> { repository.saveDeckChanges(changes) }

        assertEquals(scenario.before, repository.loadDeckState(scenario.before.deck.id))
        assertTrue("файлы удалены при откате: $deletedFiles", deletedFiles.isEmpty())
    }

    @Test
    fun operationsOnMissingDeckFail() = runTest {
        val deck = createDeck(55, "Нет такой")

        assertSuspendThrows<IllegalStateException> { repository.loadDeckState(deck.id) }
        assertSuspendThrows<IllegalStateException> { repository.renameDeck(deck.id, "Имя") }
        assertSuspendThrows<IllegalStateException> {
            repository.saveDeckChanges(DeckChanges(deck, emptyList(), emptySet()))
        }
    }

    @Test
    fun deletedCardsLoseTheirFiles() = runTest {
        val state = DeckState(buildFullDeck(), listOf(buildPlainCard(1), buildPlainCard(2, 1), buildPlainCard(3, 2)))
        seedDeck(database, state)

        repository.saveDeckChanges(DeckChanges(state.deck, emptyList(), setOf(1L, 3L)))

        assertEquals(setOf(buildSeedContentFile(1), buildSeedContentFile(3)), deletedFiles.toSet())
        assertEquals(2, deletedFiles.size)
        assertEquals(listOf(2L), repository.loadDeckState(state.deck.id).cards.map { it.id })
    }

    @Test
    fun deletingDeckClearsItsDraftAndCurrentChoice() = runTest {
        val state = DeckState(buildFullDeck(id = 1), listOf(buildPlainCard(1), buildPlainCard(2, 1)))
        val other = DeckState(buildFullDeck(id = 2), listOf(buildPlainCard(3)))
        seedDeck(database, state)
        seedDeck(database, other)
        seedDraft(database, deckId = 1, contentFile = "draft-aaaa.dks")
        appState.saveCurrentDeckId(1)

        repository.deleteDeck(1)

        assertNull(database.draftDao().findDraft())
        assertNull(appState.loadCurrentDeckId())
        assertEquals(
            setOf(buildSeedContentFile(1), buildSeedContentFile(2), "draft-aaaa.dks"),
            deletedFiles.toSet(),
        )
        assertEquals(listOf(DeckSummary(2, other.deck.name)), repository.listDecks())
        assertEquals(other.sortCardsById(), repository.loadDeckState(2))
    }

    @Test
    fun deletingDeckKeepsDraftAndCurrentChoiceOfAnotherDeck() = runTest {
        val state = DeckState(buildFullDeck(id = 1), listOf(buildPlainCard(1)))
        val other = DeckState(buildFullDeck(id = 2), listOf(buildPlainCard(3)))
        seedDeck(database, state)
        seedDeck(database, other)
        seedDraft(database, deckId = 2, contentFile = "draft-bbbb.dks")
        appState.saveCurrentDeckId(2)

        repository.deleteDeck(1)

        assertNotNull(database.draftDao().findDraft())
        assertEquals(2L, database.draftDao().findDraft()!!.deckId)
        assertEquals(2L, appState.loadCurrentDeckId())
        assertEquals(listOf(buildSeedContentFile(1)), deletedFiles)
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
