package app.deckly.data.repository

import app.deckly.data.db.DecklyDatabase
import app.deckly.data.strokes.StrokeFiles
import app.deckly.domain.model.CardContent
import app.deckly.domain.port.CardContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Штрихи карточки: имя файла в базе, содержимое в [StrokeFiles]. */
class FileCardContentRepository(
    private val database: DecklyDatabase,
    private val files: StrokeFiles,
) : CardContentRepository {

    /** Нет карточки — `IllegalStateException`; файл пропал или повреждён — `StrokeFileException`. */
    override suspend fun loadContent(cardId: Long): CardContent {
        val card = checkNotNull(database.cardDao().findCard(cardId)) { "Карточки $cardId нет в базе" }
        val (front, back) = withContext(Dispatchers.IO) { files.readFile(card.contentFile) }
        return CardContent(cardId, front, back)
    }
}
