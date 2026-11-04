package app.deckly.data.strokes

import app.deckly.data.buildTestStroke
import app.deckly.data.createTestStorage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeFilesTest {

    private val storage = createTestStorage().also { it.ensureDirectories() }
    private val files = StrokeFiles(storage.strokesDir)
    private val strokes = listOf(buildTestStroke(1))

    private fun listNames(): Set<String> = storage.strokesDir.list().orEmpty().toSet()

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
        val readme = File(storage.strokesDir, "README").apply { writeText("не трогать") }
        val foreignPng = File(storage.strokesDir, "card-1-x.png").apply { writeText("png") }
        val referencedCard = files.writeCardFile(1, strokes, emptyList())
        val referencedDraft = files.writeDraftFile(strokes, emptyList())
        val orphanCard = files.writeCardFile(2, strokes, emptyList())
        val orphanDraft = files.writeDraftFile(strokes, emptyList())
        val temporary = File(storage.strokesDir, "card-3-0123456789abcdef.dks.tmp").apply { writeText("недописан") }
        val otherTemporary = File(storage.strokesDir, "x.tmp").apply { writeText("x") }

        files.cleanUpStrokeFiles(setOf(referencedCard, referencedDraft))

        assertTrue(gitConfig.isFile)
        assertTrue(notes.isFile)
        assertTrue(readme.isFile)
        assertTrue(foreignPng.isFile)
        assertEquals(
            setOf(referencedCard, referencedDraft, "README", "card-1-x.png"),
            listNames(),
        )
        assertFalse(File(storage.strokesDir, orphanCard).exists())
        assertFalse(File(storage.strokesDir, orphanDraft).exists())
        assertFalse(temporary.exists())
        assertFalse(otherTemporary.exists())
    }

    @Test
    fun writtenAndDeletedFilesAreReported() {
        val reported = mutableListOf<File>()
        val reporting = StrokeFiles(storage.strokesDir) { reported += it }

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
        val reporting = StrokeFiles(storage.strokesDir) { reported += it }
        File(storage.rootDir, "outside.dks").writeText("мой файл")

        reporting.deleteContentFile("card-1-0000000000000000.dks")
        reporting.deleteContentFile("../outside.dks")

        assertEquals(emptyList<File>(), reported)
    }

    @Test
    fun cleanUpReportsDeletedFilesOnly() {
        val reported = mutableListOf<File>()
        val reporting = StrokeFiles(storage.strokesDir) { reported += it }
        val kept = files.writeCardFile(1, strokes, emptyList())
        val orphan = files.writeCardFile(2, strokes, emptyList())
        val temporary = File(storage.strokesDir, "y.tmp").apply { writeText("y") }
        File(storage.strokesDir, "README").writeText("не трогать")

        reporting.cleanUpStrokeFiles(setOf(kept))

        assertEquals(setOf(File(storage.strokesDir, orphan), temporary), reported.toSet())
        assertEquals(2, reported.size)
    }

    @Test
    fun cleanUpWithoutStrokesFolderDoesNothing() {
        val bare = StrokeFiles(File(storage.rootDir, "no-such-folder"))

        bare.cleanUpStrokeFiles(emptySet())

        assertFalse(File(storage.rootDir, "no-such-folder").exists())
    }
}
