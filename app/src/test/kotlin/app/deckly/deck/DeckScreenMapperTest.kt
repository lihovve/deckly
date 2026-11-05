package app.deckly.deck

import app.deckly.domain.model.Card
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.review.ReviewOutcome
import app.deckly.domain.review.TransitionKind
import app.deckly.fixtures.buildCard
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import app.deckly.fixtures.buildEpochRange
import app.deckly.fixtures.buildPassDeck
import app.deckly.ui.contract.ActionBlock
import app.deckly.ui.contract.ActionState
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.DeckBody
import app.deckly.ui.contract.DeckDialog
import app.deckly.ui.contract.DeckFooter
import app.deckly.ui.contract.PositionKind
import app.deckly.ui.contract.PositionMetric
import app.deckly.ui.contract.SectionTab
import app.deckly.ui.contract.SuccessMetric
import app.deckly.ui.contract.TabState
import app.deckly.ui.contract.TransitionKind as UiTransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckScreenMapperTest {
    // --- обучение ------------------------------------------------------------------------------

    @Test
    fun learningShowsPositionOfPassAndSuccessesOfThreshold() {
        // Проход эпохи 1 из 40 карточек, курсор на четырнадцатой, у неё 3 успеха из 4 (макет learn-front).
        val cards = buildEpochRange(1, firstId = 1, count = 40).map { if (it.id == 14L) it.copy(successes = 3) else it }
        val state = buildDeckState(buildPassDeck(1, passCursor = 13), cards)

        val screen = map(state)

        assertEquals(PositionMetric(PositionKind.LEARNING, 14, 40), screen.header.position)
        assertEquals(SuccessMetric(3, 4), screen.header.successes)
        assertEquals(DeckBody.Card(CardSideShown.FRONT, isContentDamaged = false), screen.body)
        assertFalse(screen.header.canCreateCard)
    }

    @Test
    fun ratingsOnlyOnBackAndPostponeBlockedAfterReveal() {
        val cards = buildEpochRange(1, 1, 5)
        val withFront = buildDeckState(buildPassDeck(1, passCursor = 1, visibleSide = Side.FRONT), cards)
        val withBack = buildDeckState(
            buildPassDeck(1, passCursor = 1, visibleSide = Side.BACK),
            cards.map { if (it.id == 2L) it.copy(answerRevealed = true) else it },
        )
        val frontAgain = buildDeckState(
            buildPassDeck(1, passCursor = 1, visibleSide = Side.FRONT),
            cards.map { if (it.id == 2L) it.copy(answerRevealed = true) else it },
        )

        // Вопрос: оценок нет, перенос доступен.
        assertEquals(DeckFooter.Learning(ActionState(true), ActionState(true), areRatingsShown = false), map(withFront).footer)
        // Ответ: оценки есть, перенос заблокирован открытым ответом.
        val blocked = ActionState(false, ActionBlock.ANSWER_REVEALED)
        assertEquals(DeckFooter.Learning(blocked, ActionState(true), areRatingsShown = true), map(withBack).footer)
        // Снова вопрос: оценок нет, перенос по-прежнему заблокирован.
        assertEquals(DeckFooter.Learning(blocked, ActionState(true), areRatingsShown = false), map(frontAgain).footer)
        assertEquals(CardSideShown.BACK, (map(withBack).body as DeckBody.Card).side)
    }

    @Test
    fun lastCardOfPassBlocksPostponeWithReason() {
        val state = buildDeckState(buildPassDeck(1, passCursor = 4), buildEpochRange(1, 1, 5))

        val footer = map(state).footer as DeckFooter.Learning

        assertEquals(ActionState(false, ActionBlock.LAST_IN_PASS), footer.postpone)
    }

    @Test
    fun alreadyPostponedCardBlocksPostponeWithReason() {
        val cards = buildEpochRange(1, 1, 5).map { if (it.id == 2L) it.copy(postponed = true) else it }
        val state = buildDeckState(buildPassDeck(1, passCursor = 1), cards)

        val footer = map(state).footer as DeckFooter.Learning

        assertEquals(ActionState(false, ActionBlock.ALREADY_POSTPONED), footer.postpone)
    }

    @Test
    fun resumedPassShowsPositionInWholePass() {
        // Прерванный проход IV из 100 карточек, после вставки продолжается с 41-й (макет learn-resume).
        val cards = buildEpochRange(4, firstId = 1, count = 100).map { if (it.id == 41L) it.copy(successes = 1) else it }
        val state = buildDeckState(buildPassDeck(4, passCursor = 40), cards)

        val screen = map(state)

        assertEquals(PositionMetric(PositionKind.LEARNING, 41, 100), screen.header.position)
        assertEquals(SuccessMetric(1, 3), screen.header.successes)
    }

    @Test
    fun cardsExcludedFromPassDoNotCountInPassSizeButCountInTab() {
        val cards = buildEpochRange(2, 1, 5) + buildCard(6, Section.EPOCH_2, position = 5, excludedFromPass = true)
        val state = buildDeckState(buildPassDeck(2), cards)

        val screen = map(state)

        assertEquals(PositionMetric(PositionKind.LEARNING, 1, 5), screen.header.position)
        assertEquals(6, screen.tabs.single { it.section == SectionTab.EPOCH_2 }.count)
    }

    // --- просмотр ------------------------------------------------------------------------------

    @Test
    fun insertPassMarksFirstEpochActiveAndBrowsesPausedEpoch() {
        // Вставной проход I идёт, прерванный IV из 100 карточек открыт для просмотра (макет browse-e4-insert).
        val epoch4 = buildEpochRange(4, firstId = 1000, count = 100).map {
            if (it.id == 1040L) it.copy(successes = 1) else it
        }
        val deck = buildDeck(
            activeSection = Section.EPOCH_4,
            currentEpoch = 1,
            passStarted = true,
            pausedEpoch = 4,
            pausedCursor = 40,
            browseCursor = 40,
        )
        val state = buildDeckState(deck, buildEpochRange(1, 1, 40) + epoch4)

        val screen = map(state)

        assertEquals(PositionMetric(PositionKind.BROWSING, 41, 100), screen.header.position)
        assertEquals(SuccessMetric(1, 3), screen.header.successes)
        assertEquals(listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE), screen.header.cardMenu)
        assertEquals(DeckFooter.Browsing(canBrowse = true), screen.footer)
        assertEquals(
            listOf(
                TabState(SectionTab.NEW, 0, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_1, 40, isOpen = false, isActive = true),
                TabState(SectionTab.EPOCH_2, 0, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_3, 0, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_4, 100, isOpen = true, isActive = false),
                TabState(SectionTab.LEARNED, 0, isOpen = false, isActive = false),
            ),
            screen.tabs,
        )
    }

    @Test
    fun inactiveEpochIsBrowsedWithoutRatingsAndWithEpochThreshold() {
        // Открыта II, пока обучение идёт по I: просмотр без оценок, у карточки 2 успеха из 4.
        val deck = buildDeck(activeSection = Section.EPOCH_2, currentEpoch = 1, passStarted = true, browseCursor = 0)
        val cards = buildEpochRange(1, 1, 3) + buildEpochRange(2, 100, 23).map { if (it.id == 100L) it.copy(successes = 2) else it }
        val state = buildDeckState(deck, cards)

        val screen = map(state)

        assertEquals(PositionMetric(PositionKind.BROWSING, 1, 23), screen.header.position)
        assertEquals(SuccessMetric(2, 4), screen.header.successes)
        assertEquals(DeckFooter.Browsing(canBrowse = true), screen.footer)
    }

    @Test
    fun epochFourThresholdIsThree() {
        val deck = buildDeck(activeSection = Section.EPOCH_4, currentEpoch = 1)
        val state = buildDeckState(deck, buildEpochRange(4, 1, 3).map { it.copy(successes = 2) })

        assertEquals(SuccessMetric(2, 3), map(state).header.successes)
    }

    @Test
    fun browsingPositionFollowsBrowseCursor() {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1, browseCursor = 4)

        val screen = map(buildDeckState(deck, buildEpochRange(3, 1, 9)))

        assertEquals(PositionMetric(PositionKind.BROWSING, 5, 9), screen.header.position)
    }

    @Test
    fun singleCardCannotBeBrowsed() {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1)

        val screen = map(buildDeckState(deck, buildEpochRange(3, 1, 1)))

        assertEquals(DeckFooter.Browsing(canBrowse = false), screen.footer)
    }

    @Test
    fun twoCardsCanBeBrowsed() {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1)

        val screen = map(buildDeckState(deck, buildEpochRange(3, 1, 2)))

        assertEquals(DeckFooter.Browsing(canBrowse = true), screen.footer)
    }

    @Test
    fun unstartedPassOfOpenActiveEpochIsBrowsedNotLearned() {
        val deck = buildDeck(activeSection = Section.EPOCH_1, currentEpoch = 1, passStarted = false)

        val screen = map(buildDeckState(deck, buildEpochRange(1, 1, 3)))

        assertEquals(PositionKind.BROWSING, screen.header.position?.kind)
        assertEquals(DeckFooter.Browsing(canBrowse = true), screen.footer)
    }

    @Test
    fun visibleSideIsShownInBrowsing() {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 1, visibleSide = Side.BACK)

        val screen = map(buildDeckState(deck, buildEpochRange(3, 1, 3)))

        assertEquals(CardSideShown.BACK, (screen.body as DeckBody.Card).side)
    }

    // --- секции и меню -------------------------------------------------------------------------

    @Test
    fun poolOffersCreateAndEdit() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1, browseCursor = 1)
        val pool = (1L..112L).map { buildCard(it, Section.NEW, position = it.toInt()) }

        val screen = map(buildDeckState(deck, pool))

        assertTrue(screen.header.canCreateCard)
        assertEquals(listOf(CardMenuItem.EDIT, CardMenuItem.DELETE), screen.header.cardMenu)
        assertEquals(PositionMetric(PositionKind.BROWSING, 2, 112), screen.header.position)
        assertNull(screen.header.successes)
        assertEquals(DeckFooter.Browsing(canBrowse = true), screen.footer)
    }

    @Test
    fun learnedOffersOnlyDelete() {
        val deck = buildDeck(activeSection = Section.LEARNED, currentEpoch = 1, browseCursor = 1)
        val learned = (1L..61L).map { buildCard(it, Section.LEARNED, position = it.toInt(), successes = 3) }

        val screen = map(buildDeckState(deck, learned))

        assertEquals(listOf(CardMenuItem.DELETE), screen.header.cardMenu)
        assertFalse(screen.header.canCreateCard)
        assertNull(screen.header.successes)
        assertEquals(PositionMetric(PositionKind.BROWSING, 2, 61), screen.header.position)
    }

    @Test
    fun activeEpochMenuOffersReturnAndDelete() {
        val state = buildDeckState(buildPassDeck(2), buildEpochRange(2, 1, 3))

        assertEquals(listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE), map(state).header.cardMenu)
    }

    @Test
    fun emptySectionHasNoCardActions() {
        val deck = buildDeck(activeSection = Section.EPOCH_4, currentEpoch = 1)
        val state = buildDeckState(deck, buildEpochRange(1, 1, 3))

        val screen = map(state)

        assertEquals(DeckBody.EmptySection(isPool = false), screen.body)
        assertEquals(DeckFooter.None, screen.footer)
        assertTrue(screen.header.cardMenu.isEmpty())
        assertNull(screen.header.position)
        assertNull(screen.header.successes)
        assertFalse(screen.header.canCreateCard)
    }

    @Test
    fun emptyPoolOffersOnlyCreation() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1)

        val screen = map(buildDeckState(deck, buildEpochRange(1, 1, 3)))

        assertEquals(DeckBody.EmptySection(isPool = true), screen.body)
        assertTrue(screen.header.canCreateCard)
        assertTrue(screen.header.cardMenu.isEmpty())
        assertEquals(DeckFooter.None, screen.footer)
    }

    @Test
    fun emptyLearnedStackHasNothing() {
        val deck = buildDeck(activeSection = Section.LEARNED, currentEpoch = 1)

        val screen = map(buildDeckState(deck, buildEpochRange(1, 1, 3)))

        assertEquals(DeckBody.EmptySection(isPool = false), screen.body)
        assertTrue(screen.header.cardMenu.isEmpty())
    }

    @Test
    fun tabsShowSectionCountsOpenAndActive() {
        val deck = buildDeck(activeSection = Section.EPOCH_3, currentEpoch = 2)
        val cards = buildEpochRange(1, 1, 2) + buildEpochRange(2, 10, 3) + buildEpochRange(3, 20, 4) +
            listOf(buildCard(50, Section.NEW), buildCard(51, Section.LEARNED))

        val tabs = map(buildDeckState(deck, cards)).tabs

        assertEquals(
            listOf(
                TabState(SectionTab.NEW, 1, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_1, 2, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_2, 3, isOpen = false, isActive = true),
                TabState(SectionTab.EPOCH_3, 4, isOpen = true, isActive = false),
                TabState(SectionTab.EPOCH_4, 0, isOpen = false, isActive = false),
                TabState(SectionTab.LEARNED, 1, isOpen = false, isActive = false),
            ),
            tabs,
        )
    }

    @Test
    fun restingCardIsNotCountedInTabs() {
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 1)
        val cards = buildEpochRange(1, 1, 2) + listOf(buildCard(50, Section.NEW)) +
            listOf(buildCard(60, Section.RESTING), buildCard(61, Section.RESTING, position = 1))

        val tabs = map(buildDeckState(deck, cards)).tabs

        assertEquals(
            listOf(
                TabState(SectionTab.NEW, 1, isOpen = true, isActive = false),
                TabState(SectionTab.EPOCH_1, 2, isOpen = false, isActive = true),
                TabState(SectionTab.EPOCH_2, 0, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_3, 0, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_4, 0, isOpen = false, isActive = false),
                TabState(SectionTab.LEARNED, 0, isOpen = false, isActive = false),
            ),
            tabs,
        )
    }

    // --- ожидание ------------------------------------------------------------------------------

    @Test
    fun waitingStateMarksWaitingEpochActive() {
        // «На сегодня всё»: II не созрела, I и новые пусты. Движок оставил открытым пул, ▶ у II (макет pool-waiting).
        val deck = buildDeck(activeSection = Section.NEW, currentEpoch = 2, waitingEpoch = 2)
        val cards = buildEpochRange(2, 1, 23) + buildEpochRange(3, 100, 9) + (200L..260L).map { buildCard(it, Section.LEARNED) }

        val screen = map(buildDeckState(deck, cards), dialog = DeckDialog.DONE_FOR_TODAY)

        assertEquals(DeckDialog.DONE_FOR_TODAY, screen.dialog)
        assertEquals(
            listOf(
                TabState(SectionTab.NEW, 0, isOpen = true, isActive = false),
                TabState(SectionTab.EPOCH_1, 0, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_2, 23, isOpen = false, isActive = true),
                TabState(SectionTab.EPOCH_3, 9, isOpen = false, isActive = false),
                TabState(SectionTab.EPOCH_4, 0, isOpen = false, isActive = false),
                TabState(SectionTab.LEARNED, 61, isOpen = false, isActive = false),
            ),
            screen.tabs,
        )
        assertEquals(DeckBody.EmptySection(isPool = true), screen.body)
        assertTrue(screen.header.canCreateCard)
    }

    @Test
    fun emptyFirstEpochWhileSecondWaitsHasNoCardActions() {
        // Макет empty-e1-waiting: открыта пустая I, ▶ у II, действий и листания нет.
        val deck = buildDeck(activeSection = Section.EPOCH_1, currentEpoch = 2, waitingEpoch = 2)

        val screen = map(buildDeckState(deck, buildEpochRange(2, 1, 23)))

        assertEquals(DeckBody.EmptySection(isPool = false), screen.body)
        assertEquals(DeckFooter.None, screen.footer)
        assertTrue(screen.header.cardMenu.isEmpty())
        assertTrue(screen.tabs.single { it.section == SectionTab.EPOCH_2 }.isActive)
        assertTrue(screen.tabs.single { it.section == SectionTab.EPOCH_1 }.isOpen)
    }

    // --- переходы ------------------------------------------------------------------------------

    @Test
    fun transitionKindsAndCounts() {
        val regular = buildDeckState(buildPassDeck(2), buildEpochRange(2, 1, 23))
        val inserted = buildDeckState(
            buildDeck(activeSection = Section.EPOCH_1, currentEpoch = 1, passStarted = true, pausedEpoch = 4, pausedCursor = 40),
            buildEpochRange(1, 1, 40) + buildEpochRange(4, 1000, 100),
        )
        val resumed = buildDeckState(buildPassDeck(4, passCursor = 40), buildEpochRange(4, 1, 100))

        val regularScreen = map(regular, ReviewOutcome.Transition(2, 23, TransitionKind.REGULAR))
        val insertedScreen = map(inserted, ReviewOutcome.Transition(1, 40, TransitionKind.INSERTED))
        val resumedScreen = map(resumed, ReviewOutcome.Transition(4, 60, TransitionKind.RESUMED))

        assertEquals(DeckBody.Transition(2, UiTransitionKind.REGULAR, remaining = 23, total = 23), regularScreen.body)
        assertEquals(DeckBody.Transition(1, UiTransitionKind.INSERTED, remaining = 40, total = 40), insertedScreen.body)
        assertEquals(DeckBody.Transition(4, UiTransitionKind.RESUMED, remaining = 60, total = 100), resumedScreen.body)
        assertEquals(DeckFooter.StartPass(UiTransitionKind.REGULAR), regularScreen.footer)
        assertEquals(DeckFooter.StartPass(UiTransitionKind.INSERTED), insertedScreen.footer)
        assertEquals(DeckFooter.StartPass(UiTransitionKind.RESUMED), resumedScreen.footer)
    }

    @Test
    fun transitionHidesCardMetricsMenuAndCreation() {
        val state = buildDeckState(buildPassDeck(2), buildEpochRange(2, 1, 23))

        val screen = map(state, ReviewOutcome.Transition(2, 23, TransitionKind.REGULAR))

        assertNull(screen.header.position)
        assertNull(screen.header.successes)
        assertTrue(screen.header.cardMenu.isEmpty())
        assertFalse(screen.header.canCreateCard)
        // Вкладки на экране перехода обычные: открыта и активна II.
        assertEquals(TabState(SectionTab.EPOCH_2, 23, isOpen = true, isActive = true), screen.tabs[2])
    }

    // --- прочее --------------------------------------------------------------------------------

    @Test
    fun deckNameAndDialogArePassedThrough() {
        val deck = buildDeck(name = "Немецкий A2", activeSection = Section.EPOCH_3, currentEpoch = 1)
        val state = buildDeckState(deck, buildEpochRange(3, 1, 3))

        val screen = map(state, dialog = DeckDialog.CONFIRM_DELETE_CARD)

        assertEquals("Немецкий A2", screen.header.deckName)
        assertEquals(DeckDialog.CONFIRM_DELETE_CARD, screen.dialog)
        assertNull(map(state).dialog)
    }

    @Test
    fun damagedContentIsPassedToCardBody() {
        val state = buildDeckState(buildPassDeck(1), buildEpochRange(1, 1, 3))

        val screen = map(state, isContentDamaged = true)

        assertEquals(DeckBody.Card(CardSideShown.FRONT, isContentDamaged = true), screen.body)
    }

    @Test
    fun damagedFlagIsIgnoredOnEmptySection() {
        val empty = map(buildDeckState(buildDeck(activeSection = Section.EPOCH_4), emptyList<Card>()), isContentDamaged = true)

        assertEquals(DeckBody.EmptySection(isPool = false), empty.body)
    }

    private fun map(
        state: DeckState,
        transition: ReviewOutcome.Transition? = null,
        dialog: DeckDialog? = null,
        isContentDamaged: Boolean = false,
    ) = buildDeckScreenState(state, transition, dialog, isContentDamaged)
}
