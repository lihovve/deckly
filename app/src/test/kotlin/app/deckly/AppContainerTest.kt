package app.deckly

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.DecklyData
import app.deckly.data.db.DatabaseDamagedException
import app.deckly.fakes.buildFakeData
import app.deckly.navigation.AppScreen
import app.deckly.ui.contract.StartupState
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppContainerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val context: Context = application

    @Test
    fun storageIsOpenedOnceNoMatterHowManyStarts() = runBlocking {
        var openCount = 0
        val data = buildFakeData(context, folder.newFolder())
        val container = AppContainer(application, hasFileAccess = { true }, openStorage = { _, _ -> openCount++; data })

        // Новая activity после выхода из прежней запускает загрузку заново, а писатель в базу должен быть один.
        val first = container.startupLoader.loadStart()
        val second = container.startupLoader.loadStart()

        assertEquals(1, openCount)
        assertSame(data, first.data)
        assertSame(data, second.data)
        assertSame(data, container.requireData())
    }

    @Test
    fun simultaneousStartsOpenStorageOnce() = runBlocking {
        var openCount = 0
        val data = buildFakeData(context, folder.newFolder())
        val container = AppContainer(
            application,
            hasFileAccess = { true },
            openStorage = { _, _ -> openCount++; delay(50); data },
        )

        val results = listOf(
            async { container.startupLoader.loadStart() },
            async { container.startupLoader.loadStart() },
        ).awaitAll()

        assertEquals(1, openCount)
        assertSame(data, results[0].data)
        assertSame(data, results[1].data)
    }

    @Test
    fun failedOpeningIsNotRemembered() = runBlocking {
        var isDamaged = true
        val data = buildFakeData(context, folder.newFolder())
        val container = AppContainer(
            application,
            hasFileAccess = { true },
            openStorage = { _, _ -> if (isDamaged) throw DatabaseDamagedException(File("deckly.db"), "повреждена"); data },
        )

        val failed = container.startupLoader.loadStart()
        isDamaged = false
        val repaired = container.startupLoader.loadStart()

        assertEquals(AppScreen.Startup(StartupState.DATABASE_DAMAGED), failed.screen)
        assertEquals(AppScreen.DeckList, repaired.screen)
    }

    @Test
    fun dataIsNotAvailableBeforeOpening() {
        val container = AppContainer(application, hasFileAccess = { false }, openStorage = { _, _ -> error("не открывать") })

        val error = runCatching { container.requireData() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun noAccessLeavesStorageClosed() = runBlocking {
        val container = AppContainer(application, hasFileAccess = { false }, openStorage = { _, _ -> error("не открывать") })

        val result = container.startupLoader.loadStart()

        assertEquals(AppScreen.Startup(StartupState.NEEDS_FILE_ACCESS), result.screen)
    }

    @Test
    fun realStorageIsOpenedInDecklyFolderOfSharedStorage() = runBlocking {
        val container = AppContainer(application, hasFileAccess = { true })

        val result = container.startupLoader.loadStart()

        val database = File(Environment.getExternalStorageDirectory(), "Deckly/deckly.db")
        if (!database.exists()) fail("База должна лежать в ${database.path}")
        assertEquals(AppScreen.DeckList, result.screen)
        container.requireData().close()
    }

    @Test
    fun clockAndRandomAreProvided() {
        val container = AppContainer(application, hasFileAccess = { false })

        container.clock.computeToday()
        container.random.nextInt()
    }
}
