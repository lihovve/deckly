package app.deckly.design.e

import app.deckly.ui.contract.ActionBlock
import app.deckly.ui.contract.PositionKind

/**
 * Тексты экрана колоды. Интерфейс английский, постоянные фразы взяты из `design/build.py` дословно, и `DeckTextsTest`
 * проверяет, что каждая там есть; склеенные из частей строки собирают функции ниже по форматам макета. Слово «card»
 * согласуется с числом ([formatCardCount]): в макете везде «cards», это упрощение статичной вёрстки.
 */
internal object DeckTexts {
    // --- шапка ---
    const val LEARNING = "Learning"
    const val BROWSING = "Browse"
    const val SUCCESSES = "Successes"
    const val DECK_HEADING_SUFFIX = "Choose deck"
    const val CREATE_CARD = "Create card"
    const val CARD_ACTIONS = "Card actions"
    const val CLOSE_MENU = "Close menu"

    // --- вкладки ---
    const val SECTIONS = "Deck sections"
    const val CURRENTLY_LEARNING = "currently learning"

    // --- меню карточки ---
    const val EDIT = "Edit"
    const val RETURN_TO_NEW = "Return to New"
    const val DELETE_CARD = "Delete card"

    // --- нижняя строка ---
    const val POSTPONE = "Move to end of pass"
    const val SHOW_ANSWER = "Show answer"
    const val SHOW_QUESTION = "Show question"
    const val NOT_KNEW = "Missed it"
    const val KNEW = "Nailed it"
    const val PREVIOUS_CARD = "Previous card"
    const val NEXT_CARD = "Next card"
    const val START_PASS = "Start pass"
    const val CONTINUE_PASS = "Continue"

    // --- причины, по которым нельзя отложить: в описании кнопки после двоеточия строчными ---
    const val REASON_ANSWER_REVEALED = "Answer already revealed"
    const val REASON_ALREADY_POSTPONED = "Already postponed"
    const val REASON_LAST_IN_PASS = "Last card"

    // --- лист ---
    const val QUESTION = "Question"
    const val ANSWER = "Answer"
    const val HANDWRITTEN_CARD = "Handwritten card"
    const val DRAWING_UNAVAILABLE = "Drawing unavailable"
    const val EMPTY_SECTION = "Empty section"
    const val NO_CARDS = "No cards"
    const val NO_NEW_CARDS = "No new cards"
    const val NEXT_PASS_PREFIX = "Next pass: epoch"
    const val INSERTED_PASS = "Extra pass"
    const val RESUMED_PASS = "Resume pass"
    const val REMAINING_SUFFIX = "remaining"

    // --- окна ---
    const val KEEP = "Keep"
    const val DELETE_TITLE = "Delete card?"
    const val DELETE_TEXT = "Both sides will be deleted. You can't undo this."
    const val DELETE = "Delete"
    const val RETURN_TITLE = "Return to New?"
    const val RETURN_TEXT = "Your success count will reset."
    const val RETURN = "Return"
    const val NEED_CARD_TITLE = "Nothing to review yet"
    const val NEED_CARD_TEXT = "No cards to study."
    const val DONE_TITLE = "All done for today"
    const val OPEN_NEW = "Open New"

    private val epochNames = listOf("I", "II", "III", "IV")

    fun formatPositionValue(position: Int, total: Int): String = "$position / $total"

    fun findPositionLabel(kind: PositionKind): String = when (kind) {
        PositionKind.LEARNING -> LEARNING
        PositionKind.BROWSING -> BROWSING
    }

    /** «Learning, card 14 of 40»: `aria-label` счётчика позиции. */
    fun describePosition(kind: PositionKind, position: Int, total: Int): String =
        "${findPositionLabel(kind)}, card $position of $total"

    fun formatSuccessesValue(successes: Int, threshold: Int): String = "$successes / $threshold"

    /** «English A2. Choose deck»: `aria-label` названия колоды. */
    fun describeDeckHeading(deckName: String): String = "$deckName. $DECK_HEADING_SUFFIX"

    fun formatEpoch(epoch: Int): String = epochNames[epoch - 1]

    /** «60 of 100 remaining»: возврат к прерванному проходу. */
    fun formatRemaining(remaining: Int, total: Int): String = "$remaining of $total $REMAINING_SUFFIX"

    fun describeNextPass(epoch: Int): String = "$NEXT_PASS_PREFIX ${formatEpoch(epoch)}"

    /** «New, 112 cards» или «II, 23 cards, currently learning»: `aria-label` вкладки секции. */
    fun describeTab(label: String, cardCount: Int, isActive: Boolean): String =
        "$label, ${formatCardCount(cardCount)}" + if (isActive) ", $CURRENTLY_LEARNING" else ""

    /**
     * «Move to end of pass» или «Move to end of pass: answer already revealed»: причина строчными, как
     * `blocked_reason.lower()` в макете. Блокировки «виден вопрос» у переноса не бывает (она у оценок), причину тогда
     * не называем.
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
