package app.deckly.data.repository

import androidx.room3.withWriteTransaction
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.DraftEntity
import app.deckly.data.strokes.StrokeFiles
import app.deckly.domain.editor.Draft
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.DeckChanges
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.port.DraftRepository
import app.deckly.domain.port.StoredDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Черновик редактора и запись содержимого карточки. Порядок записи один везде: новый файл под новым
 * именем, затем транзакция базы со ссылкой на него, затем удаление старого файла. Убийство между
 * шагами оставляет лишний файл, но не теряет ни одной карточки: лишнее убирает
 * [removeUnreferencedStrokeFiles] при следующем открытии.
 */
class RoomDraftRepository(
    private val database: DecklyDatabase,
    private val files: StrokeFiles,
) : DraftRepository {

    /**
     * Черновик правки карточки, которой уже нет (убили между удалением карточки и удалением
     * черновика), отбрасывается: возвращается `null`, строка и файл удалены. Файл штрихов пропал
     * или повреждён — `StrokeFileException`.
     */
    override suspend fun loadDraft(): StoredDraft? {
        val row = database.draftDao().findDraft() ?: return null
        val editedCardId = row.cardId
        if (editedCardId != null && database.cardDao().findCard(editedCardId) == null) {
            discardDraft()
            return null
        }
        val (front, back) = withContext(Dispatchers.IO) { files.readFile(row.contentFile) }
        val draft = Draft(editedCardId, front, back, Side.valueOf(row.visibleSide), Section.valueOf(row.returnSection))
        return StoredDraft(row.deckId, draft)
    }

    override suspend fun storeDraft(stored: StoredDraft) {
        val draft = stored.draft
        val newFile = withContext(Dispatchers.IO) { files.writeDraftFile(draft.front, draft.back) }
        val previousFile = try {
            database.withWriteTransaction<String?> {
                val previous = database.draftDao().findDraft()?.contentFile
                database.draftDao().insertDraft(
                    DraftEntity(
                        deckId = stored.deckId,
                        cardId = draft.cardId,
                        visibleSide = draft.visibleSide.name,
                        returnSection = draft.returnSection.name,
                        contentFile = newFile,
                    ),
                )
                previous
            }
        } catch (failure: Throwable) {
            deleteFiles(listOf(newFile))
            throw failure
        }
        deleteFiles(listOfNotNull(previousFile))
    }

    override suspend fun discardDraft() {
        val draftFile = database.withWriteTransaction {
            val file = database.draftDao().findDraft()?.contentFile
            database.draftDao().deleteDraft()
            file
        }
        deleteFiles(listOfNotNull(draftFile))
    }

    override suspend fun allocateCardId(): Long =
        database.withWriteTransaction {
            val appState = checkNotNull(database.appStateDao().findAppState()) { "В базе нет строки app_state" }
            database.appStateDao().updateNextCardId(appState.nextCardId + 1)
            appState.nextCardId
        }

    /**
     * Карточка — та, что есть в `changes.upsertedCards` и нет в базе (вставляется), либо правленая
     * существующая (меняется только ссылка на файл). Любая ошибка в транзакции откатывает её, а
     * новый файл удаляется: старый файл и черновик остаются как были.
     */
    override suspend fun commitDraft(changes: DeckChanges, content: CardContent) {
        val cardId = content.cardId
        val newFile = withContext(Dispatchers.IO) { files.writeCardFile(cardId, content.front, content.back) }
        val obsoleteFiles = try {
            database.withWriteTransaction<List<String>> {
                val previousFile = database.cardDao().findCard(cardId)?.contentFile
                val deletedCardFiles = database.writeDeckChanges(changes, NewCardFile(cardId, newFile))
                if (previousFile != null) {
                    database.cardDao().updateContentFile(cardId, newFile)
                }
                checkNotNull(database.cardDao().findCard(cardId)) { "Карточки $cardId нет ни в базе, ни в изменениях" }
                val draftFile = database.draftDao().findDraft()?.contentFile
                database.draftDao().deleteDraft()
                listOfNotNull(previousFile, draftFile) + deletedCardFiles
            }
        } catch (failure: Throwable) {
            deleteFiles(listOf(newFile))
            throw failure
        }
        deleteFiles(obsoleteFiles)
    }

    private suspend fun deleteFiles(names: List<String>) {
        withContext(Dispatchers.IO) { names.forEach(files::deleteContentFile) }
    }
}
