package app.deckly.data.storage

import java.io.File
import java.io.IOException

/**
 * Открытая папка хранения. В приложении это `Environment.getExternalStorageDirectory()/Deckly`,
 * в тестах — временная папка. Всё, что лежит в [rootDir] и не названо здесь, чужое: его не трогаем.
 */
class DecklyStorage(val rootDir: File) {

    val databaseFile: File = File(rootDir, DATABASE_FILE_NAME)

    val strokesDir: File = File(rootDir, STROKES_DIR_NAME)

    /** Создаёт [rootDir] и `strokes/`, если их нет. Вызов безопасно повторять. */
    fun ensureDirectories() {
        strokesDir.mkdirs()
        if (!strokesDir.isDirectory) throw IOException("Не удалось создать папку ${strokesDir.absolutePath}")
    }

    private companion object {
        const val DATABASE_FILE_NAME = "deckly.db"
        const val STROKES_DIR_NAME = "strokes"
    }
}
