package app.deckly.fakes

import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.createDeck as buildNewDeck
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckSummary

/**
 * Несколько колод в памяти для списка колод и формы новой колоды. [createdNames] хранит имена по порядку
 * создания, новая колода получает следующий id и встаёт в конец списка, как в базе.
 */
class FakeDeckCatalog(initial: List<DeckState> = emptyList(), private val onDelete: (Long) -> Unit = {}) : DeckRepository {
    val decks = initial.toMutableList()
    val createdNames = mutableListOf<String>()

    /** Переименования по порядку: id колоды и новое название. */
    val renames = mutableListOf<Pair<Long, String>>()

    /** Удалённые колоды по порядку. [onDelete] — что ещё делает хранилище при удалении (пометку «текущая» снимает оно). */
    val deletedIds = mutableListOf<Long>()
    private var nextDeckId = (initial.maxOfOrNull { it.deck.id } ?: 0L) + 1

    /** Сколько раз читали состояние колоды целиком: список колод его не читает. */
    var stateLoads = 0
        private set

    override suspend fun listDecks(): List<DeckSummary> = decks.map(::summarize)

    override suspend fun createDeck(name: String): Deck {
        createdNames += name
        val deck = buildNewDeck(nextDeckId++, name)
        decks += DeckState(deck, emptyList())
        return deck
    }

    override suspend fun loadDeckState(deckId: Long): DeckState {
        stateLoads++
        return decks.first { it.deck.id == deckId }
    }

    override suspend fun renameDeck(deckId: Long, name: String) {
        renames += deckId to name
        val index = decks.indexOfFirst { it.deck.id == deckId }
        decks[index] = decks[index].copy(deck = decks[index].deck.copy(name = name))
    }

    override suspend fun deleteDeck(deckId: Long) {
        deletedIds += deckId
        decks.removeAll { it.deck.id == deckId }
        onDelete(deckId)
    }

    override suspend fun saveDeckChanges(changes: DeckChanges) = error("Порт не нужен тесту")
}

/** Сводка колоды из её состояния: то, что репозиторий отдаёт списком колод одним запросом. */
fun summarize(state: DeckState): DeckSummary = DeckSummary(
    id = state.deck.id,
    name = state.deck.name,
    currentEpoch = state.deck.currentEpoch,
    cardCount = state.cards.size,
    learnedCount = state.cards.count { it.section == Section.LEARNED },
)
