package app.deckly.data.repository

import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.mapCardEntity
import app.deckly.data.db.mapCardState
import app.deckly.data.db.mapDeckEntity
import app.deckly.data.strokes.StrokeFiles
import app.deckly.domain.model.DeckChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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

/**
 * Удаляет [names] после сорвавшейся записи, но только когда [wasCommitted] под `NonCancellable` подтвердил,
 * что транзакция не прошла: отмена может прийти уже после коммита, и тогда файлы нужны базе. Если
 * проверить исход не удалось, файлы остаются: лишний файл уберёт уборка при открытии, а нужный вернуть нельзя.
 */
internal suspend fun deleteFilesUnlessCommitted(
    files: StrokeFiles,
    names: List<String>,
    wasCommitted: suspend () -> Boolean,
) {
    if (names.isEmpty()) return
    withContext(NonCancellable + Dispatchers.IO) {
        val isCommitted = try {
            wasCommitted()
        } catch (unknown: Exception) {
            true
        }
        if (!isCommitted) names.forEach(files::deleteContentFile)
    }
}
