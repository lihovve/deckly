package app.deckly.domain.review

import app.deckly.domain.model.Card
import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.epochNumber
import app.deckly.domain.model.findEpochSection
import app.deckly.domain.model.listSectionCards
import kotlin.math.max

/**
 * Открыта ли активная эпоха с начатым проходом. Только тогда курсор `passCursor` ведёт по проходу;
 * в любой другой открытой секции карточки листаются курсором `browseCursor`.
 */
internal fun isActivePassOpen(deck: Deck): Boolean =
    deck.passStarted && deck.activeSection == findEpochSection(deck.currentEpoch)

/**
 * Почему карточку нельзя отложить в конец прохода, или `null`, если можно (ТЗ, «Перенос в конец
 * прохода»). Если причин несколько, названа самая сильная: открытый ответ, затем прошлый перенос,
 * затем последнее место.
 */
internal fun findPostponeBlock(state: DeckState, card: Card): UnavailableReason? = when {
    card.answerRevealed -> UnavailableReason.ANSWER_REVEALED
    card.postponed -> UnavailableReason.ALREADY_POSTPONED
    state.deck.passCursor == listPassCards(state, state.deck.currentEpoch).lastIndex ->
        UnavailableReason.LAST_IN_PASS

    else -> null
}

/** Карточка, которую показывает приложение: по `passCursor` прохода или по `browseCursor` секции. */
fun findCurrentCard(state: DeckState): Card? {
    val deck = state.deck
    return if (isActivePassOpen(deck)) {
        listPassCards(state, deck.currentEpoch).getOrNull(deck.passCursor)
    } else {
        state.listSectionCards(deck.activeSection).getOrNull(deck.browseCursor)
    }
}

/** Первое открытие ответа в проходе блокирует перенос. Вне начатого прохода активной эпохи — без изменений. */
fun revealAnswer(state: DeckState): DeckState {
    if (!isActivePassOpen(state.deck)) return state
    val card = findCurrentCard(state) ?: return state
    return replaceCard(state, card.copy(answerRevealed = true))
}

fun rateKnew(state: DeckState, today: Int): DeckState {
    val card = requireRatableCard(state)
    val rated = card.copy(successes = card.successes + 1, lastSuccessDay = today)
    return advancePass(replaceCard(state, rated))
}

/** Эпоха 1: успех минус один, не ниже нуля; эпохи 2–4: карточка помечается и в конце прохода спускается. */
fun rateDidNotKnow(state: DeckState): DeckState {
    val card = requireRatableCard(state)
    val rated = if (state.deck.currentEpoch == 1) {
        card.copy(successes = max(0, card.successes - 1))
    } else {
        card.copy(failed = true)
    }
    return advancePass(replaceCard(state, rated))
}

/** Переносит текущую карточку в конец прохода; курсор остаётся на месте, и показывается следующая. */
fun postponeCard(state: DeckState): DeckState {
    val card = requireCurrentPassCard(state)
    val block = findPostponeBlock(state, card)
    require(block == null) { "Перенос недоступен: $block" }
    val section = findEpochSection(state.deck.currentEpoch)
    val endPosition = state.cards.filter { it.section == section }.maxOf { it.position } + 1
    return replaceCard(state, card.copy(position = endPosition, postponed = true))
}

fun deleteCard(state: DeckState, cardId: Long): DeckState {
    requireCard(state, cardId)
    return DeckState(buildDeckAfterRemoval(state, cardId), state.cards.filter { it.id != cardId })
}

/** Карточка эпохи уходит в конец пула новых с нулём успехов и сброшенными флагами прохода. */
fun returnToNew(state: DeckState, cardId: Long, today: Int): DeckState {
    val card = requireCard(state, cardId)
    require(card.section.epochNumber != null) { "В пул новых возвращаются только карточки эпох: ${card.section}" }
    val endPosition = (state.listSectionCards(Section.NEW).maxOfOrNull { it.position } ?: -1) + 1
    val returned = card.copy(
        section = Section.NEW,
        position = endPosition,
        successes = 0,
        sectionDay = today,
        postponed = false,
        answerRevealed = false,
        failed = false,
        checking = false,
        excludedFromPass = false,
    )
    return DeckState(buildDeckAfterRemoval(state, cardId), state.cards.map { if (it.id == cardId) returned else it })
}

/** Листание открытой секции по кругу; в пустой секции курсор остаётся на нуле. */
fun browseSection(state: DeckState, step: Int): DeckState {
    val size = state.listSectionCards(state.deck.activeSection).size
    if (size == 0) return state
    val cursor = (state.deck.browseCursor + step).mod(size)
    return state.copy(deck = state.deck.copy(browseCursor = cursor))
}

private fun requireCurrentPassCard(state: DeckState): Card {
    require(isActivePassOpen(state.deck)) { "Оценка и перенос возможны только в начатом проходе открытой активной эпохи" }
    return requireNotNull(findCurrentCard(state)) { "В проходе нет текущей карточки" }
}

private fun requireRatableCard(state: DeckState): Card {
    val card = requireCurrentPassCard(state)
    require(card.answerRevealed) { "Оценка возможна только после открытия ответа" }
    return card
}

private fun requireCard(state: DeckState, cardId: Long): Card =
    requireNotNull(state.cards.find { it.id == cardId }) { "Карточки $cardId нет в колоде" }

private fun replaceCard(state: DeckState, card: Card): DeckState =
    state.copy(cards = state.cards.map { if (it.id == card.id) card else it })

private fun advancePass(state: DeckState): DeckState =
    state.copy(deck = state.deck.copy(passCursor = state.deck.passCursor + 1))

/**
 * Колода после того, как карточка [cardId] выпала из своей секции (удалена или возвращена в пул):
 * курсоры прохода, прерванного прохода и листания сдвинуты так, чтобы следующей осталась та же
 * карточка. Индексы считаются по состоянию до удаления.
 */
private fun buildDeckAfterRemoval(state: DeckState, cardId: Long): Deck {
    val deck = state.deck
    val passCursor = if (deck.passStarted) {
        computeCursorAfterRemoval(deck.passCursor, listPassCards(state, deck.currentEpoch), cardId)
    } else {
        deck.passCursor
    }
    val pausedCursor = if (deck.pausedEpoch != 0) {
        computeCursorAfterRemoval(deck.pausedCursor, listPassCards(state, deck.pausedEpoch), cardId)
    } else {
        deck.pausedCursor
    }
    val browsed = state.listSectionCards(deck.activeSection)
    val shiftedBrowseCursor = computeCursorAfterRemoval(deck.browseCursor, browsed, cardId)
    val browseCursor = if (shiftedBrowseCursor >= browsed.count { it.id != cardId }) 0 else shiftedBrowseCursor
    return deck.copy(passCursor = passCursor, pausedCursor = pausedCursor, browseCursor = browseCursor)
}

private fun computeCursorAfterRemoval(cursor: Int, cards: List<Card>, removedId: Long): Int {
    val index = cards.indexOfFirst { it.id == removedId }
    return if (index in 0 until cursor) cursor - 1 else cursor
}
