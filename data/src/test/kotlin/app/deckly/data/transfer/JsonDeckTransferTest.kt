package app.deckly.data.transfer

import app.deckly.data.CancelAfterWriteStrokeFiles
import app.deckly.data.CancelWhenDatabaseDispatcher
import app.deckly.data.FailingAfterStrokeFiles
import app.deckly.data.Scenario
import app.deckly.data.TestRepositories
import app.deckly.data.assertSuspendThrows
import app.deckly.data.buildFlaggedCard
import app.deckly.data.buildFullDeck
import app.deckly.data.buildInterruptedPassScenario
import app.deckly.data.buildPlainCard
import app.deckly.data.buildWaitingEpochScenario
import app.deckly.data.createTestStorage
import app.deckly.data.listStrokeFileNames
import app.deckly.data.loadNormalizedDeck
import app.deckly.data.seedDeck
import app.deckly.data.strokes.StrokeFileException
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.collectDeckChanges
import app.deckly.domain.model.createDeck
import app.deckly.domain.port.ImportException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JsonDeckTransferTest {

    private val repos = TestRepositories(createTestStorage())

    @After
    fun closeRepositories() {
        repos.close()
    }

    private suspend fun exportToText(deckId: Long): String {
        val output = ByteArrayOutputStream()
        repos.transfer.exportDeck(deckId, output)
        return output.toString(Charsets.UTF_8)
    }

    private fun asStream(text: String): ByteArrayInputStream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    /** Исход импорта текста строкой: имя `ImportError`, `imported` или `crash <класс>` при постороннем сбое. */
    private suspend fun importOutcome(text: String): String =
        try {
            repos.transfer.importDeck(asStream(text))
            "imported"
        } catch (failure: ImportException) {
            failure.error.name
        } catch (failure: Exception) {
            "crash ${failure::class.simpleName}"
        }

    private suspend fun saveScenario(scenario: Scenario) {
        seedDeck(repos, scenario.before)
        repos.decks.saveDeckChanges(collectDeckChanges(scenario.before, scenario.after))
    }

    private suspend fun readNextCardId(): Long = repos.database.appStateDao().findAppState()!!.nextCardId

    @Test
    fun exportThenImportGivesSameState() = runTest {
        val scenario = buildInterruptedPassScenario()
        saveScenario(scenario)

        val imported = repos.transfer.importDeck(asStream(exportToText(scenario.after.deck.id)))

        assertNotEquals(scenario.after.deck.id, imported.id)
        assertEquals(
            loadNormalizedDeck(repos, scenario.after.deck.id),
            loadNormalizedDeck(repos, imported.id),
        )
    }

    @Test
    fun exportThenImportKeepsWaitingEpochAndTail() = runTest {
        val scenario = buildWaitingEpochScenario()
        saveScenario(scenario)

        val imported = repos.transfer.importDeck(asStream(exportToText(scenario.after.deck.id)))

        val copy = loadNormalizedDeck(repos, imported.id)
        assertEquals(loadNormalizedDeck(repos, scenario.after.deck.id), copy)
        assertEquals(2, copy.state.deck.waitingEpoch)
        assertEquals(3, copy.state.deck.tail.epoch1.size)
    }

    @Test
    fun exportThenImportKeepsEveryField() = runTest {
        val deck = buildFullDeck(1).copy(
            tail = EpochValues(listOf(11L, 12L, 13L), emptyList(), listOf(7L), emptyList()),
        )
        val cards = listOf(
            buildFlaggedCard(7),
            buildFlaggedCard(11).copy(section = Section.EPOCH_2, position = 0, successes = 3, lastSuccessDay = 77),
            buildFlaggedCard(12).copy(position = 1),
            buildPlainCard(13, position = 2).copy(section = Section.LEARNED, successes = 4),
        )
        seedDeck(repos, DeckState(deck, cards))

        val imported = repos.transfer.importDeck(asStream(exportToText(1)))

        assertEquals(loadNormalizedDeck(repos, 1), loadNormalizedDeck(repos, imported.id))
        assertEquals(3, imported.tail.epoch1.size)
    }

    @Test
    fun importedCardsGetNewIdsAndTailFollowsThem() = runTest {
        val scenario = buildWaitingEpochScenario()
        saveScenario(scenario)
        val sourceIds = scenario.after.cards.map { it.id }.toSet()

        val imported = repos.transfer.importDeck(asStream(exportToText(scenario.after.deck.id)))

        val importedState = repos.decks.loadDeckState(imported.id)
        val importedIds = importedState.cards.map { it.id }.toSet()
        assertEquals(sourceIds.size, importedIds.size)
        assertTrue(importedIds.intersect(sourceIds).isEmpty())
        assertTrue(importedState.deck.tail.epoch1.all { it in importedIds })
    }

    @Test
    fun emptyDeckRoundTrips() = runTest {
        val deck = repos.decks.createDeck("Пустая")

        val imported = repos.transfer.importDeck(asStream(exportToText(deck.id)))

        assertEquals(createDeck(imported.id, "Пустая"), imported)
        assertEquals(DeckState(imported, emptyList()), repos.decks.loadDeckState(imported.id))
    }

    @Test
    fun importAlwaysCreatesNewDeck() = runTest {
        val scenario = buildWaitingEpochScenario()
        saveScenario(scenario)
        val text = exportToText(scenario.after.deck.id)

        val first = repos.transfer.importDeck(asStream(text))
        val second = repos.transfer.importDeck(asStream(text))

        val deckIds = repos.decks.listDecks().map { it.id }
        assertEquals(3, deckIds.size)
        assertEquals(setOf(scenario.after.deck.id, first.id, second.id), deckIds.toSet())
        val cardIds = deckIds.flatMap { id -> repos.decks.loadDeckState(id).cards.map { it.id } }
        assertEquals(cardIds.size, cardIds.toSet().size)
    }

    @Test
    fun exportStartsWithFormatAndVersion() = runTest {
        val deck = repos.decks.createDeck("Пустая")

        val text = exportToText(deck.id)

        assertTrue(text, text.startsWith("""{"format":"deckly-deck","version":1,"deck":{"""))
    }

    @Test
    fun exportSkipsTailIdsOfDeletedCards() = runTest {
        val deck = createDeck(1, "С хвостом").copy(
            tail = EpochValues(listOf(101L, 777L, 102L), listOf(999L), emptyList(), emptyList()),
        )
        seedDeck(repos, DeckState(deck, listOf(buildPlainCard(101), buildPlainCard(102, 1))))

        val text = exportToText(1)

        assertTrue(text, text.contains(""""tail":[[101,102],[],[],[]]"""))
        val imported = repos.transfer.importDeck(asStream(text))
        assertEquals(2, imported.tail.epoch1.size)
        assertEquals(emptyList<Long>(), imported.tail.epoch2)
    }

    @Test
    fun exportOfMissingDeckFailsWritingNothing() = runTest {
        val output = ByteArrayOutputStream()

        assertSuspendThrows<IllegalStateException> { repos.transfer.exportDeck(55, output) }

        assertEquals(0, output.size())
    }

    @Test
    fun exportWithMissingStrokeFileWritesNothing() = runTest {
        seedDeck(repos, DeckState(createDeck(1, "Колода"), listOf(buildPlainCard(101), buildPlainCard(102, 1))))
        File(repos.storage.strokesDir, repos.database.cardDao().findCard(102)!!.contentFile).delete()
        val output = ByteArrayOutputStream()

        assertSuspendThrows<StrokeFileException> { repos.transfer.exportDeck(1, output) }

        assertEquals(0, output.size())
    }

    @Test
    fun foreignFileIsNotADeck() = runTest {
        val foreign = listOf(
            """"hello"""",
            "{}",
            "[]",
            "",
            "просто текст",
            """{"format":"other-app","version":1}""",
            """{"format":5,"version":1}""",
            """{"version":1,"deck":{},"cards":[]}""",
        )

        val outcomes = foreign.associateWith { importOutcome(it) }

        assertEquals(foreign.associateWith { "NOT_A_DECK_FILE" }, outcomes)
        assertEquals(emptyList<Any>(), repos.decks.listDecks())
    }

    @Test
    fun newerVersionIsUnsupported() = runTest {
        val newer = VALID_FILE.replaceOnce(""""version":1""", """"version":2""")
        val newerWithOtherShape = """{"format":"deckly-deck","version":7,"decks":[{"title":"x"}]}"""

        assertEquals("UNSUPPORTED_VERSION", importOutcome(newer))
        assertEquals("UNSUPPORTED_VERSION", importOutcome(newerWithOtherShape))
        assertEquals(emptyList<Any>(), repos.decks.listDecks())
    }

    @Test
    fun damagedDeckCreatesNothing() = runTest {
        val cases = listOf(
            damage("unknown card section", """"section":"EPOCH_2","position":0""", """"section":"EPOCH_9","position":0"""),
            damage("unknown active section", """"activeSection":"EPOCH_2"""", """"activeSection":"MOON""""),
            damage("unknown visible side", """"visibleSide":"FRONT"""", """"visibleSide":"LEFT""""),
            damage("current epoch 0", """"currentEpoch":2""", """"currentEpoch":0"""),
            damage("current epoch 5", """"currentEpoch":2""", """"currentEpoch":5"""),
            damage("waiting epoch 1", """"waitingEpoch":0""", """"waitingEpoch":1"""),
            damage("waiting epoch 5", """"waitingEpoch":0""", """"waitingEpoch":5"""),
            damage("paused epoch 1", """"pausedEpoch":0""", """"pausedEpoch":1"""),
            damage("paused epoch 5", """"pausedEpoch":0""", """"pausedEpoch":5"""),
            damage("negative pass cursor", """"passCursor":1""", """"passCursor":-1"""),
            damage("negative browse cursor", """"browseCursor":0""", """"browseCursor":-1"""),
            damage("negative paused cursor", """"pausedCursor":0""", """"pausedCursor":-3"""),
            damage("negative portion count", """"portionRatedCount":3""", """"portionRatedCount":-1"""),
            damage("negative card position", """"section":"EPOCH_2","position":0""", """"section":"EPOCH_2","position":-1"""),
            damage("negative successes", """"successes":1""", """"successes":-1"""),
            damage("negative pass count", """"passCount":[5,1,0,0]""", """"passCount":[5,-1,0,0]"""),
            damage("tail id of unknown card", """"tail":[[11],[],[],[]]""", """"tail":[[11,99],[],[],[]]"""),
            damage("duplicate card id", """"id":12""", """"id":11"""),
            damage("pressure above one", "[3.0,4.0,1.0]", "[3.0,4.0,1.5]"),
            damage("negative pressure", "[1.0,2.0,0.5]", "[1.0,2.0,-0.1]"),
            damage("zero width", """"width":6.0""", """"width":0.0"""),
            damage("negative width", """"width":6.0""", """"width":-2.0"""),
            damage("textual width", """"width":6.0""", """"width":"wide""""),
            damage("point of two numbers", "[1.0,2.0,0.5]", "[1.0,2.0]"),
            damage("point of four numbers", "[1.0,2.0,0.5]", "[1.0,2.0,0.5,0.1]"),
            damage("three pass counts", """"passCount":[5,1,0,0]""", """"passCount":[5,1,0]"""),
            damage("five last pass days", "[null,120,null,null]", "[null,120,null,null,1]"),
            damage("three tails", """"tail":[[11],[],[],[]]""", """"tail":[[11],[],[]]"""),
            damage("unknown key", """"name":"Тест",""", """"name":"Тест","extra":1,"""),
            damage("missing field", """"successes":1,""", ""),
            damage("number instead of name", """"name":"Тест"""", """"name":5"""),
            damage("missing version", """"version":1,""", ""),
            damage("version zero", """"version":1""", """"version":0"""),
        )
        val decksBefore = repos.decks.listDecks()
        val filesBefore = listStrokeFileNames(repos.storage)
        val nextCardIdBefore = readNextCardId()

        val accepted = cases.mapNotNull { (name, text) ->
            val outcome = importOutcome(text)
            if (outcome == "DAMAGED") null else "$name: $outcome"
        }

        assertEquals("повреждённый файл принят или опознан неверно", emptyList<String>(), accepted)
        assertEquals(decksBefore, repos.decks.listDecks())
        assertEquals(filesBefore, listStrokeFileNames(repos.storage))
        assertEquals("повреждённый файл не должен расходовать id карточек", nextCardIdBefore, readNextCardId())
    }

    @Test
    fun deckFileCutAtAnyLengthIsDamaged() = runTest {
        val head = """{"format":"deckly-deck"""".length

        val wrong = (head until VALID_FILE.length).mapNotNull { length ->
            val outcome = importOutcome(VALID_FILE.substring(0, length))
            if (outcome == "DAMAGED") null else "$length: $outcome"
        }

        assertEquals(emptyList<String>(), wrong)
        assertEquals(emptyList<Any>(), repos.decks.listDecks())
    }

    @Test
    fun truncatedExportIsDamagedAndCreatesNothing() = runTest {
        val scenario = buildWaitingEpochScenario()
        saveScenario(scenario)
        val text = exportToText(scenario.after.deck.id)
        val decksBefore = repos.decks.listDecks()
        val filesBefore = listStrokeFileNames(repos.storage)

        assertEquals("DAMAGED", importOutcome(text.substring(0, text.length / 2)))

        assertEquals(decksBefore, repos.decks.listDecks())
        assertEquals(filesBefore, listStrokeFileNames(repos.storage))
    }

    @Test
    fun validFileImports() = runTest {
        val imported = repos.transfer.importDeck(asStream(VALID_FILE))

        val state = repos.decks.loadDeckState(imported.id)
        val epochTwoCard = state.cards.single { it.section == Section.EPOCH_2 }
        assertEquals("Тест", imported.name)
        assertEquals(2, state.cards.size)
        assertEquals(listOf(epochTwoCard.id), imported.tail.epoch1)
        assertEquals(1, repos.contents.loadContent(epochTwoCard.id).front.size)
    }

    @Test
    fun importFailedFileWriteLeavesNothing() = runTest {
        val filesBefore = listStrokeFileNames(repos.storage)
        val failing = JsonDeckTransfer(
            repos.database,
            FailingAfterStrokeFiles(repos.storage.strokesDir, allowedWrites = 1),
            repos.drafts,
        )

        assertSuspendThrows<IOException> { failing.importDeck(asStream(VALID_FILE)) }

        assertEquals(emptyList<Any>(), repos.decks.listDecks())
        assertEquals("записанный файл первой карточки убран", filesBefore, listStrokeFileNames(repos.storage))
    }

    @Test
    fun importFailedTransactionLeavesNothing() = runTest {
        // Счётчик указывает на занятый id: первая карточка вставляется, вторая падает внутри транзакции.
        seedDeck(repos, DeckState(createDeck(1, "Занято"), listOf(buildPlainCard(102))))
        repos.database.appStateDao().updateNextCardId(101)
        val filesBefore = listStrokeFileNames(repos.storage)
        val decksBefore = repos.decks.listDecks()

        assertSuspendThrows<Exception> { repos.transfer.importDeck(asStream(VALID_FILE)) }

        assertEquals("колода откатилась вместе с первой карточкой", decksBefore, repos.decks.listDecks())
        assertEquals(listOf(102L), repos.decks.loadDeckState(1).cards.map { it.id })
        assertEquals("файлы убраны после отката", filesBefore, listStrokeFileNames(repos.storage))
    }

    @Test
    fun cancelledImportAfterTransactionKeepsFiles() = runBlocking {
        val scenario = buildWaitingEpochScenario()
        saveScenario(scenario)
        val text = exportToText(scenario.after.deck.id)
        // Колода появляется в той же транзакции, что и ссылки на файлы: две колоды в базе значит «коммит прошёл».
        val dispatcher = CancelWhenDatabaseDispatcher(repos.storage.databaseFile, "SELECT COUNT(*) FROM deck") { it >= 2 }

        val job = launch(dispatcher) { repos.transfer.importDeck(asStream(text)) }
        job.join()
        dispatcher.close()

        assertTrue("сценарий не дошёл до отмены после коммита", dispatcher.cancelled && job.isCancelled)
        repos.reopen()
        val deckIds = repos.decks.listDecks().map { it.id }
        assertEquals(2, deckIds.size)
        for (deckId in deckIds) {
            for (card in repos.decks.loadDeckState(deckId).cards) {
                assertEquals(card.id, repos.contents.loadContent(card.id).cardId)
            }
        }
    }

    @Test
    fun cancelledImportBeforeTransactionLeavesNothing() = runBlocking {
        val filesBefore = listStrokeFileNames(repos.storage)
        val files = CancelAfterWriteStrokeFiles(repos.storage.strokesDir)
        val transfer = JsonDeckTransfer(repos.database, files, repos.drafts)

        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { transfer.importDeck(asStream(VALID_FILE)) }
        files.job = job
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(emptyList<Any>(), repos.decks.listDecks())
        assertEquals(filesBefore, listStrokeFileNames(repos.storage))
    }

    private fun damage(name: String, old: String, new: String): Pair<String, String> =
        name to VALID_FILE.replaceOnce(old, new)

    private fun String.replaceOnce(old: String, new: String): String {
        require(split(old).size == 2) { "«$old» должно встречаться в тексте ровно один раз" }
        return replace(old, new)
    }

    private companion object {
        const val CARD_WITH_STROKE = """{"id":11,"section":"EPOCH_2","position":0,"successes":1,"sectionDay":118,""" +
            """"lastSuccessDay":119,"createdAt":1793800000000,"postponed":false,"answerRevealed":false,""" +
            """"failed":false,"checking":false,"excludedFromPass":false,""" +
            """"front":[{"width":6.0,"points":[[1.0,2.0,0.5],[3.0,4.0,1.0]]}],"back":[]}"""

        const val CARD_WITHOUT_STROKES = """{"id":12,"section":"NEW","position":0,"successes":0,"sectionDay":100,""" +
            """"lastSuccessDay":null,"createdAt":1793800000001,"postponed":false,"answerRevealed":false,""" +
            """"failed":false,"checking":false,"excludedFromPass":false,"front":[],"back":[]}"""

        const val DECK = """"deck":{"name":"Тест","activeSection":"EPOCH_2","currentEpoch":2,"passStarted":true,""" +
            """"passCount":[5,1,0,0],"passCursor":1,"browseCursor":0,"lastPassDay":[null,120,null,null],""" +
            """"waitingEpoch":0,"pausedEpoch":0,"pausedCursor":0,"portionRatedCount":3,""" +
            """"visibleSide":"FRONT","tail":[[11],[],[],[]]}"""

        /** Корректный файл колоды: две карточки, у первой штрих, `tail` ссылается на неё. */
        const val VALID_FILE = """{"format":"deckly-deck","version":1,""" + DECK +
            ""","cards":[""" + CARD_WITH_STROKE + "," + CARD_WITHOUT_STROKES + "]}"
    }
}
