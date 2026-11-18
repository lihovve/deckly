package app.deckly.decks

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/** Файлы колод через `ContentResolver`: адреса выдаёт системное окно выбора (Storage Access Framework). */
class ContentResolverDeckFiles(private val resolver: ContentResolver) : DeckFiles {
    override suspend fun readAll(file: String): ByteArray = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(Uri.parse(file)) ?: throw FileNotFoundException("Нет потока чтения: $file")
        input.use { it.readBytes() }
    }

    // Режим «wt» стирает прежнее содержимое: поверх существующего длинного файла хвост бы остался.
    override suspend fun write(file: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val output = resolver.openOutputStream(Uri.parse(file), "wt") ?: throw FileNotFoundException("Нет потока записи: $file")
            output.use { it.write(bytes) }
        }
    }

    override suspend fun deleteIfEmpty(file: String) {
        val isEmpty = withContext(Dispatchers.IO) { findSize(Uri.parse(file)) == 0L }
        if (isEmpty) delete(file)
    }

    // Размер, который сообщает провайдер; `null`, если он его не сообщает: такой файл считается непустым.
    private fun findSize(uri: Uri): Long? =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    override suspend fun delete(file: String) {
        val isDeleted = withContext(Dispatchers.IO) { DocumentsContract.deleteDocument(resolver, Uri.parse(file)) }
        if (!isDeleted) throw IOException("Файл не удалён: $file")
    }
}
