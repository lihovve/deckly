package app.deckly.data.storage

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

/**
 * Сообщает MediaStore об изменениях в папке хранения, чтобы компьютер по MTP видел свежие размеры
 * файлов. Файлы штрихов репозитории записывают через `StrokeFiles`, и он вызывает [recordChanged]
 * для каждого записанного и каждого удалённого файла; [notifyChanged] приложение вызывает, когда
 * уходит в фон. База и её `-journal` передаются при каждом вызове: Room пишет их постоянно.
 */
class MediaStoreNotifier(context: Context, private val storage: DecklyStorage) {

    private val context: Context = context.applicationContext
    private val changedFiles = LinkedHashSet<File>()

    /** Запоминает файл, записанный или удалённый после прошлого [notifyChanged]. */
    fun recordChanged(file: File) {
        synchronized(changedFiles) { changedFiles += file }
    }

    /** Передаёт `MediaScannerConnection` базу, её `-journal` и все запомненные файлы, затем забывает их. */
    fun notifyChanged() {
        val database = storage.databaseFile
        val paths = mutableListOf(database.absolutePath, database.absolutePath + JOURNAL_SUFFIX)
        synchronized(changedFiles) {
            changedFiles.mapTo(paths) { it.absolutePath }
            changedFiles.clear()
        }
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private companion object {
        const val JOURNAL_SUFFIX = "-journal"
    }
}
