package app.deckly.navigation

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.DecklyData
import app.deckly.data.db.DatabaseDamagedException
import app.deckly.fakes.FakeAppStateRepository
import app.deckly.fakes.buildFakeData
import app.deckly.startup.StartupLoader
import app.deckly.ui.contract.StartupState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaScannerConnection

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dispatcher: TestDispatcher
    private var hasFileAccess = true
    private var openCount = 0
    private var damaged = false
    private lateinit var rootDir: File

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        rootDir = folder.newFolder()
        ShadowMediaScannerConnection.reset()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsOnLoadingAndShowsLoadedScreen() {
        val viewModel = createViewModel(currentDeckId = 3)

        assertEquals(AppScreen.Loading, viewModel.screen.value)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppScreen.Deck(3), viewModel.screen.value)
    }

    @Test
    fun navigateChangesScreen() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.navigate(AppScreen.DeckName)

        assertEquals(AppScreen.DeckName, viewModel.screen.value)
    }

    @Test
    fun resumeAfterGrantingAccessLoadsAgain() {
        hasFileAccess = false
        val viewModel = createViewModel(currentDeckId = 3)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppScreen.Startup(StartupState.NEEDS_FILE_ACCESS), viewModel.screen.value)

        // Владелец выдал доступ в настройках и вернулся: приложение продолжает само.
        hasFileAccess = true
        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppScreen.Deck(3), viewModel.screen.value)
        assertEquals(1, openCount)
    }

    @Test
    fun resumeWithoutGrantedAccessKeepsRequestScreen() {
        hasFileAccess = false
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppScreen.Startup(StartupState.NEEDS_FILE_ACCESS), viewModel.screen.value)
        assertEquals(0, openCount)
    }

    @Test
    fun resumeDoesNothingOutsideAccessRequest() {
        val viewModel = createViewModel(currentDeckId = 3)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.navigate(AppScreen.DeckList)

        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppScreen.DeckList, viewModel.screen.value)
        assertEquals(1, openCount)
    }

    @Test
    fun resumeDoesNotRetryDamagedDatabase() {
        damaged = true
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppScreen.Startup(StartupState.DATABASE_DAMAGED), viewModel.screen.value)

        damaged = false
        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppScreen.Startup(StartupState.DATABASE_DAMAGED), viewModel.screen.value)
    }

    @Test
    fun repeatedResumeWhileLoadingLoadsOnce() {
        hasFileAccess = false
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        hasFileAccess = true

        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, openCount)
    }

    @Test
    fun stopTellsMediaStoreAboutChanges() {
        val viewModel = createViewModel(currentDeckId = 3)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(ShadowMediaScannerConnection.getSavedPaths().isEmpty())

        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertTrue(File(rootDir, "deckly.db").absolutePath in ShadowMediaScannerConnection.getSavedPaths())
    }

    @Test
    fun otherLifecycleEventsDoNotTouchMediaStore() {
        val viewModel = createViewModel(currentDeckId = 3)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertTrue(ShadowMediaScannerConnection.getSavedPaths().isEmpty())
    }

    @Test
    fun stopBeforeStorageIsOpenedDoesNothing() {
        hasFileAccess = false
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertFalse(ShadowMediaScannerConnection.getSavedPaths().isNotEmpty())
    }

    private fun createViewModel(currentDeckId: Long? = null): AppViewModel {
        val data: DecklyData = buildFakeData(
            context = context,
            rootDir = rootDir,
            appState = FakeAppStateRepository(currentDeckId),
        )
        val loader = StartupLoader(
            hasFileAccess = { hasFileAccess },
            openData = {
                openCount++
                if (damaged) throw DatabaseDamagedException(File("deckly.db"), "повреждена")
                data
            },
        )
        return AppViewModel(loader)
    }
}
