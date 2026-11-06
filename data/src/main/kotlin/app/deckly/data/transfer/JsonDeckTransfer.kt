package app.deckly.data.transfer

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.mapCard
import app.deckly.data.db.mapCardEntity
import app.deckly.data.db.mapDeck
import app.deckly.data.db.mapDeckEntity
import app.deckly.data.repository.RoomDraftRepository
import app.deckly.data.repository.deleteFilesUnlessCommitted
import app.deckly.data.strokes.StrokeFiles
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.Deck
import app.deckly.domain.model.EpochValues
import app.deckly.domain.port.DeckTransfer
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream

/**
 * Экспорт и импорт колоды одним JSON-файлом (формат `deckly-deck`: пишется версия 2, читаются версии 1 и 2). Импорт делает всё в
 * том же порядке, что и запись карточки: файл проверяется целиком, потом пишутся файлы штрихов,
 * потом одна транзакция базы. Любой сбой после первого записанного файла удаляет записанное.
 */
class JsonDeckTransfer(
    private val database: DecklyDatabase,
    private val files: StrokeFiles,
    private val draftRepository: RoomDraftRepository,
) : DeckTransfer {

    /**
     * Нет колоды — `IllegalStateException`, пропал или повреждён файл штрихов — `StrokeFileException`.
     * В обоих случаях в [output] ничего не записано: файл собирается в памяти целиком.
     */
    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun exportDeck(deckId: Long, output: OutputStream) {
        val (deckEntity, cardEntities) = database.withReadTransaction {
            val deck = checkNotNull(database.deckDao().findDeck(deckId)) { "Колоды $deckId нет в базе" }
            deck to database.cardDao().listCards(deckId)
        }
        val file = withContext(Dispatchers.IO) {
            val contents = cardEntities.map { entity ->
                val (front, back) = files.readFile(entity.contentFile)
                CardContent(entity.id, front, back)
            }
            buildDeckFile(mapDeck(deckEntity), cardEntities.map(::mapCard), contents)
        }
        withContext(Dispatchers.IO) {
            // Сначала весь файл в память, потом в поток одним вызовом: сбой кодирования не оставит в потоке половину файла.
            val bytes = ByteArrayOutputStream().also { STRICT_JSON.encodeToStream(DeckFile.serializer(), file, it) }
            output.write(bytes.toByteArray())
            output.flush()
        }
    }

    /**
     * Создаёт новую колоду с новыми id карточек, `tail` пересчитан по ним. Плохой файл —
     * `ImportException`, и тогда в базе, в счётчике id и в `strokes/` ничего не меняется.
     */
    override suspend fun importDeck(input: InputStream): Deck {
        val parsed = withContext(Dispatchers.IO) { parseDeckFile(input.readBytes()) }
        val newIds = draftRepository.allocateCardIds(parsed.cards.size)
        val newIdByFileId = parsed.cards.map { it.card.id }.zip(newIds).toMap()
        val deck = parsed.deck.copy(tail = renumberTail(parsed.deck.tail, newIdByFileId))
        val writtenFiles = mutableListOf<String>()
        var createdDeckId: Long? = null
        try {
            withContext(Dispatchers.IO) {
                parsed.cards.forEachIndexed { index, parsedCard ->
                    writtenFiles += files.writeCardFile(newIds[index], parsedCard.front, parsedCard.back)
                }
            }
            return database.withWriteTransaction<Deck> {
                val deckId = database.deckDao().insertDeck(mapDeckEntity(deck))
                createdDeckId = deckId
                parsed.cards.forEachIndexed { index, parsedCard ->
                    val card = parsedCard.card.copy(id = newIds[index])
                    database.cardDao().insertCard(mapCardEntity(card, deckId, writtenFiles[index]))
                }
                deck.copy(id = deckId)
            }
        } catch (failure: Throwable) {
            // Отмена может прийти после коммита: тогда колода есть, и её файлы нужны.
            deleteFilesUnlessCommitted(files, writtenFiles) {
                createdDeckId?.let { database.deckDao().findDeck(it) != null } ?: false
            }
            throw failure
        }
    }

    private fun renumberTail(tail: EpochValues<List<Long>>, newIdByFileId: Map<Long, Long>): EpochValues<List<Long>> =
        EpochValues(
            tail.epoch1.map { newIdByFileId.getValue(it) },
            tail.epoch2.map { newIdByFileId.getValue(it) },
            tail.epoch3.map { newIdByFileId.getValue(it) },
            tail.epoch4.map { newIdByFileId.getValue(it) },
        )
}
