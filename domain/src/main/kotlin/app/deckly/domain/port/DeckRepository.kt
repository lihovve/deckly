package app.deckly.domain.port

import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState

data class DeckSummary(val id: Long, val name: String)

/** Колоды и состояние алгоритма. Реализация — модуль `data`. */
interface DeckRepository {

    /** Колоды по id, то есть в порядке создания. */
    suspend fun listDecks(): List<DeckSummary>

    /** Новая колода в начальном состоянии (`createDeck(id, name)` из domain). */
    suspend fun createDeck(name: String): Deck

    suspend fun renameDeck(deckId: Long, name: String)

    /** Удаляет колоду, её карточки, файлы их штрихов и черновик, если он этой колоды. */
    suspend fun deleteDeck(deckId: Long)

    suspend fun loadDeckState(deckId: Long): DeckState

    /** Результат одного шага движка: колода и изменённые карточки сохраняются одной транзакцией. */
    suspend fun saveDeckChanges(changes: DeckChanges)
}
