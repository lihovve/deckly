package app.deckly.domain.editor

import app.deckly.domain.model.Card
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.listSectionCards

/**
 * Черновик: то, что хранится и переживает перезапуск. [cardId] равен `null` при создании новой
 * карточки; [returnSection] — секция, в которую приложение вернётся после сохранения.
 */
data class Draft(
    val cardId: Long?,
    val front: List<Stroke>,
    val back: List<Stroke>,
    val visibleSide: Side,
    val returnSection: Section,
)

/**
 * Сохранение черновика. Новая карточка встаёт в конец пула с нулём успехов и id [newCardId]; правка
 * существующей карточку не меняет, меняется только [CardContent] (тогда [newCardId] не используется).
 * Пустые стороны допустимы.
 */
fun saveDraft(
    state: DeckState,
    draft: Draft,
    newCardId: Long,
    today: Int,
    now: Long,
): Pair<DeckState, CardContent> {
    val editedCardId = draft.cardId
    if (editedCardId != null) {
        requireNotNull(state.cards.find { it.id == editedCardId }) { "Карточки $editedCardId нет в колоде" }
        return state to CardContent(editedCardId, draft.front, draft.back)
    }
    val position = (state.listSectionCards(Section.NEW).maxOfOrNull { it.position } ?: -1) + 1
    val card = Card(
        id = newCardId,
        section = Section.NEW,
        position = position,
        successes = 0,
        sectionDay = today,
        lastSuccessDay = null,
        createdAt = now,
        postponed = false,
        answerRevealed = false,
        failed = false,
        checking = false,
        excludedFromPass = false,
    )
    return state.copy(cards = state.cards + card) to CardContent(newCardId, draft.front, draft.back)
}
