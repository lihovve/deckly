package app.deckly.decks

import java.io.OutputStream

/**
 * Файлы колод, которые пользователь выбрал в системном окне: [file] — адрес из системы (`content://…`), приложение его
 * не разбирает. Сбой доступа даёт `IOException` или `SecurityException`; блокирующую работу с потоками делает реализация.
 */
interface DeckFiles {
    /** Весь файл целиком: файл колоды читается в память всё равно, а писатель базы не ждёт медленный источник. */
    suspend fun readAll(file: String): ByteArray

    /** Открывает [file] на запись (прежнее содержимое стирается), отдаёт поток [block] и закрывает его. */
    suspend fun write(file: String, block: suspend (OutputStream) -> Unit)

    /** Удаляет [file]: так убирается пустой файл, который создал системный выбор, если запись не удалась. */
    suspend fun delete(file: String)
}

/** Что список колод просит у хоста: системное окно выбора файла. Хост открывает его и сообщает выбор модели. */
sealed interface FileRequest {
    /** Окно создания файла `application/json` с предложенным именем. */
    data class CreateDeckFile(val suggestedName: String) : FileRequest

    /** Окно выбора файла для импорта: любой, чужой файл отсеет сам импорт. */
    data object OpenDeckFile : FileRequest
}

private val FORBIDDEN_IN_FILE_NAME = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

/** Предложенное имя файла экспорта: «<имя колоды>.json», символы, запрещённые в именах файлов, заменены на `_`. */
fun suggestExportFileName(deckName: String): String {
    val name = deckName.trim().replace(FORBIDDEN_IN_FILE_NAME, "_")
    return "${name.ifEmpty { "deck" }}.json"
}
