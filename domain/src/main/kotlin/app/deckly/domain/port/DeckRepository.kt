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

    /** Колода и все её карточки, карточки по id. Колоды нет — `IllegalStateException`. */
    suspend fun loadDeckState(deckId: Long): DeckState

    /**
     * Результат одного шага движка: колода и изменённые карточки сохраняются одной транзакцией.
     * Карточки, которой нет в базе, или колоды нет — `IllegalStateException`, в базе ничего не меняется:
     * новые карточки создаёт только `commitDraft` или импорт. Файлы штрихов удалённых карточек
     * стираются после коммита.
     */
    suspend fun saveDeckChanges(changes: DeckChanges)
}
