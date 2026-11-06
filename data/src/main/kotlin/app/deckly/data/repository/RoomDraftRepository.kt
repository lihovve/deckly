package app.deckly.data.repository

import androidx.room3.withWriteTransaction
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.DraftEntity
import app.deckly.data.strokes.StrokeFileException
import app.deckly.data.strokes.StrokeFiles
import app.deckly.domain.editor.Draft
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.port.DraftRepository
import app.deckly.domain.port.StoredDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Черновик редактора и запись содержимого карточки. Порядок записи один везде: новый файл под новым
 * именем, затем транзакция базы со ссылкой на него, затем удаление старого файла. Убийство между
 * шагами оставляет лишний файл, но не теряет ни одной карточки: лишнее убирает
 * [removeUnreferencedStrokeFiles] при следующем открытии.
 *
 * Все методы идут по одной честной очереди (FIFO): вызов начинается, только когда закончился
 * предыдущий. Так сохранение, которое запоздало, не перезапишет более новое и не воскресит черновик
 * после сохранения карточки. Если запись прервана (ошибка или отмена), новый файл удаляется, только
 * когда подтверждено, что транзакция не прошла: прошедшую транзакцию файл должен пережить.
 */
class RoomDraftRepository(
    private val database: DecklyDatabase,
    private val files: StrokeFiles,
) : DraftRepository {

    /** `Mutex` в kotlinx.coroutines честный: ожидающие получают его в порядке запросов. */
    private val queue = Mutex()

    /**
     * Черновик правки карточки, которой уже нет (убили между удалением карточки и удалением
     * черновика), отбрасывается: возвращается `null`, строка и файл удалены. Файл по ссылке пропал
     * (копия снята посреди сохранения): читается самый новый из оставшихся файлов черновика. Если
     * читать нечего или файл повреждён, строка черновика удаляется и возвращается `null`: иначе
     * приложение падало бы при каждом запуске.
     */
    override suspend fun loadDraft(): StoredDraft? = queue.withLock { loadDraftInQueue() }

    private suspend fun loadDraftInQueue(): StoredDraft? {
        val row = database.draftDao().findDraft() ?: return null
        val editedCardId = row.cardId
        if (editedCardId != null && database.cardDao().findCard(editedCardId) == null) {
            discardDraftInQueue()
            return null
        }
        val sides = withContext(Dispatchers.IO) { readDraftSidesOrNull(row.contentFile) }
        if (sides == null) {
            discardDraftInQueue()
            return null
        }
        val (front, back) = sides
        val draft = Draft(editedCardId, front, back, Side.valueOf(row.visibleSide), Section.valueOf(row.returnSection))
        return StoredDraft(row.deckId, draft)
    }

    private fun readDraftSidesOrNull(referencedFile: String): Pair<CardSide, CardSide>? {
        val name = if (files.hasFile(referencedFile)) referencedFile else files.findNewestDraftFile()
        if (name == null) return null
        return try {
            files.readFile(name)
        } catch (damaged: StrokeFileException) {
            null
        }
    }

    override suspend fun storeDraft(stored: StoredDraft): Unit = queue.withLock {
        val draft = stored.draft
        var newFile: String? = null
        val previousFile = try {
            // Имя запоминается внутри блока: отмена может прийти, когда файл уже записан, а блок ещё не вернулся.
            withContext(Dispatchers.IO) { newFile = files.writeDraftFile(draft.front, draft.back) }
            val file = checkNotNull(newFile)
            database.withWriteTransaction<String?> {
                val previous = database.draftDao().findDraft()?.contentFile
                database.draftDao().insertDraft(
                    DraftEntity(
                        deckId = stored.deckId,
                        cardId = draft.cardId,
                        visibleSide = draft.visibleSide.name,
                        returnSection = draft.returnSection.name,
                        contentFile = file,
                    ),
                )
                previous
            }
        } catch (failure: Throwable) {
            newFile?.let { file ->
                deleteFilesUnlessCommitted(files, listOf(file)) { database.draftDao().findDraft()?.contentFile == file }
            }
            throw failure
        }
        deleteFiles(listOfNotNull(previousFile))
    }

    override suspend fun discardDraft(): Unit = queue.withLock { discardDraftInQueue() }

    private suspend fun discardDraftInQueue() {
        val draftFile = database.withWriteTransaction {
            val file = database.draftDao().findDraft()?.contentFile
            database.draftDao().deleteDraft()
            file
        }
        deleteFiles(listOfNotNull(draftFile))
    }

    override suspend fun allocateCardId(): Long = allocateCardIds(1).single()

    /** Выдаёт [count] подряд идущих id одной транзакцией: импорту колоды нужны сразу все. */
    suspend fun allocateCardIds(count: Int): List<Long> = queue.withLock {
        require(count >= 0) { "Число id не может быть отрицательным: $count" }
        database.withWriteTransaction {
            val appState = checkNotNull(database.appStateDao().findAppState()) { "В базе нет строки app_state" }
            if (count > 0) database.appStateDao().updateNextCardId(appState.nextCardId + count)
            List(count) { appState.nextCardId + it }
        }
    }

    /**
     * Карточка — та, что есть в `changes.upsertedCards` и нет в базе (вставляется), либо правленая
     * существующая (меняется только ссылка на файл). Любая ошибка в транзакции откатывает её, а
     * новый файл удаляется: старый файл и черновик остаются как были.
     */
    override suspend fun commitDraft(changes: DeckChanges, content: CardContent): Unit = queue.withLock {
        val cardId = content.cardId
        var newFile: String? = null
        val obsoleteFiles = try {
            withContext(Dispatchers.IO) { newFile = files.writeCardFile(cardId, content.front, content.back) }
            val file = checkNotNull(newFile)
            database.withWriteTransaction<List<String>> {
                val previousFile = database.cardDao().findCard(cardId)?.contentFile
                val deletedCardFiles = database.writeDeckChanges(changes, NewCardFile(cardId, file))
                if (previousFile != null) {
                    database.cardDao().updateContentFile(cardId, file)
                }
                checkNotNull(database.cardDao().findCard(cardId)) { "Карточки $cardId нет ни в базе, ни в изменениях" }
                val draftFile = database.draftDao().findDraft()?.contentFile
                database.draftDao().deleteDraft()
                listOfNotNull(previousFile, draftFile) + deletedCardFiles
            }
        } catch (failure: Throwable) {
            newFile?.let { file ->
                deleteFilesUnlessCommitted(files, listOf(file)) { database.cardDao().findCard(cardId)?.contentFile == file }
            }
            throw failure
        }
        deleteFiles(obsoleteFiles)
    }

    private suspend fun deleteFiles(names: List<String>) {
        withContext(Dispatchers.IO) { names.forEach(files::deleteContentFile) }
    }
}
