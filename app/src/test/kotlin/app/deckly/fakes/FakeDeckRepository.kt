package app.deckly.fakes

import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckSummary
import kotlinx.coroutines.CompletableDeferred

/**
 * Одна колода в памяти. Записанное применяется к [stored] так же, как база применяет [DeckChanges];
 * [savedChanges] хранит все записи по порядку. Пока задан [saveGate], запись стоит и ждёт его завершения.
 */
class FakeDeckRepository(initial: DeckState) : DeckRepository {
    var stored: DeckState = initial
        private set
    val savedChanges = mutableListOf<DeckChanges>()
    var saveGate: CompletableDeferred<Unit>? = null

    override suspend fun loadDeckState(deckId: Long): DeckState {
        check(deckId == stored.deck.id) { "Колоды $deckId нет" }
        return stored
    }

    override suspend fun saveDeckChanges(changes: DeckChanges) {
        saveGate?.await()
        savedChanges += changes
        val upsertedIds = changes.upsertedCards.map { it.id }.toSet()
        val kept = stored.cards.filter { it.id !in changes.deletedCardIds && it.id !in upsertedIds }
        val replaced = stored.cards.mapNotNull { old -> changes.upsertedCards.find { it.id == old.id } }
        val added = changes.upsertedCards.filter { new -> stored.cards.none { it.id == new.id } }
        // Порядок карточек в списке не важен алгоритму: он везде сортирует по position.
        stored = DeckState(changes.deck, kept + replaced + added)
    }

    override suspend fun listDecks(): List<DeckSummary> = error("Порт не нужен тесту")

    override suspend fun createDeck(name: String): Deck = error("Порт не нужен тесту")

    override suspend fun renameDeck(deckId: Long, name: String) = error("Порт не нужен тесту")

    override suspend fun deleteDeck(deckId: Long) = error("Порт не нужен тесту")
}
