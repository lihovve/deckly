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
    fun repeatedSaveDoesNotTouchTheDatabaseFile() = runTest {
        repository.saveCurrentDeckId(5)
        val afterFirstSave = readFileChangeCounter()

        repeat(3) { repository.saveCurrentDeckId(5) }

        // Счётчик изменений в заголовке файла растёт на каждой транзакции, которая что-то записала. Экран колоды пишет
        // текущую колоду при каждом открытии, поэтому повтор обязан быть бесплатным. SQLite не помечает страницу изменённой,
        // когда запись строки не изменилась, и повтор не пишет ни журнал, ни файл. Тест упадёт, если запрос заменят на
        // такой, который пишет всегда (например, `INSERT OR REPLACE`).
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
