package app.deckly.decks

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/** Файлы колод через `ContentResolver`: адреса выдаёт системное окно выбора (Storage Access Framework). */
class ContentResolverDeckFiles(private val resolver: ContentResolver) : DeckFiles {
    override suspend fun readAll(file: String): ByteArray = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(Uri.parse(file)) ?: throw FileNotFoundException("Нет потока чтения: $file")
        input.use { it.readBytes() }
    }

    // Режим «wt» стирает прежнее содержимое: поверх существующего длинного файла хвост бы остался.
    override suspend fun write(file: String, block: suspend (OutputStream) -> Unit) {
        val output = withContext(Dispatchers.IO) {
            resolver.openOutputStream(Uri.parse(file), "wt") ?: throw FileNotFoundException("Нет потока записи: $file")
        }
        output.use { block(it) }
    }

    override suspend fun delete(file: String) {
        val isDeleted = withContext(Dispatchers.IO) { DocumentsContract.deleteDocument(resolver, Uri.parse(file)) }
        if (!isDeleted) throw IOException("Файл не удалён: $file")
    }
}
