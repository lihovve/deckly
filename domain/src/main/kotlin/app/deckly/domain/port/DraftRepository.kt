package app.deckly.domain.port

import app.deckly.domain.editor.Draft
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.DeckChanges

/** Черновик вместе с колодой, в которой открыт редактор. */
data class StoredDraft(val deckId: Long, val draft: Draft)

/**
 * Черновик редактора: в каждый момент один, переживает убийство приложения. Вызывать из одного потока в
 * порядке штрихов: реализация выполняет вызовы строго по очереди, и какой из них вызван позже, тот и
 * останется записанным.
 */
interface DraftRepository {
    suspend fun loadDraft(): StoredDraft?

    /** Записывает черновик. Не путать с `saveDraft` из domain: тот строит карточку, а этот пишет на диск. */
    suspend fun storeDraft(stored: StoredDraft)

    suspend fun discardDraft()

    /** Выдаёт id для новой карточки; id не переиспользуются. */
    suspend fun allocateCardId(): Long

    /** Записывает карточку, её содержимое и удаляет черновик. */
    suspend fun commitDraft(changes: DeckChanges, content: CardContent)
}
