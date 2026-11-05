package app.deckly.deck

import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.epochNumber
import app.deckly.domain.model.findEpochSection
import app.deckly.domain.model.listSectionCards
import app.deckly.domain.review.Availability
import app.deckly.domain.review.CardAction
import app.deckly.domain.review.ReviewOutcome
import app.deckly.domain.review.UnavailableReason
import app.deckly.domain.review.computeCardActions
import app.deckly.domain.review.findCurrentCard
import app.deckly.domain.review.findReleaseThreshold
import app.deckly.domain.review.isActivePassOpen
import app.deckly.domain.review.listPassCards
import app.deckly.domain.review.TransitionKind as DomainTransitionKind
import app.deckly.ui.contract.ActionBlock
import app.deckly.ui.contract.ActionState
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.DeckBody
import app.deckly.ui.contract.DeckDialog
import app.deckly.ui.contract.DeckFooter
import app.deckly.ui.contract.DeckHeader
import app.deckly.ui.contract.DeckScreenState
import app.deckly.ui.contract.PositionKind
import app.deckly.ui.contract.PositionMetric
import app.deckly.ui.contract.SectionTab
import app.deckly.ui.contract.SuccessMetric
import app.deckly.ui.contract.TabState
import app.deckly.ui.contract.TransitionKind as UiTransitionKind

/**
 * Состояние экрана колоды по состоянию алгоритма (правила: `design/README.md`, `design/build.py`, ТЗ).
 * Чистая функция: диалог, экран перехода и признак повреждённого файла штрихов приходят снаружи, потому
 * что алгоритм о них не знает.
 *
 * Что показано и что доступно, решает алгоритм: [computeCardActions] и [isActivePassOpen]. Правила тут
 * только перевод в типы контракта, своих правил обучения нет.
 */
fun buildDeckScreenState(
    state: DeckState,
    transition: ReviewOutcome.Transition?,
    dialog: DeckDialog?,
    isContentDamaged: Boolean,
): DeckScreenState {
    val view = DeckView(state, transition)
    return DeckScreenState(
        header = buildHeader(view),
        tabs = buildTabs(state),
        body = buildBody(view, isContentDamaged),
        footer = buildFooter(view),
        dialog = dialog,
    )
}

/** Что экран показывает: переход, пустая секция, карточка прохода или карточка просмотра. */
private class DeckView(val state: DeckState, val transition: ReviewOutcome.Transition?) {
    val deck = state.deck
    val currentCard = findCurrentCard(state)
    val actions = computeCardActions(state)
    val isActivePass = isActivePassOpen(deck)
    val sectionSize = state.listSectionCards(deck.activeSection).size
}

private fun buildTabs(state: DeckState): List<TabState> {
    val deck = state.deck
    val activeSection = findEpochSection(deck.currentEpoch)
    return SectionTab.entries.map { tab ->
        val section = tab.toSection()
        TabState(tab, state.listSectionCards(section).size, isOpen = section == deck.activeSection, isActive = section == activeSection)
    }
}

private fun buildHeader(view: DeckView): DeckHeader {
    val deck = view.deck
    val card = view.currentCard.takeIf { view.transition == null }
    val position = when {
        card == null -> null
        view.isActivePass ->
            PositionMetric(PositionKind.LEARNING, deck.passCursor + 1, listPassCards(view.state, deck.currentEpoch).size)
        else -> PositionMetric(PositionKind.BROWSING, deck.browseCursor + 1, view.sectionSize)
    }
    val epoch = card?.section?.epochNumber
    return DeckHeader(
        deckName = deck.name,
        position = position,
        successes = if (card != null && epoch != null) SuccessMetric(card.successes, findReleaseThreshold(epoch)) else null,
        canCreateCard = CardAction.CREATE in view.actions,
        cardMenu = if (view.transition == null) buildCardMenu(view.actions.keys) else emptyList(),
    )
}

// Порядок пунктов постоянный; какие из них есть, говорит алгоритм по таблице «Действия с карточкой».
private fun buildCardMenu(actions: Set<CardAction>): List<CardMenuItem> =
    buildList {
        if (CardAction.EDIT in actions) add(CardMenuItem.EDIT)
        if (CardAction.RETURN_TO_NEW in actions) add(CardMenuItem.RETURN_TO_NEW)
        if (CardAction.DELETE in actions) add(CardMenuItem.DELETE)
    }

private fun buildBody(view: DeckView, isContentDamaged: Boolean): DeckBody {
    val transition = view.transition
    if (transition != null) {
        // Оставшихся может быть меньше, чем в проходе: после вставки прерванный проход продолжается с середины.
        val total = listPassCards(view.state, transition.epoch).size
        return DeckBody.Transition(transition.epoch, transition.kind.toUi(), transition.remaining, total)
    }
    if (view.currentCard == null) return DeckBody.EmptySection(isPool = view.deck.activeSection == Section.NEW)
    return DeckBody.Card(view.deck.visibleSide.toShown(), isContentDamaged)
}

private fun buildFooter(view: DeckView): DeckFooter {
    val transition = view.transition
    if (transition != null) return DeckFooter.StartPass(transition.kind.toUi())
    if (view.currentCard == null) return DeckFooter.None
    if (!view.isActivePass) return DeckFooter.Browsing(canBrowse = view.sectionSize > 1)
    return DeckFooter.Learning(
        postpone = view.actions.getValue(CardAction.POSTPONE).toActionState(),
        flip = view.actions.getValue(CardAction.FLIP).toActionState(),
        areRatingsShown = view.deck.visibleSide == Side.BACK,
    )
}

fun SectionTab.toSection(): Section = when (this) {
    SectionTab.NEW -> Section.NEW
    SectionTab.EPOCH_1 -> Section.EPOCH_1
    SectionTab.EPOCH_2 -> Section.EPOCH_2
    SectionTab.EPOCH_3 -> Section.EPOCH_3
    SectionTab.EPOCH_4 -> Section.EPOCH_4
    SectionTab.LEARNED -> Section.LEARNED
}

fun Side.toShown(): CardSideShown = when (this) {
    Side.FRONT -> CardSideShown.FRONT
    Side.BACK -> CardSideShown.BACK
}

private fun DomainTransitionKind.toUi(): UiTransitionKind = when (this) {
    DomainTransitionKind.REGULAR -> UiTransitionKind.REGULAR
    DomainTransitionKind.INSERTED -> UiTransitionKind.INSERTED
    DomainTransitionKind.RESUMED -> UiTransitionKind.RESUMED
}

private fun Availability.toActionState(): ActionState = when (this) {
    Availability.Available -> ActionState(isAvailable = true)
    is Availability.Unavailable -> ActionState(isAvailable = false, block = reason.toBlock())
}

private fun UnavailableReason.toBlock(): ActionBlock = when (this) {
    UnavailableReason.FRONT_VISIBLE -> ActionBlock.FRONT_VISIBLE
    UnavailableReason.ANSWER_REVEALED -> ActionBlock.ANSWER_REVEALED
    UnavailableReason.ALREADY_POSTPONED -> ActionBlock.ALREADY_POSTPONED
    UnavailableReason.LAST_IN_PASS -> ActionBlock.LAST_IN_PASS
}
