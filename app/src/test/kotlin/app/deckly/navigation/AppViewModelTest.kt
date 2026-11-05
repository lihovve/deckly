package app.deckly.navigation

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import org.junit.Assert.assertNotSame
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

    // --- хранилище ViewModel экрана ------------------------------------------------------------

    @Test
    fun leavingScreenClearsItsViewModels() {
        val viewModel = createViewModel(currentDeckId = 3)
        dispatcher.scheduler.advanceUntilIdle()
        val deckProbe = obtainProbe(viewModel.entry.value)

        viewModel.navigate(AppScreen.DeckList)

        assertTrue("уход с колоды очищает её ViewModel", deckProbe.isCleared)
        assertFalse("новый экран получает своё хранилище", obtainProbe(viewModel.entry.value).isCleared)
    }

    @Test
    fun returnToEqualScreenGivesNewStoreAndNewViewModels() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.navigate(AppScreen.Deck(3))
        val first = viewModel.entry.value
        val firstProbe = obtainProbe(first)

        // Тот же экран, но переход состоялся: ViewModel заново читает данные в init, а не живёт дальше.
        viewModel.navigate(AppScreen.Deck(3))
        val second = viewModel.entry.value

        assertEquals(first.screen, second.screen)
        assertNotSame(first, second)
        assertNotSame(first.viewModelStore, second.viewModelStore)
        assertTrue(firstProbe.isCleared)
        assertNotSame(firstProbe, obtainProbe(second))
        assertFalse(obtainProbe(second).isCleared)
    }

    @Test
    fun everyNavigationIncreasesToken() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val loaded = viewModel.entry.value.token

        viewModel.navigate(AppScreen.DeckList)
        val afterFirst = viewModel.entry.value.token
        viewModel.navigate(AppScreen.DeckList)
        val afterSecond = viewModel.entry.value.token

        assertTrue(afterFirst > loaded)
        assertTrue(afterSecond > afterFirst)
    }

    @Test
    fun screenFlowStillCarriesScreenValue() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.navigate(AppScreen.DeckName)

        assertEquals(AppScreen.DeckName, viewModel.screen.value)
        assertEquals(AppScreen.DeckName, viewModel.entry.value.screen)
    }

    @Test
    fun clearingAppViewModelClearsCurrentScreenViewModels() {
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, viewModelFactory { initializer { createViewModel() } })[AppViewModel::class]
        dispatcher.scheduler.advanceUntilIdle()
        val probe = obtainProbe(viewModel.entry.value)

        store.clear()

        assertTrue(probe.isCleared)
    }

    private class ProbeViewModel : ViewModel() {
        var isCleared = false
            private set

        override fun onCleared() {
            isCleared = true
        }
    }

    // ViewModel экрана, как её берёт AppRoot: из хранилища записи, через фабрику.
    private fun obtainProbe(entry: ScreenEntry): ProbeViewModel =
        ViewModelProvider.create(entry.viewModelStore, viewModelFactory { initializer { ProbeViewModel() } })[ProbeViewModel::class]

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
