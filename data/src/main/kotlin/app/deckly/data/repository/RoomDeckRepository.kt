package app.deckly.data.repository

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.mapCard
import app.deckly.data.db.mapCardState
import app.deckly.data.db.mapDeck
import app.deckly.data.db.mapDeckEntity
import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckSummary
import app.deckly.domain.model.createDeck as buildInitialDeck

/**
 * Колоды и состояние алгоритма в Room. Файлы штрихов удаляются [deleteFile] только после коммита
 * транзакции: при откате ни один файл не тронут.
 */
class RoomDeckRepository(
    private val database: DecklyDatabase,
    private val deleteFile: (String) -> Unit,
) : DeckRepository {

    override suspend fun listDecks(): List<DeckSummary> =
        database.deckDao().listDeckNames().map { DeckSummary(it.id, it.name) }

    override suspend fun createDeck(name: String): Deck {
        val id = database.deckDao().insertDeck(mapDeckEntity(buildInitialDeck(0, name)))
        return buildInitialDeck(id, name)
    }

    override suspend fun renameDeck(deckId: Long, name: String) {
        val updated = database.deckDao().updateDeckName(deckId, name)
        check(updated == 1) { "Колоды $deckId нет в базе" }
    }

    override suspend fun deleteDeck(deckId: Long) {
        val files = database.withWriteTransaction {
            val draft = database.draftDao().findDraft()?.takeIf { it.deckId == deckId }
            val files = database.cardDao().listDeckContentFiles(deckId) + listOfNotNull(draft?.contentFile)
            database.draftDao().deleteDraftOfDeck(deckId)
            database.appStateDao().clearCurrentDeck(deckId)
            database.deckDao().deleteDeck(deckId)
            files
        }
        files.forEach(deleteFile)
    }

    override suspend fun loadDeckState(deckId: Long): DeckState =
        database.withReadTransaction {
            val deck = checkNotNull(database.deckDao().findDeck(deckId)) { "Колоды $deckId нет в базе" }
            DeckState(mapDeck(deck), database.cardDao().listCards(deckId).map(::mapCard))
        }

    override suspend fun saveDeckChanges(changes: DeckChanges) {
        val files = database.withWriteTransaction {
            val deckId = changes.deck.id
            val updatedDecks = database.deckDao().updateDeck(mapDeckEntity(changes.deck))
            check(updatedDecks == 1) { "Колоды $deckId нет в базе" }
            for (card in changes.upsertedCards) {
                val updatedCards = database.cardDao().updateCardState(mapCardState(card))
                // Новые карточки создаёт только commitDraft или импорт.
                check(updatedCards == 1) { "Карточки ${card.id} нет в базе" }
            }
            val files = database.cardDao().listContentFiles(changes.deletedCardIds)
            database.cardDao().deleteCards(changes.deletedCardIds)
            files
        }
        files.forEach(deleteFile)
    }
}
