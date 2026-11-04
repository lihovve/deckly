package app.deckly.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.createTestStorage
import java.io.File
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaScannerConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStoreNotifierTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage = createTestStorage()
    private val notifier = MediaStoreNotifier(context, storage)
    private val databasePaths = setOf(
        storage.databaseFile.absolutePath,
        storage.databaseFile.absolutePath + "-journal",
    )

    @Before
    fun forgetEarlierScans() {
        ShadowMediaScannerConnection.reset()
    }

    @Test
    fun scansDatabaseJournalAndRecordedFiles() {
        val written = File(storage.strokesDir, "card-1-0123456789abcdef.dks")
        val removed = File(storage.strokesDir, "card-2-0123456789abcdef.dks")
        notifier.recordChanged(written)
        notifier.recordChanged(removed)

        notifier.notifyChanged()

        assertEquals(databasePaths + written.absolutePath + removed.absolutePath, ShadowMediaScannerConnection.getSavedPaths())
    }

    @Test
    fun forgetsRecordedFilesAfterNotifying() {
        notifier.recordChanged(File(storage.strokesDir, "card-1-0123456789abcdef.dks"))
        notifier.notifyChanged()
        ShadowMediaScannerConnection.reset()

        notifier.notifyChanged()

        assertEquals(databasePaths, ShadowMediaScannerConnection.getSavedPaths())
    }

    @Test
    fun scansDatabaseEvenWhenNothingIsRecorded() {
        notifier.notifyChanged()

        assertEquals(databasePaths, ShadowMediaScannerConnection.getSavedPaths())
    }

    @Test
    fun recordsFromSeveralThreadsWithoutLosingFiles() {
        val files = (1..40_000).map { File(storage.strokesDir, "card-$it-0123456789abcdef.dks") }
        val start = CountDownLatch(1)

        val threads = files.chunked(5_000).map { chunk ->
            Thread {
                start.await()
                chunk.forEach(notifier::recordChanged)
            }.also { it.start() }
        }
        start.countDown()
        threads.forEach { it.join() }
        notifier.notifyChanged()

        assertEquals(databasePaths + files.map { it.absolutePath }, ShadowMediaScannerConnection.getSavedPaths())
    }
}
