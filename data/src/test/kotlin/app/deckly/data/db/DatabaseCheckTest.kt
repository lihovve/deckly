package app.deckly.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.TestRepositories
import app.deckly.data.assertSuspendThrows
import app.deckly.data.buildPlainCard
import app.deckly.data.createTestStorage
import app.deckly.data.openDecklyData
import app.deckly.data.seedDeck
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.createDeck
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseCheckTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage = createTestStorage()
    private val databaseFile = storage.databaseFile

    private fun listNames(dir: File): Set<String> = dir.list().orEmpty().toSet()

    /** Настоящая база Deckly с колодой и [cardCount] карточками, закрытая; возвращает то, что в ней лежит. */
    private suspend fun createRealDatabase(cardCount: Int): DeckState {
        val repos = TestRepositories(storage)
        val state = DeckState(createDeck(1, "Колода"), (1..cardCount).map { buildPlainCard(100L + it, it) })
        seedDeck(repos, state)
        repos.close()
        return state
    }

    private fun assertDamagedAndUntouched(file: File) {
        val bytesBefore = file.readBytes()
        val namesBefore = listNames(file.parentFile!!)

        val failure = runCatching { checkDatabaseIntegrity(file) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is DatabaseDamagedException)
        assertEquals(file, (failure as DatabaseDamagedException).file)
        assertTrue("файл базы изменён", bytesBefore.contentEquals(file.readBytes()))
        assertEquals("рядом с базой появились файлы", namesBefore, listNames(file.parentFile!!))
    }

    @Test
    fun healthyDatabasePassesCheck() = runTest {
        createRealDatabase(cardCount = 20)
        val bytesBefore = databaseFile.readBytes()

        checkDatabaseIntegrity(databaseFile)

        assertTrue(bytesBefore.contentEquals(databaseFile.readBytes()))
    }

    @Test
    fun missingOrEmptyFileMeansNewDatabase() {
        checkDatabaseIntegrity(databaseFile)
        assertFalse("проверка не должна создавать базу", databaseFile.exists())

        databaseFile.writeBytes(ByteArray(0))
        checkDatabaseIntegrity(databaseFile)
        assertEquals(0L, databaseFile.length())
    }

    @Test
    fun garbageFileIsReportedAndLeftAlone() {
        databaseFile.writeText("это не база SQLite, а просто текст, который длиннее заголовка базы")

        assertDamagedAndUntouched(databaseFile)
    }

    @Test
    fun truncatedDatabaseIsReportedAndLeftAlone() = runTest {
        createRealDatabase(cardCount = 200)
        RandomAccessFile(databaseFile, "rw").use { it.setLength(it.length() / 2) }

        assertDamagedAndUntouched(databaseFile)
    }

    @Test
    fun corruptedPagesWithIntactHeaderAreReportedAndLeftAlone() = runTest {
        createRealDatabase(cardCount = 50)
        RandomAccessFile(databaseFile, "rw").use { file ->
            val garbage = Random(7).nextBytes((file.length() - PAGE_SIZE).toInt())
            file.seek(PAGE_SIZE)
            file.write(garbage)
        }

        assertDamagedAndUntouched(databaseFile)
    }

    @Test
    fun corruptedDataPageIsFoundByQuickCheckAndLeftAlone() = runTest {
        createRealDatabase(cardCount = 200)
        RandomAccessFile(databaseFile, "rw").use { file ->
            // Последняя страница — лист данных: база с ней открывается, а quick_check видит порчу.
            file.seek(file.length() - PAGE_SIZE)
            file.write(Random(11).nextBytes(PAGE_SIZE.toInt()))
        }
        assertDatabaseOpensAndQuickCheckFails()

        assertDamagedAndUntouched(databaseFile)
    }

    @Test
    fun unusedPageIsReportedByQuickCheckAndLeftAlone() = runTest {
        createRealDatabase(cardCount = 50)
        RandomAccessFile(databaseFile, "rw").use { file ->
            // Файл и заголовок говорят о лишней странице, на которую никто не ссылается.
            val pages = file.length() / PAGE_SIZE
            file.seek(file.length())
            file.write(ByteArray(PAGE_SIZE.toInt()))
            file.seek(PAGE_COUNT_OFFSET)
            file.writeInt((pages + 1).toInt())
        }
        assertDatabaseOpensAndQuickCheckFails()

        assertDamagedAndUntouched(databaseFile)
    }

    /** Предпосылка двух тестов выше: открытие базы проходит, ошибка видна только `PRAGMA quick_check`. */
    private fun assertDatabaseOpensAndQuickCheckFails() {
        val copy = File(storage.rootDir, "probe-copy.db")
        databaseFile.copyTo(copy)
        try {
            val database = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE) { }
            try {
                val answer = runCatching {
                    database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                    }
                }
                assertTrue("quick_check не нашёл ошибок: $answer", answer.isFailure || answer.getOrThrow() != listOf("ok"))
            } finally {
                database.close()
            }
        } finally {
            copy.delete()
            File(copy.path + "-journal").delete()
        }
    }

    @Test
    fun interruptedTransactionIsRolledBackNotReportedAsDamage() = runTest {
        val before = createRealDatabase(cardCount = 3)
        val sizeBefore = databaseFile.length()
        val copyForCheck = File(storage.rootDir.parentFile, "${storage.rootDir.name}-copy-check").apply { mkdirs() }
        val copyForOpen = File(storage.rootDir.parentFile, "${storage.rootDir.name}-copy-open").apply { mkdirs() }
        val raw = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            // Сырое соединение Robolectric держит журнал в памяти; Room пишет его в файл (TRUNCATE).
            raw.rawQuery("PRAGMA journal_mode=TRUNCATE", null).use { it.moveToFirst() }
            raw.beginTransaction()
            // Столько данных, что страницы не помещаются в кэш SQLite и уходят в файл базы до коммита.
            repeat(INSERTED_ROWS) { index -> raw.insertOrThrow("card", null, buildCardRow(1_000L + index)) }
            // Состояние «убили посреди записи»: файл базы уже изменён, рядом лежит горячий журнал.
            val journal = File(databaseFile.path + "-journal")
            assertTrue("журнал должен быть непустым", journal.length() > 0)
            assertTrue("страницы должны были уйти в файл базы", databaseFile.length() > sizeBefore)
            for (copy in listOf(copyForCheck, copyForOpen)) {
                databaseFile.copyTo(File(copy, "deckly.db"))
                journal.copyTo(File(copy, "deckly.db-journal"))
            }
        } finally {
            raw.endTransaction()
            raw.close()
        }

        checkDatabaseIntegrity(File(copyForCheck, "deckly.db"))

        openDecklyData(context, copyForOpen).use { data ->
            assertEquals(before, data.decks.loadDeckState(1))
        }
    }

    private fun buildCardRow(id: Long): ContentValues = ContentValues().apply {
        put("id", id)
        put("deck_id", 1L)
        put("section", "NEW")
        put("position", 0)
        put("successes", 0)
        put("section_day", 0)
        putNull("last_success_day")
        put("created_at", 0L)
        put("postponed", 0)
        put("answer_revealed", 0)
        put("failed", 0)
        put("checking", 0)
        put("excluded_from_pass", 0)
        put("content_file", "x".repeat(CONTENT_FILE_CHARS))
    }

    private companion object {
        const val PAGE_SIZE = 4096L
        const val PAGE_COUNT_OFFSET = 28L
        const val INSERTED_ROWS = 4_000
        const val CONTENT_FILE_CHARS = 1_000
    }
}
