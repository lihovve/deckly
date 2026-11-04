package app.deckly.data.repository

import androidx.room3.withReadTransaction
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.strokes.StrokeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Уборка при открытии хранилища: удаляет файлы штрихов, на которые не ссылается ни карточка, ни
 * черновик, и недописанные `*.tmp`. Это то, что остаётся, если приложение убили между записью файла
 * и транзакцией или между транзакцией и удалением старого файла. Звать только пока никто не пишет.
 */
suspend fun removeUnreferencedStrokeFiles(database: DecklyDatabase, files: StrokeFiles) {
    val referenced = database.withReadTransaction {
        database.cardDao().listAllContentFiles().toSet() + listOfNotNull(database.draftDao().findDraft()?.contentFile)
    }
    withContext(Dispatchers.IO) { files.cleanUpStrokeFiles(referenced) }
}
