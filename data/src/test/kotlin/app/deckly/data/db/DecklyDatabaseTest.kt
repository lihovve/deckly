package app.deckly.data.db

import app.deckly.data.buildFlaggedCard
import app.deckly.data.buildPlainCard
import app.deckly.data.createTestStorage
import app.deckly.data.openTestDatabase
import app.deckly.domain.model.createDeck
import androidx.room3.useWriterConnection
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DecklyDatabaseTest {

    private val storage = createTestStorage()
    private lateinit var database: DecklyDatabase

    @Before
    fun openDatabase() {
        database = openTestDatabase(storage)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun databaseUsesTruncateJournal() = runTest {
        assertEquals("truncate", readPragmaText("PRAGMA journal_mode"))
    }

    @Test
    fun databaseLivesInRootFolder() = runTest {
        database.appStateDao().findAppState()

        assertTrue(storage.databaseFile.isFile)
        assertEquals(storage.rootDir, storage.databaseFile.parentFile)
        assertEquals("deckly.db", storage.databaseFile.name)
    }

    @Test
    fun databaseHasAllFourTables() = runTest {
        val tables = readColumn("SELECT name FROM sqlite_master WHERE type = 'table'")

        assertTrue(tables.toString(), tables.containsAll(listOf("deck", "card", "draft", "app_state")))
    }

    @Test
    fun freshDatabaseHasAppStateWithoutCurrentDeck() = runTest {
        val appState = database.appStateDao().findAppState()

        assertNotNull(appState)
        assertEquals(1, appState!!.id)
        assertNull(appState.currentDeckId)
        assertEquals(1L, appState.nextCardId)
    }

    @Test
    fun deckIdsAreNeverReused() = runTest {
        val first = database.deckDao().insertDeck(mapDeckEntity(createDeck(0, "Первая")))
        database.deckDao().deleteDeck(first)

        val second = database.deckDao().insertDeck(mapDeckEntity(createDeck(0, "Вторая")))

        assertNotEquals(first, second)
    }

    @Test
    fun deletingDeckDeletesItsCards() = runTest {
        val deckId = database.deckDao().insertDeck(mapDeckEntity(createDeck(0, "Колода")))
        database.cardDao().insertCard(mapCardEntity(buildPlainCard(1), deckId, "card-1-a.dks"))
        database.cardDao().insertCard(mapCardEntity(buildPlainCard(2, position = 1), deckId, "card-2-b.dks"))

        database.deckDao().deleteDeck(deckId)

        assertEquals(emptyList<CardEntity>(), database.cardDao().listCards(deckId))
    }

    @Test
    fun cardStateUpdateChangesStateAndKeepsDeckAndContentFile() = runTest {
        val deckId = database.deckDao().insertDeck(mapDeckEntity(createDeck(0, "Колода")))
        database.cardDao().insertCard(mapCardEntity(buildPlainCard(42), deckId, "card-42-old.dks"))
        val changed = buildFlaggedCard(42)

        database.cardDao().updateCardState(mapCardState(changed))

        val stored = database.cardDao().findCard(42)
        assertNotNull(stored)
        assertEquals(changed, mapCard(stored!!))
        assertEquals(deckId, stored.deckId)
        assertEquals("card-42-old.dks", stored.contentFile)
    }

    private suspend fun readPragmaText(sql: String): String =
        database.useWriterConnection { connection ->
            connection.usePrepared(sql) { statement ->
                statement.step()
                statement.getText(0)
            }
        }

    private suspend fun readColumn(sql: String): List<String> =
        database.useWriterConnection { connection ->
            connection.usePrepared(sql) { statement ->
                val values = mutableListOf<String>()
                while (statement.step()) values += statement.getText(0)
                values
            }
        }
}
