package app.deckly.data.repository

import app.deckly.data.CancelAfterWriteStrokeFiles
import app.deckly.data.CancelWhenDatabaseDispatcher
import app.deckly.data.FailingWriteStrokeFiles
import app.deckly.data.GatedDraftFiles
import app.deckly.data.SCENARIO_DAY
import app.deckly.data.TestRepositories
import app.deckly.data.assertSuspendThrows
import app.deckly.data.buildPlainCard
import app.deckly.data.buildSeedContent
import app.deckly.data.buildSeedContentFile
import app.deckly.data.buildTestStroke
import app.deckly.data.createTestStorage
import app.deckly.data.hasStrokeFile
import app.deckly.data.listStrokeFileNames
import app.deckly.data.seedDeck
import app.deckly.domain.editor.Draft
import app.deckly.domain.editor.saveDraft
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.collectDeckChanges
import app.deckly.domain.model.createDeck
import app.deckly.domain.port.StoredDraft
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomDraftRepositoryTest {

    private val repos = TestRepositories(createTestStorage())
    private val deck = createDeck(1, "Колода")
    private val baseState = DeckState(deck, listOf(buildPlainCard(101, 0), buildPlainCard(102, 1)))

    @After
    fun closeRepositories() {
        repos.close()
    }

    private fun buildNewCardDraft(seed: Int = 7, side: Side = Side.BACK): Draft =
        Draft(null, listOf(buildTestStroke(seed)), listOf(buildTestStroke(seed + 1)), side, Section.NEW)

    private fun buildEditDraft(cardId: Long, seed: Int = 20): Draft =
        Draft(cardId, listOf(buildTestStroke(seed)), emptyList(), Side.FRONT, Section.NEW)

    private fun listDraftFiles(): List<String> =
        listStrokeFileNames(repos.storage).filter { it.startsWith("draft-") }

    /** Репозиторий черновика, у которого запись файлов падает; база и удаление файлов настоящие. */
    private fun buildDraftsOnFullDisk(): RoomDraftRepository =
        RoomDraftRepository(repos.database, FailingWriteStrokeFiles(repos.storage.strokesDir))

    @Test
    fun newCardCommitWritesFileAndRemovesDraft() = runTest {
        seedDeck(repos, baseState)
        val draft = buildNewCardDraft()
        repos.drafts.storeDraft(StoredDraft(1, draft))
        val cardId = repos.drafts.allocateCardId()
        val (after, content) = saveDraft(baseState, draft, cardId, SCENARIO_DAY, now = 1_000L)

        repos.drafts.commitDraft(collectDeckChanges(baseState, after), content)

        val stored = repos.decks.loadDeckState(1).cards.single { it.id == cardId }
        assertEquals(Section.NEW, stored.section)
        assertEquals(2, stored.position)
        assertEquals(content, repos.contents.loadContent(cardId))
        assertNull(repos.drafts.loadDraft())
        assertEquals(emptyList<String>(), listDraftFiles())
        assertEquals(
            setOf(buildSeedContentFile(101), buildSeedContentFile(102)),
            listStrokeFileNames(repos.storage).filterNot { it.startsWith("card-$cardId-") }.toSet(),
        )
    }

    @Test
    fun editCommitReplacesFileAndDeletesOld() = runTest {
        seedDeck(repos, baseState)
        val oldFile = repos.database.cardDao().findCard(101)!!.contentFile
        val draft = buildEditDraft(101)
        repos.drafts.storeDraft(StoredDraft(1, draft))
        val (after, content) = saveDraft(baseState, draft, newCardId = 0, today = SCENARIO_DAY, now = 1_000L)

        repos.drafts.commitDraft(collectDeckChanges(baseState, after), content)

        val stored = repos.database.cardDao().findCard(101)!!
        assertNotEquals(oldFile, stored.contentFile)
        assertFalse(hasStrokeFile(repos.storage, oldFile))
        assertEquals(content, repos.contents.loadContent(101))
        assertEquals(baseState.cards.first(), repos.decks.loadDeckState(1).cards.first())
        assertEquals(emptyList<String>(), listDraftFiles())
        assertNull(repos.drafts.loadDraft())
    }

    @Test
    fun draftSurvivesReopeningOnSameSide() = runTest {
        seedDeck(repos, baseState)
        val stored = StoredDraft(1, buildNewCardDraft(side = Side.BACK).copy(returnSection = Section.EPOCH_2))

        repos.drafts.storeDraft(stored)
        repos.reopen()

        assertEquals(stored, repos.drafts.loadDraft())
    }

    @Test
    fun savingDraftReplacesItsFile() = runTest {
        seedDeck(repos, baseState)
        repos.drafts.storeDraft(StoredDraft(1, buildNewCardDraft(seed = 1)))
        val second = StoredDraft(1, buildNewCardDraft(seed = 2))

        repos.drafts.storeDraft(second)

        assertEquals(1, listDraftFiles().size)
        assertEquals(second, repos.drafts.loadDraft())
    }

    @Test
    fun discardingDraftRemovesItsRowAndFile() = runTest {
        seedDeck(repos, baseState)
        repos.drafts.storeDraft(StoredDraft(1, buildNewCardDraft()))

        repos.drafts.discardDraft()

        assertNull(repos.drafts.loadDraft())
        assertNull(repos.database.draftDao().findDraft())
        assertEquals(emptyList<String>(), listDraftFiles())
    }

    @Test
    fun draftOfDeletedCardIsDropped() = runTest {
        seedDeck(repos, baseState)
        repos.drafts.storeDraft(StoredDraft(1, buildEditDraft(101)))
        // Убили между удалением карточки и удалением черновика.
        repos.decks.saveDeckChanges(DeckChanges(deck, emptyList(), setOf(101L)))

        assertNull(repos.drafts.loadDraft())

        assertNull(repos.database.draftDao().findDraft())
        assertEquals(emptyList<String>(), listDraftFiles())
    }

    @Test
    fun allocatedCardIdsGrowByOne() = runTest {
        assertEquals(1L, repos.drafts.allocateCardId())
        assertEquals(2L, repos.drafts.allocateCardId())

        repos.reopen()

        assertEquals(3L, repos.drafts.allocateCardId())
    }

    @Test
    fun allocatedCardIdBatchesAreConsecutiveAndDisjoint() = runTest {
        assertEquals(listOf(1L, 2L, 3L), repos.drafts.allocateCardIds(3))
        assertEquals(emptyList<Long>(), repos.drafts.allocateCardIds(0))

        repos.reopen()

        assertEquals(listOf(4L, 5L), repos.drafts.allocateCardIds(2))
        assertEquals(6L, repos.drafts.allocateCardId())
    }

    @Test
    fun cardIdsAreNeverReused() = runTest {
        seedDeck(repos, baseState)
        val draft = buildNewCardDraft()
        val firstId = repos.drafts.allocateCardId()
        val (after, content) = saveDraft(baseState, draft, firstId, SCENARIO_DAY, now = 1_000L)
        repos.drafts.commitDraft(collectDeckChanges(baseState, after), content)
        repos.decks.saveDeckChanges(DeckChanges(deck, emptyList(), setOf(firstId)))

        val secondId = repos.drafts.allocateCardId()

        assertTrue("$secondId должен быть больше $firstId", secondId > firstId)
    }

    @Test
    fun failedCommitFileWriteChangesNothing() = runTest {
        seedDeck(repos, baseState)
        val draft = buildNewCardDraft()
        repos.drafts.storeDraft(StoredDraft(1, draft))
        val cardId = repos.drafts.allocateCardId()
        val (after, content) = saveDraft(baseState, draft, cardId, SCENARIO_DAY, now = 1_000L)
        val namesBefore = listStrokeFileNames(repos.storage)

        assertSuspendThrows<IOException> {
            buildDraftsOnFullDisk().commitDraft(collectDeckChanges(baseState, after), content)
        }

        assertNull(repos.database.cardDao().findCard(cardId))
        assertNotNull(repos.database.draftDao().findDraft())
        assertEquals("ничего не удалено и не добавлено", namesBefore, listStrokeFileNames(repos.storage))
    }

    @Test
    fun failedEditCommitFileWriteKeepsOldFile() = runTest {
        seedDeck(repos, baseState)
        val draft = buildEditDraft(101)
        repos.drafts.storeDraft(StoredDraft(1, draft))
        val (after, content) = saveDraft(baseState, draft, newCardId = 0, today = SCENARIO_DAY, now = 1_000L)
        val namesBefore = listStrokeFileNames(repos.storage)

        assertSuspendThrows<IOException> {
            buildDraftsOnFullDisk().commitDraft(collectDeckChanges(baseState, after), content)
        }

        assertEquals("ничего не удалено и не добавлено", namesBefore, listStrokeFileNames(repos.storage))
        assertEquals(buildSeedContent(101), repos.contents.loadContent(101))
        assertEquals(StoredDraft(1, draft), repos.drafts.loadDraft())
    }

    @Test
    fun failedDraftFileWriteKeepsPreviousDraftAndItsFile() = runTest {
        seedDeck(repos, baseState)
        val previous = StoredDraft(1, buildNewCardDraft(seed = 1))
        repos.drafts.storeDraft(previous)
        val previousFile = repos.database.draftDao().findDraft()!!.contentFile

        assertSuspendThrows<IOException> {
            buildDraftsOnFullDisk().storeDraft(StoredDraft(1, buildNewCardDraft(seed = 2)))
        }

        assertEquals(previousFile, repos.database.draftDao().findDraft()!!.contentFile)
        assertTrue(hasStrokeFile(repos.storage, previousFile))
        assertEquals(previous, repos.drafts.loadDraft())
    }

    @Test
    fun draftFileIsKeptWhenTheOutcomeCannotBeChecked() = runTest {
        seedDeck(repos, baseState)
        val namesBefore = listStrokeFileNames(repos.storage)
        repos.database.close()

        assertSuspendThrows<Exception> { repos.drafts.storeDraft(StoredDraft(1, buildNewCardDraft())) }

        // База закрыта: узнать, прошла ли транзакция, нельзя, а в сомнении файл остаётся.
        val orphan = (listStrokeFileNames(repos.storage) - namesBefore).single()
        repos.reopen()
        removeUnreferencedStrokeFiles(repos.database, repos.files)
        assertFalse("строки черновика нет, при следующем открытии файл убран", hasStrokeFile(repos.storage, orphan))
    }

    @Test
    fun failedCommitKeepsOldFileAndDraft() = runTest {
        seedDeck(repos, baseState)
        val draft = buildEditDraft(101)
        repos.drafts.storeDraft(StoredDraft(1, draft))
        val (_, content) = saveDraft(baseState, draft, newCardId = 0, today = SCENARIO_DAY, now = 1_000L)
        val namesBefore = listStrokeFileNames(repos.storage)
        val changesOfMissingDeck = DeckChanges(createDeck(999, "Нет такой"), emptyList(), emptySet())

        assertSuspendThrows<IllegalStateException> { repos.drafts.commitDraft(changesOfMissingDeck, content) }

        assertEquals("новый файл должен быть убран, старые остаться", namesBefore, listStrokeFileNames(repos.storage))
        assertEquals(buildSeedContent(101), repos.contents.loadContent(101))
        assertEquals(StoredDraft(1, draft), repos.drafts.loadDraft())
    }

    @Test
    fun overlappingStoresKeepTheLastStroke() = runBlocking {
        seedDeck(repos, baseState)
        val gated = GatedDraftFiles(repos.storage.strokesDir)
        val drafts = RoomDraftRepository(repos.database, gated)
        val older = buildNewCardDraft(seed = 1)
        val newer = older.copy(front = older.front + buildTestStroke(2))

        val first = async(Dispatchers.Default) { drafts.storeDraft(StoredDraft(1, older)) }
        assertTrue(gated.firstWritten.await(5, TimeUnit.SECONDS))
        val second = async(Dispatchers.Default) { drafts.storeDraft(StoredDraft(1, newer)) }
        // Без очереди второе сохранение проходит целиком, пока первое ждёт; с очередью оно ждёт конца первого.
        withTimeoutOrNull(SETTLE_MILLIS) { second.await() }
        gated.release.countDown()
        first.await()
        second.await()

        assertEquals(StoredDraft(1, newer), drafts.loadDraft())
        assertEquals(1, listDraftFiles().size)
    }

    @Test
    fun storeInFlightDoesNotResurrectCommittedDraft() = runBlocking {
        seedDeck(repos, baseState)
        val gated = GatedDraftFiles(repos.storage.strokesDir)
        val drafts = RoomDraftRepository(repos.database, gated)
        val draft = buildNewCardDraft()
        val cardId = drafts.allocateCardId()
        val (after, content) = saveDraft(baseState, draft, cardId, SCENARIO_DAY, now = 1_000L)

        val lateStore = async(Dispatchers.Default) { drafts.storeDraft(StoredDraft(1, draft)) }
        assertTrue(gated.firstWritten.await(5, TimeUnit.SECONDS))
        val commit = async(Dispatchers.Default) { drafts.commitDraft(collectDeckChanges(baseState, after), content) }
        // Без очереди сохранение карточки проходит, пока черновик ещё в полёте.
        withTimeoutOrNull(SETTLE_MILLIS) { commit.await() }
        gated.release.countDown()
        lateStore.await()
        commit.await()

        assertNull("после сохранения карточки черновика быть не должно", drafts.loadDraft())
        assertNull(repos.database.draftDao().findDraft())
        assertEquals(emptyList<String>(), listDraftFiles())
        assertEquals(3, repos.decks.loadDeckState(1).cards.size)
    }

    @Test
    fun storeInFlightDoesNotResurrectDiscardedDraft() = runBlocking {
        seedDeck(repos, baseState)
        val gated = GatedDraftFiles(repos.storage.strokesDir)
        val drafts = RoomDraftRepository(repos.database, gated)

        val lateStore = async(Dispatchers.Default) { drafts.storeDraft(StoredDraft(1, buildNewCardDraft())) }
        assertTrue(gated.firstWritten.await(5, TimeUnit.SECONDS))
        val discard = async(Dispatchers.Default) { drafts.discardDraft() }
        withTimeoutOrNull(SETTLE_MILLIS) { discard.await() }
        gated.release.countDown()
        lateStore.await()
        discard.await()

        assertNull(drafts.loadDraft())
        assertNull(repos.database.draftDao().findDraft())
        assertEquals(emptyList<String>(), listDraftFiles())
    }

    @Test
    fun loadDraftWaitsForStoreInFlight() = runBlocking {
        seedDeck(repos, baseState)
        val gated = GatedDraftFiles(repos.storage.strokesDir)
        val drafts = RoomDraftRepository(repos.database, gated)
        val stored = StoredDraft(1, buildNewCardDraft())

        val store = async(Dispatchers.Default) { drafts.storeDraft(stored) }
        assertTrue(gated.firstWritten.await(5, TimeUnit.SECONDS))
        val load = async(Dispatchers.Default) { drafts.loadDraft() }
        val earlyAnswer = withTimeoutOrNull(SETTLE_MILLIS) { load.await() }
        gated.release.countDown()
        store.await()

        assertNull("чтение не должно обгонять сохранение, начатое раньше", earlyAnswer)
        assertEquals(stored, load.await())
    }

    @Test
    fun allocateCardIdWaitsForStoreInFlight() = runBlocking {
        seedDeck(repos, baseState)
        val gated = GatedDraftFiles(repos.storage.strokesDir)
        val drafts = RoomDraftRepository(repos.database, gated)
        val expectedId = repos.database.appStateDao().findAppState()!!.nextCardId

        val store = async(Dispatchers.Default) { drafts.storeDraft(StoredDraft(1, buildNewCardDraft())) }
        assertTrue(gated.firstWritten.await(5, TimeUnit.SECONDS))
        val allocate = async(Dispatchers.Default) { drafts.allocateCardId() }
        val earlyAnswer = withTimeoutOrNull(SETTLE_MILLIS) { allocate.await() }
        gated.release.countDown()
        store.await()

        assertNull("выдача id идёт по очереди после начатого раньше сохранения", earlyAnswer)
        assertEquals(expectedId, allocate.await())
    }

    @Test
    fun cancelledEditCommitAfterTransactionKeepsFile() = runBlocking {
        seedDeck(repos, baseState)
        val draft = buildEditDraft(101)
        repos.drafts.storeDraft(StoredDraft(1, draft))
        val (after, content) = saveDraft(baseState, draft, newCardId = 0, today = SCENARIO_DAY, now = 1_000L)
        // Строка черновика исчезает в той же транзакции, что и ссылка на новый файл: значит, коммит прошёл.
        val dispatcher = CancelWhenDatabaseDispatcher(repos.storage.databaseFile, "SELECT COUNT(*) FROM draft") { it == 0 }

        val job = launch(dispatcher) { repos.drafts.commitDraft(collectDeckChanges(baseState, after), content) }
        job.join()
        dispatcher.close()

        assertTrue("сценарий не дошёл до отмены после коммита", dispatcher.cancelled && job.isCancelled)
        repos.reopen()
        assertEquals(content, repos.contents.loadContent(101))
    }

    @Test
    fun cancelledNewCardCommitAfterTransactionKeepsFile() = runBlocking {
        seedDeck(repos, baseState)
        val draft = buildNewCardDraft()
        repos.drafts.storeDraft(StoredDraft(1, draft))
        val cardId = repos.drafts.allocateCardId()
        val (after, content) = saveDraft(baseState, draft, cardId, SCENARIO_DAY, now = 1_000L)
        val dispatcher = CancelWhenDatabaseDispatcher(repos.storage.databaseFile, "SELECT COUNT(*) FROM draft") { it == 0 }

        val job = launch(dispatcher) { repos.drafts.commitDraft(collectDeckChanges(baseState, after), content) }
        job.join()
        dispatcher.close()

        assertTrue("сценарий не дошёл до отмены после коммита", dispatcher.cancelled && job.isCancelled)
        repos.reopen()
        assertEquals(content, repos.contents.loadContent(cardId))
    }

    @Test
    fun cancelledStoreDraftAfterTransactionKeepsFile() = runBlocking {
        seedDeck(repos, baseState)
        val stored = StoredDraft(1, buildNewCardDraft())
        val dispatcher = CancelWhenDatabaseDispatcher(repos.storage.databaseFile, "SELECT COUNT(*) FROM draft") { it == 1 }

        val job = launch(dispatcher) { repos.drafts.storeDraft(stored) }
        job.join()
        dispatcher.close()

        assertTrue("сценарий не дошёл до отмены после коммита", dispatcher.cancelled && job.isCancelled)
        repos.reopen()
        assertEquals(stored, repos.drafts.loadDraft())
    }

    @Test
    fun cancelledStoreDraftBeforeTransactionLeavesNoOrphan() = runBlocking {
        seedDeck(repos, baseState)
        val namesBefore = listStrokeFileNames(repos.storage)
        val files = CancelAfterWriteStrokeFiles(repos.storage.strokesDir)
        val drafts = RoomDraftRepository(repos.database, files)

        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            drafts.storeDraft(StoredDraft(1, buildNewCardDraft()))
        }
        files.job = job
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertNull(repos.database.draftDao().findDraft())
        assertEquals("записанный файл убран, транзакции не было", namesBefore, listStrokeFileNames(repos.storage))
    }

    @Test
    fun cancelledCommitBeforeTransactionLeavesNoOrphan() = runBlocking {
        seedDeck(repos, baseState)
        val draft = buildEditDraft(101)
        repos.drafts.storeDraft(StoredDraft(1, draft))
        val (after, content) = saveDraft(baseState, draft, newCardId = 0, today = SCENARIO_DAY, now = 1_000L)
        val namesBefore = listStrokeFileNames(repos.storage)
        val files = CancelAfterWriteStrokeFiles(repos.storage.strokesDir)
        val drafts = RoomDraftRepository(repos.database, files)

        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            drafts.commitDraft(collectDeckChanges(baseState, after), content)
        }
        files.job = job
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(namesBefore, listStrokeFileNames(repos.storage))
        assertEquals(buildSeedContent(101), repos.contents.loadContent(101))
        assertNotNull(repos.drafts.loadDraft())
    }

    @Test
    fun killBeforeEditTransactionKeepsNewerFileAndRemovesTemporary() = runTest {
        seedDeck(repos, baseState)
        // Шаг 1 выполнен: файл правки записан. Транзакции и удаления не было. Плюс недописанный .tmp.
        val orphan = repos.files.writeCardFile(101, listOf(buildTestStroke(50)), emptyList())
        val referencedTime = File(repos.storage.strokesDir, buildSeedContentFile(101)).lastModified()
        File(repos.storage.strokesDir, orphan).setLastModified(referencedTime + 5_000)
        val temporary = File(repos.storage.strokesDir, "card-101-0123456789abcdef.dks.tmp").apply { writeText("x") }

        repos.reopen()
        removeUnreferencedStrokeFiles(repos.database, repos.files)

        // Файл новее файла по ссылке: от базы старше strokes/ его не отличить, поэтому он остаётся.
        assertTrue(hasStrokeFile(repos.storage, orphan))
        assertFalse(temporary.exists())
        assertEquals(baseState, repos.decks.loadDeckState(1))
        assertEquals(buildSeedContent(101), repos.contents.loadContent(101))
        assertEquals(buildSeedContent(102), repos.contents.loadContent(102))
    }

    @Test
    fun orphanOfInterruptedEditIsRemovedAfterTheNextEdit() = runTest {
        seedDeck(repos, baseState)
        val orphan = repos.files.writeCardFile(101, listOf(buildTestStroke(50)), emptyList())
        File(repos.storage.strokesDir, orphan).setLastModified(10_000)
        val draft = buildEditDraft(101)
        val (after, content) = saveDraft(baseState, draft, newCardId = 0, today = SCENARIO_DAY, now = 1_000L)
        repos.drafts.commitDraft(collectDeckChanges(baseState, after), content)
        val referenced = repos.database.cardDao().findCard(101)!!.contentFile
        File(repos.storage.strokesDir, referenced).setLastModified(20_000)

        repos.reopen()
        removeUnreferencedStrokeFiles(repos.database, repos.files)

        assertEquals(setOf(referenced, buildSeedContentFile(102)), listStrokeFileNames(repos.storage))
    }

    @Test
    fun killBeforeOldFileDeletionKeepsNewFile() = runTest {
        seedDeck(repos, baseState)
        val oldFile = buildSeedContentFile(101)
        val oldBytes = File(repos.storage.strokesDir, oldFile).readBytes()
        val draft = buildEditDraft(101)
        val (after, content) = saveDraft(baseState, draft, newCardId = 0, today = SCENARIO_DAY, now = 1_000L)
        repos.drafts.commitDraft(collectDeckChanges(baseState, after), content)
        // Убили после транзакции, до удаления старого файла: старый файл остался на месте.
        File(repos.storage.strokesDir, oldFile).apply {
            writeBytes(oldBytes)
            setLastModified(1_000)
        }

        repos.reopen()
        removeUnreferencedStrokeFiles(repos.database, repos.files)

        assertFalse(hasStrokeFile(repos.storage, oldFile))
        assertEquals(content, repos.contents.loadContent(101))
        assertEquals(buildSeedContent(102), repos.contents.loadContent(102))
    }

    @Test
    fun killBeforeCreationTransactionLeavesNoOrphan() = runTest {
        seedDeck(repos, baseState)
        // Выдан id новой карточки и записан её файл, до транзакции убили.
        val cardId = repos.drafts.allocateCardId()
        val orphan = repos.files.writeCardFile(cardId, listOf(buildTestStroke(50)), emptyList())

        repos.reopen()
        removeUnreferencedStrokeFiles(repos.database, repos.files)

        assertFalse(hasStrokeFile(repos.storage, orphan))
        assertEquals(baseState, repos.decks.loadDeckState(1))
    }

    @Test
    fun staleStrokesAreKeptAndRead() = runTest {
        seedDeck(repos, baseState)
        val oldFile = buildSeedContentFile(101)
        val oldBytes = File(repos.storage.strokesDir, oldFile).readBytes()
        val draft = buildEditDraft(101)
        val (after, content) = saveDraft(baseState, draft, newCardId = 0, today = SCENARIO_DAY, now = 1_000L)
        repos.drafts.commitDraft(collectDeckChanges(baseState, after), content)
        val newFile = repos.database.cardDao().findCard(101)!!.contentFile
        // Копия снята посреди правки: база уже ссылается на новый файл, а в strokes/ ещё старый.
        File(repos.storage.strokesDir, newFile).delete()
        File(repos.storage.strokesDir, oldFile).writeBytes(oldBytes)

        repos.reopen()
        removeUnreferencedStrokeFiles(repos.database, repos.files)

        assertTrue("старый файл — единственная копия, его нельзя удалять", hasStrokeFile(repos.storage, oldFile))
        assertEquals(buildSeedContent(101), repos.contents.loadContent(101))
    }

    @Test
    fun missingDraftFileFallsBackToNewestDraftFile() = runTest {
        seedDeck(repos, baseState)
        repos.drafts.storeDraft(StoredDraft(1, buildNewCardDraft(seed = 1)))
        val referenced = repos.database.draftDao().findDraft()!!.contentFile
        File(repos.storage.strokesDir, referenced).delete()
        val older = repos.files.writeDraftFile(listOf(buildTestStroke(40)), emptyList())
        val newer = repos.files.writeDraftFile(listOf(buildTestStroke(41)), emptyList())
        File(repos.storage.strokesDir, older).setLastModified(1_000)
        File(repos.storage.strokesDir, newer).setLastModified(5_000)

        val loaded = repos.drafts.loadDraft()!!

        assertEquals(listOf(buildTestStroke(41)), loaded.draft.front)
        assertNotNull(repos.database.draftDao().findDraft())
    }

    @Test
    fun missingDraftFileWithoutAnyOtherDropsTheDraft() = runTest {
        seedDeck(repos, baseState)
        repos.drafts.storeDraft(StoredDraft(1, buildNewCardDraft()))
        File(repos.storage.strokesDir, repos.database.draftDao().findDraft()!!.contentFile).delete()

        assertNull(repos.drafts.loadDraft())

        assertNull(repos.database.draftDao().findDraft())
        assertNull("следующий запуск не должен падать снова", repos.drafts.loadDraft())
    }

    @Test
    fun damagedDraftFileDropsTheDraft() = runTest {
        seedDeck(repos, baseState)
        repos.drafts.storeDraft(StoredDraft(1, buildNewCardDraft()))
        val file = File(repos.storage.strokesDir, repos.database.draftDao().findDraft()!!.contentFile)
        file.writeBytes(file.readBytes().copyOf(10))

        assertNull(repos.drafts.loadDraft())

        assertNull(repos.database.draftDao().findDraft())
        assertEquals(emptyList<String>(), listDraftFiles())
    }

    @Test
    fun cleanUpKeepsFilesOfCardsAndDraft() = runTest {
        seedDeck(repos, baseState)
        repos.drafts.storeDraft(StoredDraft(1, buildNewCardDraft()))
        val namesBefore = listStrokeFileNames(repos.storage)

        removeUnreferencedStrokeFiles(repos.database, repos.files)

        assertEquals(namesBefore, listStrokeFileNames(repos.storage))
        assertEquals(buildSeedContent(101), repos.contents.loadContent(101))
        assertNotNull(repos.drafts.loadDraft())
    }

    private companion object {
        /** Сколько ждать, чтобы вызов без очереди успел начаться; с очередью он и не начнётся. */
        const val SETTLE_MILLIS = 300L
    }
}
