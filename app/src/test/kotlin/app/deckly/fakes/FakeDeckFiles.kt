package app.deckly.fakes

import app.deckly.decks.DeckFiles
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Файлы колод в памяти: [contents] — что в файле, [written] и [deleted] — какие файлы писали и удаляли по порядку.
 * [readFailure] и [writeFailure] — чем заканчивается чтение и запись; [readGate] и [writeGate] держат их, пока тест не отпустит.
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

    override suspend fun write(file: String, block: suspend (OutputStream) -> Unit) {
        written += file
        // Файл создаёт системное окно выбора, поэтому он есть и пустым, и после неудачной записи.
        contents[file] = ByteArray(0)
        val buffer = ByteArrayOutputStream()
        writeGate?.await()
        block(buffer)
        writeFailure?.let { throw it }
        contents[file] = buffer.toByteArray()
    }

    override suspend fun delete(file: String) {
        deleted += file
        contents.remove(file)
    }
}
