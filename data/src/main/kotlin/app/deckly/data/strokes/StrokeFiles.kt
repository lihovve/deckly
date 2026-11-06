package app.deckly.data.strokes

import app.deckly.data.timing.DecklyTiming
import app.deckly.domain.model.CardSide
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

private const val CONTENT_SUFFIX = ".dks"
private const val TEMPORARY_SUFFIX = ".tmp"
private const val CARD_PREFIX = "card-"
private const val DRAFT_PREFIX = "draft-"

/** `card-<id>-<что угодно>.dks`: id карточки выделяется только из таких имён. */
private val CARD_FILE_NAME = Regex("""^card-(\d+)-.*\.dks$""")

/**
 * Что база помнит о файлах штрихов: файл каждой карточки (`id → имя`), файл черновика и счётчик
 * `next_card_id`. Уборка удаляет только то, что по этим данным доказуемо лишнее.
 */
class StrokeReferences(
    val cardFiles: Map<Long, String>,
    val draftFile: String?,
    val nextCardId: Long,
)

/**
 * Файлы штрихов в папке [strokesDir]: `card-<cardId>-<16 hex>.dks` и `draft-<16 hex>.dks`. У каждой
 * записи новое имя. Запись идёт в `<имя>.tmp`, потом `renameTo`, потом сбрасывается на диск сама папка
 * ([syncDirectory]): иначе после сбоя питания переименование могло бы потеряться, а база уже ссылалась
 * бы на файл. Возвращаются имена без пути. Всё, что не названо здесь, чужое и не трогается.
 *
 * [onFileChanged] получает каждый записанный и каждый удалённый файл (после того, как это случилось):
 * так MediaStore узнаёт обо всех изменениях папки.
 */
open class StrokeFiles(
    private val strokesDir: File,
    private val onFileChanged: (File) -> Unit = {},
    private val syncDirectory: (File) -> Unit = ::forceDirectoryToDisk,
) {

    // Запись открыта для переопределения ради тестов: сбой записи при настоящем удалении.
    open fun writeCardFile(cardId: Long, front: CardSide, back: CardSide): String =
        writeNewFile("$CARD_PREFIX$cardId-", front, back)

    open fun writeDraftFile(front: CardSide, back: CardSide): String =
        writeNewFile(DRAFT_PREFIX, front, back)

    /** Читает файл; пропавший, повреждённый или с недопустимым именем — [StrokeFileException]. */
    fun readFile(name: String): Pair<CardSide, CardSide> {
        val file = findOwnFile(name) ?: throw StrokeFileException("Недопустимое имя файла штрихов: $name")
        try {
            return file.inputStream().buffered().use { readStrokeFile(it) }
        } catch (missing: FileNotFoundException) {
            throw StrokeFileException("Файла штрихов $name нет", missing)
        }
    }

    /** Есть ли в папке обычный файл с таким именем. Имя с путём — всегда нет. */
    fun hasFile(name: String): Boolean = findOwnFile(name)?.isFile == true

    /** Самый новый по времени изменения файл карточки [cardId] или `null`: запасное чтение, если файл по ссылке пропал. */
    fun findNewestCardFile(cardId: Long): String? =
        findNewestFile { CARD_FILE_NAME.matchEntire(it)?.groupValues?.get(1) == cardId.toString() }

    /** Самый новый по времени изменения файл черновика или `null`. */
    fun findNewestDraftFile(): String? =
        findNewestFile { it.startsWith(DRAFT_PREFIX) && it.endsWith(CONTENT_SUFFIX) }

    /** Удаляет файл, если он есть. Имя не из этой папки игнорируется: чужое не трогаем никогда. */
    fun deleteContentFile(name: String) {
        val file = findOwnFile(name)?.takeIf { it.isFile } ?: return
        if (file.delete()) onFileChanged(file)
    }

    /**
     * Уборка при открытии хранилища, пока никто не пишет. Удаляет только доказуемо лишнее.
     * - `X.tmp`: если база ссылается на `X`, а `X` нет, это записанный до конца файл, у которого потерялось
     *   переименование (данные сброшены на диск раньше его) — его переименовывают в `X`. Иначе `X.tmp` удаляется.
     * - `card-<id>-*.dks`: удаляется, если карточка есть в базе, ссылается на другой существующий файл, а этот
     *   файл СТРОГО старше файла по ссылке (старая версия), или если карточки нет, а `id < next_card_id`
     *   (удалённая карточка или неудавшееся создание). Файл карточки новее базы (`id >= next_card_id`), файл
     *   не старше файла по ссылке (база могла быть откачена назад, и это последняя правка) и единственная
     *   копия при пропавшем файле по ссылке остаются.
     * - `draft-*.dks`: удаляется, если строки черновика нет или файл строго старше существующего файла по
     *   ссылке.
     * Правило одно: в сомнении файл остаётся. Остальное, в том числе имена не нашего вида, не трогается.
     */
    fun cleanUpStrokeFiles(references: StrokeReferences) {
        val referencedNames = references.cardFiles.values.toSet() + listOfNotNull(references.draftFile)
        recoverCommittedTemporaryFiles(referencedNames)
        val files = strokesDir.listFiles()?.filter { it.isFile } ?: return
        for (file in files) {
            val name = file.name
            val isObsolete = when {
                name.endsWith(TEMPORARY_SUFFIX) -> true
                !isContentFileName(name) || name in referencedNames -> false
                name.startsWith(CARD_PREFIX) -> isObsoleteCardFile(file, references)
                else -> references.draftFile == null || isOlderThanReferenced(file, references.draftFile)
            }
            if (isObsolete && file.delete()) onFileChanged(file)
        }
    }

    private fun recoverCommittedTemporaryFiles(referencedNames: Set<String>) {
        val temporaries = strokesDir.listFiles()?.filter { it.isFile && it.name.endsWith(TEMPORARY_SUFFIX) } ?: return
        for (temporary in temporaries) {
            val target = File(strokesDir, temporary.name.removeSuffix(TEMPORARY_SUFFIX))
            if (target.name in referencedNames && !target.exists() && temporary.renameTo(target)) {
                syncDirectoryBestEffort()
                onFileChanged(target)
            }
        }
    }

    private fun isObsoleteCardFile(file: File, references: StrokeReferences): Boolean {
        val cardId = CARD_FILE_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull() ?: return false
        val referencedFile = references.cardFiles[cardId]
        return if (referencedFile != null) isOlderThanReferenced(file, referencedFile) else cardId < references.nextCardId
    }

    /**
     * Файл строго старше существующего файла по ссылке. Равное время (после git или MTP времена могут
     * совпасть), более позднее и пропавший файл по ссылке — не «старше»: такой файл остаётся.
     */
    private fun isOlderThanReferenced(file: File, referencedName: String): Boolean {
        val referenced = File(strokesDir, referencedName)
        return referenced.isFile && file.lastModified() < referenced.lastModified()
    }

    private fun writeNewFile(prefix: String, front: CardSide, back: CardSide): String {
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
        syncDirectoryBestEffort()
        onFileChanged(File(strokesDir, name))
        return name
    }

    /**
     * Сбрасывает папку на диск, чтобы переименование пережило сбой питания. Если файловая система этого не
     * умеет, запись не падает: данные файла уже на диске, а потерянное переименование восстановит уборка.
     */
    private fun syncDirectoryBestEffort() {
        try {
            syncDirectory(strokesDir)
        } catch (unsupported: IOException) {
            // см. выше
        }
    }

    private fun generateFreeName(prefix: String): String {
        while (true) {
            val name = prefix + "%016x".format(Random.nextLong()) + CONTENT_SUFFIX
            if (!File(strokesDir, name).exists()) return name
        }
    }

    private fun findNewestFile(isWanted: (String) -> Boolean): String? =
        strokesDir.listFiles()
            ?.filter { it.isFile && isWanted(it.name) && !it.name.endsWith(TEMPORARY_SUFFIX) }
            ?.maxByOrNull { it.lastModified() }
            ?.name

    /** Файл в [strokesDir] по простому имени; имя с путём, `.` и `..` — не наш файл. */
    private fun findOwnFile(name: String): File? {
        if (name.isEmpty() || name == "." || name == ".." || name != File(name).name) return null
        return File(strokesDir, name)
    }

    private fun isContentFileName(name: String): Boolean =
        name.endsWith(CONTENT_SUFFIX) && (name.startsWith(CARD_PREFIX) || name.startsWith(DRAFT_PREFIX))
}

/** Сколько в [strokesDir] файлов карточек `card-*.dks`. Папки нет — ноль. */
fun countCardFiles(strokesDir: File): Int =
    strokesDir.listFiles()?.count {
        it.isFile && it.name.startsWith(CARD_PREFIX) && it.name.endsWith(CONTENT_SUFFIX)
    } ?: 0

private val isForceLogged = AtomicBoolean(false)

/**
 * Сбрасывает на диск саму папку: `fsync` её дескриптора (на Linux и Android каталог открывается только на чтение).
 * Отладочный лог времени: сбой пишется всегда, успех один раз за процесс. Исключение идёт дальше, как и без лога.
 */
fun forceDirectoryToDisk(dir: File) {
    val startNanos = System.nanoTime()
    try {
        FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
    } catch (error: Exception) {
        DecklyTiming.logWarning("force папки ${dir.name} не удался", error)
        throw error
    }
    if (DecklyTiming.isEnabled && isForceLogged.compareAndSet(false, true)) {
        DecklyTiming.logInfo { "force папки ${dir.name} сработал за ${DecklyTiming.describeElapsed(startNanos)}ms" }
    }
}
