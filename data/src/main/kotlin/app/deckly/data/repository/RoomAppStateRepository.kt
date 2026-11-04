package app.deckly.data.repository

import app.deckly.data.db.DecklyDatabase
import app.deckly.domain.port.AppStateRepository

class RoomAppStateRepository(private val database: DecklyDatabase) : AppStateRepository {

    override suspend fun loadCurrentDeckId(): Long? = database.appStateDao().findAppState()?.currentDeckId

    override suspend fun saveCurrentDeckId(deckId: Long) {
        database.appStateDao().updateCurrentDeck(deckId)
    }
}
