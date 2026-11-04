package app.deckly.ui.contract

/**
 * Строка списка колод. [activeEpoch] — эпоха, в которой идёт проход (`null`, если колода пуста или
 * всё выучено), [isAllLearned] — все карточки выучены, [isCurrent] — колода открыта по умолчанию.
 */
data class DeckRow(
    val id: Long,
    val name: String,
    val cardCount: Int,
    val activeEpoch: Int?,
    val isAllLearned: Boolean,
    val isCurrent: Boolean,
)

/** [canGoBack] — есть текущая колода, к которой можно вернуться. */
data class DeckListState(val decks: List<DeckRow>, val canGoBack: Boolean)

sealed interface DeckListAction {
    data class OpenDeck(val id: Long) : DeckListAction

    data object CreateDeck : DeckListAction

    data object Back : DeckListAction
}

/** Форма новой колоды: [canSave] — имя не пустое. */
data class DeckNameState(val name: String, val canSave: Boolean)

sealed interface DeckNameAction {
    data class ChangeName(val text: String) : DeckNameAction

    data object Save : DeckNameAction

    data object Cancel : DeckNameAction
}
