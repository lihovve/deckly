package app.deckly.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update

/** Колонки `id` и `name` колоды: список колод не читает остальное. */
data class DeckNameRow(val id: Long, val name: String)

/** Какой файл штрихов у карточки: всё, что нужно уборке. */
data class CardFileRow(val id: Long, @ColumnInfo(name = "content_file") val contentFile: String)

@Dao
abstract class DeckDao {

    /** Возвращает выданный id: колоды нумерует `AUTOINCREMENT`. */
    @Insert
    abstract suspend fun insertDeck(deck: DeckEntity): Long

    /** Возвращает число обновлённых строк: 0, если такой колоды нет. */
    @Update
    abstract suspend fun updateDeck(deck: DeckEntity): Int

    @Query(
        "UPDATE deck\n" +
        "   SET name = :name\n" +
        " WHERE deck.id = :deckId",
    )
    abstract suspend fun updateDeckName(deckId: Long, name: String): Int

    @Query(
        "SELECT *\n" +
        "  FROM deck\n" +
        " WHERE deck.id = :deckId",
    )
    abstract suspend fun findDeck(deckId: Long): DeckEntity?

    @Query(
        "  SELECT deck.id,\n" +
        "         deck.name\n" +
        "    FROM deck\n" +
        "ORDER BY deck.id",
    )
    abstract suspend fun listDeckNames(): List<DeckNameRow>

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

    /**
     * Обновляет состояние алгоритма, не трогая колоду и ссылку на файл штрихов. Возвращает число
     * обновлённых строк: 0, если такой карточки нет.
     */
    @Update(entity = CardEntity::class)
    abstract suspend fun updateCardState(update: CardStateUpdate): Int

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

    @Query(
        "SELECT card.content_file\n" +
        "  FROM card\n" +
        " WHERE card.id IN (:cardIds)",
    )
    abstract suspend fun listContentFiles(cardIds: Collection<Long>): List<String>

    @Query(
        "SELECT card.content_file\n" +
        "  FROM card\n" +
        " WHERE card.deck_id = :deckId",
    )
    abstract suspend fun listDeckContentFiles(deckId: Long): List<String>

    @Query(
        "SELECT card.id,\n" +
        "       card.content_file\n" +
        "  FROM card",
    )
    abstract suspend fun listCardFiles(): List<CardFileRow>

    /** Подменяет ссылку на файл штрихов, не трогая состояние алгоритма. Возвращает число строк. */
    @Query(
        "UPDATE card\n" +
        "   SET content_file = :contentFile\n" +
        " WHERE card.id = :cardId",
    )
    abstract suspend fun updateContentFile(cardId: Long, contentFile: String): Int

    @Query(
        "DELETE\n" +
        "  FROM card\n" +
        " WHERE card.id IN (:cardIds)",
    )
    abstract suspend fun deleteCards(cardIds: Collection<Long>)
}

@Dao
abstract class DraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDraft(draft: DraftEntity)

    @Query(
        "SELECT *\n" +
        "  FROM draft\n" +
        " WHERE draft.id = 1",
    )
    abstract suspend fun findDraft(): DraftEntity?

    @Query(
        "DELETE\n" +
        "  FROM draft\n" +
        " WHERE draft.id = 1",
    )
    abstract suspend fun deleteDraft()

    @Query(
        "DELETE\n" +
        "  FROM draft\n" +
        " WHERE draft.deck_id = :deckId",
    )
    abstract suspend fun deleteDraftOfDeck(deckId: Long)
}

@Dao
abstract class AppStateDao {

    @Query(
        "SELECT *\n" +
        "  FROM app_state\n" +
        " WHERE app_state.id = 1",
    )
    abstract suspend fun findAppState(): AppStateEntity?

    @Query(
        "UPDATE app_state\n" +
        "   SET current_deck_id = :deckId\n" +
        " WHERE app_state.id = 1",
    )
    abstract suspend fun updateCurrentDeck(deckId: Long)

    @Query(
        "UPDATE app_state\n" +
        "   SET next_card_id = :nextCardId\n" +
        " WHERE app_state.id = 1",
    )
    abstract suspend fun updateNextCardId(nextCardId: Long)

    /** Сбрасывает текущую колоду, если это [deckId]; выбор другой колоды не трогает. */
    @Query(
        "UPDATE app_state\n" +
        "   SET current_deck_id = NULL\n" +
        " WHERE app_state.current_deck_id = :deckId",
    )
    abstract suspend fun clearCurrentDeck(deckId: Long)
}
