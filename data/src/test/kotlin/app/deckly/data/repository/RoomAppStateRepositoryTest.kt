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
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    @Test
    fun savingTheSameCurrentDeckChangesNoRows() = runTest {
        val dao = database.appStateDao()

        // Пустое значение `NULL` не равно никакой колоде: первая запись меняет строку.
        assertEquals(1, dao.updateCurrentDeck(5))
        assertEquals(5L, repository.loadCurrentDeckId())

        assertEquals(0, dao.updateCurrentDeck(5))
        assertEquals(5L, repository.loadCurrentDeckId())

        assertEquals(1, dao.updateCurrentDeck(8))
        assertEquals(8L, repository.loadCurrentDeckId())

        assertEquals(0, dao.updateCurrentDeck(8))
        assertEquals(8L, repository.loadCurrentDeckId())
    }

    @Test
    fun savingTheSameDeckAgainAfterClearingChangesTheRow() = runTest {
        val dao = database.appStateDao()
        dao.updateCurrentDeck(5)

        dao.clearCurrentDeck(5)
        assertNull(repository.loadCurrentDeckId())

        assertEquals(1, dao.updateCurrentDeck(5))
        assertEquals(5L, repository.loadCurrentDeckId())
    }

    @Test
    fun repeatedSaveDoesNotTouchTheDatabaseFile() = runTest {
        repository.saveCurrentDeckId(5)
        val afterFirstSave = readFileChangeCounter()

        repeat(3) { repository.saveCurrentDeckId(5) }

        // Счётчик изменений в заголовке файла растёт на каждой транзакции, которая что-то записала. Повтор страниц не
        // меняет. SQLite не пишет страницу и по прежнему запросу, если значение в строке то же (проверено без условия),
        // поэтому тест держит это свойство, а не само условие: условие даёт 0 строк и делает намерение явным.
        assertEquals(afterFirstSave, readFileChangeCounter())
        assertEquals(5L, repository.loadCurrentDeckId())

        repository.saveCurrentDeckId(8)

        assertEquals(afterFirstSave + 1, readFileChangeCounter())
        assertEquals(8L, repository.loadCurrentDeckId())
    }

    // Байты 24..27 заголовка SQLite — «file change counter», big-endian.
    private fun readFileChangeCounter(): Long =
        RandomAccessFile(storage.databaseFile, "r").use { file ->
            val header = ByteArray(28)
            file.readFully(header)
            ByteBuffer.wrap(header, 24, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        }
}
