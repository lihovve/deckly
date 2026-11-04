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

    @Query(
        "DELETE\n" +
        "  FROM deck\n" +
        " WHERE deck.id = :deckId",
    )
    abstract suspend fun deleteDeck(deckId: Long)
}

@Dao
abstract class CardDao {

    @Insert
    abstract suspend fun insertCard(card: CardEntity)

    /** Обновляет состояние алгоритма, не трогая колоду и ссылку на файл штрихов. */
    @Update(entity = CardEntity::class)
    abstract suspend fun updateCardState(update: CardStateUpdate)

    @Query(
        "SELECT *\n" +
        "  FROM card\n" +
        " WHERE card.id = :cardId",
    )
    abstract suspend fun findCard(cardId: Long): CardEntity?

    @Query(
        "  SELECT *\n" +
        "    FROM card\n" +
        "   WHERE card.deck_id = :deckId\n" +
        "ORDER BY card.id",
    )
    abstract suspend fun listCards(deckId: Long): List<CardEntity>
}

@Dao
abstract class AppStateDao {

    @Query(
        "SELECT *\n" +
        "  FROM app_state\n" +
        " WHERE app_state.id = 1",
    )
    abstract suspend fun findAppState(): AppStateEntity?
}
