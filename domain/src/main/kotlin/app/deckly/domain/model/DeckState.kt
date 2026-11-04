package app.deckly.domain.model

/** Колода со всеми карточками: то, что алгоритм получает на вход и возвращает. */
data class DeckState(val deck: Deck, val cards: List<Card>)

/** Карточки секции в порядке `position`. */
fun DeckState.listSectionCards(section: Section): List<Card> =
    cards.filter { it.section == section }.sortedBy { it.position }
