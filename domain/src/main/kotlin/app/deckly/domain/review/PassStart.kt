package app.deckly.domain.review

import app.deckly.domain.model.Card
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.epochNumber
import app.deckly.domain.model.findEpochSection
import kotlin.random.Random

private const val FIRST_EPOCH_REFILL_LIMIT = 40
private const val LEARNED_CHECK_LIMIT = 10
private const val MAX_OFFSET = 5

/**
 * Старт прохода эпохи (ТЗ, «Алгоритм обучения»): выпуск, проверка выученных, добор, перемешивание —
 * строго в таком порядке; затем у карточек эпохи сбрасываются флаги прохода. Поля прохода в `Deck`
 * не трогает: их ставит движок.
 */
fun startPass(state: DeckState, epoch: Int, today: Int, random: Random): DeckState {
    val section = findEpochSection(epoch)
    var cards = releaseCards(state.cards, epoch, today)
    if (epoch == 4 && isLearnedCheckPass(state.deck)) {
        cards = takeLearnedForCheck(cards, today)
    }
    if (epoch == 1) {
        cards = refillFromPool(cards, today)
    }
    val orderedIds = cards.filter { it.section == section }.sortedBy { it.position }.map { it.id }
    val shuffledIds = shuffleWithOffset(orderedIds, state.deck.tail[epoch], random)
    val newPositions = shuffledIds.withIndex().associate { (index, id) -> id to index }
    val startedCards = cards.map { card ->
        val position = newPositions[card.id] ?: return@map card
        card.copy(
            position = position,
            postponed = false,
            answerRevealed = false,
            failed = false,
            excludedFromPass = false,
        )
    }
    return state.copy(cards = startedCards)
}

/** Карточки прохода эпохи в порядке показа: без тех, что попали в эпоху после старта прохода. */
fun listPassCards(state: DeckState, epoch: Int): List<Card> {
    val section = findEpochSection(epoch)
    return state.cards
        .filter { it.section == section && !it.excludedFromPass }
        .sortedBy { it.position }
}

/**
 * Перемешивание с отступом (ТЗ): первые m мест не занимают карточки с последних m мест прошлого
 * прохода (последние записи [previousTail]); m = min(5, ⌈N/4⌉), при одной карточке правила нет.
 * Карточки из [previousTail], которых во входе уже нет, не учитываются.
 */
fun shuffleWithOffset(cardIds: List<Long>, previousTail: List<Long>, random: Random): List<Long> {
    val size = cardIds.size
    if (size <= 1) return cardIds
    val frontSize = minOf(MAX_OFFSET, (size + 3) / 4)
    val result = cardIds.shuffled(random).toMutableList()
    val present = cardIds.toSet()
    val forbidden = previousTail.takeLast(frontSize).filter { it in present }.toSet()
    for (index in 0 until frontSize) {
        if (result[index] !in forbidden) continue
        // Подходящая замена есть всегда: frontSize <= size / 2.
        val swapIndex = (frontSize until size).first { result[it] !in forbidden }
        result[index] = result[swapIndex].also { result[swapIndex] = result[index] }
    }
    return result
}

private fun releaseCards(cards: List<Card>, epoch: Int, today: Int): List<Card> {
    val section = findEpochSection(epoch)
    val releasedIds = cards
        .filter { it.section == section && isReadyForRelease(it, epoch) }
        .sortedBy { it.position }
        .map { it.id }
    return moveCards(cards, releasedIds, findReleaseSection(epoch), today)
}

private fun isReadyForRelease(card: Card, epoch: Int): Boolean {
    if (card.successes < findReleaseThreshold(epoch)) return false
    if (epoch != 1) return true
    // Между заучиванием и выпуском из эпохи 1 должна пройти хотя бы одна ночь.
    val lastSuccessDay = card.lastSuccessDay ?: return false
    return lastSuccessDay > card.sectionDay
}

/** Сколько успехов выпускает карточку из эпохи: 4, 4, 4 и 3 (ТЗ, «Пороги успехов»). */
fun findReleaseThreshold(epoch: Int): Int = if (epoch == 4) 3 else 4

private fun findReleaseSection(epoch: Int): Section =
    if (epoch == 4) Section.LEARNED else findEpochSection(epoch + 1)

private fun takeLearnedForCheck(cards: List<Card>, today: Int): List<Card> {
    val checkedIds = cards
        .filter { isReadyForCheck(it, today) }
        .sortedBy { it.position }
        .take(LEARNED_CHECK_LIMIT)
        .map { it.id }
    val checkedIdSet = checkedIds.toSet()
    return moveCards(cards, checkedIds, Section.EPOCH_4, today)
        .map { if (it.id in checkedIdSet) it.copy(checking = true) else it }
}

private fun refillFromPool(cards: List<Card>, today: Int): List<Card> {
    val room = FIRST_EPOCH_REFILL_LIMIT - cards.count { it.section == Section.EPOCH_1 }
    if (room <= 0) return cards
    val refillIds = cards
        .filter { it.section == Section.NEW }
        .sortedBy { it.position }
        .take(room)
        .map { it.id }
    return moveCards(cards, refillIds, Section.EPOCH_1, today)
}

/**
 * Переносит карточки в конец секции назначения в порядке [movingIds]. [successes] — успехи после
 * переезда: ноль, а при спуске после ошибки на один меньше порога новой эпохи.
 */
internal fun moveCards(
    cards: List<Card>,
    movingIds: List<Long>,
    destination: Section,
    today: Int,
    successes: Int = 0,
): List<Card> {
    if (movingIds.isEmpty()) return cards
    val firstPosition = (cards.filter { it.section == destination }.maxOfOrNull { it.position } ?: -1) + 1
    val newPositions = movingIds.withIndex().associate { (index, id) -> id to firstPosition + index }
    return cards.map { card ->
        val position = newPositions[card.id] ?: return@map card
        moveCard(card, destination, position, today, successes)
    }
}

/**
 * Карточка в новой секции: успехи и флаги прохода обнулены, день попадания — сегодня. В эпоху она
 * приезжает с [Card.excludedFromPass]: если эпоха стартует сейчас (добор, проверка выученных),
 * `startPass` флаг сбросит, иначе карточка дождётся старта прохода своей эпохи.
 */
private fun moveCard(card: Card, destination: Section, position: Int, today: Int, successes: Int): Card = card.copy(
    section = destination,
    position = position,
    successes = successes,
    sectionDay = today,
    postponed = false,
    answerRevealed = false,
    failed = false,
    checking = false,
    excludedFromPass = destination.epochNumber != null,
)
