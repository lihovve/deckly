package app.deckly.data.storage

import app.deckly.data.createTestStorage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecklyStorageTest {

    @Test
    fun pathsAreInsideRootFolder() {
        val root = File("/sdcard/Deckly")
        val storage = DecklyStorage(root)

        assertEquals(File(root, "deckly.db"), storage.databaseFile)
        assertEquals(File(root, "strokes"), storage.strokesDir)
    }

    @Test
    fun ensureDirectoriesCreatesRootAndStrokesFolder() {
        val storage = DecklyStorage(File(createTestStorage().rootDir, "Deckly"))

        storage.ensureDirectories()

        assertTrue(storage.rootDir.isDirectory)
        assertTrue(storage.strokesDir.isDirectory)
    }

    @Test
    fun ensureDirectoriesKeepsExistingFiles() {
        val storage = createTestStorage()
        storage.ensureDirectories()
        val foreign = File(storage.rootDir, "notes.txt").apply { writeText("мои заметки") }
        val stroke = File(storage.strokesDir, "card-1-a.dks").apply { writeText("x") }

        storage.ensureDirectories()

        assertEquals("мои заметки", foreign.readText())
        assertEquals("x", stroke.readText())
    }
}
