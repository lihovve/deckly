package app.deckly.domain.model

/** Что записать одним атомарным вызовом после шага алгоритма. */
data class DeckChanges(
    val deck: Deck,
    val upsertedCards: List<Card>,
    val deletedCardIds: Set<Long>,
)

/** Колода после шага, новые и изменённые карточки (в порядке `after`) и исчезнувшие id. */
fun collectDeckChanges(before: DeckState, after: DeckState): DeckChanges {
    val cardsBefore = before.cards.associateBy { it.id }
    val idsAfter = after.cards.map { it.id }.toSet()
    return DeckChanges(
        deck = after.deck,
        upsertedCards = after.cards.filter { cardsBefore[it.id] != it },
        deletedCardIds = cardsBefore.keys - idsAfter,
    )
}
