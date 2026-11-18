package app.deckly.decks

/**
 * Файлы колод, которые пользователь выбрал в системном окне: [file] — адрес из системы (`content://…`), приложение его
 * не разбирает. Сбой доступа даёт `IOException` или `SecurityException`; блокирующую работу с потоками делает реализация.
 */
interface DeckFiles {
    /** Весь файл целиком: файл колоды читается в память всё равно, а писатель базы не ждёт медленный источник. */
    suspend fun readAll(file: String): ByteArray

    /** Пишет [bytes] в [file]: прежнее содержимое стирается (выбор замены в системном окне — согласие пользователя). */
    suspend fun write(file: String, bytes: ByteArray)

    /**
     * Удаляет [file] только пустой: размер 0 значит «только что создан системным окном». Размер неизвестен или больше нуля —
     * файл не трогается: окно создания файла возвращает и адрес существующего файла, если пользователь подтвердил замену.
     */
    suspend fun deleteIfEmpty(file: String)

    /** Удаляет [file] как есть: после сбоя записи прежнего содержимого уже нет, а половина файла хуже, чем его отсутствие. */
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
