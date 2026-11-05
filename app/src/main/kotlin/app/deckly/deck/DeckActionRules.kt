package app.deckly.deck

import app.deckly.domain.model.DeckState
import app.deckly.domain.review.Availability
import app.deckly.domain.review.CardAction
import app.deckly.domain.review.computeCardActions
import app.deckly.domain.review.findCurrentCard
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.DeckAction

/**
 * Применимо ли [action] к тому, что сейчас на экране. Нажатие может прийти позже, чем экран успел
 * смениться (двойное «Знал»: после первого уже показана следующая карточка лицом), а движок на такое
 * бросает исключение. Неприменимое действие просто пропускается.
 *
 * Пока открыт диалог, действуют только его ответы; пока показан экран перехода, только начало
 * прохода, вкладки и список колод. Остальное решает алгоритм: [computeCardActions].
 */
fun isDeckActionApplicable(state: DeckState, hasTransition: Boolean, hasDialog: Boolean, action: DeckAction): Boolean {
    if (hasDialog) return action == DeckAction.ConfirmDialog || action == DeckAction.DismissDialog
    if (hasTransition) {
        return action == DeckAction.StartPass || action is DeckAction.OpenSection || action == DeckAction.OpenDeckList
    }
    val available = computeCardActions(state)
    return when (action) {
        is DeckAction.OpenSection, DeckAction.OpenDeckList -> true
        DeckAction.Flip -> CardAction.FLIP in available
        // Оценке нужен открытый ответ, а не только оборот на экране: это условие движка.
        DeckAction.Knew, DeckAction.DidNotKnow ->
            available[CardAction.KNEW] == Availability.Available && findCurrentCard(state)?.answerRevealed == true
        DeckAction.Postpone -> available[CardAction.POSTPONE] == Availability.Available
        DeckAction.ShowPrevious, DeckAction.ShowNext -> CardAction.BROWSE in available
        is DeckAction.ChooseMenuItem -> toCardAction(action.item) in available
        DeckAction.CreateCard -> CardAction.CREATE in available
        DeckAction.StartPass, DeckAction.ConfirmDialog, DeckAction.DismissDialog -> false
    }
}

private fun toCardAction(item: CardMenuItem): CardAction = when (item) {
    CardMenuItem.EDIT -> CardAction.EDIT
    CardMenuItem.RETURN_TO_NEW -> CardAction.RETURN_TO_NEW
    CardMenuItem.DELETE -> CardAction.DELETE
}
