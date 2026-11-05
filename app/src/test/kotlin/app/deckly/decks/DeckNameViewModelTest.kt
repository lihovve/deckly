package app.deckly.decks

import app.deckly.SerialWriter
import app.deckly.fakes.FakeAppStateRepository
import app.deckly.fakes.FakeDeckCatalog
import app.deckly.navigation.AppScreen
import app.deckly.ui.contract.DeckNameAction
import app.deckly.ui.contract.DeckNameState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeckNameViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val navigated = mutableListOf<AppScreen>()
    private val appState = FakeAppStateRepository()
    private val catalog = FakeDeckCatalog()
    private lateinit var viewModel: DeckNameViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        startWith()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun formStartsEmptyAndCannotBeSaved() {
        assertEquals(DeckNameState("", canSave = false), viewModel.state.value)
    }

    @Test
    fun typedNameIsPublishedAtOnce() {
        // Без ожидания очереди: поле ввода берёт текст из состояния, и отставание теряло бы символы.
        viewModel.onAction(DeckNameAction.ChangeName("Н"))
        assertEquals(DeckNameState("Н", canSave = true), viewModel.state.value)

        viewModel.onAction(DeckNameAction.ChangeName("Не"))
        assertEquals("Не", viewModel.state.value.name)
    }

    @Test
    fun canSaveFollowsNonBlankName() {
        viewModel.onAction(DeckNameAction.ChangeName("   "))
        runUntilIdle()
        assertEquals(DeckNameState("   ", canSave = false), viewModel.state.value)

        viewModel.onAction(DeckNameAction.ChangeName("Немецкий A2"))
        runUntilIdle()
        assertEquals(DeckNameState("Немецкий A2", canSave = true), viewModel.state.value)

        viewModel.onAction(DeckNameAction.ChangeName(""))
        runUntilIdle()
        assertEquals(DeckNameState("", canSave = false), viewModel.state.value)
    }

    @Test
    fun blankNameCannotBeSaved() {
        viewModel.onAction(DeckNameAction.Save)
        viewModel.onAction(DeckNameAction.ChangeName("  \t "))
        viewModel.onAction(DeckNameAction.Save)
        runUntilIdle()

        assertTrue(catalog.createdNames.isEmpty())
        assertTrue(navigated.isEmpty())
        assertNull(appState.currentDeckId)
    }

    @Test
    fun createdDeckOpens() {
        viewModel.onAction(DeckNameAction.ChangeName("Немецкий A2"))
        viewModel.onAction(DeckNameAction.Save)
        runUntilIdle()

        assertEquals(listOf("Немецкий A2"), catalog.createdNames)
        val created = catalog.decks.single().deck
        // Новая колода становится текущей, и приложение открывает её пул.
        assertEquals(created.id, appState.currentDeckId)
        assertEquals(listOf<AppScreen>(AppScreen.Deck(created.id)), navigated)
    }

    @Test
    fun nameIsTrimmed() {
        viewModel.onAction(DeckNameAction.ChangeName("  Формулы \n"))
        viewModel.onAction(DeckNameAction.Save)
        runUntilIdle()

        assertEquals(listOf("Формулы"), catalog.createdNames)
    }

    @Test
    fun nameTypedRightBeforeSaveIsUsed() {
        // Ввод и нажатие приходят подряд, без паузы на перерисовку: сохраняется то, что набрано последним.
        viewModel.onAction(DeckNameAction.ChangeName("Н"))
        viewModel.onAction(DeckNameAction.ChangeName("Нов"))
        viewModel.onAction(DeckNameAction.Save)
        runUntilIdle()

        assertEquals(listOf("Нов"), catalog.createdNames)
    }

    @Test
    fun doubleSaveCreatesOneDeck() {
        viewModel.onAction(DeckNameAction.ChangeName("Немецкий A2"))
        viewModel.onAction(DeckNameAction.Save)
        viewModel.onAction(DeckNameAction.Save)
        runUntilIdle()

        assertEquals(1, catalog.decks.size)
        assertEquals(1, navigated.size)
    }

    @Test
    fun inputAfterSaveIsIgnored() {
        viewModel.onAction(DeckNameAction.ChangeName("Немецкий A2"))
        viewModel.onAction(DeckNameAction.Save)
        viewModel.onAction(DeckNameAction.ChangeName("Другое"))
        viewModel.onAction(DeckNameAction.Cancel)
        runUntilIdle()

        assertEquals(listOf("Немецкий A2"), catalog.createdNames)
        assertEquals(1, navigated.size)
        assertEquals("Немецкий A2", viewModel.state.value.name)
    }

    @Test
    fun cancelReturnsToDeckListWithoutCreating() {
        viewModel.onAction(DeckNameAction.ChangeName("Немецкий A2"))
        viewModel.onAction(DeckNameAction.Cancel)
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.DeckList), navigated)
        assertTrue(catalog.createdNames.isEmpty())
        assertNull(appState.currentDeckId)
    }

    @Test
    fun doubleCancelNavigatesOnce() {
        viewModel.onAction(DeckNameAction.Cancel)
        viewModel.onAction(DeckNameAction.Cancel)
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.DeckList), navigated)
    }

    @Test
    fun creationIsWrittenThroughSerialWriter() {
        val writerScheduler = TestCoroutineScheduler()
        startWith(writerScheduler)

        viewModel.onAction(DeckNameAction.ChangeName("Немецкий A2"))
        viewModel.onAction(DeckNameAction.Save)
        runUntilIdle()
        // Пока писатель не работает, колоды нет и экран не сменился.
        assertTrue(catalog.decks.isEmpty())
        assertTrue(navigated.isEmpty())

        writerScheduler.advanceUntilIdle()
        runUntilIdle()

        assertEquals(1, catalog.decks.size)
        assertEquals(1, navigated.size)
    }

    private fun startWith(writerScheduler: TestCoroutineScheduler = scheduler) {
        viewModel = DeckNameViewModel(
            decks = catalog,
            appState = appState,
            writer = SerialWriter(CoroutineScope(SupervisorJob() + StandardTestDispatcher(writerScheduler))),
            navigate = { navigated += it },
        )
    }

    private fun runUntilIdle() = scheduler.advanceUntilIdle()
}
