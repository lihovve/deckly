package app.deckly.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.DraftEntity
import app.deckly.data.db.mapCardEntity
import app.deckly.data.db.mapDeckEntity
import app.deckly.data.db.openDecklyDatabase
import app.deckly.data.repository.FileCardContentRepository
import app.deckly.data.repository.RoomAppStateRepository
import app.deckly.data.repository.RoomDeckRepository
import app.deckly.data.repository.RoomDraftRepository
import app.deckly.data.storage.DecklyStorage
import app.deckly.data.strokes.StrokeFiles
import app.deckly.data.transfer.JsonDeckTransfer
import app.deckly.data.strokes.writeStrokeFile
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue

/** Временные папки тестов: удаляются при выходе из JVM, чтобы не копиться в системной папке. */
private val temporaryRoots = ConcurrentLinkedQueue<File>().also { roots ->
    Runtime.getRuntime().addShutdownHook(Thread { roots.forEach { it.deleteRecursively() } })
}

/** Хранилище во временной папке: тесты идут без планшета. Папка существует, `strokes/` ещё нет. */
fun createTestStorage(): DecklyStorage {
    val root = Files.createTempDirectory("deckly-test").toFile()
    temporaryRoots.add(root)
    return DecklyStorage(root)
}

/** Открывает базу хранилища так же, как приложение, но на контексте Robolectric. */
fun openTestDatabase(storage: DecklyStorage): DecklyDatabase =
    openDecklyDatabase(ApplicationProvider.getApplicationContext<Context>(), storage)

/**
 * Файлы штрихов, у которых запись нового файла падает, как при переполненном диске. Чтение и
 * удаление настоящие: так видно, не стёрли ли что-то раньше времени.
 */
class FailingWriteStrokeFiles(strokesDir: File) : StrokeFiles(strokesDir) {
    override fun writeCardFile(cardId: Long, front: CardSide, back: CardSide): String =
        throw IOException("Диск полон")

    override fun writeDraftFile(front: CardSide, back: CardSide): String =
        throw IOException("Диск полон")
}

/** Пишет первые [allowedWrites] файлов карточек, на следующем падает: диск закончился посреди импорта. */
class FailingAfterStrokeFiles(strokesDir: File, private val allowedWrites: Int) : StrokeFiles(strokesDir) {
    private var writes = 0

    override fun writeCardFile(cardId: Long, front: CardSide, back: CardSide): String {
        if (writes >= allowedWrites) throw IOException("Диск полон")
        writes++
        return super.writeCardFile(cardId, front, back)
    }
}

/** База, файлы и все репозитории одного хранилища. [reopen] — как перезапуск приложения. */
class TestRepositories(val storage: DecklyStorage) {
    lateinit var database: DecklyDatabase
        private set
    lateinit var files: StrokeFiles
        private set
    lateinit var decks: RoomDeckRepository
        private set
    lateinit var contents: FileCardContentRepository
        private set
    lateinit var drafts: RoomDraftRepository
        private set
    lateinit var appState: RoomAppStateRepository
        private set
    lateinit var transfer: JsonDeckTransfer
        private set

    init {
        open()
    }

    private fun open() {
        database = openTestDatabase(storage)
        files = StrokeFiles(storage.strokesDir)
        decks = RoomDeckRepository(database, files)
        contents = FileCardContentRepository(database, files)
        drafts = RoomDraftRepository(database, files)
        appState = RoomAppStateRepository(database)
        transfer = JsonDeckTransfer(database, files, drafts)
    }

    fun reopen() {
        close()
        open()
    }

    fun close() {
        database.close()
    }
}

/** Имя файла штрихов, под которым [seedDeck] кладёт карточку. */
fun buildSeedContentFile(cardId: Long): String = "card-$cardId-seed.dks"

/** Пишет файл штрихов под заданным именем в обход `StrokeFiles`: так создаются «чужие» и «старые» файлы. */
fun writeSeedFile(storage: DecklyStorage, name: String, content: CardContent) {
    storage.ensureDirectories()
    FileOutputStream(File(storage.strokesDir, name)).use { writeStrokeFile(it, content.front, content.back) }
}

/**
 * Кладёт колоду, её карточки и файлы штрихов (`buildSeedContent`) прямо в базу и в папку с id из
 * [state]: другого способа получить карточки без `commitDraft` нет.
 */
suspend fun seedDeck(repos: TestRepositories, state: DeckState) {
    repos.database.deckDao().insertDeck(mapDeckEntity(state.deck))
    for (card in state.cards) {
        val file = buildSeedContentFile(card.id)
        writeSeedFile(repos.storage, file, buildSeedContent(card.id))
        repos.database.cardDao().insertCard(mapCardEntity(card, state.deck.id, file))
    }
    // В настоящей базе id выдаёт счётчик, поэтому он всегда выше id существующих карточек.
    val maxCardId = state.cards.maxOfOrNull { it.id } ?: return
    val nextCardId = repos.database.appStateDao().findAppState()!!.nextCardId
    if (nextCardId <= maxCardId) repos.database.appStateDao().updateNextCardId(maxCardId + 1)
}

/** Кладёт в базу черновик колоды [deckId] с файлом [contentFile] без штрихов. */
suspend fun seedDraft(repos: TestRepositories, deckId: Long, contentFile: String) {
    writeSeedFile(repos.storage, contentFile, CardContent(0, CardSide(), CardSide()))
    repos.database.draftDao().insertDraft(
        DraftEntity(
            deckId = deckId,
            cardId = null,
            visibleSide = "FRONT",
            returnSection = "NEW",
            contentFile = contentFile,
        ),
    )
}

/** Колода и штрихи её карточек без id: карточки перенумерованы с 0 по порядку id, `tail` пересчитан. */
data class NormalizedDeck(val state: DeckState, val contents: List<CardContent>)

/** Загружает колоду и штрихи карточек и убирает из них id: так сравнивают оригинал и импортированную копию. */
suspend fun loadNormalizedDeck(repos: TestRepositories, deckId: Long): NormalizedDeck {
    val state = repos.decks.loadDeckState(deckId)
    val cards = state.cards.sortedBy { it.id }
    val indexById = cards.withIndex().associate { (index, card) -> card.id to index.toLong() }
    val contents = cards.map { card ->
        val content = repos.contents.loadContent(card.id)
        CardContent(indexById.getValue(card.id), content.front, content.back)
    }
    val tail = state.deck.tail
    val renumberedTail = EpochValues(
        tail.epoch1.map { indexById.getValue(it) },
        tail.epoch2.map { indexById.getValue(it) },
        tail.epoch3.map { indexById.getValue(it) },
        tail.epoch4.map { indexById.getValue(it) },
    )
    val normalized = DeckState(
        state.deck.copy(id = 0, tail = renumberedTail),
        cards.map { it.copy(id = indexById.getValue(it.id)) },
    )
    return NormalizedDeck(normalized, contents)
}

fun hasStrokeFile(storage: DecklyStorage, name: String): Boolean = File(storage.strokesDir, name).isFile

fun listStrokeFileNames(storage: DecklyStorage): Set<String> = storage.strokesDir.list().orEmpty().toSet()
