package app.deckly.decks

import app.deckly.SerialWriter
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.fakes.FakeAppStateRepository
import app.deckly.fakes.FakeDeckCatalog
import app.deckly.fixtures.buildCard
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import app.deckly.fixtures.buildEpochRange
import app.deckly.navigation.AppScreen
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val navigated = mutableListOf<AppScreen>()
    private val appState = FakeAppStateRepository()
    private lateinit var catalog: FakeDeckCatalog
    private lateinit var viewModel: DeckListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- строки списка -------------------------------------------------------------------------

    @Test
    fun rowsShowCountActiveEpochAndCurrent() {
        appState.currentDeckId = 1
        startWith(buildTypicalDecks())

        runUntilIdle()

        assertEquals(
            DeckListState(
                decks = listOf(
                    DeckRow(1, "Немецкий A2", cardCount = 5, activeEpoch = 1, isAllLearned = false, isCurrent = true),
                    DeckRow(2, "Английские времена", cardCount = 3, activeEpoch = 2, isAllLearned = false, isCurrent = false),
                    DeckRow(3, "Формулы", cardCount = 4, activeEpoch = null, isAllLearned = true, isCurrent = false),
                    DeckRow(4, "Пустая", cardCount = 0, activeEpoch = null, isAllLearned = false, isCurrent = false),
                ),
                canGoBack = true,
            ),
            readState(),
        )
    }

    @Test
    fun allLearnedDeckIsMarked() {
        startWith(buildTypicalDecks())

        runUntilIdle()

        val rows = readState().decks.associateBy { it.id }
        assertTrue(rows.getValue(3).isAllLearned)
        assertFalse(rows.getValue(1).isAllLearned)
        // Одна невыученная карточка снимает отметку.
        assertFalse(isAllLearned(learnedCards = 3, otherCards = 1))
        // Пустая колода не «выучена»: выучивать в ней нечего.
        assertFalse(rows.getValue(4).isAllLearned)
    }

    @Test
    fun restingCardsAreCountedInDeckRow() {
        // Колода из одних ждущих карточек: на вкладках их нет, но строка не показывает «0 карточек».
        val resting = (1L..3L).map { buildCard(it, Section.RESTING, position = it.toInt()) }

        val row = buildDeckRow(buildDeckState(buildDeck(id = 9, name = "Ожидание", currentEpoch = 1), resting), isCurrent = false)

        assertEquals(3, row.cardCount)
        assertEquals(1, row.activeEpoch)
        assertFalse(row.isAllLearned)
    }

    @Test
    fun rowsKeepCreationOrder() {
        startWith(buildTypicalDecks().reversed())

        runUntilIdle()

        // Порядок строк даёт репозиторий (по id), а не число карточек или имя.
        assertEquals(listOf(4L, 3L, 2L, 1L), readState().decks.map { it.id })
    }

    @Test
    fun waitingEpochRowShowsItsEpoch() {
        val deck = buildDeck(id = 1, name = "Ожидание", currentEpoch = 3)
        startWith(listOf(buildDeckState(deck, buildEpochRange(3, 1, 2))))

        runUntilIdle()

        assertEquals(3, readState().decks.single().activeEpoch)
    }

    @Test
    fun stateAppearsOnlyAfterLoading() {
        startWith(buildTypicalDecks())
        assertNull(viewModel.state.value)

        runUntilIdle()

        assertEquals(4, readState().decks.size)
    }

    @Test
    fun noDecksShowsEmptyListWithoutWayBack() {
        startWith(emptyList())

        runUntilIdle()

        assertEquals(DeckListState(emptyList(), canGoBack = false), readState())
    }

    @Test
    fun currentDeckThatIsNotInListIsNotMarked() {
        appState.currentDeckId = 99
        startWith(buildTypicalDecks())

        runUntilIdle()

        assertTrue(readState().decks.none { it.isCurrent })
        assertFalse(readState().canGoBack)
    }

    // --- действия ------------------------------------------------------------------------------

    @Test
    fun openingDeckMakesItCurrent() {
        appState.currentDeckId = 1
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.OpenDeck(2))
        runUntilIdle()

        assertEquals(2L, appState.currentDeckId)
        assertEquals(listOf<AppScreen>(AppScreen.Deck(2)), navigated)
    }

    @Test
    fun openingDeckIsWrittenThroughSerialWriter() {
        val writerScheduler = TestCoroutineScheduler()
        startWith(buildTypicalDecks(), writerScheduler)
        runUntilIdle()

        viewModel.onAction(DeckListAction.OpenDeck(2))
        runUntilIdle()
        // Запись ждёт писателя, и пока она не прошла, на колоду не переходят.
        assertNull(appState.currentDeckId)
        assertTrue(navigated.isEmpty())

        writerScheduler.advanceUntilIdle()
        runUntilIdle()

        assertEquals(2L, appState.currentDeckId)
        assertEquals(listOf<AppScreen>(AppScreen.Deck(2)), navigated)
    }

    @Test
    fun openingUnknownDeckIsIgnored() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.OpenDeck(99))
        runUntilIdle()

        assertNull(appState.currentDeckId)
        assertTrue(navigated.isEmpty())
    }

    @Test
    fun createDeckOpensTheForm() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.CreateDeck)
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.DeckName), navigated)
        assertTrue(catalog.createdNames.isEmpty())
    }

    @Test
    fun backGoesToCurrentDeck() {
        appState.currentDeckId = 2
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.Back)
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.Deck(2)), navigated)
    }

    @Test
    fun backWithoutCurrentDeckStaysInList() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.Back)
        runUntilIdle()

        assertTrue(navigated.isEmpty())
    }

    @Test
    fun actionSentBeforeLoadingFinishesIsApplied() {
        startWith(buildTypicalDecks())

        viewModel.onAction(DeckListAction.CreateDeck)
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.DeckName), navigated)
    }

    // --- вспомогательное -----------------------------------------------------------------------

    /** Четыре колоды: текущая с обучением, с эпохой 2, полностью выученная и пустая. */
    private fun buildTypicalDecks(): List<DeckState> = listOf(
        buildDeckState(
            buildDeck(id = 1, name = "Немецкий A2", currentEpoch = 1),
            buildEpochRange(1, 1, 3) + (10L..11L).map { buildCard(it, Section.NEW, position = (it - 10).toInt()) },
        ),
        buildDeckState(buildDeck(id = 2, name = "Английские времена", currentEpoch = 2), buildEpochRange(2, 20, 3)),
        buildDeckState(
            buildDeck(id = 3, name = "Формулы", currentEpoch = 1),
            (30L..33L).map { buildCard(it, Section.LEARNED, position = (it - 30).toInt()) },
        ),
        buildDeckState(buildDeck(id = 4, name = "Пустая", currentEpoch = 1), emptyList()),
    )

    private fun isAllLearned(learnedCards: Int, otherCards: Int): Boolean {
        val cards = (1..learnedCards).map { buildCard(it.toLong(), Section.LEARNED, position = it) } +
            (1..otherCards).map { buildCard(100L + it, Section.EPOCH_4, position = it) }
        return buildDeckRow(buildDeckState(buildDeck(id = 9, name = "x"), cards), isCurrent = false).isAllLearned
    }

    private fun startWith(decks: List<DeckState>, writerScheduler: TestCoroutineScheduler = scheduler) {
        catalog = FakeDeckCatalog(decks)
        viewModel = DeckListViewModel(
            decks = catalog,
            appState = appState,
            writer = SerialWriter(CoroutineScope(SupervisorJob() + StandardTestDispatcher(writerScheduler))),
            navigate = { navigated += it },
        )
    }

    private fun runUntilIdle() = scheduler.advanceUntilIdle()

    private fun readState(): DeckListState = checkNotNull(viewModel.state.value)
}
