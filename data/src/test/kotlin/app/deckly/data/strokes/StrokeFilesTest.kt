package app.deckly.data.strokes

import app.deckly.data.buildTestStroke
import app.deckly.data.createTestStorage
import app.deckly.domain.model.Stroke
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeFilesTest {

    private val storage = createTestStorage().also { it.ensureDirectories() }
    private val files = StrokeFiles(storage.strokesDir)
    private val strokes = listOf(buildTestStroke(1))

    private fun listNames(): Set<String> = storage.strokesDir.list().orEmpty().toSet()

    private fun writeRaw(name: String, text: String = "x"): File =
        File(storage.strokesDir, name).apply { writeText(text) }

    private fun buildReferences(
        cards: Map<Long, String> = emptyMap(),
        draft: String? = null,
        nextCardId: Long = 100,
    ) = StrokeReferences(cards, draft, nextCardId)

    @Test
    fun cardFileNameHasCardIdAndRandomSuffix() {
        val first = files.writeCardFile(42, strokes, emptyList())
        val second = files.writeCardFile(42, strokes, emptyList())

        val pattern = Regex("card-42-[0-9a-f]{16}\\.dks")
        assertTrue(first, pattern.matches(first))
        assertTrue(second, pattern.matches(second))
        assertNotEquals(first, second)
    }

    @Test
    fun draftFileNameHasRandomSuffix() {
        val name = files.writeDraftFile(strokes, emptyList())

        assertTrue(name, Regex("draft-[0-9a-f]{16}\\.dks").matches(name))
    }

    @Test
    fun writtenFileReadsBackAndLeavesNoTemporaryFile() {
        val name = files.writeCardFile(7, strokes, listOf(buildTestStroke(2)))

        assertEquals(strokes to listOf(buildTestStroke(2)), files.readFile(name))
        assertEquals(setOf(name), listNames())
    }

    @Test
    fun readingMissingFileIsReported() {
        val failure = runCatching { files.readFile("card-1-0000000000000000.dks") }.exceptionOrNull()

        assertTrue(failure.toString(), failure is StrokeFileException)
    }

    @Test
    fun deleteRemovesFileAndIgnoresMissingOne() {
        val name = files.writeCardFile(7, strokes, emptyList())

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
        val referencedCard = files.writeCardFile(1, strokes, emptyList())
        val referencedDraft = files.writeDraftFile(strokes, emptyList())
        val deletedCard = files.writeCardFile(2, strokes, emptyList())
        val orphanDraft = files.writeDraftFile(strokes, emptyList())
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
        val deleted = files.writeCardFile(5, strokes, emptyList())

        files.cleanUpStrokeFiles(buildReferences(nextCardId = 100))

        assertEquals(emptySet<String>(), listNames())
        assertFalse(deleted in listNames())
    }

    @Test
    fun filesOfCardsNewerThanDatabaseAreKept() {
        val newer = files.writeCardFile(500, strokes, emptyList())
        val justAllocated = files.writeCardFile(100, strokes, emptyList())

        files.cleanUpStrokeFiles(buildReferences(nextCardId = 100))

        assertEquals(setOf(newer, justAllocated), listNames())
    }

    @Test
    fun staleVersionOfExistingCardIsRemovedWhenReferencedFileExists() {
        val current = files.writeCardFile(7, strokes, emptyList())
        val stale = files.writeCardFile(7, listOf(buildTestStroke(9)), emptyList())

        files.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to current)))

        assertEquals(setOf(current), listNames())
        assertFalse(stale in listNames())
    }

    @Test
    fun fileOfCardIsKeptWhenReferencedFileIsMissing() {
        val onlyCopy = files.writeCardFile(7, strokes, emptyList())

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
        val name = files.writeCardFile(7, strokes, listOf(buildTestStroke(2)))
        // После сбоя питания переименование .tmp не дошло до диска: на диске только .tmp.
        File(storage.strokesDir, name).renameTo(File(storage.strokesDir, "$name.tmp"))
        val reported = mutableListOf<File>()
        val reporting = StrokeFiles(storage.strokesDir, { reported += it })

        reporting.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to name)))

        assertEquals(setOf(name), listNames())
        assertEquals(strokes to listOf(buildTestStroke(2)), files.readFile(name))
        assertEquals(listOf(File(storage.strokesDir, name)), reported)
    }

    @Test
    fun committedTemporaryFileOfDraftIsRecovered() {
        val name = files.writeDraftFile(strokes, emptyList())
        File(storage.strokesDir, name).renameTo(File(storage.strokesDir, "$name.tmp"))

        files.cleanUpStrokeFiles(buildReferences(draft = name))

        assertEquals(setOf(name), listNames())
        assertEquals(strokes to emptyList<Stroke>(), files.readFile(name))
    }

    @Test
    fun orphanTemporaryFilesAreDeleted() {
        val referenced = files.writeCardFile(7, strokes, emptyList())
        // .tmp, на который никто не ссылается; .tmp, у которого настоящий файл уже есть; чужое имя.
        writeRaw("card-8-0123456789abcdef.dks.tmp")
        writeRaw("$referenced.tmp")
        writeRaw("x.tmp")

        files.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to referenced)))

        assertEquals(setOf(referenced), listNames())
        assertEquals(strokes to emptyList<Stroke>(), files.readFile(referenced))
    }

    @Test
    fun draftFileWithoutRowIsRemoved() {
        val draft = files.writeDraftFile(strokes, emptyList())

        files.cleanUpStrokeFiles(buildReferences(draft = null))

        assertFalse(draft in listNames())
    }

    @Test
    fun staleDraftFileIsRemovedOnlyWhenTheReferencedOneExists() {
        val current = files.writeDraftFile(strokes, emptyList())
        val stale = files.writeDraftFile(listOf(buildTestStroke(9)), emptyList())

        files.cleanUpStrokeFiles(buildReferences(draft = current))
        assertEquals(setOf(current), listNames())

        val onlyCopy = files.writeDraftFile(strokes, emptyList())
        files.cleanUpStrokeFiles(buildReferences(draft = "draft-ffffffffffffffff.dks"))
        assertEquals(setOf(current, onlyCopy), listNames())
        assertFalse(stale in listNames())
    }

    @Test
    fun cleanUpReportsDeletedFilesOnly() {
        val reported = mutableListOf<File>()
        val reporting = StrokeFiles(storage.strokesDir, { reported += it })
        val kept = files.writeCardFile(1, strokes, emptyList())
        val deleted = files.writeCardFile(2, strokes, emptyList())
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

        val cardFile = reporting.writeCardFile(7, strokes, emptyList())
        val draftFile = reporting.writeDraftFile(strokes, emptyList())
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

        val cardFile = syncing.writeCardFile(1, strokes, emptyList())
        syncing.writeDraftFile(strokes, emptyList())

        assertEquals(listOf(storage.strokesDir to true, storage.strokesDir to true), synced)
        assertTrue(cardFile in listNames())
    }

    @Test
    fun directoryIsSyncedAfterRecoveringTemporaryFile() {
        val name = files.writeCardFile(7, strokes, emptyList())
        File(storage.strokesDir, name).renameTo(File(storage.strokesDir, "$name.tmp"))
        val synced = mutableListOf<File>()
        val syncing = StrokeFiles(storage.strokesDir, syncDirectory = { synced += it })

        syncing.cleanUpStrokeFiles(buildReferences(cards = mapOf(7L to name)))

        assertEquals(listOf(storage.strokesDir), synced)
    }

    @Test
    fun failingDirectorySyncDoesNotFailTheWrite() {
        val failing = StrokeFiles(storage.strokesDir, syncDirectory = { throw IOException("fsync папки не поддержан") })

        val name = failing.writeCardFile(1, strokes, emptyList())

        assertEquals(setOf(name), listNames())
        assertEquals(strokes to emptyList<Stroke>(), failing.readFile(name))
    }

    @Test
    fun defaultDirectorySyncWorksOnRealFolder() {
        val name = files.writeCardFile(1, strokes, emptyList())

        // Настоящий fsync папки через FileChannel не должен падать ни на JVM, ни на Android.
        forceDirectoryToDisk(storage.strokesDir)

        assertEquals(setOf(name), listNames())
    }

    @Test
    fun newestFileIsChosenByModificationTime() {
        val older = files.writeCardFile(1, strokes, emptyList()).also { touch(it, 1_000) }
        val newer = files.writeCardFile(1, strokes, emptyList()).also { touch(it, 5_000) }
        // Файл другой карточки с тем же началом id новее всех, но не подходит.
        files.writeCardFile(12, strokes, emptyList()).also { touch(it, 9_000) }
        val draftOlder = files.writeDraftFile(strokes, emptyList()).also { touch(it, 2_000) }
        val draftNewer = files.writeDraftFile(strokes, emptyList()).also { touch(it, 6_000) }

        assertEquals(newer, files.findNewestCardFile(1))
        assertNull(files.findNewestCardFile(2))
        assertEquals(draftNewer, files.findNewestDraftFile())
        assertTrue(older != newer && draftOlder != draftNewer)
    }

    @Test
    fun cardFilesAreCountedByNameShape() {
        files.writeCardFile(1, strokes, emptyList())
        files.writeCardFile(2, strokes, emptyList())
        files.writeDraftFile(strokes, emptyList())
        writeRaw("card-3-0123456789abcdef.dks.tmp")
        writeRaw("README")

        assertEquals(2, countCardFiles(storage.strokesDir))
        assertEquals(0, countCardFiles(File(storage.rootDir, "no-such-folder")))
    }

    private fun touch(name: String, millis: Long) {
        assertTrue(File(storage.strokesDir, name).setLastModified(millis))
    }
}
