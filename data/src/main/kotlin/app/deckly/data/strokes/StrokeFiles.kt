package app.deckly.data.strokes

import app.deckly.domain.model.Stroke
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.random.Random

private const val CONTENT_SUFFIX = ".dks"
private const val TEMPORARY_SUFFIX = ".tmp"
private const val CARD_PREFIX = "card-"
private const val DRAFT_PREFIX = "draft-"

/**
 * Файлы штрихов в папке [strokesDir]: `card-<cardId>-<16 hex>.dks` и `draft-<16 hex>.dks`. У каждой
 * записи новое имя. Запись идёт в `<имя>.tmp`, потом `renameTo`, поэтому полуфайла под настоящим
 * именем не бывает. Возвращаются имена без пути. Всё, что не названо здесь, чужое и не трогается.
 *
 * [onFileChanged] получает каждый записанный и каждый удалённый файл (после того, как это случилось):
 * так MediaStore узнаёт обо всех изменениях папки.
 */
open class StrokeFiles(
    private val strokesDir: File,
    private val onFileChanged: (File) -> Unit = {},
) {

    // Запись открыта для переопределения ради тестов: сбой записи при настоящем удалении.
    open fun writeCardFile(cardId: Long, front: List<Stroke>, back: List<Stroke>): String =
        writeNewFile("$CARD_PREFIX$cardId-", front, back)

    open fun writeDraftFile(front: List<Stroke>, back: List<Stroke>): String =
        writeNewFile(DRAFT_PREFIX, front, back)

    /** Читает файл; пропавший, повреждённый или с недопустимым именем — [StrokeFileException]. */
    fun readFile(name: String): Pair<List<Stroke>, List<Stroke>> {
        val file = findOwnFile(name) ?: throw StrokeFileException("Недопустимое имя файла штрихов: $name")
        try {
            return file.inputStream().buffered().use { readStrokeFile(it) }
        } catch (missing: FileNotFoundException) {
            throw StrokeFileException("Файла штрихов $name нет", missing)
        }
    }

    /** Удаляет файл, если он есть. Имя не из этой папки игнорируется: чужое не трогаем никогда. */
    fun deleteContentFile(name: String) {
        val file = findOwnFile(name)?.takeIf { it.isFile } ?: return
        if (file.delete()) onFileChanged(file)
    }

    /**
     * Удаляет в [strokesDir] все `*.tmp` и файлы `card-*.dks` и `draft-*.dks`, которых нет в
     * [referenced]. Остальное не трогает. Вызывается при открытии хранилища, пока никто не пишет.
     */
    fun cleanUpStrokeFiles(referenced: Set<String>) {
        val candidates = strokesDir.listFiles()?.filter { it.isFile } ?: return
        for (file in candidates) {
            val isObsolete = file.name.endsWith(TEMPORARY_SUFFIX) ||
                (isContentFileName(file.name) && file.name !in referenced)
            if (isObsolete && file.delete()) onFileChanged(file)
        }
    }

    private fun writeNewFile(prefix: String, front: List<Stroke>, back: List<Stroke>): String {
        val name = generateFreeName(prefix)
        val temporary = File(strokesDir, name + TEMPORARY_SUFFIX)
        try {
            FileOutputStream(temporary).use { stream ->
                writeStrokeFile(stream, front, back)
                stream.flush()
                // Транзакция базы ссылается на файл: он должен дойти до диска раньше неё.
                stream.fd.sync()
            }
            if (!temporary.renameTo(File(strokesDir, name))) {
                throw IOException("Не удалось переименовать ${temporary.name} в $name")
            }
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
        onFileChanged(File(strokesDir, name))
        return name
    }

    private fun generateFreeName(prefix: String): String {
        while (true) {
            val name = prefix + "%016x".format(Random.nextLong()) + CONTENT_SUFFIX
            if (!File(strokesDir, name).exists()) return name
        }
    }

    /** Файл в [strokesDir] по простому имени; имя с путём, `.` и `..` — не наш файл. */
    private fun findOwnFile(name: String): File? {
        if (name.isEmpty() || name == "." || name == ".." || name != File(name).name) return null
        return File(strokesDir, name)
    }

    private fun isContentFileName(name: String): Boolean =
        name.endsWith(CONTENT_SUFFIX) && (name.startsWith(CARD_PREFIX) || name.startsWith(DRAFT_PREFIX))
}
