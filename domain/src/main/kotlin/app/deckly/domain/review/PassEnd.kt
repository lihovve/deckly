package app.deckly.domain.review

import app.deckly.domain.model.Card
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.findEpochSection

private const val TAIL_SIZE = 5
private const val DEMOTED_SUCCESSES = 3
private const val CHECK_DID_NOT_KNOW_SUCCESSES = 2

/**
 * Шаги 1–5 завершения прохода (ТЗ, «Алгоритм обучения»): `tail`, ожидание ночи, спуск после ошибки, итог проверки
 * выученных, `lastPassDay`. Шаг 6 — счётчик проходов и выбор следующей эпохи — и возврат к
 * прерванному проходу делает движок. Для эпохи 1 (обычной и вставной) остаются `tail` и ожидание ночи.
 */
fun finishPass(state: DeckState, epoch: Int, today: Int): DeckState {
    val passCards = listPassCards(state, epoch)
    val deck = state.deck.copy(tail = state.deck.tail.replace(epoch, passCards.takeLast(TAIL_SIZE).map { it.id }))
    if (epoch == 1) return DeckState(deck, sendToRest(state.cards, today))

    val demotedIds = passCards.filter { it.failed && !it.checking }.map { it.id }
    var cards = moveCards(state.cards, demotedIds, findEpochSection(epoch - 1), today, DEMOTED_SUCCESSES)
    if (epoch == 4) {
        cards = applyLearnedCheckResult(cards, passCards.filter { it.checking }.map { it.id }, today)
    }
    // Проход непустой, если к его концу в нём осталась хоть одна карточка (до спуска и итога проверки).
    val lastPassDay = if (passCards.isNotEmpty()) deck.lastPassDay.replace(epoch, today) else deck.lastPassDay
    return DeckState(deck.copy(lastPassDay = lastPassDay), cards)
}

/**
 * Ожидание ночи (ТЗ): карточки эпохи 1, набравшие порог в день попадания в неё, уходят в `RESTING`. Если проход
 * закончился уже после ночи, карточка остаётся в эпохе и уезжает на ближайшем старте прохода эпохи 1.
 */
private fun sendToRest(cards: List<Card>, today: Int): List<Card> {
    val restingIds = cards
        .filter { it.section == Section.EPOCH_1 && it.successes >= findReleaseThreshold(1) && today <= it.sectionDay }
        .sortedBy { it.position }
        .map { it.id }
    return moveCards(cards, restingIds, Section.RESTING, today)
}

/**
 * «Знал» — проверяемая карточка возвращается в конец стека выученных с сегодняшним днём; «не знал» —
 * остаётся в эпохе 4 с двумя успехами (на один меньше порога) как обычная карточка.
 */
private fun applyLearnedCheckResult(
    cards: List<Card>,
    checkingIds: List<Long>,
    today: Int,
): List<Card> {
    val checking = cards.filter { it.id in checkingIds }.sortedBy { it.position }
    val knewIds = checking.filter { !it.failed }.map { it.id }
    val didNotKnowIds = checking.filter { it.failed }.map { it.id }.toSet()
    return moveCards(cards, knewIds, Section.LEARNED, today).map { card ->
        if (card.id in didNotKnowIds) card.copy(successes = CHECK_DID_NOT_KNOW_SUCCESSES, checking = false) else card
    }
}
