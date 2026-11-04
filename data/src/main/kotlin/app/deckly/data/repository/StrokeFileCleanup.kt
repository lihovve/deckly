package app.deckly.data.repository

import androidx.room3.withReadTransaction
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.strokes.StrokeFiles
import app.deckly.data.strokes.StrokeReferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Уборка при открытии хранилища: читает из базы, на какие файлы штрихов она ссылается и каким будет
 * следующий id карточки, и просит [StrokeFiles.cleanUpStrokeFiles] удалить только доказуемо лишнее
 * (правила там). Это то, что остаётся, если приложение убили между записью файла и транзакцией или
 * между транзакцией и удалением старого файла. Звать только пока никто не пишет.
 */
suspend fun removeUnreferencedStrokeFiles(database: DecklyDatabase, files: StrokeFiles) {
    val references = database.withReadTransaction {
        StrokeReferences(
            cardFiles = database.cardDao().listCardFiles().associate { it.id to it.contentFile },
            draftFile = database.draftDao().findDraft()?.contentFile,
            nextCardId = checkNotNull(database.appStateDao().findAppState()) { "В базе нет строки app_state" }.nextCardId,
        )
    }
    withContext(Dispatchers.IO) { files.cleanUpStrokeFiles(references) }
}
