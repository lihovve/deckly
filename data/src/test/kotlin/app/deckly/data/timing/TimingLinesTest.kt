package app.deckly.data.timing

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.TestRepositories
import app.deckly.data.buildPlainCard
import app.deckly.data.buildTestStroke
import app.deckly.data.createTestStorage
import app.deckly.data.openDecklyData
import app.deckly.data.seedDeck
import app.deckly.domain.editor.Draft
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.createDeck
import app.deckly.domain.port.StoredDraft
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** Строки разбивки времени: текст собирают проверяемые функции, а пока лог выключен, строки не собираются. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimingLinesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val originalFlags = context.applicationInfo.flags

    @Before
    fun startFromReleaseWithoutTagLevel() {
        context.applicationInfo.flags = originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        ShadowLog.setLoggable(DecklyTiming.TAG, Log.INFO)
        DecklyTiming.enableIfRequested(context)
        ShadowLog.clear()
    }

    @After
    fun restore() {
        context.applicationInfo.flags = originalFlags
        ShadowLog.setLoggable(DecklyTiming.TAG, Log.INFO)
        DecklyTiming.enableIfRequested(context)
    }

    private fun enableLog() {
        ShadowLog.setLoggable(DecklyTiming.TAG, Log.DEBUG)
        DecklyTiming.enableIfRequested(context)
    }

    private fun readLines(): List<String> = ShadowLog.getLogsForTag(DecklyTiming.TAG).map { it.msg }

    // --- тексты строк ---------------------------------------------------------------------------

    @Test
    fun openLineListsProcessAgeAndEveryStage() {
        val line = DecklyTiming.describeOpen(
            processAgeMs = 812.34,
            quickCheckNanos = 48_100_000,
            roomNanos = 35_200_000,
            totalNanos = 745_600_000,
        )

        assertEquals("open sinceProcessStart=812.3ms quickCheck=48.1ms room=35.2ms total=745.6ms", line)
    }

    @Test
    fun cleanupLineSplitsReferencesAndFiles() {
        assertEquals(
            "cleanup references=612.0ms files=14.9ms",
            DecklyTiming.describeCleanup(referencesNanos = 612_000_000, filesNanos = 14_900_000),
        )
    }

    @Test
    fun draftLineHasFileTransactionDeletionAndStrokeCountsOfBothSides() {
        assertEquals(
            "draft file=3.2ms db=17.4ms delete=0.4ms strokes=12/0",
            DecklyTiming.describeDraftStore(
                fileNanos = 3_200_000,
                transactionNanos = 17_400_000,
                deleteNanos = 400_000,
                frontStrokes = 12,
                backStrokes = 0,
            ),
        )
    }

    @Test
    fun millisecondsUseOneDigitAndDotWhateverTheLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"))

            assertEquals("cleanup references=0.5ms files=1234.6ms", DecklyTiming.describeCleanup(500_000, 1_234_567_890))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    // --- строки при открытии хранения и записи черновика -----------------------------------------

    @Test
    fun openingStorageWritesOpenAndCleanupLinesWhenTheLogIsOn() = runTest {
        enableLog()
        val storage = createTestStorage()

        openDecklyData(context, storage.rootDir).use {
            val lines = readLines()
            val open = lines.single { it.startsWith("open ") }
            val cleanup = lines.single { it.startsWith("cleanup ") }
            assertTrue(open, Regex("open sinceProcessStart=-?\\d+\\.\\dms quickCheck=\\d+\\.\\dms room=\\d+\\.\\dms total=\\d+\\.\\dms").matches(open))
            assertTrue(cleanup, Regex("cleanup references=\\d+\\.\\dms files=\\d+\\.\\dms").matches(cleanup))
            assertTrue("в строках нет пути хранилища", lines.none { it.contains(storage.rootDir.name) })
        }
    }

    @Test
    fun openingStorageWritesNothingWhenTheLogIsOff() = runTest {
        val storage = createTestStorage()

        openDecklyData(context, storage.rootDir).use {
            assertTrue(readLines().isEmpty())
        }
    }

    @Test
    fun storingDraftWritesOneLineWithoutNamesWhenTheLogIsOn() = runTest {
        val repos = TestRepositories(createTestStorage())
        try {
            val deck = createDeck(1, "Секретная колода")
            seedDeck(repos, DeckState(deck, listOf(buildPlainCard(101, 0))))
            val draft = Draft(null, CardSide(listOf(buildTestStroke(1), buildTestStroke(2))), CardSide(listOf(buildTestStroke(3))), Side.FRONT, Section.NEW)
            enableLog()

            repos.drafts.storeDraft(StoredDraft(1, draft))

            val line = readLines().single { it.startsWith("draft ") }
            assertTrue(line, Regex("draft file=\\d+\\.\\dms db=\\d+\\.\\dms delete=\\d+\\.\\dms strokes=2/1").matches(line))
            assertFalse(line.contains("Секретная"))
        } finally {
            repos.close()
        }
    }

    @Test
    fun secondDraftWriteDeletesThePreviousFileAndLogsItsTime() = runTest {
        val repos = TestRepositories(createTestStorage())
        try {
            seedDeck(repos, DeckState(createDeck(1, "Колода"), listOf(buildPlainCard(101, 0))))
            val draft = Draft(null, CardSide(listOf(buildTestStroke(1))), CardSide(), Side.FRONT, Section.NEW)
            repos.drafts.storeDraft(StoredDraft(1, draft))
            val firstFiles = listDraftFileNames(repos)
            enableLog()

            repos.drafts.storeDraft(StoredDraft(1, draft.copy(front = CardSide(listOf(buildTestStroke(5))))))

            val lines = readLines().filter { it.startsWith("draft ") }
            assertEquals(1, lines.size)
            assertTrue(lines[0], Regex("draft file=\\d+\\.\\dms db=\\d+\\.\\dms delete=\\d+\\.\\dms strokes=1/0").matches(lines[0]))
            val secondFiles = listDraftFileNames(repos)
            assertEquals(1, secondFiles.size)
            assertTrue("прежний файл удалён", secondFiles.intersect(firstFiles.toSet()).isEmpty())
        } finally {
            repos.close()
        }
    }

    private fun listDraftFileNames(repos: TestRepositories): List<String> =
        repos.storage.strokesDir.list().orEmpty().filter { it.startsWith("draft-") }

    @Test
    fun storingDraftWritesNothingWhenTheLogIsOff() = runTest {
        val repos = TestRepositories(createTestStorage())
        try {
            seedDeck(repos, DeckState(createDeck(1, "Колода"), listOf(buildPlainCard(101, 0))))
            val draft = Draft(null, CardSide(listOf(buildTestStroke(1))), CardSide(), Side.FRONT, Section.NEW)

            repos.drafts.storeDraft(StoredDraft(1, draft))

            assertTrue(readLines().isEmpty())
        } finally {
            repos.close()
        }
    }
}
