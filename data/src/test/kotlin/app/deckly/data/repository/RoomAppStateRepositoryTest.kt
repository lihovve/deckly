package app.deckly.data.repository

import app.deckly.data.createTestStorage
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.openTestDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomAppStateRepositoryTest {

    private val storage = createTestStorage()
    private lateinit var database: DecklyDatabase
    private lateinit var repository: RoomAppStateRepository

    @Before
    fun openRepository() {
        database = openTestDatabase(storage)
        repository = RoomAppStateRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun currentDeckIdRoundTrips() = runTest {
        assertNull(repository.loadCurrentDeckId())

        repository.saveCurrentDeckId(5)
        assertEquals(5L, repository.loadCurrentDeckId())

        repository.saveCurrentDeckId(8)
        database.close()
        openRepository()

        assertEquals(8L, repository.loadCurrentDeckId())
    }
}
