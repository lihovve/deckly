package app.deckly.domain.port

import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState

/**
 * Строка списка колод: всё, что список показывает, без самих карточек. [cardCount] — все карточки колоды, в том числе
 * ждущие ночи, [learnedCount] — из них в стеке выученных.
 */
data class DeckSummary(
    val id: Long,
    val name: String,
    val currentEpoch: Int,
    val cardCount: Int,
    val learnedCount: Int,
)

/** Колоды и состояние алгоритма. Реализация — модуль `data`. */
interface DeckRepository {

    /**
     * Колоды по id, то есть в порядке создания, со счётчиками карточек. Один запрос: карточки и состояния колод для
     * списка не читаются.
     */
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
