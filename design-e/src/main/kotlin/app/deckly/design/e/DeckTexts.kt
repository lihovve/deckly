package app.deckly.design.e

import app.deckly.ui.contract.ActionBlock
import app.deckly.ui.contract.PositionKind

/**
 * Тексты экрана колоды. Постоянные фразы взяты из `design/build.py` дословно, и `DeckTextsTest` проверяет, что каждая
 * там есть; склеенные из частей строки собирают функции ниже по форматам макета. Слово «карточка» согласуется с
 * числом ([formatCardCount]): в макете везде «карточек», это упрощение статичной вёрстки.
 */
internal object DeckTexts {
    // --- шапка ---
    const val LEARNING = "Обучение"
    const val BROWSING = "Просмотр"
    const val SUCCESSES = "Успехи"
    const val DECK_HEADING_SUFFIX = "Выбрать колоду"
    const val CREATE_CARD = "Создать карточку"
    const val CARD_ACTIONS = "Действия с карточкой"

    // --- вкладки ---
    const val SECTIONS = "Секции колоды"

    // --- меню карточки ---
    const val EDIT = "Редактировать"
    const val RETURN_TO_NEW = "Вернуть в новые"
    const val DELETE_CARD = "Удалить карточку"

    // --- нижняя строка ---
    const val POSTPONE = "В конец прохода"
    const val SHOW_ANSWER = "Показать ответ"
    const val SHOW_QUESTION = "Показать вопрос"
    const val NOT_KNEW = "Не знал"
    const val KNEW = "Знал"
    const val PREVIOUS_CARD = "Предыдущая карточка"
    const val NEXT_CARD = "Следующая карточка"
    const val START_PASS = "Начать проход"
    const val CONTINUE_PASS = "Продолжить"

    // --- причины, по которым нельзя отложить: в описании кнопки после двоеточия строчными ---
    const val REASON_ANSWER_REVEALED = "Ответ уже открыт"
    const val REASON_ALREADY_POSTPONED = "Уже отложена"
    const val REASON_LAST_IN_PASS = "Последняя"

    // --- лист ---
    const val QUESTION = "Вопрос"
    const val ANSWER = "Ответ"
    const val HANDWRITTEN_CARD = "Рукописная карточка"
    const val EMPTY_SECTION = "Пустая секция"
    const val NO_CARDS = "Нет карточек"
    const val NO_NEW_CARDS = "Нет новых карточек"
    const val NEXT_PASS_PREFIX = "Следующий проход: эпоха"
    const val INSERTED_PASS = "Вставной проход"
    const val RESUMED_PASS = "Продолжение прохода"
    const val REMAINING_PREFIX = "Осталось"

    // --- окна ---
    const val KEEP = "Оставить"
    const val DELETE_TITLE = "Удалить карточку?"
    const val DELETE_TEXT = "Вопрос и ответ будут удалены безвозвратно."
    const val DELETE = "Удалить"
    const val RETURN_TITLE = "Вернуть в новые?"
    const val RETURN_TEXT = "Успехи обнулятся."
    const val RETURN = "Вернуть"
    const val NEED_CARD_TITLE = "Пока нечего повторять"
    const val NEED_CARD_TEXT = "Нет карточек для обучения."
    const val DONE_TITLE = "На сегодня всё"
    const val OPEN_NEW = "Открыть новые"

    private val epochNames = listOf("I", "II", "III", "IV")

    fun formatPositionValue(position: Int, total: Int): String = "$position / $total"

    fun findPositionLabel(kind: PositionKind): String = when (kind) {
        PositionKind.LEARNING -> LEARNING
        PositionKind.BROWSING -> BROWSING
    }

    /** «Обучение, карточка 14 из 40»: `aria-label` счётчика позиции. */
    fun describePosition(kind: PositionKind, position: Int, total: Int): String =
        "${findPositionLabel(kind)}, карточка $position из $total"

    fun formatSuccessesValue(successes: Int, threshold: Int): String = "$successes / $threshold"

    /** «Немецкий A2. Выбрать колоду»: `aria-label` названия колоды. */
    fun describeDeckHeading(deckName: String): String = "$deckName. $DECK_HEADING_SUFFIX"

    fun formatEpoch(epoch: Int): String = epochNames[epoch - 1]

    /** «Осталось 60 из 100»: возврат к прерванному проходу. */
    fun formatRemaining(remaining: Int, total: Int): String = "$REMAINING_PREFIX $remaining из $total"

    fun describeNextPass(epoch: Int): String = "$NEXT_PASS_PREFIX ${formatEpoch(epoch)}"

    /**
     * «В конец прохода» или «В конец прохода: ответ уже открыт»: причина строчными, как `blocked_reason.lower()` в
     * макете. Блокировки «виден вопрос» у переноса не бывает (она у оценок), причину тогда не называем.
     */
    fun describePostpone(block: ActionBlock?): String {
        val reason = when (block) {
            ActionBlock.ANSWER_REVEALED -> REASON_ANSWER_REVEALED
            ActionBlock.ALREADY_POSTPONED -> REASON_ALREADY_POSTPONED
            ActionBlock.LAST_IN_PASS -> REASON_LAST_IN_PASS
            ActionBlock.FRONT_VISIBLE, null -> return POSTPONE
        }
        return "$POSTPONE: ${reason.lowercase()}"
    }
}

/** Тексты, которых в макетах нет: решения плана 4. `DeckTextsTest` фиксирует, что их ровно столько, сколько здесь. */
internal object DeckTextsBeyondMockup {
    /** Файл штрихов карточки не открылся: лист пуст, а описание для доступности говорит об этом (решение 6 плана 4). */
    const val DRAWING_DAMAGED = "Рисунок не открылся"
}
