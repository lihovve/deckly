package app.deckly.domain.port

/** Состояние приложения вне колоды: приложение запускается там, где его закрыли. */
interface AppStateRepository {
    suspend fun loadCurrentDeckId(): Long?
    suspend fun saveCurrentDeckId(deckId: Long)
}
