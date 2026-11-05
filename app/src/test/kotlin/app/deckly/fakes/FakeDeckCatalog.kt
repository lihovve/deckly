package app.deckly.fakes

import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.createDeck as buildNewDeck
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckSummary

/**
 * Несколько колод в памяти для списка колод и формы новой колоды. [createdNames] хранит имена по порядку
 * создания, новая колода получает следующий id и встаёт в конец списка, как в базе.
 */
class FakeDeckCatalog(initial: List<DeckState> = emptyList()) : DeckRepository {
    val decks = initial.toMutableList()
    val createdNames = mutableListOf<String>()
    private var nextDeckId = (initial.maxOfOrNull { it.deck.id } ?: 0L) + 1

    override suspend fun listDecks(): List<DeckSummary> = decks.map { DeckSummary(it.deck.id, it.deck.name) }

    override suspend fun createDeck(name: String): Deck {
        createdNames += name
        val deck = buildNewDeck(nextDeckId++, name)
        decks += DeckState(deck, emptyList())
        return deck
    }

    override suspend fun loadDeckState(deckId: Long): DeckState = decks.first { it.deck.id == deckId }

    override suspend fun renameDeck(deckId: Long, name: String) = error("Порт не нужен тесту")

    override suspend fun deleteDeck(deckId: Long) = error("Порт не нужен тесту")

    override suspend fun saveDeckChanges(changes: DeckChanges) = error("Порт не нужен тесту")
}
