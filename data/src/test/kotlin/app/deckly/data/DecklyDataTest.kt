package app.deckly.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.db.DatabaseDamagedException
import app.deckly.data.db.DatabaseMissingException
import app.deckly.domain.editor.Draft
import app.deckly.domain.editor.saveDraft
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.collectDeckChanges
import app.deckly.domain.port.StoredDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaScannerConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DecklyDataTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage = createTestStorage()
    private val rootDir = storage.rootDir

    @Before
    fun forgetEarlierScans() {
        ShadowMediaScannerConnection.reset()
    }

    private fun buildDraft(): Draft =
        Draft(null, CardSide(listOf(buildTestStroke(3))), CardSide(listOf(buildTestStroke(4))), Side.BACK, Section.NEW)

    @Test
    fun openingCreatesFoldersAndCleansStrokes() = runTest {
        // База, в которой выдан id 1 и 2, но карточек нет: так выглядит неудавшееся создание.
        openDecklyData(context, rootDir).use { data ->
            data.drafts.allocateCardId()
            data.drafts.allocateCardId()
            assertTrue(storage.strokesDir.isDirectory)
            assertTrue(storage.databaseFile.isFile)
        }
        val temporary = File(storage.strokesDir, "x.tmp").apply { writeText("недописан") }
        val orphan = File(storage.strokesDir, "card-1-0123456789abcdef.dks").apply { writeText("ничей") }
        val foreign = File(storage.strokesDir, "README").apply { writeText("не трогать") }

        openDecklyData(context, rootDir).use {
            assertFalse(temporary.exists())
            assertFalse(orphan.exists())
            assertTrue(foreign.isFile)
        }
    }

    @Test
    fun freshInstallOpensNormally() = runTest {
        openDecklyData(context, rootDir).use { data ->
            assertEquals(emptyList<Any>(), data.decks.listDecks())
            assertTrue(storage.strokesDir.isDirectory)
            assertTrue(storage.databaseFile.isFile)
        }
    }

    @Test
    fun missingDatabaseWithCardFilesRefusesToOpen() = runTest {
        storage.ensureDirectories()
        writeSeedFile(storage, "card-5-0123456789abcdef.dks", buildSeedContent(5))
        writeSeedFile(storage, "card-6-0123456789abcdef.dks", buildSeedContent(6))
        writeSeedFile(storage, "draft-0123456789abcdef.dks", buildSeedContent(7))
        val filesBefore = listStrokeFileNames(storage)
        val rootBefore = rootDir.list().orEmpty().toSet()

        val failure = assertSuspendThrows<DatabaseMissingException> { openDecklyData(context, rootDir) }

        assertEquals(2, failure.cardFileCount)
        assertEquals(rootDir, failure.dir)
        assertEquals(filesBefore, listStrokeFileNames(storage))
        assertEquals("база не должна создаваться", rootBefore, rootDir.list().orEmpty().toSet())
    }

    @Test
    fun emptyDatabaseFileWithCardFilesRefusesToOpen() = runTest {
        storage.ensureDirectories()
        writeSeedFile(storage, "card-5-0123456789abcdef.dks", buildSeedContent(5))
        writeSeedFile(storage, "card-6-0123456789abcdef.dks", buildSeedContent(6))
        storage.databaseFile.writeBytes(ByteArray(0))
        val filesBefore = listStrokeFileNames(storage)

        val failure = assertSuspendThrows<DatabaseMissingException> { openDecklyData(context, rootDir) }

        assertEquals(2, failure.cardFileCount)
        assertEquals(filesBefore, listStrokeFileNames(storage))
        assertEquals("пустой файл остался пустым", 0L, storage.databaseFile.length())
    }

    @Test
    fun missingDatabaseWithoutCardFilesOpens() = runTest {
        storage.ensureDirectories()
        writeSeedFile(storage, "draft-0123456789abcdef.dks", buildSeedContent(7))

        openDecklyData(context, rootDir).use { data ->
            assertEquals(emptyList<Any>(), data.decks.listDecks())
        }
        assertEquals("черновик без базы убирается как лишний", emptySet<String>(), listStrokeFileNames(storage))
    }

    @Test
    fun openingCreatesMissingRootFolder() = runTest {
        val missingRoot = File(rootDir, "Deckly")

        openDecklyData(context, missingRoot).use {
            assertTrue(File(missingRoot, "strokes").isDirectory)
            assertTrue(File(missingRoot, "deckly.db").isFile)
        }
    }

    @Test
    fun closingReleasesTheDatabase() = runTest {
        val data = openDecklyData(context, rootDir)
        data.decks.createDeck("Колода")

        data.close()

        assertSuspendThrows<Exception> { data.decks.listDecks() }
    }

    @Test
    fun garbageFileIsReportedAndLeftAlone() = runTest {
        val garbage = "это не база SQLite, а просто текст, который длиннее заголовка базы".toByteArray()
        storage.databaseFile.writeBytes(garbage)

        assertSuspendThrows<DatabaseDamagedException> { openDecklyData(context, rootDir) }

        assertTrue("повреждённую базу нельзя молча стирать", storage.databaseFile.readBytes().contentEquals(garbage))
        assertEquals("Room не должен открываться", setOf("deckly.db"), rootDir.list().orEmpty().toSet())
    }

    @Test
    fun reopeningKeepsEverything() = runTest {
        val first = openDecklyData(context, rootDir)
        val deck = first.decks.createDeck("Колода")
        val before = first.decks.loadDeckState(deck.id)
        val cardId = first.drafts.allocateCardId()
        val (after, content) = saveDraft(before, buildDraft(), cardId, today = 200, now = 1_000L)
        first.drafts.commitDraft(collectDeckChanges(before, after), content)
        val draft = StoredDraft(deck.id, buildDraft().copy(returnSection = Section.EPOCH_2))
        first.drafts.storeDraft(draft)
        first.appState.saveCurrentDeckId(deck.id)
        first.close()

        openDecklyData(context, rootDir).use { second ->
            assertEquals(listOf(deck.id), second.decks.listDecks().map { it.id })
            assertEquals(after, second.decks.loadDeckState(deck.id))
            assertEquals(content, second.contents.loadContent(cardId))
            assertEquals(draft, second.drafts.loadDraft())
            assertEquals(deck.id, second.appState.loadCurrentDeckId())
        }
    }

    @Test
    fun notifierScansDatabaseAndChangedFiles() = runTest {
        openDecklyData(context, rootDir).use { data ->
            val deck = data.decks.createDeck("Колода")
            val before = data.decks.loadDeckState(deck.id)
            val draft = buildDraft()
            data.drafts.storeDraft(StoredDraft(deck.id, draft))
            val draftFile = listStrokeFileNames(storage).single()
            val cardId = data.drafts.allocateCardId()
            val (after, content) = saveDraft(before, draft, cardId, today = 200, now = 1_000L)
            data.drafts.commitDraft(collectDeckChanges(before, after), content)
            val cardFile = listStrokeFileNames(storage).single()

            data.mediaStore.notifyChanged()

            assertEquals(
                setOf(
                    storage.databaseFile.absolutePath,
                    storage.databaseFile.absolutePath + "-journal",
                    File(storage.strokesDir, cardFile).absolutePath,
                    File(storage.strokesDir, draftFile).absolutePath,
                ),
                ShadowMediaScannerConnection.getSavedPaths(),
            )
            ShadowMediaScannerConnection.reset()

            data.mediaStore.notifyChanged()

            assertEquals(
                setOf(storage.databaseFile.absolutePath, storage.databaseFile.absolutePath + "-journal"),
                ShadowMediaScannerConnection.getSavedPaths(),
            )
        }
    }

    @Test
    fun notifierAlsoReportsDeletedFiles() = runTest {
        openDecklyData(context, rootDir).use { data ->
            val deck = data.decks.createDeck("Колода")
            val before = data.decks.loadDeckState(deck.id)
            val cardId = data.drafts.allocateCardId()
            val (after, content) = saveDraft(before, buildDraft(), cardId, today = 200, now = 1_000L)
            data.drafts.commitDraft(collectDeckChanges(before, after), content)
            val cardFile = File(storage.strokesDir, listStrokeFileNames(storage).single())
            data.mediaStore.notifyChanged()
            ShadowMediaScannerConnection.reset()

            data.decks.saveDeckChanges(DeckChanges(after.deck, emptyList(), setOf(cardId)))
            data.mediaStore.notifyChanged()

            assertFalse(cardFile.exists())
            assertTrue(ShadowMediaScannerConnection.getSavedPaths().contains(cardFile.absolutePath))
        }
    }

    @Test
    fun importedFilesAreReported() = runTest {
        openDecklyData(context, rootDir).use { data ->
            val deck = data.decks.createDeck("Колода")
            val before = data.decks.loadDeckState(deck.id)
            val cardId = data.drafts.allocateCardId()
            val (after, content) = saveDraft(before, buildDraft(), cardId, today = 200, now = 1_000L)
            data.drafts.commitDraft(collectDeckChanges(before, after), content)
            val exported = ByteArrayOutputStream().also { data.transfer.exportDeck(deck.id, it) }
            data.mediaStore.notifyChanged()
            ShadowMediaScannerConnection.reset()

            val imported = data.transfer.importDeck(ByteArrayInputStream(exported.toByteArray()))
            data.mediaStore.notifyChanged()

            val importedCardId = data.decks.loadDeckState(imported.id).cards.single().id
            val importedFile = listStrokeFileNames(storage).single { it.startsWith("card-$importedCardId-") }
            assertEquals(
                setOf(
                    storage.databaseFile.absolutePath,
                    storage.databaseFile.absolutePath + "-journal",
                    File(storage.strokesDir, importedFile).absolutePath,
                ),
                ShadowMediaScannerConnection.getSavedPaths(),
            )
        }
    }
}
