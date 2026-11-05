package app.deckly.deck

import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.fixtures.buildCard
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import app.deckly.fixtures.buildEpochRange
import app.deckly.fixtures.buildPassDeck
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.SectionTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckActionRulesTest {
    private val ratings = listOf(DeckAction.Knew, DeckAction.DidNotKnow)

    // --- обучение ------------------------------------------------------------------------------

    @Test
    fun ratingsNeedTheAnswerToBeSeen() {
        val cards = buildEpochRange(1, 1, 5)
        val front = buildDeckState(buildPassDeck(1, passCursor = 1, visibleSide = Side.FRONT), cards)
        val back = buildDeckState(
            buildPassDeck(1, passCursor = 1, visibleSide = Side.BACK),
            cards.map { if (it.id == 2L) it.copy(answerRevealed = true) else it },
        )
        // Ответ открывали, потом вернулись к вопросу: флаг стоит, но лицо видно, оценка недоступна.
        val frontAfterReveal = buildDeckState(
            buildPassDeck(1, passCursor = 1, visibleSide = Side.FRONT),
            cards.map { if (it.id == 2L) it.copy(answerRevealed = true) else it },
        )
        // Лист на обороте, но флаг ответа не стоит: так не бывает, а движок на такое бросает исключение.
        val backWithoutFlag = buildDeckState(buildPassDeck(1, passCursor = 1, visibleSide = Side.BACK), cards)

        for (rating in ratings) {
            assertFalse(isApplicable(front, rating))
            assertTrue(isApplicable(back, rating))
            assertFalse(isApplicable(frontAfterReveal, rating))
            assertFalse(isApplicable(backWithoutFlag, rating))
        }
    }

    @Test
    fun repeatedRatingTapAfterCardChangedIsNotApplicable() {
        // Первое «Знал» уже показало следующую карточку лицом: второе нажатие оценить её не может.
        val afterFirstKnew = buildDeckState(buildPassDeck(1, passCursor = 1, visibleSide = Side.FRONT), buildEpochRange(1, 1, 5))

        assertFalse(isApplicable(afterFirstKnew, DeckAction.Knew))
    }

    @Test
    fun postponeFollowsBlocks() {
        val cards = buildEpochRange(1, 1, 5)
        val free = buildDeckState(buildPassDeck(1, passCursor = 1), cards)
        val last = buildDeckState(buildPassDeck(1, passCursor = 4), cards)
        val revealed = buildDeckState(buildPassDeck(1, passCursor = 1), cards.map { it.copy(answerRevealed = true) })
        val postponed = buildDeckState(buildPassDeck(1, passCursor = 1), cards.map { it.copy(postponed = true) })

        assertTrue(isApplicable(free, DeckAction.Postpone))
        assertFalse(isApplicable(last, DeckAction.Postpone))
        assertFalse(isApplicable(revealed, DeckAction.Postpone))
        assertFalse(isApplicable(postponed, DeckAction.Postpone))
    }

    @Test
    fun browsingIsNotApplicableInActivePass() {
        val state = buildDeckState(buildPassDeck(1, passCursor = 1), buildEpochRange(1, 1, 5))

        assertFalse(isApplicable(state, DeckAction.ShowNext))
        assertFalse(isApplicable(state, DeckAction.ShowPrevious))
    }

    @Test
    fun browsingNeedsACard() {
        val inactive = buildDeckState(buildDeck(activeSection = Section.EPOCH_3), buildEpochRange(3, 1, 3))
        val empty = buildDeckState(buildDeck(activeSection = Section.EPOCH_4), buildEpochRange(3, 1, 3))

        assertTrue(isApplicable(inactive, DeckAction.ShowNext))
        assertTrue(isApplicable(inactive, DeckAction.ShowPrevious))
        assertFalse(isApplicable(empty, DeckAction.ShowNext))
    }

    @Test
    fun ratingAndPostponeAreNotApplicableOutsideActivePass() {
        val inactive = buildDeckState(buildDeck(activeSection = Section.EPOCH_3, visibleSide = Side.BACK), buildEpochRange(3, 1, 3))

        for (action in ratings + DeckAction.Postpone) assertFalse(isApplicable(inactive, action))
    }

    @Test
    fun flipNeedsACard() {
        val withCard = buildDeckState(buildDeck(activeSection = Section.EPOCH_3), buildEpochRange(3, 1, 3))
        val empty = buildDeckState(buildDeck(activeSection = Section.EPOCH_4), buildEpochRange(3, 1, 3))

        assertTrue(isApplicable(withCard, DeckAction.Flip))
        assertFalse(isApplicable(empty, DeckAction.Flip))
    }

    // --- меню и создание -----------------------------------------------------------------------

    @Test
    fun menuItemsFollowSection() {
        val pool = buildDeckState(buildDeck(activeSection = Section.NEW), listOf(buildCard(1, Section.NEW)))
        val epoch = buildDeckState(buildDeck(activeSection = Section.EPOCH_3), buildEpochRange(3, 1, 3))
        val learned = buildDeckState(buildDeck(activeSection = Section.LEARNED), listOf(buildCard(1, Section.LEARNED)))
        val emptyEpoch = buildDeckState(buildDeck(activeSection = Section.EPOCH_4), emptyList())

        assertEquals(listOf(true, false, true), listMenuApplicability(pool))
        assertEquals(listOf(false, true, true), listMenuApplicability(epoch))
        assertEquals(listOf(false, false, true), listMenuApplicability(learned))
        assertEquals(listOf(false, false, false), listMenuApplicability(emptyEpoch))
    }

    @Test
    fun creationIsApplicableInEmptyAndFilledPoolOnly() {
        val emptyPool = buildDeckState(buildDeck(activeSection = Section.NEW), emptyList())
        val filledPool = buildDeckState(buildDeck(activeSection = Section.NEW), listOf(buildCard(1, Section.NEW)))
        val epoch = buildDeckState(buildDeck(activeSection = Section.EPOCH_3), buildEpochRange(3, 1, 3))

        assertTrue(isApplicable(emptyPool, DeckAction.CreateCard))
        assertTrue(isApplicable(filledPool, DeckAction.CreateCard))
        assertFalse(isApplicable(epoch, DeckAction.CreateCard))
    }

    // --- всегда допустимое, диалог, переход ----------------------------------------------------

    @Test
    fun tabsAndDeckListAreAlwaysApplicable() {
        val state = buildDeckState(buildDeck(activeSection = Section.EPOCH_4), emptyList())

        for (tab in SectionTab.entries) assertTrue(isApplicable(state, DeckAction.OpenSection(tab)))
        assertTrue(isApplicable(state, DeckAction.OpenDeckList))
    }

    @Test
    fun confirmDismissAndStartPassNeedTheirScreen() {
        val state = buildDeckState(buildPassDeck(1), buildEpochRange(1, 1, 5))

        assertFalse(isApplicable(state, DeckAction.ConfirmDialog))
        assertFalse(isApplicable(state, DeckAction.DismissDialog))
        assertFalse(isApplicable(state, DeckAction.StartPass))
        assertTrue(isApplicable(state, DeckAction.ConfirmDialog, hasDialog = true))
        assertTrue(isApplicable(state, DeckAction.DismissDialog, hasDialog = true))
        assertTrue(isApplicable(state, DeckAction.StartPass, hasTransition = true))
    }

    @Test
    fun dialogBlocksEverythingButItsOwnAnswers() {
        val back = buildDeckState(
            buildPassDeck(1, passCursor = 1, visibleSide = Side.BACK),
            buildEpochRange(1, 1, 5).map { it.copy(answerRevealed = true) },
        )
        val blocked = listOf(
            DeckAction.Knew, DeckAction.DidNotKnow, DeckAction.Flip, DeckAction.Postpone, DeckAction.ShowNext,
            DeckAction.OpenSection(SectionTab.NEW), DeckAction.CreateCard, DeckAction.OpenDeckList,
            DeckAction.ChooseMenuItem(CardMenuItem.DELETE), DeckAction.StartPass,
        )

        for (action in blocked) assertFalse(action.toString(), isApplicable(back, action, hasDialog = true))
    }

    @Test
    fun transitionAllowsOnlyStartTabsAndDeckList() {
        val state = buildDeckState(buildPassDeck(1), buildEpochRange(1, 1, 5))
        val allowed = listOf(DeckAction.StartPass, DeckAction.OpenSection(SectionTab.EPOCH_3), DeckAction.OpenDeckList)
        val blocked = listOf(
            DeckAction.Flip, DeckAction.Knew, DeckAction.Postpone, DeckAction.ShowNext, DeckAction.CreateCard,
            DeckAction.ChooseMenuItem(CardMenuItem.DELETE), DeckAction.ConfirmDialog,
        )

        for (action in allowed) assertTrue(action.toString(), isApplicable(state, action, hasTransition = true))
        for (action in blocked) assertFalse(action.toString(), isApplicable(state, action, hasTransition = true))
    }

    private fun listMenuApplicability(state: DeckState): List<Boolean> =
        CardMenuItem.entries.map { isApplicable(state, DeckAction.ChooseMenuItem(it)) }

    private fun isApplicable(
        state: DeckState,
        action: DeckAction,
        hasTransition: Boolean = false,
        hasDialog: Boolean = false,
    ) = isDeckActionApplicable(state, hasTransition, hasDialog, action)
}
