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
import app.deckly.data.strokes.writeStrokeFile
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Stroke
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
    override fun writeCardFile(cardId: Long, front: List<Stroke>, back: List<Stroke>): String =
        throw IOException("Диск полон")

    override fun writeDraftFile(front: List<Stroke>, back: List<Stroke>): String =
        throw IOException("Диск полон")
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
}

/** Кладёт в базу черновик колоды [deckId] с файлом [contentFile] без штрихов. */
suspend fun seedDraft(repos: TestRepositories, deckId: Long, contentFile: String) {
    writeSeedFile(repos.storage, contentFile, CardContent(0, emptyList(), emptyList()))
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

fun hasStrokeFile(storage: DecklyStorage, name: String): Boolean = File(storage.strokesDir, name).isFile

fun listStrokeFileNames(storage: DecklyStorage): Set<String> = storage.strokesDir.list().orEmpty().toSet()
