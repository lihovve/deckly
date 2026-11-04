package app.deckly.ui.contract

/** Шесть секций колоды в порядке вкладок: пул новых, четыре эпохи, выученные. */
enum class SectionTab { NEW, EPOCH_1, EPOCH_2, EPOCH_3, EPOCH_4, LEARNED }

/** Какая сторона карточки видна на листе. */
enum class CardSideShown { FRONT, BACK }

/**
 * Доступность действия. Если оно недоступно, [block] называет причину: дизайн сам решает, спрятать
 * действие или показать серым, и подбирает слова для причины.
 */
data class ActionState(val isAvailable: Boolean, val block: ActionBlock? = null)

/** Почему действие недоступно. */
enum class ActionBlock {
    /** Оценить можно только после переворота: сейчас виден вопрос. */
    FRONT_VISIBLE,

    /** Перенести в конец прохода нельзя: ответ уже показан. */
    ANSWER_REVEALED,

    /** Карточка уже переносилась в этом проходе. */
    ALREADY_POSTPONED,

    /** Карточка последняя в проходе, переносить некуда. */
    LAST_IN_PASS,
}
