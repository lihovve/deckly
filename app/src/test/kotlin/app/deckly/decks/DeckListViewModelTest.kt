package app.deckly.decks

import app.deckly.SerialWriter
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.port.ImportError
import app.deckly.domain.port.ImportException
import app.deckly.fakes.FakeAppStateRepository
import app.deckly.fakes.FakeDeckCatalog
import app.deckly.fakes.FakeDeckFiles
import app.deckly.fakes.FakeDeckTransfer
import app.deckly.fakes.summarize
import app.deckly.fixtures.buildCard
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import app.deckly.fixtures.buildEpochRange
import app.deckly.navigation.AppScreen
import app.deckly.navigation.DeckToRename
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckRow
import app.deckly.ui.contract.DeleteDeckDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val navigated = mutableListOf<AppScreen>()
    private val appState = FakeAppStateRepository()
    private lateinit var catalog: FakeDeckCatalog
    private lateinit var transfer: FakeDeckTransfer
    private lateinit var viewModel: DeckListViewModel
    private val files = FakeDeckFiles()
    private val collector = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private val requests = mutableListOf<FileRequest>()
    private val reports = mutableListOf<Pair<String, Throwable>>()

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

        val row = buildDeckRow(summarize(buildDeckState(buildDeck(id = 9, name = "Ожидание", currentEpoch = 1), resting)), isCurrent = false)

        assertEquals(3, row.cardCount)
        assertEquals(1, row.activeEpoch)
        assertFalse(row.isAllLearned)
    }

    @Test
    fun listDoesNotLoadDeckStates() {
        appState.currentDeckId = 1
        startWith(buildTypicalDecks())

        runUntilIdle()

        // Строки собираются из сводок одного запроса: ни карточки, ни состояние колоды не читаются.
        assertEquals(4, readState().decks.size)
        assertEquals(0, catalog.stateLoads)
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

        assertEquals(listOf<AppScreen>(AppScreen.DeckName()), navigated)
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

        assertEquals(listOf<AppScreen>(AppScreen.DeckName()), navigated)
    }

    // --- переименование и удаление -------------------------------------------------------------

    @Test
    fun renameOpensTheFormWithThatDeck() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.RenameDeck(2))
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.DeckName(DeckToRename(2, "Английские времена"))), navigated)
        assertTrue("форма пишет сама, список ничего не меняет", catalog.renames.isEmpty())
    }

    @Test
    fun renameOfUnknownDeckIsIgnored() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.RenameDeck(99))
        runUntilIdle()

        assertTrue(navigated.isEmpty())
    }

    @Test
    fun askingToDeleteShowsTheDialogWithTheNameAndTheCountOfCards() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.AskDeleteDeck(3))
        runUntilIdle()

        assertEquals(DeleteDeckDialog(3, "Формулы", cardCount = 4), readState().deleteDialog)
        assertTrue("спросили, но ничего не удалили", catalog.deletedIds.isEmpty())
        assertEquals(4, readState().decks.size)
    }

    @Test
    fun askingAboutAnotherDeckReplacesTheDialog() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.AskDeleteDeck(3))
        viewModel.onAction(DeckListAction.AskDeleteDeck(4))
        runUntilIdle()

        assertEquals(DeleteDeckDialog(4, "Пустая", cardCount = 0), readState().deleteDialog)
    }

    @Test
    fun askingAboutUnknownDeckShowsNoDialog() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.AskDeleteDeck(99))
        runUntilIdle()

        assertNull(readState().deleteDialog)
    }

    @Test
    fun keepingClosesTheDialogAndDeletesNothing() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        viewModel.onAction(DeckListAction.AskDeleteDeck(3))

        viewModel.onAction(DeckListAction.DismissDeleteDialog)
        runUntilIdle()

        assertNull(readState().deleteDialog)
        assertTrue(catalog.deletedIds.isEmpty())
        assertEquals(4, readState().decks.size)
    }

    @Test
    fun confirmDeletesTheDeckAndShowsTheListWithoutIt() {
        appState.currentDeckId = 1
        startWith(buildTypicalDecks())
        runUntilIdle()
        viewModel.onAction(DeckListAction.AskDeleteDeck(3))

        viewModel.onAction(DeckListAction.ConfirmDeleteDeck)
        runUntilIdle()

        assertEquals(listOf(3L), catalog.deletedIds)
        assertEquals(listOf(1L, 2L, 4L), readState().decks.map { it.id })
        assertNull(readState().deleteDialog)
        // Удалили не текущую: «Назад» по-прежнему ведёт к ней.
        assertTrue(readState().canGoBack)
        assertTrue(readState().decks.first { it.id == 1L }.isCurrent)
    }

    @Test
    fun deletingTheCurrentDeckLeavesNoCurrentDeckAndNoWayBack() {
        appState.currentDeckId = 1
        startWith(buildTypicalDecks(), onDelete = { id -> if (appState.currentDeckId == id) appState.currentDeckId = null })
        runUntilIdle()
        viewModel.onAction(DeckListAction.AskDeleteDeck(1))

        viewModel.onAction(DeckListAction.ConfirmDeleteDeck)
        runUntilIdle()

        // Хранилище снимает пометку «текущая»: стрелки «назад» нет, следующий запуск откроет список.
        assertNull(appState.currentDeckId)
        assertFalse(readState().canGoBack)
        assertTrue(readState().decks.none { it.isCurrent })
        assertEquals(listOf(2L, 3L, 4L), readState().decks.map { it.id })
        assertTrue("после удаления на колоду не переходим", navigated.isEmpty())
    }

    @Test
    fun deletingTheLastDeckShowsAnEmptyList() {
        startWith(listOf(buildTypicalDecks().first()))
        runUntilIdle()
        viewModel.onAction(DeckListAction.AskDeleteDeck(1))

        viewModel.onAction(DeckListAction.ConfirmDeleteDeck)
        runUntilIdle()

        assertEquals(DeckListState(emptyList(), canGoBack = false), readState())
    }

    @Test
    fun deletionIsWrittenThroughSerialWriterAndTheDialogClosesAtOnce() {
        val writerScheduler = TestCoroutineScheduler()
        startWith(buildTypicalDecks(), writerScheduler)
        runUntilIdle()
        viewModel.onAction(DeckListAction.AskDeleteDeck(3))
        runUntilIdle()

        viewModel.onAction(DeckListAction.ConfirmDeleteDeck)
        runUntilIdle()
        // Писатель ещё не работал: колода на месте, окно уже закрыто.
        assertTrue(catalog.deletedIds.isEmpty())
        assertEquals(4, readState().decks.size)
        assertNull(readState().deleteDialog)

        writerScheduler.advanceUntilIdle()
        runUntilIdle()

        assertEquals(listOf(3L), catalog.deletedIds)
        assertEquals(listOf(1L, 2L, 4L), readState().decks.map { it.id })
    }

    @Test
    fun secondConfirmWhileTheDeletionIsRunningDoesNothing() {
        val writerScheduler = TestCoroutineScheduler()
        startWith(buildTypicalDecks(), writerScheduler)
        runUntilIdle()
        viewModel.onAction(DeckListAction.AskDeleteDeck(3))
        runUntilIdle()

        viewModel.onAction(DeckListAction.ConfirmDeleteDeck)
        viewModel.onAction(DeckListAction.ConfirmDeleteDeck)
        runUntilIdle()
        writerScheduler.advanceUntilIdle()
        runUntilIdle()

        assertEquals(listOf(3L), catalog.deletedIds)
        assertEquals(3, readState().decks.size)
    }

    @Test
    fun confirmWithoutADialogDeletesNothing() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.ConfirmDeleteDeck)
        runUntilIdle()

        assertTrue(catalog.deletedIds.isEmpty())
        assertEquals(4, readState().decks.size)
    }

    @Test
    fun confirmAfterKeepDeletesNothing() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        viewModel.onAction(DeckListAction.AskDeleteDeck(3))
        viewModel.onAction(DeckListAction.DismissDeleteDialog)

        viewModel.onAction(DeckListAction.ConfirmDeleteDeck)
        runUntilIdle()

        assertTrue(catalog.deletedIds.isEmpty())
    }

    // --- экспорт --------------------------------------------------------------------------------

    @Test
    fun exportAsksTheHostForAFileNamedAfterTheDeck() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.ExportDeck(2))
        runUntilIdle()

        assertEquals(FileRequest.CreateDeckFile("Английские времена.json"), requests.single())
        assertTrue("до выбора файла ничего не пишется", transfer.exportedIds.isEmpty())
    }

    @Test
    fun exportNameHasForbiddenCharactersReplaced() {
        startWith(listOf(buildDeckState(buildDeck(id = 1, name = "A/B: 1?"))))
        runUntilIdle()

        viewModel.onAction(DeckListAction.ExportDeck(1))
        runUntilIdle()

        assertEquals(FileRequest.CreateDeckFile("A_B_ 1_.json"), requests.single())
    }

    @Test
    fun exportOfUnknownDeckAsksNothing() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.ExportDeck(99))
        runUntilIdle()

        assertTrue(requests.isEmpty())
    }

    @Test
    fun chosenFileGetsTheExportOfTheAskedDeck() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        viewModel.onAction(DeckListAction.ExportDeck(3))
        runUntilIdle()

        viewModel.onExportFileChosen("content://picked/1")
        runUntilIdle()

        assertEquals(listOf(3L), transfer.exportedIds)
        assertEquals("deck:3", String(files.contents.getValue("content://picked/1")))
        assertTrue(files.deleted.isEmpty())
        assertEquals(4, readState().decks.size)
        assertFalse(readState().isImportErrorShown)
    }

    @Test
    fun declinedFileChoiceExportsNothing() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        viewModel.onAction(DeckListAction.ExportDeck(3))
        runUntilIdle()

        viewModel.onExportFileChosen(null)
        runUntilIdle()

        assertTrue(transfer.exportedIds.isEmpty())
        assertTrue(files.written.isEmpty())
        assertTrue(files.deleted.isEmpty())
    }

    @Test
    fun fileChosenWithoutARequestIsNotWrittenAndTheEmptyFileIsRemoved() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onExportFileChosen("content://picked/1")
        runUntilIdle()

        assertTrue(transfer.exportedIds.isEmpty())
        assertTrue(files.written.isEmpty())
        assertEquals(listOf("content://picked/1"), files.deleted)
    }

    @Test
    fun exportFileChosenWhileAnImportRunsIsNotWrittenAndTheEmptyFileIsRemoved() {
        startWith(buildTypicalDecks())
        files.readGate = CompletableDeferred()
        runUntilIdle()
        files.contents["content://picked/1"] = "Первая".toByteArray()
        viewModel.onAction(DeckListAction.ExportDeck(3))
        runUntilIdle()
        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()

        viewModel.onExportFileChosen("content://picked/2")
        runUntilIdle()

        assertTrue(transfer.exportedIds.isEmpty())
        assertEquals(listOf("content://picked/2"), files.deleted)
        files.readGate!!.complete(Unit)
        runUntilIdle()
    }

    @Test
    fun failedExportDeletesTheCreatedFileAndOnlyWritesTheLog() {
        startWith(buildTypicalDecks())
        transfer.exportFailure = IOException("диск полон")
        runUntilIdle()
        viewModel.onAction(DeckListAction.ExportDeck(3))
        runUntilIdle()

        viewModel.onExportFileChosen("content://picked/1")
        runUntilIdle()

        assertEquals(listOf("content://picked/1"), files.deleted)
        assertFalse(files.contents.containsKey("content://picked/1"))
        assertEquals(1, reports.size)
        assertTrue(reports.single().second is IOException)
        // Окна об ошибке экспорта нет: список и окна как были.
        assertFalse(readState().isImportErrorShown)
        assertNull(readState().deleteDialog)
        assertEquals(4, readState().decks.size)
    }

    @Test
    fun failedWriteOfTheFileAlsoDeletesIt() {
        startWith(buildTypicalDecks())
        files.writeFailure = IOException("нет доступа")
        runUntilIdle()
        viewModel.onAction(DeckListAction.ExportDeck(3))
        runUntilIdle()

        viewModel.onExportFileChosen("content://picked/1")
        runUntilIdle()

        assertEquals(listOf("content://picked/1"), files.deleted)
        assertEquals(1, reports.size)
    }

    @Test
    fun exportWorksAgainAfterAFailedOne() {
        startWith(buildTypicalDecks())
        transfer.exportFailure = IOException("сбой")
        runUntilIdle()
        viewModel.onAction(DeckListAction.ExportDeck(3))
        viewModel.onExportFileChosen("content://picked/1")
        runUntilIdle()
        transfer.exportFailure = null

        viewModel.onAction(DeckListAction.ExportDeck(3))
        runUntilIdle()
        viewModel.onExportFileChosen("content://picked/2")
        runUntilIdle()

        assertEquals("deck:3", String(files.contents.getValue("content://picked/2")))
    }

    @Test
    fun secondExportWhileTheFirstIsRunningDoesNothing() {
        startWith(buildTypicalDecks())
        files.writeGate = CompletableDeferred()
        runUntilIdle()
        viewModel.onAction(DeckListAction.ExportDeck(3))
        runUntilIdle()
        viewModel.onExportFileChosen("content://picked/1")
        runUntilIdle()
        requests.clear()

        viewModel.onAction(DeckListAction.ExportDeck(2))
        viewModel.onExportFileChosen("content://picked/2")
        runUntilIdle()
        assertTrue("новое окно выбора не открывается", requests.isEmpty())
        assertEquals(listOf("content://picked/1"), files.written)

        files.writeGate!!.complete(Unit)
        runUntilIdle()

        assertEquals(listOf(3L), transfer.exportedIds)
        assertEquals(listOf("content://picked/1"), files.written)
    }

    @Test
    fun exportIsNotQueuedBehindTheWriter() {
        // Медленный источник или приёмник файла не должен держать запись базы: экспорт только читает и идёт мимо писателя.
        val writerScheduler = TestCoroutineScheduler()
        startWith(buildTypicalDecks(), writerScheduler)
        runUntilIdle()
        viewModel.onAction(DeckListAction.ExportDeck(3))
        runUntilIdle()

        viewModel.onExportFileChosen("content://picked/1")
        runUntilIdle()

        assertEquals("deck:3", String(files.contents.getValue("content://picked/1")))
    }

    // --- импорт ---------------------------------------------------------------------------------

    @Test
    fun importButtonAsksTheHostToOpenAFile() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onAction(DeckListAction.ImportDeck)
        runUntilIdle()

        assertEquals(FileRequest.OpenDeckFile, requests.single())
    }

    @Test
    fun chosenFileCreatesANewDeckThatIsNotCurrent() {
        appState.currentDeckId = 1
        startWith(buildTypicalDecks())
        runUntilIdle()
        files.contents["content://picked/1"] = "Новая".toByteArray()

        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), readState().decks.map { it.id })
        val imported = readState().decks.last()
        assertEquals("Новая", imported.name)
        assertFalse("импортированная колода не становится текущей", imported.isCurrent)
        assertEquals(1L, appState.currentDeckId)
        assertTrue(readState().decks.first { it.id == 1L }.isCurrent)
        assertTrue(readState().canGoBack)
        assertFalse(readState().isImportErrorShown)
        assertTrue("на колоду не переходим", navigated.isEmpty())
    }

    @Test
    fun importIsWrittenThroughTheSerialWriter() {
        val writerScheduler = TestCoroutineScheduler()
        startWith(buildTypicalDecks(), writerScheduler)
        runUntilIdle()
        files.contents["content://picked/1"] = "Новая".toByteArray()

        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()
        // Писатель ещё не работал: колоды нет.
        assertEquals(4, readState().decks.size)

        writerScheduler.advanceUntilIdle()
        runUntilIdle()

        assertEquals(5, readState().decks.size)
    }

    @Test
    fun declinedImportChoiceDoesNothing() {
        startWith(buildTypicalDecks())
        runUntilIdle()

        viewModel.onImportFileChosen(null)
        runUntilIdle()

        assertEquals(4, readState().decks.size)
        assertFalse(readState().isImportErrorShown)
    }

    @Test
    fun everyKindOfImportExceptionShowsTheErrorAndChangesNothing() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        val before = readState().decks
        files.contents["content://picked/1"] = "что-то".toByteArray()

        for (error in ImportError.entries) {
            transfer.importFailure = ImportException(error)

            viewModel.onImportFileChosen("content://picked/1")
            runUntilIdle()

            assertTrue("$error: окно ошибки", readState().isImportErrorShown)
            assertEquals("$error: список как был", before, readState().decks)
            assertEquals("$error: каталог как был", 4, catalog.decks.size)
            viewModel.onAction(DeckListAction.DismissImportError)
            runUntilIdle()
            assertFalse(readState().isImportErrorShown)
        }
    }

    @Test
    fun damagedFileThatImportRejectsShowsTheError() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        files.contents["content://picked/1"] = "BAD".toByteArray()

        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()

        assertTrue(readState().isImportErrorShown)
        assertEquals(4, readState().decks.size)
        assertEquals(4, catalog.decks.size)
    }

    @Test
    fun unreadableFileShowsTheErrorAndNeverReachesImport() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        files.readFailure = IOException("файл пропал")

        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()

        assertTrue(readState().isImportErrorShown)
        assertEquals(4, catalog.decks.size)
    }

    @Test
    fun fileThatTheAppMayNotReadShowsTheError() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        files.readFailure = SecurityException("нет права")

        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()

        assertTrue(readState().isImportErrorShown)
    }

    @Test
    fun errorWindowClosesAndImportWorksAgain() {
        startWith(buildTypicalDecks())
        runUntilIdle()
        files.contents["content://picked/1"] = "BAD".toByteArray()
        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()
        viewModel.onAction(DeckListAction.DismissImportError)
        files.contents["content://picked/2"] = "Хорошая".toByteArray()

        viewModel.onImportFileChosen("content://picked/2")
        runUntilIdle()

        assertFalse(readState().isImportErrorShown)
        assertEquals("Хорошая", readState().decks.last().name)
        assertEquals(5, readState().decks.size)
    }

    @Test
    fun secondImportWhileTheFirstIsRunningDoesNothing() {
        startWith(buildTypicalDecks())
        files.readGate = CompletableDeferred()
        runUntilIdle()
        files.contents["content://picked/1"] = "Первая".toByteArray()
        files.contents["content://picked/2"] = "Вторая".toByteArray()

        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()
        viewModel.onAction(DeckListAction.ImportDeck)
        viewModel.onImportFileChosen("content://picked/2")
        runUntilIdle()
        assertTrue("новое окно выбора не открывается", requests.isEmpty())

        files.readGate!!.complete(Unit)
        runUntilIdle()

        assertEquals(listOf("Первая"), readState().decks.drop(4).map { it.name })
    }

    @Test
    fun importBlocksExportWhileItIsRunning() {
        startWith(buildTypicalDecks())
        files.readGate = CompletableDeferred()
        runUntilIdle()
        files.contents["content://picked/1"] = "Первая".toByteArray()
        viewModel.onImportFileChosen("content://picked/1")
        runUntilIdle()

        viewModel.onAction(DeckListAction.ExportDeck(2))
        runUntilIdle()

        assertTrue(requests.isEmpty())
        files.readGate!!.complete(Unit)
        runUntilIdle()
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
        return buildDeckRow(summarize(buildDeckState(buildDeck(id = 9, name = "x"), cards)), isCurrent = false).isAllLearned
    }

    private fun startWith(
        decks: List<DeckState>,
        writerScheduler: TestCoroutineScheduler = scheduler,
        onDelete: (Long) -> Unit = {},
    ) {
        catalog = FakeDeckCatalog(decks, onDelete)
        transfer = FakeDeckTransfer(catalog)
        viewModel = DeckListViewModel(
            decks = catalog,
            appState = appState,
            writer = SerialWriter(CoroutineScope(SupervisorJob() + StandardTestDispatcher(writerScheduler))),
            transfer = transfer,
            files = files,
            report = { message, failure -> reports += message to failure },
            navigate = { navigated += it },
        )
        // Просьбы хосту собираются с самого начала, как это делает хост списка.
        collector.launch { viewModel.fileRequests.collect { requests += it } }
    }

    private fun runUntilIdle() = scheduler.advanceUntilIdle()

    private fun readState(): DeckListState = checkNotNull(viewModel.state.value)
}
