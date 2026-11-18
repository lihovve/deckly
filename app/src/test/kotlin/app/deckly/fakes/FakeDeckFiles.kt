package app.deckly.fakes

import app.deckly.decks.DeckFiles
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

/**
 * Файлы колод в памяти: [contents] — что в файле (нет записи — файла нет или размер неизвестен), [written] — какие файлы
 * открывали на запись, [deleted] — какие файлы удалены. [readFailure] и [writeFailure] — чем заканчивается чтение и
 * запись; [readGate] и [writeGate] держат их, пока тест не отпустит. Запись стирает прежнее содержимое до сбоя, как режим «wt»,
 * а при сбое оставляет половину байтов.
 */
class FakeDeckFiles : DeckFiles {
    val contents = mutableMapOf<String, ByteArray>()
    val written = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    var readGate: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null

    override suspend fun readAll(file: String): ByteArray {
        readGate?.await()
        readFailure?.let { throw it }
        return contents[file] ?: throw IOException("Нет файла $file")
    }

    override suspend fun write(file: String, bytes: ByteArray) {
        written += file
        contents[file] = ByteArray(0)
        writeGate?.await()
        // Запись может упасть посередине: половина байтов уже в файле, и он непуст.
        writeFailure?.let {
            contents[file] = bytes.copyOf(bytes.size / 2)
            throw it
        }
        contents[file] = bytes
    }

    override suspend fun deleteIfEmpty(file: String) {
        if (contents[file]?.isEmpty() == true) delete(file)
    }

    override suspend fun delete(file: String) {
        deleted += file
        contents.remove(file)
    }
}
