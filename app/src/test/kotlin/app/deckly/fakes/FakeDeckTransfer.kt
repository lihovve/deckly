package app.deckly.fakes

import app.deckly.domain.model.Deck
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckTransfer
import app.deckly.domain.port.ImportError
import app.deckly.domain.port.ImportException
import java.io.InputStream
import java.io.OutputStream

/**
 * Обмен колодами в памяти. Экспорт пишет `deck:<id>`; импорт берёт название колоды из содержимого файла и создаёт её в
 * [catalog], а файл «BAD» отвергает так же, как настоящий импорт: исключением и без следа в каталоге.
 */
class FakeDeckTransfer(private val catalog: DeckRepository) : DeckTransfer {
    val exportedIds = mutableListOf<Long>()
    var exportFailure: Exception? = null
    var importFailure: ImportException? = null

    override suspend fun exportDeck(deckId: Long, output: OutputStream) {
        exportedIds += deckId
        output.write("deck:$deckId".toByteArray())
        exportFailure?.let { throw it }
    }

    override suspend fun importDeck(input: InputStream): Deck {
        val text = String(input.readBytes())
        importFailure?.let { throw it }
        if (text == "BAD") throw ImportException(ImportError.DAMAGED)
        return catalog.createDeck(text)
    }
}
