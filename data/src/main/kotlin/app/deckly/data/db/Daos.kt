package app.deckly.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
abstract class DeckDao {

    /** Возвращает выданный id: колоды нумерует `AUTOINCREMENT`. */
    @Insert
    abstract suspend fun insertDeck(deck: DeckEntity): Long

    @Query("DELETE FROM deck WHERE id = :deckId")
    abstract suspend fun deleteDeck(deckId: Long)
}

@Dao
abstract class CardDao {

    @Insert
    abstract suspend fun insertCard(card: CardEntity)

    /** Обновляет состояние алгоритма, не трогая колоду и ссылку на файл штрихов. */
    @Update(entity = CardEntity::class)
    abstract suspend fun updateCardState(update: CardStateUpdate)

    @Query("SELECT * FROM card WHERE id = :cardId")
    abstract suspend fun findCard(cardId: Long): CardEntity?

    @Query("SELECT * FROM card WHERE deck_id = :deckId ORDER BY id")
    abstract suspend fun listCards(deckId: Long): List<CardEntity>
}

@Dao
abstract class AppStateDao {

    @Query("SELECT * FROM app_state WHERE id = 1")
    abstract suspend fun findAppState(): AppStateEntity?
}
