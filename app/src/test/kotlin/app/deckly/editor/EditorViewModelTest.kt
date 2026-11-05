package app.deckly.editor

import app.deckly.DayClock
import app.deckly.SerialWriter
import app.deckly.domain.day.computeDayNumber
import app.deckly.domain.editor.Draft
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.port.StoredDraft
import app.deckly.fakes.FakeCardContentRepository
import app.deckly.fakes.FakeDeckRepository
import app.deckly.fakes.FakeDraftRepository
import app.deckly.fixtures.buildCard
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import app.deckly.fixtures.buildPath
import app.deckly.fixtures.buildStroke
import app.deckly.ink.DrawingTool
import app.deckly.navigation.AppScreen
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.EditorTool
import app.deckly.ui.contract.Thickness
import java.time.LocalDateTime
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
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
class EditorViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val now = LocalDateTime.of(2026, 11, 4, 12, 0)
    private val today = computeDayNumber(now)
    private val navigated = mutableListOf<AppScreen>()
    private val contents = FakeCardContentRepository()
    private val drafts = FakeDraftRepository()
    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: EditorViewModel

    private val first = buildStroke(1f to 1f, 5f to 5f)
    private val second = buildStroke(20f to 20f, 25f to 25f)
    private val touchingFirst = buildPath(3f to 3f)
    private val farAway = buildPath(500f to 500f)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        repository = FakeDeckRepository(buildPoolState())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- черновик (Review Focus 1) -------------------------------------------------------------

    @Test
    fun firstStrokeCreatesDraftAndEachActionStoresIt() {
        startNewCard()
        runUntilIdle()

        // До первого штриха черновика нет: смена стороны, инструмента и толщины его не создаёт.
        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        viewModel.onAction(EditorAction.SelectSide(CardSideShown.FRONT))
        viewModel.onAction(EditorAction.SelectTool(EditorTool.ERASER))
        viewModel.onAction(EditorAction.SelectThickness(Thickness.THICK))
        runUntilIdle()
        assertTrue(drafts.storedCalls.isEmpty())

        viewModel.onStroke(first)
        runUntilIdle()
        assertEquals(listOf(listOf(first)), drafts.storedCalls.map { it.draft.front })

        viewModel.onErase(touchingFirst, 2f)
        runUntilIdle()
        assertEquals(emptyList<Stroke>(), drafts.storedCalls.last().draft.front)

        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()
        assertEquals(listOf(first), drafts.storedCalls.last().draft.front)

        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        runUntilIdle()
        assertEquals(4, drafts.storedCalls.size)
        assertEquals(Side.BACK, drafts.storedCalls.last().draft.visibleSide)
        assertEquals(setOf(DECK_ID), drafts.storedCalls.map { it.deckId }.toSet())
    }

    @Test
    fun eraseThatMissesEverythingStoresNothing() {
        startNewCard()
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()

        viewModel.onErase(farAway, 2f)
        runUntilIdle()

        assertEquals(1, drafts.storedCalls.size)
        assertEquals(listOf(first), viewModel.canvas.value.strokes)
    }

    @Test
    fun rapidStrokesAreStoredInOrder() {
        startNewCard()
        runUntilIdle()

        viewModel.onStroke(first)
        viewModel.onStroke(second)
        viewModel.onStroke(first)
        runUntilIdle()

        assertEquals(listOf(1, 2, 3), drafts.storedCalls.map { it.draft.front.size })
        assertEquals(listOf(first, second, first), viewModel.canvas.value.strokes)
    }

    @Test
    fun strokeIsPublishedOnlyAfterDraftIsStored() {
        startNewCard()
        runUntilIdle()
        val gate = CompletableDeferred<Unit>()
        drafts.storeGate = gate

        viewModel.onStroke(first)
        runUntilIdle()
        assertTrue(viewModel.canvas.value.strokes.isEmpty())
        assertFalse(viewModel.state.value.canUndo)

        gate.complete(Unit)
        runUntilIdle()

        assertEquals(listOf(first), viewModel.canvas.value.strokes)
        assertTrue(viewModel.state.value.canUndo)
    }

    @Test
    fun draftIsStoredThroughSerialWriter() {
        val writerScheduler = TestCoroutineScheduler()
        startNewCard(writerScheduler)
        runUntilIdle()

        viewModel.onStroke(first)
        runUntilIdle()
        assertTrue(drafts.storedCalls.isEmpty())

        writerScheduler.advanceUntilIdle()
        runUntilIdle()
        assertEquals(1, drafts.storedCalls.size)
    }

    @Test
    fun resumedDraftOpensOnSavedSide() {
        val saved = Draft(null, listOf(first), listOf(second), Side.BACK, Section.EPOCH_2)
        drafts.stored = StoredDraft(DECK_ID, saved)
        startResume()

        runUntilIdle()

        assertEquals(CardSideShown.BACK, viewModel.state.value.side)
        assertEquals(listOf(second), viewModel.canvas.value.strokes)
        assertTrue(viewModel.state.value.isNewCard)
        // История отмены после перезапуска пуста, а сама загрузка черновик не перезаписывает.
        assertFalse(viewModel.state.value.canUndo)
        assertTrue(drafts.storedCalls.isEmpty())
        assertTrue(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun resumedDraftKeepsStoringAndReturnsToItsSection() {
        drafts.stored = StoredDraft(DECK_ID, Draft(null, listOf(first), emptyList(), Side.FRONT, Section.EPOCH_2))
        startResume()
        runUntilIdle()

        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        runUntilIdle()
        // Черновик уже есть, поэтому смена стороны записывается сразу.
        assertEquals(Side.BACK, drafts.storedCalls.single().draft.visibleSide)

        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.EPOCH_2, NEW_CARD_ID)), navigated)
    }

    @Test
    fun resumedEditOfExistingCardIsNotNew() {
        drafts.stored = StoredDraft(DECK_ID, Draft(2, listOf(first), emptyList(), Side.FRONT, Section.NEW))
        startResume()

        runUntilIdle()

        assertFalse(viewModel.state.value.isNewCard)
    }

    // --- сохранение ----------------------------------------------------------------------------

    @Test
    fun saveAppendsNewCardAndReturnsToSection() {
        startNewCard()
        runUntilIdle()
        viewModel.onStroke(first)
        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        viewModel.onStroke(second)
        runUntilIdle()

        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        val (changes, content) = drafts.commits.single()
        val added = changes.upsertedCards.single()
        assertEquals(listOf(100L), drafts.allocatedIds)
        assertEquals(100L, added.id)
        assertEquals(Section.NEW, added.section)
        assertEquals(3, added.position)
        assertEquals(today, added.sectionDay)
        assertEquals(NOW_MILLIS, added.createdAt)
        assertEquals(100L, content.cardId)
        assertEquals(listOf(first), content.front)
        assertEquals(listOf(second), content.back)
        assertTrue(changes.deletedCardIds.isEmpty())
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW, NEW_CARD_ID)), navigated)
        assertEquals(0, drafts.discardCount)
    }

    @Test
    fun savingNewCardShowsIt() {
        startNewCard()
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()

        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        // Экран колоды откроется на созданной карточке, а не на первой в пуле.
        val screen = navigated.single() as AppScreen.Deck
        assertEquals(drafts.commits.single().first.upsertedCards.single().id, screen.focusCardId)
        assertEquals(NEW_CARD_ID, screen.focusCardId)
    }

    @Test
    fun savingEditReturnsToEditedCard() {
        startEditing(cardId = 2)
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()

        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW, focusCardId = 2)), navigated)
    }

    @Test
    fun cancellingNewCardReturnsToCardShownBefore() {
        startWith(EditorStart.NewCard(Section.NEW, returnFocusCardId = 3))
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()

        viewModel.onAction(EditorAction.Cancel)
        viewModel.onAction(EditorAction.ConfirmDialog)
        runUntilIdle()

        // Созданной карточки нет, пул открывается там, где стоял до «+».
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW, focusCardId = 3)), navigated)
        assertTrue(drafts.commits.isEmpty())
    }

    @Test
    fun savingNewCardIgnoresCardShownBefore() {
        startWith(EditorStart.NewCard(Section.NEW, returnFocusCardId = 3))
        runUntilIdle()

        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        // Сохранённая карточка важнее прежней: показывается она.
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW, NEW_CARD_ID)), navigated)
    }

    @Test
    fun leavingWithoutSavingFocusesNoCard() {
        startNewCard()
        runUntilIdle()
        viewModel.onAction(EditorAction.Cancel)
        viewModel.onAction(EditorAction.ConfirmDialog)
        runUntilIdle()

        // Отменённая карточка не создана, удалённая не существует: фокусироваться не на чем.
        startEditing(cardId = 2)
        runUntilIdle()
        viewModel.onAction(EditorAction.Delete)
        viewModel.onAction(EditorAction.ConfirmDialog)
        runUntilIdle()

        assertEquals(
            listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW), AppScreen.Deck(DECK_ID, Section.NEW)),
            navigated,
        )
        assertTrue(navigated.all { (it as AppScreen.Deck).focusCardId == null })
    }

    @Test
    fun saveWithEmptySidesIsAllowedAndNeverStoredADraft() {
        startNewCard()
        runUntilIdle()

        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        assertTrue(drafts.storedCalls.isEmpty())
        val (_, content) = drafts.commits.single()
        assertTrue(content.front.isEmpty() && content.back.isEmpty())
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW, NEW_CARD_ID)), navigated)
    }

    @Test
    fun savingEditChangesContentOnlyAndAllocatesNoId() {
        startEditing(cardId = 2)
        runUntilIdle()
        assertFalse(viewModel.state.value.isNewCard)
        assertEquals(FakeCardContentRepository.buildContent(2).front, viewModel.canvas.value.strokes)

        viewModel.onStroke(second)
        runUntilIdle()
        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        val (changes, content) = drafts.commits.single()
        assertTrue(drafts.allocatedIds.isEmpty())
        assertTrue(changes.upsertedCards.isEmpty())
        assertTrue(changes.deletedCardIds.isEmpty())
        assertEquals(2L, content.cardId)
        assertEquals(FakeCardContentRepository.buildContent(2).front + second, content.front)
        assertEquals(FakeCardContentRepository.buildContent(2).back, content.back)
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW, focusCardId = 2)), navigated)
    }

    @Test
    fun editingStartsWithoutDraftAndFirstChangeCreatesIt() {
        startEditing(cardId = 2)
        runUntilIdle()

        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        runUntilIdle()
        assertTrue(drafts.storedCalls.isEmpty())

        viewModel.onStroke(second)
        runUntilIdle()

        val stored = drafts.storedCalls.single()
        assertEquals(2L, stored.draft.cardId)
        assertEquals(Side.BACK, stored.draft.visibleSide)
        assertEquals(FakeCardContentRepository.buildContent(2).back + second, stored.draft.back)
    }

    @Test
    fun damagedContentOpensEmptyForEditing() {
        contents.damagedCardIds += 2
        startEditing(cardId = 2)

        runUntilIdle()

        assertTrue(viewModel.canvas.value.strokes.isEmpty())
        assertTrue(viewModel.canvas.value.isPenEnabled)
        assertFalse(viewModel.state.value.isNewCard)
    }

    @Test
    fun doubleSaveCommitsOnce() {
        startNewCard()
        runUntilIdle()

        viewModel.onAction(EditorAction.Save)
        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        assertEquals(1, drafts.commits.size)
        assertEquals(1, navigated.size)
    }

    @Test
    fun inputAfterSaveIsIgnored() {
        startNewCard()
        runUntilIdle()

        viewModel.onAction(EditorAction.Save)
        viewModel.onStroke(first)
        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        runUntilIdle()

        assertTrue(drafts.storedCalls.isEmpty())
        assertFalse(viewModel.canvas.value.isPenEnabled)
    }

    // --- окна ----------------------------------------------------------------------------------

    @Test
    fun cancelAsksBeforeDiscarding() {
        startNewCard()
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()

        viewModel.onAction(EditorAction.Cancel)
        runUntilIdle()
        assertEquals(EditorDialog.DISCARD_DRAFT, viewModel.state.value.dialog)
        assertEquals(0, drafts.discardCount)
        assertTrue(navigated.isEmpty())

        viewModel.onAction(EditorAction.DismissDialog)
        runUntilIdle()
        assertNull(viewModel.state.value.dialog)
        assertEquals(0, drafts.discardCount)

        viewModel.onAction(EditorAction.Cancel)
        viewModel.onAction(EditorAction.ConfirmDialog)
        runUntilIdle()

        assertEquals(1, drafts.discardCount)
        assertTrue(drafts.commits.isEmpty())
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW)), navigated)
    }

    @Test
    fun backOffersSaveAndExit() {
        startNewCard()
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()

        viewModel.onAction(EditorAction.Back)
        runUntilIdle()
        assertEquals(EditorDialog.SAVE_ON_BACK, viewModel.state.value.dialog)
        assertTrue(drafts.commits.isEmpty())

        viewModel.onAction(EditorAction.DismissDialog)
        runUntilIdle()
        assertNull(viewModel.state.value.dialog)
        assertTrue(navigated.isEmpty())

        viewModel.onAction(EditorAction.Back)
        viewModel.onAction(EditorAction.ConfirmDialog)
        runUntilIdle()

        assertEquals(listOf(first), drafts.commits.single().second.front)
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW, NEW_CARD_ID)), navigated)
    }

    @Test
    fun deleteFromEditorRemovesCardAndDraft() {
        startEditing(cardId = 2)
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()

        viewModel.onAction(EditorAction.Delete)
        runUntilIdle()
        assertEquals(EditorDialog.DELETE_CARD, viewModel.state.value.dialog)
        assertTrue(repository.savedChanges.isEmpty())

        viewModel.onAction(EditorAction.ConfirmDialog)
        runUntilIdle()

        assertEquals(setOf(2L), repository.savedChanges.single().deletedCardIds)
        assertTrue(repository.stored.cards.none { it.id == 2L })
        assertEquals(1, drafts.discardCount)
        assertTrue(drafts.commits.isEmpty())
        assertEquals(listOf<AppScreen>(AppScreen.Deck(DECK_ID, Section.NEW)), navigated)
    }

    @Test
    fun cancelOfEditAndDeleteOfNewCardAreIgnored() {
        startNewCard()
        runUntilIdle()
        viewModel.onAction(EditorAction.Delete)
        runUntilIdle()
        assertNull(viewModel.state.value.dialog)

        startEditing(cardId = 2)
        runUntilIdle()
        viewModel.onAction(EditorAction.Cancel)
        runUntilIdle()
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun confirmAndDismissWithoutDialogDoNothing() {
        startNewCard()
        runUntilIdle()

        viewModel.onAction(EditorAction.ConfirmDialog)
        viewModel.onAction(EditorAction.DismissDialog)
        runUntilIdle()

        assertTrue(navigated.isEmpty())
        assertTrue(drafts.commits.isEmpty())
        assertEquals(0, drafts.discardCount)
    }

    @Test
    fun dialogBlocksActionsAndDrawingUntilAnswered() {
        startNewCard()
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()
        viewModel.onAction(EditorAction.Back)
        runUntilIdle()

        // На листе есть штрих, который можно и стереть, и отменить, и дополнить: всё это под окном не действует.
        viewModel.onStroke(second)
        viewModel.onErase(touchingFirst, 2f)
        viewModel.onAction(EditorAction.Undo)
        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        viewModel.onAction(EditorAction.SelectTool(EditorTool.ERASER))
        viewModel.onAction(EditorAction.Save)
        runUntilIdle()

        assertEquals(1, drafts.storedCalls.size)
        assertEquals(listOf(first), viewModel.canvas.value.strokes)
        assertTrue(drafts.commits.isEmpty())
        assertEquals(CardSideShown.FRONT, viewModel.state.value.side)
        assertEquals(EditorTool.PEN, viewModel.state.value.tool)
        assertEquals(EditorDialog.SAVE_ON_BACK, viewModel.state.value.dialog)
    }

    // --- холст и обновление экрана (Review Focus 2) --------------------------------------------

    @Test
    fun dialogDisablesPenAndBumpsRevision() {
        startNewCard()
        runUntilIdle()
        val initial = viewModel.canvas.value
        assertTrue(initial.isPenEnabled)

        viewModel.onAction(EditorAction.Back)
        runUntilIdle()
        val opened = viewModel.canvas.value
        assertFalse(opened.isPenEnabled)
        assertEquals(initial.uiRevision + 1, opened.uiRevision)

        viewModel.onAction(EditorAction.DismissDialog)
        runUntilIdle()
        val closed = viewModel.canvas.value
        assertTrue(closed.isPenEnabled)
        assertEquals(opened.uiRevision + 1, closed.uiRevision)
    }

    @Test
    fun undoAvailabilityChangeBumpsRevision() {
        startNewCard()
        runUntilIdle()
        val initial = viewModel.canvas.value.uiRevision

        viewModel.onStroke(first)
        runUntilIdle()
        // Появилась возможность отмены: кнопка вне холста изменилась.
        assertEquals(initial + 1, viewModel.canvas.value.uiRevision)

        viewModel.onAction(EditorAction.Undo)
        runUntilIdle()
        assertEquals(initial + 2, viewModel.canvas.value.uiRevision)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test
    fun ordinaryStrokeDoesNotBumpRevision() {
        startNewCard()
        runUntilIdle()
        viewModel.onStroke(first)
        runUntilIdle()
        val revision = viewModel.canvas.value.uiRevision

        viewModel.onStroke(second)
        viewModel.onErase(farAway, 2f)
        runUntilIdle()

        assertEquals(revision, viewModel.canvas.value.uiRevision)
        assertEquals(listOf(first, second), viewModel.canvas.value.strokes)
    }

    @Test
    fun eraseThatRemovesStrokeDoesNotBumpRevisionWhileUndoStaysAvailable() {
        startNewCard()
        runUntilIdle()
        viewModel.onStroke(first)
        viewModel.onStroke(second)
        runUntilIdle()
        val revision = viewModel.canvas.value.uiRevision

        viewModel.onErase(touchingFirst, 2f)
        runUntilIdle()

        assertEquals(listOf(second), viewModel.canvas.value.strokes)
        assertEquals(revision, viewModel.canvas.value.uiRevision)
    }

    @Test
    fun toolAndThicknessSetPenAndBumpRevision() {
        startNewCard()
        runUntilIdle()
        val initial = viewModel.canvas.value
        assertEquals(DrawingTool.PEN, initial.tool)
        assertEquals(6f, initial.penWidthPx, 0f)

        viewModel.onAction(EditorAction.SelectThickness(Thickness.THIN))
        runUntilIdle()
        assertEquals(3f, viewModel.canvas.value.penWidthPx, 0f)
        assertEquals(initial.uiRevision + 1, viewModel.canvas.value.uiRevision)

        viewModel.onAction(EditorAction.SelectThickness(Thickness.THICK))
        viewModel.onAction(EditorAction.SelectTool(EditorTool.ERASER))
        runUntilIdle()
        assertEquals(10f, viewModel.canvas.value.penWidthPx, 0f)
        assertEquals(DrawingTool.ERASER, viewModel.canvas.value.tool)
        assertEquals(initial.uiRevision + 3, viewModel.canvas.value.uiRevision)
        assertEquals(Thickness.THICK, viewModel.state.value.thickness)
        assertEquals(EditorTool.ERASER, viewModel.state.value.tool)
    }

    @Test
    fun repeatedSelectionDoesNotBumpRevision() {
        startNewCard()
        runUntilIdle()
        val initial = viewModel.canvas.value.uiRevision

        viewModel.onAction(EditorAction.SelectTool(EditorTool.PEN))
        viewModel.onAction(EditorAction.SelectThickness(Thickness.MEDIUM))
        viewModel.onAction(EditorAction.SelectSide(CardSideShown.FRONT))
        runUntilIdle()

        assertEquals(initial, viewModel.canvas.value.uiRevision)
    }

    @Test
    fun sideChangeBumpsRevisionEvenWhenBothSidesAreEmpty() {
        startNewCard()
        runUntilIdle()
        val initial = viewModel.canvas.value.uiRevision

        viewModel.onAction(EditorAction.SelectSide(CardSideShown.BACK))
        runUntilIdle()

        // Вкладки «Вопрос / Ответ» вне холста, а список штрихов не изменился: сам лист этого не заметит.
        assertEquals(initial + 1, viewModel.canvas.value.uiRevision)
        assertEquals(CardSideShown.BACK, viewModel.state.value.side)
    }

    @Test
    fun editorIsUsableOnlyAfterLoading() {
        startEditing(cardId = 2)

        assertFalse(viewModel.canvas.value.isPenEnabled)

        runUntilIdle()

        assertTrue(viewModel.canvas.value.isPenEnabled)
    }

    @Test
    fun newCardIsReadyAtOnce() {
        startNewCard()

        assertTrue(viewModel.canvas.value.isPenEnabled)
        assertEquals(
            EditorScreenState(true, CardSideShown.FRONT, EditorTool.PEN, Thickness.MEDIUM, canUndo = false, dialog = null),
            viewModel.state.value,
        )
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun buildPoolState(): DeckState {
        val pool = (1L..3L).map { buildCard(it, Section.NEW, position = it.toInt() - 1) }
        return buildDeckState(buildDeck(id = DECK_ID, activeSection = Section.NEW, currentEpoch = 1), pool)
    }

    private fun startNewCard(writerScheduler: TestCoroutineScheduler = scheduler) =
        startWith(EditorStart.NewCard(Section.NEW), writerScheduler)

    private fun startEditing(cardId: Long) = startWith(EditorStart.EditCard(cardId, Section.NEW))

    private fun startResume() = startWith(EditorStart.ResumeDraft)

    private fun startWith(start: EditorStart, writerScheduler: TestCoroutineScheduler = scheduler) {
        viewModel = EditorViewModel(
            deckId = DECK_ID,
            start = start,
            decks = repository,
            contents = contents,
            drafts = drafts,
            writer = SerialWriter(CoroutineScope(SupervisorJob() + StandardTestDispatcher(writerScheduler))),
            clock = DayClock { now },
            random = Random(1),
            nowMillis = { NOW_MILLIS },
            navigate = { navigated += it },
        )
    }

    private fun runUntilIdle() = scheduler.advanceUntilIdle()

    private companion object {
        const val DECK_ID = 7L
        const val NEW_CARD_ID = 100L
        const val NOW_MILLIS = 1_794_000_000_000L
    }
}
