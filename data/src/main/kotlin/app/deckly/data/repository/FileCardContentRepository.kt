package app.deckly.data.repository

import app.deckly.data.db.DecklyDatabase
import app.deckly.data.strokes.StrokeFileException
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

    /**
     * Нет карточки — `IllegalStateException`. Файл по ссылке пропал (например, копия снята посреди правки):
     * читается самый новый из оставшихся файлов этой карточки, а если их нет — `StrokeFileException`.
     * Повреждённый файл по ссылке — `StrokeFileException` без запасного чтения.
     */
    override suspend fun loadContent(cardId: Long): CardContent {
        val card = checkNotNull(database.cardDao().findCard(cardId)) { "Карточки $cardId нет в базе" }
        val (front, back) = withContext(Dispatchers.IO) {
            val name = card.contentFile
            if (files.hasFile(name)) {
                files.readFile(name)
            } else {
                val newest = files.findNewestCardFile(cardId) ?: throw StrokeFileException("Файла штрихов $name нет")
                files.readFile(newest)
            }
        }
        return CardContent(cardId, front, back)
    }
}
