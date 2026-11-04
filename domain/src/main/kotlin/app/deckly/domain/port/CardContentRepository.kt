package app.deckly.domain.port

import app.deckly.domain.model.CardContent

/** Штрихи карточки: хранятся отдельно от состояния алгоритма. */
interface CardContentRepository {
    suspend fun loadContent(cardId: Long): CardContent
}
