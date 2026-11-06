package app.deckly.fakes

import app.deckly.data.strokes.StrokeFileException
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import app.deckly.domain.port.CardContentRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * Штрихи карточек в памяти. Карточка из [damagedCardIds] читается с ошибкой файла, как повреждённая или
 * пропавшая; [loadCounts] показывает, сколько раз читали каждую.
 */
class FakeCardContentRepository(
    private val contents: Map<Long, CardContent> = emptyMap(),
    val damagedCardIds: MutableSet<Long> = mutableSetOf(),
) : CardContentRepository {
    val loadCounts = mutableMapOf<Long, Int>()

    /** Пока задан, чтение стоит и ждёт завершения. */
    var loadGate: CompletableDeferred<Unit>? = null

    override suspend fun loadContent(cardId: Long): CardContent {
        loadCounts[cardId] = (loadCounts[cardId] ?: 0) + 1
        loadGate?.await()
        if (cardId in damagedCardIds) throw StrokeFileException("Файл карточки $cardId повреждён")
        return contents[cardId] ?: buildContent(cardId)
    }

    companion object {
        /** Содержимое по умолчанию: на лице один штрих, на обороте два, толщина зависит от id карточки. */
        fun buildContent(cardId: Long): CardContent = CardContent(
            cardId,
            front = CardSide(listOf(Stroke(cardId.toFloat(), listOf(StrokePoint(1f, 1f, 1f))))),
            back = CardSide(
                listOf(
                    Stroke(cardId.toFloat(), listOf(StrokePoint(2f, 2f, 1f))),
                    Stroke(cardId.toFloat(), listOf(StrokePoint(3f, 3f, 1f))),
                ),
            ),
        )
    }
}
