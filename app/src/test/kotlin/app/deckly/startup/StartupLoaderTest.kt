package app.deckly.startup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.DecklyData
import app.deckly.data.db.DatabaseDamagedException
import app.deckly.data.db.DatabaseMissingException
import app.deckly.domain.port.StoredDraft
import app.deckly.fakes.FakeAppStateRepository
import app.deckly.fakes.FakeDraftRepository
import app.deckly.fakes.buildEmptyDraft
import app.deckly.fakes.buildFakeData
import app.deckly.navigation.AppScreen
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.StartupState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupLoaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun noFileAccessNeverOpensStorage() = runTest {
        // Без доступа SQLite не откроет файл, и проверка базы сказала бы «повреждена» вместо «нет доступа».
        var openCount = 0
        val loader = StartupLoader(hasFileAccess = { false }, openData = { openCount++; error("Хранение открыто без доступа") })

        val result = loader.loadStart()

        assertEquals(AppScreen.Startup(StartupState.NEEDS_FILE_ACCESS), result.screen)
        assertNull(result.data)
        assertEquals(0, openCount)
    }

    @Test
    fun damagedAndMissingDatabaseHaveOwnStates() = runTest {
        val damaged = StartupLoader(
            hasFileAccess = { true },
            openData = { throw DatabaseDamagedException(File("deckly.db"), "повреждена") },
        )
        val missing = StartupLoader(
            hasFileAccess = { true },
            openData = { throw DatabaseMissingException(File("Deckly"), 3) },
        )

        val damagedResult = damaged.loadStart()
        val missingResult = missing.loadStart()

        assertEquals(AppScreen.Startup(StartupState.DATABASE_DAMAGED), damagedResult.screen)
        assertEquals(AppScreen.Startup(StartupState.DATABASE_MISSING), missingResult.screen)
        assertNull(damagedResult.data)
        assertNull(missingResult.data)
    }

    @Test
    fun otherOpeningErrorsAreNotSwallowed() = runTest {
        val loader = StartupLoader(hasFileAccess = { true }, openData = { throw IOException("диск") })

        val error = runCatching { loader.loadStart() }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun draftOpensEditorFirst() = runTest {
        val data = buildData(currentDeckId = 3, draft = StoredDraft(7, buildEmptyDraft()))

        val result = StartupLoader(hasFileAccess = { true }, openData = { data }).loadStart()

        assertEquals(AppScreen.Editor(7, EditorStart.ResumeDraft), result.screen)
        assertSame(data, result.data)
    }

    @Test
    fun currentDeckOpensDeck() = runTest {
        val data = buildData(currentDeckId = 3)

        val result = StartupLoader(hasFileAccess = { true }, openData = { data }).loadStart()

        assertEquals(AppScreen.Deck(3), result.screen)
        assertSame(data, result.data)
    }

    @Test
    fun noDecksOpensDeckList() = runTest {
        val data = buildData()

        val result = StartupLoader(hasFileAccess = { true }, openData = { data }).loadStart()

        assertEquals(AppScreen.DeckList, result.screen)
        assertSame(data, result.data)
    }

    private fun buildData(currentDeckId: Long? = null, draft: StoredDraft? = null): DecklyData =
        buildFakeData(
            context = context,
            rootDir = folder.newFolder(),
            appState = FakeAppStateRepository(currentDeckId),
            drafts = FakeDraftRepository(draft),
        )
}
