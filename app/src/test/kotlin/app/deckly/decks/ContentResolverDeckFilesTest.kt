package app.deckly.decks

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** Файлы колод через `ContentResolver` на адресах `file://`: потоки открываются, пишутся и читаются целиком. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContentResolverDeckFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val files = ContentResolverDeckFiles(ApplicationProvider.getApplicationContext<Context>().contentResolver)

    private fun addressOf(file: File): String = Uri.fromFile(file).toString()

    @Test
    fun writtenBytesAreTheContentOfTheFile() = runBlocking {
        val file = folder.newFile("deck.json")

        files.write(addressOf(file)) { it.write("колода".toByteArray()) }

        assertEquals("колода", file.readText())
    }

    @Test
    fun writingOverALongerFileLeavesNoTail() = runBlocking {
        val file = folder.newFile("deck.json")
        file.writeText("очень длинное прежнее содержимое")

        files.write(addressOf(file)) { it.write("коротко".toByteArray()) }

        assertEquals("коротко", file.readText())
    }

    @Test
    fun readAllReturnsTheWholeFile() = runBlocking {
        val file = folder.newFile("deck.json")
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        file.writeBytes(bytes)

        assertArrayEquals(bytes, files.readAll(addressOf(file)))
    }

    @Test
    fun missingFileIsAnIoException() {
        val missing = File(folder.root, "нет.json")

        assertThrows(IOException::class.java) { runBlocking { files.readAll(addressOf(missing)) } }
    }

    @Test
    fun failureInsideTheWriteBlockReachesTheCaller() {
        val file = folder.newFile("deck.json")

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { files.write(addressOf(file)) { error("сбой источника") } }
        }

        assertEquals("сбой источника", failure.message)
    }
}
