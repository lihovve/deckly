package app.deckly.decks

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * Файлы колод через `ContentResolver`: запись и чтение на адресах `file://`, размер и удаление через провайдер документов
 * (его роль здесь играет [SizedDocuments]: у `file://` ни размера через провайдера, ни `DocumentsContract.deleteDocument` нет).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContentResolverDeckFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val files = ContentResolverDeckFiles(ApplicationProvider.getApplicationContext<Context>().contentResolver)

    @Before
    fun setUp() {
        SizedDocuments.sizes.clear()
        SizedDocuments.deleted.clear()
        Robolectric.buildContentProvider(SizedDocuments::class.java).create(AUTHORITY)
    }

    private fun addressOf(file: File): String = Uri.fromFile(file).toString()

    private fun documentAt(id: String, size: Long?): String =
        Uri.parse("content://$AUTHORITY/document/$id").also { SizedDocuments.sizes[it] = size }.toString()

    @Test
    fun writtenBytesAreTheContentOfTheFile() = runBlocking {
        val file = folder.newFile("deck.json")

        files.write(addressOf(file), "колода".toByteArray())

        assertEquals("колода", file.readText())
    }

    @Test
    fun writingOverALongerFileLeavesNoTail() = runBlocking {
        val file = folder.newFile("deck.json")
        file.writeText("очень длинное прежнее содержимое")

        files.write(addressOf(file), "коротко".toByteArray())

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
    fun writeToAMissingFolderFails() {
        val file = File(File(folder.root, "нет-папки"), "deck.json")

        assertThrows(IOException::class.java) { runBlocking { files.write(addressOf(file), ByteArray(1)) } }
    }

    @Test
    fun emptyDocumentIsDeleted() = runBlocking {
        val document = documentAt("new", size = 0)

        files.deleteIfEmpty(document)

        assertEquals(listOf(Uri.parse(document)), SizedDocuments.deleted)
    }

    @Test
    fun nonEmptyDocumentIsLeftAlone() = runBlocking {
        val document = documentAt("users", size = 5)

        files.deleteIfEmpty(document)

        assertTrue(SizedDocuments.deleted.isEmpty())
    }

    @Test
    fun documentOfUnknownSizeIsLeftAlone() = runBlocking {
        files.deleteIfEmpty(documentAt("unknown", size = null))
        files.deleteIfEmpty("content://$AUTHORITY/document/not-registered")

        assertTrue(SizedDocuments.deleted.isEmpty())
    }

    @Test
    fun deleteRemovesTheDocumentWhateverItHolds() = runBlocking {
        val document = documentAt("half", size = 7)

        files.delete(document)

        assertEquals(listOf(Uri.parse(document)), SizedDocuments.deleted)
    }

    private companion object {
        const val AUTHORITY = "deckly.test.documents"
    }

    /** Провайдер с размерами документов и удалением, как у системного: размер через `OpenableColumns.SIZE`, удаление через `call`. */
    class SizedDocuments : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            if (!sizes.containsKey(uri)) return null
            return MatrixCursor(arrayOf(OpenableColumns.SIZE)).also { it.addRow(arrayOf(sizes[uri])) }
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            if (method != "android:deleteDocument") return super.call(method, arg, extras)
            @Suppress("DEPRECATION")
            val uri = checkNotNull(extras?.getParcelable<Uri>("uri"))
            deleted += uri
            sizes.remove(uri)
            return Bundle()
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        companion object {
            val sizes = mutableMapOf<Uri, Long?>()
            val deleted = mutableListOf<Uri>()
        }
    }
}
