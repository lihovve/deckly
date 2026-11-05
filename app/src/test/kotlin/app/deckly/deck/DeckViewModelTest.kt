package app.deckly.deck

import app.deckly.DayClock
import app.deckly.SerialWriter
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.day.computeDayNumber
import app.deckly.fakes.FakeAppStateRepository
import app.deckly.fakes.FakeCardContentRepository
import app.deckly.fakes.FakeDeckRepository
import app.deckly.fixtures.buildCard
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import app.deckly.fixtures.buildEpochRange
import app.deckly.fixtures.buildPassDeck
import app.deckly.navigation.AppScreen
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.DeckBody
import app.deckly.ui.contract.DeckDialog
import app.deckly.ui.contract.DeckFooter
import app.deckly.ui.contract.DeckScreenState
import app.deckly.ui.contract.SectionTab
import java.time.LocalDateTime
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class DeckViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val now = LocalDateTime.of(2026, 11, 4, 12, 0)
    private val today = computeDayNumber(now)
    private val navigated = mutableListOf<AppScreen>()
    private val appState = FakeAppStateRepository()
    private val contents = FakeCardContentRepository()
    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: DeckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- запуск --------------------------------------------------------------------------------

    @Test
    fun stateAppearsAfterLoadingAndDeckBecomesCurrent() {
        startWith(buildLearningState())
        assertNull(viewModel.state.value)

        runUntilIdle()

        assertEquals(DeckBody.Card(CardSideShown.FRONT, isContentDamaged = false), readScreen().body)
        assertEquals(1L, appState.currentDeckId)
        assertEquals(1L, viewModel.shownCard.value?.cardId)
        assertTrue(repository.savedChanges.isEmpty())
    }

    @Test
    fun initialSectionIsOpenedAfterLoadingAndPersisted() {
        // Возврат из редактора: открыта секция, из которой его вызвали; сторона и курсор приведены в порядок.
        val deck = buildPassDeck(1, passCursor = 2, visibleSide = Side.BACK)
        val cards = buildEpochRange(1, 1, 5).map { if (it.id == 3L) it.copy(answerRevealed = true) else it } +
            buildCard(10, Section.NEW)
        startWith(buildDeckState(deck, cards), initialSection = Section.NEW)

        runUntilIdle()

        assertTrue(readScreen().tabs[SectionTab.NEW.ordinal].isOpen)
        assertEquals(10L, viewModel.shownCard.value?.cardId)
        assertEquals(Side.FRONT, viewModel.shownCard.value?.side)
        assertEquals(Section.NEW, repository.stored.deck.activeSection)
        assertEquals(1, repository.savedChanges.size)
        // Позиция активного прохода сохранилась: вкладка I вернёт к ней.
        assertEquals(2, repository.stored.deck.passCursor)
    }

    @Test
    fun initialFocusSelectsCardInSection() {
        // Возврат из редактора: открывается пул на карточке, которую только что правили, а не на первой.
        val pool = (10L..14L).map { buildCard(it, Section.NEW, position = (it - 10).toInt()) }
        startWith(
            buildDeckState(buildPassDeck(1, passCursor = 1), buildEpochRange(1, 1, 3) + pool),
            initialSection = Section.NEW,
            initialFocusCardId = 13,
        )

        runUntilIdle()

        assertEquals(13L, viewModel.shownCard.value?.cardId)
        assertEquals(3, repository.stored.deck.browseCursor)
        assertEquals(DeckBody.Card(CardSideShown.FRONT, false), readScreen().body)
        assertEquals(4, readScreen().header.position?.position)
    }

    @Test
    fun initialSectionIsTheOneGivenNotAlwaysPool() {
        val cards = buildEpochRange(1, 1, 5) + buildEpochRange(2, 20, 3)
        startWith(buildDeckState(buildPassDeck(1, passCursor = 2), cards), initialSection = Section.EPOCH_2)

        runUntilIdle()

        assertTrue(readScreen().tabs[SectionTab.EPOCH_2.ordinal].isOpen)
        assertEquals(20L, viewModel.shownCard.value?.cardId)
        assertEquals(Section.EPOCH_2, repository.stored.deck.activeSection)
    }

    @Test
    fun previousSectionIsNeverShownBeforeInitialOne() {
        // Экран не должен сперва показать прежнюю секцию и только потом нужную: на e-ink это лишнее обновление.
        val cards = buildEpochRange(1, 1, 5) + buildCard(10, Section.NEW)
        startWith(buildDeckState(buildPassDeck(1, passCursor = 2), cards), initialSection = Section.NEW)
        val seen = mutableListOf<DeckScreenState?>()
        val collector = CoroutineScope(UnconfinedTestDispatcher(scheduler)).launch { viewModel.state.collect { seen += it } }

        runUntilIdle()
        collector.cancel()

        assertTrue(seen.filterNotNull().isNotEmpty())
        assertTrue(seen.filterNotNull().all { it.tabs[SectionTab.NEW.ordinal].isOpen })
    }

    @Test
    fun shownCardCarriesStrokesOfVisibleSide() {
        startWith(buildLearningState())
        runUntilIdle()
        assertEquals(FakeCardContentRepository.buildContent(1).front, viewModel.shownCard.value?.strokes)

        viewModel.onAction(DeckAction.Flip)
        runUntilIdle()

        val shown = checkNotNull(viewModel.shownCard.value)
        assertEquals(Side.BACK, shown.side)
        assertEquals(FakeCardContentRepository.buildContent(1).back, shown.strokes)
        assertFalse(shown.isDamaged)
    }

    @Test
    fun actionSentBeforeLoadingFinishesIsApplied() {
        startWith(buildLearningState())

        viewModel.onAction(DeckAction.Flip)
        runUntilIdle()

        assertEquals(DeckBody.Card(CardSideShown.BACK, false), readScreen().body)
    }

    // --- порядок записи и очередь действий (Review Focus 4) ------------------------------------

    @Test
    fun allWritesGoThroughSerialWriter() {
        // Писатель живёт на своём планировщике, и пока он не работает, ничего не записано и не показано.
        val writerScheduler = TestCoroutineScheduler()
        startWith(buildLearningState(), writerScheduler)
        runUntilIdle()
        assertNull(viewModel.state.value)
        assertNull(appState.currentDeckId)

        writerScheduler.advanceUntilIdle()
        runUntilIdle()
        assertEquals(1L, appState.currentDeckId)

        viewModel.onAction(DeckAction.Flip)
        runUntilIdle()
        assertTrue(repository.savedChanges.isEmpty())

        writerScheduler.advanceUntilIdle()
        runUntilIdle()
        assertEquals(1, repository.savedChanges.size)
    }

    @Test
    fun stateIsPublishedOnlyAfterWrite() {
        startWith(buildLearningState())
        runUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate

        viewModel.onAction(DeckAction.Flip)
        runUntilIdle()

        // Запись стоит: экран прежний, и в базе ничего нового.
        assertEquals(DeckBody.Card(CardSideShown.FRONT, false), readScreen().body)
        assertEquals(Side.FRONT, viewModel.shownCard.value?.side)
        assertTrue(repository.savedChanges.isEmpty())

        gate.complete(Unit)
        runUntilIdle()

        assertEquals(DeckBody.Card(CardSideShown.BACK, false), readScreen().body)
        assertEquals(Side.BACK, viewModel.shownCard.value?.side)
        assertEquals(1, repository.savedChanges.size)
    }

    @Test
    fun actionArrivedDuringWriteWaitsForItsEnd() {
        startWith(buildLearningState())
        runUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate

        viewModel.onAction(DeckAction.Flip)
        runUntilIdle()
        viewModel.onAction(DeckAction.Knew)
        runUntilIdle()
        assertTrue(repository.savedChanges.isEmpty())

        gate.complete(Unit)
        runUntilIdle()

        // «Знал» применено к состоянию после переворота, то есть к тому, что видел пользователь.
        assertEquals(2, repository.savedChanges.size)
        assertEquals(1, repository.stored.deck.passCursor)
        assertEquals(1, repository.stored.cards.single { it.id == 1L }.successes)
    }

    @Test
    fun rapidRatingsApplySequentially() {
        startWith(buildLearningState())
        runUntilIdle()

        // Три карточки подряд, нажатия без ожидания ответа экрана.
        repeat(3) {
            viewModel.onAction(DeckAction.Flip)
            viewModel.onAction(DeckAction.Knew)
        }
        runUntilIdle()

        assertEquals(3, repository.stored.deck.passCursor)
        assertEquals(listOf(1, 1, 1, 0), repository.stored.cards.sortedBy { it.id }.take(4).map { it.successes })
        // Успех записан сегодняшним днём по часам приложения.
        assertEquals(today, repository.stored.cards.single { it.id == 1L }.lastSuccessDay)
        assertEquals(6, repository.savedChanges.size)
        assertEquals(4L, viewModel.shownCard.value?.cardId)
        assertEquals(Side.FRONT, viewModel.shownCard.value?.side)
    }

    @Test
    fun repeatedKnewTapIsIgnoredNotAppliedToNextCard() {
        startWith(buildLearningState())
        runUntilIdle()

        viewModel.onAction(DeckAction.Flip)
        viewModel.onAction(DeckAction.Knew)
        viewModel.onAction(DeckAction.Knew)
        viewModel.onAction(DeckAction.DidNotKnow)
        runUntilIdle()

        assertEquals(1, repository.stored.deck.passCursor)
        assertEquals(2, repository.savedChanges.size)
        assertEquals(0, repository.stored.cards.single { it.id == 2L }.successes)
        assertEquals(2L, viewModel.shownCard.value?.cardId)
    }

    @Test
    fun flipIsPersistedWithOpenedAnswer() {
        startWith(buildLearningState())
        runUntilIdle()

        viewModel.onAction(DeckAction.Flip)
        runUntilIdle()

        val changes = repository.savedChanges.single()
        assertEquals(Side.BACK, changes.deck.visibleSide)
        assertTrue(changes.upsertedCards.single { it.id == 1L }.answerRevealed)
    }

    // --- подтверждения -------------------------------------------------------------------------

    @Test
    fun deleteAsksForConfirmation() {
        startWith(buildLearningState())
        runUntilIdle()

        viewModel.onAction(DeckAction.ChooseMenuItem(CardMenuItem.DELETE))
        runUntilIdle()
        assertEquals(DeckDialog.CONFIRM_DELETE_CARD, readScreen().dialog)
        assertTrue(repository.savedChanges.isEmpty())

        viewModel.onAction(DeckAction.DismissDialog)
        runUntilIdle()
        assertNull(readScreen().dialog)
        assertTrue(repository.savedChanges.isEmpty())

        viewModel.onAction(DeckAction.ChooseMenuItem(CardMenuItem.DELETE))
        viewModel.onAction(DeckAction.ConfirmDialog)
        runUntilIdle()

        assertNull(readScreen().dialog)
        assertEquals(setOf(1L), repository.savedChanges.single().deletedCardIds)
        assertEquals(9, readScreen().tabs[SectionTab.EPOCH_1.ordinal].count)
        // Следующая карточка прохода заняла место удалённой.
        assertEquals(2L, viewModel.shownCard.value?.cardId)
    }

    @Test
    fun returnToNewAsksForConfirmation() {
        startWith(buildLearningState())
        runUntilIdle()

        viewModel.onAction(DeckAction.ChooseMenuItem(CardMenuItem.RETURN_TO_NEW))
        runUntilIdle()
        assertEquals(DeckDialog.CONFIRM_RETURN_TO_NEW, readScreen().dialog)
        assertTrue(repository.savedChanges.isEmpty())

        viewModel.onAction(DeckAction.ConfirmDialog)
        runUntilIdle()

        val returned = repository.stored.cards.single { it.id == 1L }
        assertEquals(Section.NEW, returned.section)
        assertEquals(0, returned.successes)
        assertEquals(today, returned.sectionDay)
        assertEquals(1, readScreen().tabs[SectionTab.NEW.ordinal].count)
        assertNull(readScreen().dialog)
    }

    @Test
    fun confirmReturnToNewShowsPoolWithoutDialog() {
        // Сценарий владельца: в колоде одна карточка, она в эпохе 1.
        startWith(buildDeckState(buildPassDeck(1), listOf(buildCard(1, Section.EPOCH_1))))
        runUntilIdle()
        viewModel.onAction(DeckAction.ChooseMenuItem(CardMenuItem.RETURN_TO_NEW))
        runUntilIdle()
        assertEquals(DeckDialog.CONFIRM_RETURN_TO_NEW, readScreen().dialog)

        viewModel.onAction(DeckAction.ConfirmDialog)
        runUntilIdle()

        assertNull(readScreen().dialog)
        assertEquals(DeckBody.Card(CardSideShown.FRONT, isContentDamaged = false), readScreen().body)
        assertTrue(readScreen().tabs[SectionTab.NEW.ordinal].isOpen)
        assertEquals(1, readScreen().tabs[SectionTab.NEW.ordinal].count)
        assertEquals(0, readScreen().tabs[SectionTab.EPOCH_1.ordinal].count)
        assertEquals(1L, viewModel.shownCard.value?.cardId)
        // Проход завершён, следующий не начат: карточка остаётся в пуле, пока владелец не откроет эпоху.
        assertFalse(repository.stored.deck.passStarted)
        assertEquals(Section.NEW, repository.stored.cards.single().section)
    }

    @Test
    fun dialogBlocksOtherActionsUntilAnswered() {
        startWith(buildLearningState())
        runUntilIdle()
        viewModel.onAction(DeckAction.ChooseMenuItem(CardMenuItem.DELETE))
        runUntilIdle()

        viewModel.onAction(DeckAction.Flip)
        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_3))
        runUntilIdle()

        assertTrue(repository.savedChanges.isEmpty())
        assertEquals(DeckDialog.CONFIRM_DELETE_CARD, readScreen().dialog)
        assertEquals(Side.FRONT, viewModel.shownCard.value?.side)
    }

    @Test
    fun confirmWithoutDialogDoesNothing() {
        startWith(buildLearningState())
        runUntilIdle()
        viewModel.onAction(DeckAction.ChooseMenuItem(CardMenuItem.DELETE))
        viewModel.onAction(DeckAction.ConfirmDialog)
        runUntilIdle()

        // Повторное «Подтвердить» после закрытия окна ничего не удаляет.
        viewModel.onAction(DeckAction.ConfirmDialog)
        runUntilIdle()

        assertEquals(1, repository.savedChanges.size)
        assertEquals(setOf(1L), repository.savedChanges.single().deletedCardIds)
    }

    // --- итоги движка --------------------------------------------------------------------------

    @Test
    fun needNewCardDialogOpensPool() {
        // В пуле и эпохах пусто, в стеке одна карточка, лежащая меньше 30 дней: учить нечего.
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1)
        startWith(buildDeckState(deck, listOf(buildCard(1, Section.LEARNED, sectionDay = today - 1))))
        runUntilIdle()

        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_1))
        runUntilIdle()

        assertEquals(DeckDialog.NEED_NEW_CARD, readScreen().dialog)
        assertTrue(readScreen().tabs[SectionTab.NEW.ordinal].isOpen)
        assertTrue(readScreen().header.canCreateCard)
        assertEquals(DeckBody.EmptySection(isPool = true), readScreen().body)

        val savedBeforeConfirm = repository.savedChanges.size
        viewModel.onAction(DeckAction.ConfirmDialog)
        runUntilIdle()

        // Движок уже открыл пул: окно просто закрывается, ничего не пишется.
        assertNull(readScreen().dialog)
        assertTrue(readScreen().tabs[SectionTab.NEW.ordinal].isOpen)
        assertEquals(savedBeforeConfirm, repository.savedChanges.size)
    }

    @Test
    fun onlyRestingCardsShowDoneForTodayAgainOnEveryOpening() {
        // Сценарий владельца: четыре карточки набрали порог в день попадания, пул пуст. Раньше цепочка старта на этом
        // упиралась в страховку на сотне итераций, и приложение падало.
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1)
        val resting = (1L..4L).map { buildCard(it, Section.RESTING, position = (it - 1).toInt(), sectionDay = today) }
        startWith(buildDeckState(deck, resting))
        runUntilIdle()

        repeat(2) {
            viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_1))
            runUntilIdle()

            assertEquals(DeckDialog.DONE_FOR_TODAY, readScreen().dialog)
            assertEquals(DeckBody.EmptySection(isPool = true), readScreen().body)
            assertTrue(readScreen().tabs[SectionTab.NEW.ordinal].isOpen)
            assertEquals(0, readScreen().tabs[SectionTab.EPOCH_1.ordinal].count)
            assertEquals(EpochValues(0, 0, 0, 0), repository.stored.deck.passCount)

            viewModel.onAction(DeckAction.ConfirmDialog)
            runUntilIdle()
            assertNull(readScreen().dialog)
        }
    }

    @Test
    fun doneForTodayDialogKeepsWaitingEpochActive() {
        // II не созрела (проход был сегодня), в I и новых пусто.
        val deck = buildDeck(
            activeSection = Section.NEW,
            currentEpoch = 2,
            lastPassDay = EpochValues(null, today, null, null),
        )
        startWith(buildDeckState(deck, buildEpochRange(2, 1, 3)))
        runUntilIdle()

        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_2))
        runUntilIdle()

        assertEquals(DeckDialog.DONE_FOR_TODAY, readScreen().dialog)
        assertTrue(readScreen().tabs[SectionTab.EPOCH_2.ordinal].isActive)
        assertTrue(readScreen().tabs[SectionTab.NEW.ordinal].isOpen)

        val savedBeforeConfirm = repository.savedChanges.size
        viewModel.onAction(DeckAction.ConfirmDialog)
        runUntilIdle()

        assertNull(readScreen().dialog)
        assertTrue(readScreen().tabs[SectionTab.EPOCH_2.ordinal].isActive)
        assertTrue(readScreen().tabs[SectionTab.NEW.ordinal].isOpen)
        assertTrue(readScreen().header.canCreateCard)
        assertEquals(savedBeforeConfirm, repository.savedChanges.size)
    }

    @Test
    fun dialogOfWaitingStateClosesByDismissToo() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 2, lastPassDay = EpochValues(null, today, null, null))
        startWith(buildDeckState(deck, buildEpochRange(2, 1, 3)))
        runUntilIdle()
        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_2))
        runUntilIdle()

        viewModel.onAction(DeckAction.DismissDialog)
        runUntilIdle()

        assertNull(readScreen().dialog)
    }

    @Test
    fun passTransitionIsShownUntilStarted() {
        // Пул из 5 карточек, проход эпохи 1 ещё не начат: открытие I запускает проход и показывает переход.
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1)
        startWith(buildDeckState(deck, (1L..5L).map { buildCard(it, Section.NEW, position = it.toInt()) }))
        runUntilIdle()

        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_1))
        runUntilIdle()

        assertEquals(DeckBody.Transition(1, app.deckly.ui.contract.TransitionKind.REGULAR, 5, 5), readScreen().body)
        assertEquals(DeckFooter.StartPass(app.deckly.ui.contract.TransitionKind.REGULAR), readScreen().footer)
        assertNull(viewModel.shownCard.value)
        assertEquals(1, repository.savedChanges.size)

        // Экран перехода не записывается: после перезапуска показывается текущая карточка.
        viewModel.onAction(DeckAction.StartPass)
        runUntilIdle()

        assertEquals(DeckBody.Card(CardSideShown.FRONT, false), readScreen().body)
        assertTrue(viewModel.shownCard.value != null)
        assertEquals(1, repository.savedChanges.size)
    }

    @Test
    fun actionsExceptStartTabsAndListAreIgnoredDuringTransition() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1)
        startWith(buildDeckState(deck, (1L..5L).map { buildCard(it, Section.NEW, position = it.toInt()) }))
        runUntilIdle()
        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_1))
        runUntilIdle()

        viewModel.onAction(DeckAction.Flip)
        viewModel.onAction(DeckAction.Knew)
        viewModel.onAction(DeckAction.ChooseMenuItem(CardMenuItem.DELETE))
        runUntilIdle()

        assertEquals(1, repository.savedChanges.size)
        assertTrue(readScreen().body is DeckBody.Transition)
        assertNull(readScreen().dialog)
    }

    @Test
    fun otherTabLeavesTransition() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1)
        startWith(buildDeckState(deck, (1L..5L).map { buildCard(it, Section.NEW, position = it.toInt()) }))
        runUntilIdle()
        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_1))
        runUntilIdle()

        viewModel.onAction(DeckAction.OpenSection(SectionTab.NEW))
        runUntilIdle()

        assertEquals(DeckBody.EmptySection(isPool = true), readScreen().body)
        assertTrue(readScreen().tabs[SectionTab.NEW.ordinal].isOpen)
    }

    // --- вкладки и листание --------------------------------------------------------------------

    @Test
    fun tabSwitchIsPersistedAndActiveEpochReturnsToSavedPosition() {
        startWith(buildLearningState(passCursor = 3))
        runUntilIdle()

        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_3))
        runUntilIdle()
        assertEquals(Section.EPOCH_3, repository.stored.deck.activeSection)
        assertEquals(DeckBody.EmptySection(isPool = false), readScreen().body)

        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_1))
        runUntilIdle()

        assertEquals(4L, viewModel.shownCard.value?.cardId)
        assertEquals(3, repository.stored.deck.passCursor)
    }

    @Test
    fun browsingCyclesThroughInactiveSection() {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1)
        startWith(buildDeckState(deck, buildEpochRange(3, 10, 3)))
        runUntilIdle()

        val shown = mutableListOf<Long?>()
        repeat(3) {
            viewModel.onAction(DeckAction.ShowNext)
            runUntilIdle()
            shown += viewModel.shownCard.value?.cardId
        }
        viewModel.onAction(DeckAction.ShowPrevious)
        runUntilIdle()
        shown += viewModel.shownCard.value?.cardId

        assertEquals(listOf<Long?>(11, 12, 10, 12), shown)
        assertEquals(4, repository.savedChanges.size)
    }

    @Test
    fun browsingIsIgnoredInActivePass() {
        startWith(buildLearningState())
        runUntilIdle()

        viewModel.onAction(DeckAction.ShowNext)
        runUntilIdle()

        assertTrue(repository.savedChanges.isEmpty())
        assertEquals(1L, viewModel.shownCard.value?.cardId)
    }

    // --- переходы между экранами ---------------------------------------------------------------

    @Test
    fun createCardOpensEditorWithReturnToPoolAndShownCard() {
        // Пул из трёх карточек, показана вторая: отмена нового черновика вернёт на неё.
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1, browseCursor = 1)
        val pool = (1L..3L).map { buildCard(it, Section.NEW, position = it.toInt()) }
        startWith(buildDeckState(deck, pool))
        runUntilIdle()

        viewModel.onAction(DeckAction.CreateCard)
        runUntilIdle()

        assertEquals(
            listOf<AppScreen>(AppScreen.Editor(1, EditorStart.NewCard(Section.NEW, returnFocusCardId = 2))),
            navigated,
        )
        assertTrue(repository.savedChanges.isEmpty())
    }

    @Test
    fun createCardInEmptyPoolRemembersNoCard() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1)
        startWith(buildDeckState(deck, emptyList()))
        runUntilIdle()

        viewModel.onAction(DeckAction.CreateCard)
        runUntilIdle()

        assertEquals(
            listOf<AppScreen>(AppScreen.Editor(1, EditorStart.NewCard(Section.NEW, returnFocusCardId = null))),
            navigated,
        )
    }

    @Test
    fun editFromMenuOpensEditorOnCurrentCard() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1, browseCursor = 1)
        val pool = (1L..3L).map { buildCard(it, Section.NEW, position = it.toInt()) }
        startWith(buildDeckState(deck, pool))
        runUntilIdle()

        viewModel.onAction(DeckAction.ChooseMenuItem(CardMenuItem.EDIT))
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.Editor(1, EditorStart.EditCard(2, Section.NEW))), navigated)
    }

    @Test
    fun deckListIsOpenedFromHeader() {
        startWith(buildLearningState())
        runUntilIdle()

        viewModel.onAction(DeckAction.OpenDeckList)
        runUntilIdle()

        assertEquals(listOf<AppScreen>(AppScreen.DeckList), navigated)
    }

    // --- содержимое карточек (Review Focus 5) --------------------------------------------------

    @Test
    fun damagedContentShowsEmptySheetAndCardStillWorks() {
        contents.damagedCardIds += 1
        startWith(buildLearningState())
        runUntilIdle()

        val shown = checkNotNull(viewModel.shownCard.value)
        assertTrue(shown.isDamaged)
        assertTrue(shown.strokes.isEmpty())
        assertEquals(DeckBody.Card(CardSideShown.FRONT, isContentDamaged = true), readScreen().body)

        // С такой карточкой можно работать: перевернуть, оценить, удалить.
        viewModel.onAction(DeckAction.Flip)
        viewModel.onAction(DeckAction.Knew)
        runUntilIdle()

        assertEquals(1, repository.stored.deck.passCursor)
        assertFalse(viewModel.shownCard.value!!.isDamaged)
        assertEquals(DeckBody.Card(CardSideShown.FRONT, isContentDamaged = false), readScreen().body)
    }

    @Test
    fun missingContentFileIsTreatedAsDamaged() {
        // Пропавший файл репозиторий сообщает той же ошибкой, что и повреждённый (см. FileCardContentRepository).
        contents.damagedCardIds += 2
        startWith(buildLearningState())
        runUntilIdle()

        viewModel.onAction(DeckAction.Flip)
        viewModel.onAction(DeckAction.Knew)
        runUntilIdle()

        assertEquals(2L, viewModel.shownCard.value?.cardId)
        assertTrue(viewModel.shownCard.value!!.isDamaged)
    }

    @Test
    fun nextCardOfPassIsLoadedAheadAndNotReadTwice() {
        startWith(buildLearningState())
        runUntilIdle()

        assertEquals(1, contents.loadCounts[1])
        assertEquals(1, contents.loadCounts[2])
        assertNull(contents.loadCounts[3])

        viewModel.onAction(DeckAction.Flip)
        viewModel.onAction(DeckAction.Knew)
        runUntilIdle()

        assertEquals(2L, viewModel.shownCard.value?.cardId)
        assertEquals(1, contents.loadCounts[2])
        assertEquals(1, contents.loadCounts[3])
    }

    @Test
    fun nextCardOfBrowsedSectionIsLoadedAheadWithWrapAround() {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1, browseCursor = 2)
        startWith(buildDeckState(deck, buildEpochRange(3, 10, 3)))
        runUntilIdle()

        // Показана последняя карточка секции, следующей по кругу будет первая.
        assertEquals(1, contents.loadCounts[12])
        assertEquals(1, contents.loadCounts[10])
        assertNull(contents.loadCounts[11])
    }

    @Test
    fun singleBrowsedCardIsNotLoadedAheadAgain() {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1)
        startWith(buildDeckState(deck, buildEpochRange(3, 10, 1)))
        runUntilIdle()

        assertEquals(mapOf(10L to 1), contents.loadCounts)
    }

    @Test
    fun cardOfTransitionIsLoadedAheadToo() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1)
        startWith(buildDeckState(deck, (1L..5L).map { buildCard(it, Section.NEW, position = it.toInt()) }))
        runUntilIdle()
        viewModel.onAction(DeckAction.OpenSection(SectionTab.EPOCH_1))
        runUntilIdle()

        // Пока показан переход, первая карточка прохода уже читается.
        val firstCardId = repository.stored.cards.single { it.section == Section.EPOCH_1 && it.position == 0 }.id
        assertEquals(1, contents.loadCounts[firstCardId])
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun buildLearningState(count: Int = 10, passCursor: Int = 0): DeckState =
        buildDeckState(buildPassDeck(1, passCursor), buildEpochRange(1, 1, count))

    private fun startWith(
        state: DeckState,
        writerScheduler: TestCoroutineScheduler = scheduler,
        initialSection: Section? = null,
        initialFocusCardId: Long? = null,
    ) {
        repository = FakeDeckRepository(state)
        viewModel = DeckViewModel(
            deckId = state.deck.id,
            decks = repository,
            contents = contents,
            appState = appState,
            writer = SerialWriter(CoroutineScope(SupervisorJob() + StandardTestDispatcher(writerScheduler))),
            clock = DayClock { now },
            random = Random(1),
            navigate = { navigated += it },
            initialSection = initialSection,
            initialFocusCardId = initialFocusCardId,
        )
    }

    private fun runUntilIdle() = scheduler.advanceUntilIdle()

    private fun readScreen(): DeckScreenState = checkNotNull(viewModel.state.value)
}
