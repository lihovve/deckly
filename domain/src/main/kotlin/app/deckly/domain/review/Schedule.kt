package app.deckly.domain.review

import app.deckly.domain.model.Card
import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.findEpochSection
import kotlin.math.max

private const val LEARNED_CHECK_MIN_DAYS = 30

/**
 * Эпоха, которая получает ход после завершённого прохода (ТЗ, «Расписание эпох»).
 * [passCountAfter] — счётчик завершённой эпохи уже после увеличения. Пока кто-то ждёт
 * ([waitingEpoch] != 0), правило по счётчикам не действует и ход получает эпоха 1: созрела ли
 * ожидающая, решает движок.
 */
fun chooseNextEpoch(finishedEpoch: Int, passCountAfter: Int, waitingEpoch: Int): Int {
    require(finishedEpoch in 1..4) { "Эпоха должна быть от 1 до 4: $finishedEpoch" }
    if (waitingEpoch != 0) return 1
    val isNextEpochDue = when (finishedEpoch) {
        1 -> passCountAfter % 5 == 0
        2 -> passCountAfter % 3 == 0
        3 -> passCountAfter % 2 == 0
        else -> false
    }
    return if (isNextEpochDue) finishedEpoch + 1 else 1
}

/**
 * Пуста ли эпоха. Эпоха 4 на проходе с проверкой выученных (при нечётном `pass4`) не пуста, если в
 * неё придут карточки из стека.
 */
fun isEpochEmpty(state: DeckState, epoch: Int, today: Int): Boolean {
    val section = findEpochSection(epoch)
    if (state.cards.any { it.section == section }) return false
    val hasLearnedCheck = epoch == 4 && isLearnedCheckPass(state.deck)
    return !(hasLearnedCheck && state.cards.any { isReadyForCheck(it, today) })
}

/**
 * Созрела ли эпоха (ТЗ, «Нижняя граница интервала»): эпоха 1 и пустая эпоха созрели всегда; иначе с
 * конца её прошлого непустого прохода должно пройти не меньше 1, 3 или 7 дней. Отрицательная
 * разница дней (дату перевели назад) считается нулём.
 */
fun isEpochMature(state: DeckState, epoch: Int, today: Int): Boolean {
    if (epoch == 1) return true
    if (isEpochEmpty(state, epoch, today)) return true
    val lastPassDay = state.deck.lastPassDay[epoch] ?: return true
    return max(0, today - lastPassDay) >= findMinIntervalDays(epoch)
}

/** Лежит ли карточка в стеке выученных не меньше 30 дней и готова ли к проверке. */
fun isReadyForCheck(card: Card, today: Int): Boolean =
    card.section == Section.LEARNED && max(0, today - card.sectionDay) >= LEARNED_CHECK_MIN_DAYS

/**
 * Идёт ли следующий проход эпохи 4 с проверкой выученных: на каждом втором, то есть при нечётном
 * `pass4` (число уже завершённых проходов). Правило живёт здесь одно: им пользуются и пустота эпохи,
 * и старт прохода.
 */
internal fun isLearnedCheckPass(deck: Deck): Boolean = deck.passCount[4] % 2 == 1

private fun findMinIntervalDays(epoch: Int): Int = when (epoch) {
    2 -> 1
    3 -> 3
    4 -> 7
    else -> 0
}
