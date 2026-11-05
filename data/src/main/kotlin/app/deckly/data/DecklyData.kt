package app.deckly.data

import android.content.Context
import app.deckly.data.db.checkDatabaseIntegrity
import app.deckly.data.db.checkDatabaseIsNotMissing
import app.deckly.data.db.openDecklyDatabase
import app.deckly.data.repository.FileCardContentRepository
import app.deckly.data.repository.RoomAppStateRepository
import app.deckly.data.repository.RoomDeckRepository
import app.deckly.data.repository.RoomDraftRepository
import app.deckly.data.repository.removeUnreferencedStrokeFiles
import app.deckly.data.storage.DecklyStorage
import app.deckly.data.storage.MediaStoreNotifier
import app.deckly.data.strokes.StrokeFiles
import app.deckly.data.timing.DecklyTiming
import app.deckly.data.transfer.JsonDeckTransfer
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.CardContentRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckTransfer
import app.deckly.domain.port.DraftRepository
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Всё хранение Deckly: порты `domain` и уведомление MediaStore. Приложение получает это одной
 * функцией [openDecklyData] и закрывает, когда хранение больше не нужно. [onClose] — что закрыть
 * (у настоящего хранения это база); для заглушек в тестах приложения его можно не задавать.
 */
class DecklyData(
    val decks: DeckRepository,
    val contents: CardContentRepository,
    val drafts: DraftRepository,
    val appState: AppStateRepository,
    val transfer: DeckTransfer,
    val mediaStore: MediaStoreNotifier,
    private val onClose: () -> Unit = {},
) : Closeable {
    override fun close() {
        onClose()
    }
}

/**
 * Открывает хранение в папке [rootDir] (в приложении `Environment.getExternalStorageDirectory()/Deckly`):
 * проверяет целостность базы и то, что у файлов карточек есть база ([app.deckly.data.db.DatabaseMissingException]),
 * создаёт папки, открывает базу, убирает файлы штрихов, на которые никто
 * не ссылается, и недописанные `*.tmp`, собирает репозитории. Звать один раз при старте, пока никто
 * ничего не пишет. Повреждённая база — [app.deckly.data.db.DatabaseDamagedException], файл не тронут.
 */
suspend fun openDecklyData(context: Context, rootDir: File): DecklyData {
    DecklyTiming.enableIfDebuggable(context)
    val openStartNanos = System.nanoTime()
    val storage = DecklyStorage(rootDir)
    var quickCheckStartNanos = 0L
    var quickCheckEndNanos = 0L
    // До Room и до любых папок: повреждённая база не открывается и ничего вокруг себя не создаёт.
    withContext(Dispatchers.IO) {
        quickCheckStartNanos = System.nanoTime()
        checkDatabaseIntegrity(storage.databaseFile)
        quickCheckEndNanos = System.nanoTime()
        checkDatabaseIsNotMissing(storage)
    }
    storage.ensureDirectories()
    val mediaStore = MediaStoreNotifier(context, storage)
    val database = openDecklyDatabase(context, storage)
    val files = StrokeFiles(storage.strokesDir, mediaStore::recordChanged)
    removeUnreferencedStrokeFiles(database, files)
    val drafts = RoomDraftRepository(database, files)
    val data = DecklyData(
        decks = RoomDeckRepository(database, files),
        contents = FileCardContentRepository(database, files),
        drafts = drafts,
        appState = RoomAppStateRepository(database),
        transfer = JsonDeckTransfer(database, files, drafts),
        mediaStore = mediaStore,
        onClose = database::close,
    )
    DecklyTiming.logInfo {
        "open total=${DecklyTiming.describeElapsed(openStartNanos)}ms " +
            "quickCheck=${DecklyTiming.describeElapsed(quickCheckStartNanos, quickCheckEndNanos)}ms"
    }
    return data
}
