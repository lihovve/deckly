package app.deckly.domain.port

import app.deckly.domain.model.Deck
import java.io.InputStream
import java.io.OutputStream

enum class ImportError { NOT_A_DECK_FILE, UNSUPPORTED_VERSION, DAMAGED }

/** Файл не удалось принять; после этого в базе и в папке штрихов ничего нового нет. */
class ImportException(val error: ImportError, cause: Throwable? = null) : Exception(error.name, cause)

/** Экспорт и импорт колоды: один файл — одна колода, потоки байтов дают вызывающий. */
interface DeckTransfer {
    suspend fun exportDeck(deckId: Long, output: OutputStream)

    /** Всегда создаёт новую колоду: id колоды и карточек в файле не используются. */
    suspend fun importDeck(input: InputStream): Deck
}
