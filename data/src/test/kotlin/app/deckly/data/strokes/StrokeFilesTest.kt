package app.deckly.data.strokes

import app.deckly.data.buildTestStroke
import app.deckly.data.createTestStorage
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeFilesTest {

    private val storage = createTestStorage().also { it.ensureDirectories() }
    private val files = StrokeFiles(storage.strokesDir)
    private val strokes = CardSide(listOf(buildTestStroke(1)))

    private fun listNames(): Set<String> = storage.strokesDir.list().orEmpty().toSet()

    private fun writeRaw(name: String, text: String = "x"): File =
        File(storage.strokesDir, name).apply { writeText(text) }

    private fun buildReferences(
        cards: Map<Long, String> = emptyMap(),
        draft: String? = null,
        nextCardId: Long = 100,
    ) = StrokeReferences(cards, draft, nextCardId)

    @Test
    fun shapesAreWrittenAndReadWithTheCardAndDraftFiles() {
        val shape = Shape(Shape.Kind.HEART, 10f, 20f, 300f, 400f, 15f, 4f)
        val content = CardSide(listOf(buildTestStroke(1)), listOf(shape))
        val back = CardSide(emptyList(), listOf(shape.copy(angle = -90f)))

        val cardFile = files.writeCardFile(7, content, back)
        val draftFile = files.writeDraftFile(back, content)

        assertEquals(content to back, files.readFile(cardFile))
        assertEquals(back to content, files.readFile(draftFile))
    }

    @Test
    fun cardFileNameHasCardIdAndRandomSuffix() {
        val first = files.writeCardFile(42, strokes, CardSide())
        val second = files.writeCardFile(42, strokes, CardSide())

        val pattern = Regex("card-42-[0-9a-f]{16}\\.dks")
        assertTrue(first, pattern.matches(first))
        assertTrue(second, pattern.matches(second))
        assertNotEquals(first, second)
    }

    @Test
    fun draftFileNameHasRandomSuffix() {
        val name = files.writeDraftFile(strokes, CardSide())

        assertTrue(name, Regex("draft-[0-9a-f]{16}\\.dks").matches(name))
    }

    @Test
    fun writtenFileReadsBackAndLeavesNoTemporaryFile() {
        val name = files.writeCardFile(7, strokes, CardSide(listOf(buildTestStroke(2))))

        assertEquals(strokes to CardSide(listOf(buildTestStroke(2))), files.readFile(name))
        assertEquals(setOf(name), listNames())
    }

    @Test
    fun readingMissingFileIsReported() {
        val failure = runCatching { files.readFile("card-1-0000000000000000.dks") }.exceptionOrNull()

        assertTrue(failure.toString(), failure is StrokeFileException)
    }

    @Test
    fun deleteRemovesFileAndIgnoresMissingOne() {
        val name = files.writeCardFile(7, strokes, CardSide())

        files.deleteContentFile(name)
        files.deleteContentFile(name)

        assertEquals(emptySet<String>(), listNames())
    }

    @Test
    fun deleteIgnoresNamesOutsideStrokesFolder() {
        val outside = File(storage.rootDir, "outside.dks").apply { writeText("мой файл") }

        files.deleteContentFile("../outside.dks")
        files.deleteContentFile("..")

        assertTrue(outside.isFile)
        assertTrue(storage.strokesDir.isDirectory)
    }

    @Test
    fun cleanUpTouchesOnlyOwnFiles() {
        File(storage.rootDir, ".git").mkdirs()
        val gitConfig = File(storage.rootDir, ".git/config").apply { writeText("[core]") }
        val notes = File(storage.rootDir, "notes.txt").apply { writeText("заметки") }
        val readme = writeRaw("README", "не трогать")
        val foreignPng = writeRaw("card-1-x.png", "png")
        val referencedCard = files.writeCardFile(1, strokes, CardSide())
        val referencedDraft = files.writeDraftFile(strokes, CardSide())
        val deletedCard = files.writeCardFile(2, strokes, CardSide())
        val orphanDraft = files.writeDraftFile(strokes, CardSide())
        touch(orphanDraft, 1_000)
        touch(referencedDraft, 5_000)
        val temporary = writeRaw("card-3-0123456789abcdef.dks.tmp", "недописан")
        val otherTemporary = writeRaw("x.tmp")

        files.cleanUpStrokeFiles(buildReferences(cards = mapOf(1L to referencedCard), draft = referencedDraft))

        assertTrue(gitConfig.isFile)
        assertTrue(notes.isFile)
        assertTrue(readme.isFile)
        assertTrue(foreignPng.isFile)
        assertEquals(
            setOf(referencedCard, referencedDraft, "README", "card-1-x.png"),
            listNames(),
        )
        assertFalse(File(storage.strokesDir, deletedCard).exists())
        assertFalse(File(storage.strokesDir, orphanDraft).exists())
        assertFalse(temporary.exists())
        assertFalse(otherTemporary.exists())
    }

    @Test
    fun cleanUpWithoutStrokesFolderDoesNothing() {
        val bare = StrokeFiles(File(storage.rootDir, "no-such-folder"))

        bare.cleanUpStrokeFiles(buildReferences())

        assertFalse(File(storage.rootDir, "no-such-folder").exists())
    }

    @Test
    fun deletedCardFileIsRemoved() {
        val deleted = files.writeCardFile(5, strokes, CardSide())

        files.cleanUpStrokeFiles(buildReferences(nextCardId = 100))

        assertEquals(emptySet<String>(), listNames())
        assertFalse(deleted in listNames())
    }

    @Test
    fun filesOfCardsNewerThanDatabaseAreKept() {
        val newer = files.writeCardFile(500, strokes, CardSide())
        val justAllocated = files.writeCardFile(100, strokes, CardSide())

        files.cleanUpStrokeFiles(buildReferences(nextCardId = 100))

        assertEquals(setOf(newer, justAllocated), listNames())
    }

    @Test
    fun olderSupersededFileIsRemoved() {
        val current = files.writeCardFile(7, strokes, CardSide())
        val stale = files.writeCardFile(7, CardSide(listOf(buildTestStroke(9))), CardSide())
        touch(stale, 1_000)
        touch(current, 5_000)

        files.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to current)))

        assertEquals(setOf(current), listNames())
        assertFalse(stale in listNames())
    }

    @Test
    fun newerSupersededFileIsKept() {
        // База старше strokes/: файл по ссылке старше другого, другой может оказаться последней правкой.
        val current = files.writeCardFile(7, strokes, CardSide())
        val newer = files.writeCardFile(7, CardSide(listOf(buildTestStroke(9))), CardSide())
        touch(current, 1_000)
        touch(newer, 5_000)

        files.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to current)))

        assertEquals(setOf(current, newer), listNames())
    }

    @Test
    fun equalTimesKeepBoth() {
        val current = files.writeCardFile(7, strokes, CardSide())
        val other = files.writeCardFile(7, CardSide(listOf(buildTestStroke(9))), CardSide())
        touch(current, 3_000)
        touch(other, 3_000)

        files.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to current)))

        assertEquals(setOf(current, other), listNames())
    }

    @Test
    fun olderSupersededDraftFileIsRemoved() {
        val current = files.writeDraftFile(strokes, CardSide())
        val stale = files.writeDraftFile(CardSide(listOf(buildTestStroke(9))), CardSide())
        touch(stale, 1_000)
        touch(current, 5_000)

        files.cleanUpStrokeFiles(buildReferences(draft = current))

        assertEquals(setOf(current), listNames())
    }

    @Test
    fun newerSupersededDraftFileIsKept() {
        val current = files.writeDraftFile(strokes, CardSide())
        val newer = files.writeDraftFile(CardSide(listOf(buildTestStroke(9))), CardSide())
        touch(current, 1_000)
        touch(newer, 5_000)

        files.cleanUpStrokeFiles(buildReferences(draft = current))

        assertEquals(setOf(current, newer), listNames())
    }

    @Test
    fun equalTimesKeepBothDraftFiles() {
        val current = files.writeDraftFile(strokes, CardSide())
        val other = files.writeDraftFile(CardSide(listOf(buildTestStroke(9))), CardSide())
        touch(current, 3_000)
        touch(other, 3_000)

        files.cleanUpStrokeFiles(buildReferences(draft = current))

        assertEquals(setOf(current, other), listNames())
    }

    @Test
    fun fileOfCardIsKeptWhenReferencedFileIsMissing() {
        val onlyCopy = files.writeCardFile(7, strokes, CardSide())

        files.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to "card-7-ffffffffffffffff.dks")))

        assertEquals(setOf(onlyCopy), listNames())
    }

    @Test
    fun cardFileWithUnparsableIdIsKept() {
        writeRaw("card-abc.dks")
        writeRaw("card--x.dks")
        writeRaw("card-99999999999999999999-x.dks")

        files.cleanUpStrokeFiles(buildReferences(nextCardId = Long.MAX_VALUE))

        assertEquals(setOf("card-abc.dks", "card--x.dks", "card-99999999999999999999-x.dks"), listNames())
    }

    @Test
    fun committedTemporaryFileIsRecovered() {
        val name = files.writeCardFile(7, strokes, CardSide(listOf(buildTestStroke(2))))
        // После сбоя питания переименование .tmp не дошло до диска: на диске только .tmp.
        File(storage.strokesDir, name).renameTo(File(storage.strokesDir, "$name.tmp"))
        val reported = mutableListOf<File>()
        val reporting = StrokeFiles(storage.strokesDir, { reported += it })

        reporting.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to name)))

        assertEquals(setOf(name), listNames())
        assertEquals(strokes to CardSide(listOf(buildTestStroke(2))), files.readFile(name))
        assertEquals(listOf(File(storage.strokesDir, name)), reported)
    }

    @Test
    fun committedTemporaryFileOfDraftIsRecovered() {
        val name = files.writeDraftFile(strokes, CardSide())
        File(storage.strokesDir, name).renameTo(File(storage.strokesDir, "$name.tmp"))

        files.cleanUpStrokeFiles(buildReferences(draft = name))

        assertEquals(setOf(name), listNames())
        assertEquals(strokes to CardSide(), files.readFile(name))
    }

    @Test
    fun orphanTemporaryFilesAreDeleted() {
        val referenced = files.writeCardFile(7, strokes, CardSide())
        // .tmp, на который никто не ссылается; .tmp, у которого настоящий файл уже есть; чужое имя.
        writeRaw("card-8-0123456789abcdef.dks.tmp")
        writeRaw("$referenced.tmp")
        writeRaw("x.tmp")

        files.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to referenced)))

        assertEquals(setOf(referenced), listNames())
        assertEquals(strokes to CardSide(), files.readFile(referenced))
    }

    @Test
    fun draftFileWithoutRowIsRemoved() {
        val draft = files.writeDraftFile(strokes, CardSide())

        files.cleanUpStrokeFiles(buildReferences(draft = null))

        assertFalse(draft in listNames())
    }

    @Test
    fun staleDraftFileIsRemovedOnlyWhenTheReferencedOneExists() {
        val current = files.writeDraftFile(strokes, CardSide())
        val stale = files.writeDraftFile(CardSide(listOf(buildTestStroke(9))), CardSide())
        touch(stale, 1_000)
        touch(current, 5_000)

        files.cleanUpStrokeFiles(buildReferences(draft = current))
        assertEquals(setOf(current), listNames())

        val onlyCopy = files.writeDraftFile(strokes, CardSide())
        files.cleanUpStrokeFiles(buildReferences(draft = "draft-ffffffffffffffff.dks"))
        assertEquals(setOf(current, onlyCopy), listNames())
        assertFalse(stale in listNames())
    }

    @Test
    fun cleanUpReportsDeletedFilesOnly() {
        val reported = mutableListOf<File>()
        val reporting = StrokeFiles(storage.strokesDir, { reported += it })
        val kept = files.writeCardFile(1, strokes, CardSide())
        val deleted = files.writeCardFile(2, strokes, CardSide())
        val temporary = writeRaw("y.tmp")
        writeRaw("README", "не трогать")

        reporting.cleanUpStrokeFiles(buildReferences(cards = mapOf(1L to kept)))

        assertEquals(setOf(File(storage.strokesDir, deleted), temporary), reported.toSet())
        assertEquals(2, reported.size)
    }

    @Test
    fun writtenAndDeletedFilesAreReported() {
        val reported = mutableListOf<File>()
        val reporting = StrokeFiles(storage.strokesDir, { reported += it })

        val cardFile = reporting.writeCardFile(7, strokes, CardSide())
        val draftFile = reporting.writeDraftFile(strokes, CardSide())
        reporting.deleteContentFile(cardFile)

        assertEquals(
            listOf(
                File(storage.strokesDir, cardFile),
                File(storage.strokesDir, draftFile),
                File(storage.strokesDir, cardFile),
            ),
            reported,
        )
    }

    @Test
    fun deletingMissingOrForeignNameReportsNothing() {
        val reported = mutableListOf<File>()
        val reporting = StrokeFiles(storage.strokesDir, { reported += it })
        File(storage.rootDir, "outside.dks").writeText("мой файл")

        reporting.deleteContentFile("card-1-0000000000000000.dks")
        reporting.deleteContentFile("../outside.dks")

        assertEquals(emptyList<File>(), reported)
    }

    @Test
    fun directoryIsSyncedAfterEveryRename() {
        val synced = mutableListOf<Pair<File, Boolean>>()
        val syncing = StrokeFiles(storage.strokesDir, syncDirectory = { dir ->
            // К моменту сброса папки файл уже переименован: .tmp нет, настоящее имя есть.
            val names = dir.list().orEmpty().toList()
            synced += dir to (names.isNotEmpty() && names.none { it.endsWith(".tmp") })
        })

        val cardFile = syncing.writeCardFile(1, strokes, CardSide())
        syncing.writeDraftFile(strokes, CardSide())

        assertEquals(listOf(storage.strokesDir to true, storage.strokesDir to true), synced)
        assertTrue(cardFile in listNames())
    }

    @Test
    fun directoryIsSyncedAfterRecoveringTemporaryFile() {
        val name = files.writeCardFile(7, strokes, CardSide())
        File(storage.strokesDir, name).renameTo(File(storage.strokesDir, "$name.tmp"))
        val synced = mutableListOf<File>()
        val syncing = StrokeFiles(storage.strokesDir, syncDirectory = { synced += it })

        syncing.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to name)))

        assertEquals(listOf(storage.strokesDir), synced)
    }

    @Test
    fun failingDirectorySyncDoesNotFailTheWrite() {
        val failing = StrokeFiles(storage.strokesDir, syncDirectory = { throw IOException("fsync папки не поддержан") })

        val name = failing.writeCardFile(1, strokes, CardSide())

        assertEquals(setOf(name), listNames())
        assertEquals(strokes to CardSide(), failing.readFile(name))
    }

    @Test
    fun defaultDirectorySyncWorksOnRealFolder() {
        val name = files.writeCardFile(1, strokes, CardSide())

        // Настоящий fsync папки через FileChannel не должен падать ни на JVM, ни на Android.
        forceDirectoryToDisk(storage.strokesDir)

        assertEquals(setOf(name), listNames())
    }

    @Test
    fun directorySyncOfMissingFolderStillFails() {
        // Лог времени не глотает ошибку: исключение идёт дальше, как и без него.
        assertThrows(IOException::class.java) { forceDirectoryToDisk(File(storage.strokesDir, "нет-такой-папки")) }
    }

    @Test
    fun newestFileIsChosenByModificationTime() {
        val older = files.writeCardFile(1, strokes, CardSide()).also { touch(it, 1_000) }
        val newer = files.writeCardFile(1, strokes, CardSide()).also { touch(it, 5_000) }
        // Файл другой карточки с тем же началом id новее всех, но не подходит.
        files.writeCardFile(12, strokes, CardSide()).also { touch(it, 9_000) }
        val draftOlder = files.writeDraftFile(strokes, CardSide()).also { touch(it, 2_000) }
        val draftNewer = files.writeDraftFile(strokes, CardSide()).also { touch(it, 6_000) }

        assertEquals(newer, files.findNewestCardFile(1))
        assertNull(files.findNewestCardFile(2))
        assertEquals(draftNewer, files.findNewestDraftFile())
        assertTrue(older != newer && draftOlder != draftNewer)
    }

    @Test
    fun cardFilesAreCountedByNameShape() {
        files.writeCardFile(1, strokes, CardSide())
        files.writeCardFile(2, strokes, CardSide())
        files.writeDraftFile(strokes, CardSide())
        writeRaw("card-3-0123456789abcdef.dks.tmp")
        writeRaw("README")

        assertEquals(2, countCardFiles(storage.strokesDir))
        assertEquals(0, countCardFiles(File(storage.rootDir, "no-such-folder")))
    }

    private fun touch(name: String, millis: Long) {
        assertTrue(File(storage.strokesDir, name).setLastModified(millis))
    }
}
