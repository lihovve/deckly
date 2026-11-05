package app.deckly.fakes

import android.content.Context
import app.deckly.data.DecklyData
import app.deckly.data.storage.DecklyStorage
import app.deckly.data.storage.MediaStoreNotifier
import app.deckly.domain.editor.Draft
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.CardContentRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckSummary
import app.deckly.domain.port.DeckTransfer
import app.deckly.domain.port.DraftRepository
import app.deckly.domain.port.StoredDraft
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Хранение для тестов приложения: порты в памяти, MediaStore настоящий (его вызовы видны в Robolectric). */
fun buildFakeData(
    context: Context,
    rootDir: File,
    appState: AppStateRepository = FakeAppStateRepository(),
    drafts: DraftRepository = FakeDraftRepository(),
    onClose: () -> Unit = {},
): DecklyData = DecklyData(
    decks = UnusedDeckRepository,
    contents = UnusedCardContentRepository,
    drafts = drafts,
    appState = appState,
    transfer = UnusedDeckTransfer,
    mediaStore = MediaStoreNotifier(context, DecklyStorage(rootDir)),
    onClose = onClose,
)

fun buildEmptyDraft(returnSection: Section = Section.NEW): Draft =
    Draft(cardId = null, front = emptyList(), back = emptyList(), visibleSide = Side.FRONT, returnSection = returnSection)

class FakeAppStateRepository(var currentDeckId: Long? = null) : AppStateRepository {
    override suspend fun loadCurrentDeckId(): Long? = currentDeckId

    override suspend fun saveCurrentDeckId(deckId: Long) {
        currentDeckId = deckId
    }
}

/**
 * Черновик в памяти. Вызовы записываются по порядку: [storedCalls] — все `storeDraft`, [commits] — все
 * `commitDraft`, [discardCount] и [allocatedIds] — сколько раз сбросили черновик и выдали id. Пока задан
 * [storeGate], `storeDraft` стоит и ждёт его завершения.
 */
class FakeDraftRepository(var stored: StoredDraft? = null, private var nextCardId: Long = 100) : DraftRepository {
    val storedCalls = mutableListOf<StoredDraft>()
    val commits = mutableListOf<Pair<DeckChanges, CardContent>>()
    val allocatedIds = mutableListOf<Long>()
    var discardCount = 0
        private set
    var storeGate: CompletableDeferred<Unit>? = null

    override suspend fun loadDraft(): StoredDraft? = stored

    override suspend fun storeDraft(stored: StoredDraft) {
        storeGate?.await()
        storedCalls += stored
        this.stored = stored
    }

    override suspend fun discardDraft() {
        discardCount++
        stored = null
    }

    override suspend fun allocateCardId(): Long = nextCardId++.also { allocatedIds += it }

    override suspend fun commitDraft(changes: DeckChanges, content: CardContent) {
        commits += changes to content
        stored = null
    }
}

// Порты, которые тест не должен трогать: обращение к ним сразу видно как падение.
private object UnusedDeckRepository : DeckRepository {
    override suspend fun listDecks(): List<DeckSummary> = error("Порт не нужен тесту")

    override suspend fun createDeck(name: String): Deck = error("Порт не нужен тесту")

    override suspend fun renameDeck(deckId: Long, name: String) = error("Порт не нужен тесту")

    override suspend fun deleteDeck(deckId: Long) = error("Порт не нужен тесту")

    override suspend fun loadDeckState(deckId: Long): DeckState = error("Порт не нужен тесту")

    override suspend fun saveDeckChanges(changes: DeckChanges) = error("Порт не нужен тесту")
}

private object UnusedCardContentRepository : CardContentRepository {
    override suspend fun loadContent(cardId: Long): CardContent = error("Порт не нужен тесту")
}

private object UnusedDeckTransfer : DeckTransfer {
    override suspend fun exportDeck(deckId: Long, output: OutputStream) = error("Порт не нужен тесту")

    override suspend fun importDeck(input: InputStream): Deck = error("Порт не нужен тесту")
}
