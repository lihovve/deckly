package app.deckly.data.repository

import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.mapCardEntity
import app.deckly.data.db.mapCardState
import app.deckly.data.db.mapDeckEntity
import app.deckly.domain.model.DeckChanges

/** Карточка, которой ещё нет в базе: её вставляют с этим файлом штрихов, а не обновляют. */
internal class NewCardFile(val cardId: Long, val contentFile: String)

/**
 * Записывает результат шага движка: строку колоды, состояния карточек, удаление карточек. Вызывать
 * только внутри транзакции записи: любая ошибка должна откатить всё. Нет колоды или карточки из
 * `upsertedCards` (кроме [newCard]) — `IllegalStateException`. Возвращает файлы удалённых карточек:
 * удалять их можно только после коммита.
 */
internal suspend fun DecklyDatabase.writeDeckChanges(changes: DeckChanges, newCard: NewCardFile? = null): List<String> {
    val deckId = changes.deck.id
    check(deckDao().updateDeck(mapDeckEntity(changes.deck)) == 1) { "Колоды $deckId нет в базе" }
    for (card in changes.upsertedCards) {
        if (newCard != null && card.id == newCard.cardId && cardDao().findCard(card.id) == null) {
            cardDao().insertCard(mapCardEntity(card, deckId, newCard.contentFile))
        } else {
            // Новые карточки создаёт только commitDraft или импорт.
            check(cardDao().updateCardState(mapCardState(card)) == 1) { "Карточки ${card.id} нет в базе" }
        }
    }
    val files = cardDao().listContentFiles(changes.deletedCardIds)
    cardDao().deleteCards(changes.deletedCardIds)
    return files
}
