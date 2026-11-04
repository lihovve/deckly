package app.deckly.domain

import app.deckly.domain.model.Card
import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section

fun buildCard(
    id: Long,
    section: Section = Section.NEW,
    position: Int = 0,
    successes: Int = 0,
    sectionDay: Int = 0,
    lastSuccessDay: Int? = null,
    createdAt: Long = id,
    postponed: Boolean = false,
    answerRevealed: Boolean = false,
    failed: Boolean = false,
    checking: Boolean = false,
    excludedFromPass: Boolean = false,
): Card = Card(
    id = id,
    section = section,
    position = position,
    successes = successes,
    sectionDay = sectionDay,
    lastSuccessDay = lastSuccessDay,
    createdAt = createdAt,
    postponed = postponed,
    answerRevealed = answerRevealed,
    failed = failed,
    checking = checking,
    excludedFromPass = excludedFromPass,
)

fun buildDeck(
    id: Long = 1,
    name: String = "Колода",
    activeSection: Section = Section.NEW,
    currentEpoch: Int = 1,
    passStarted: Boolean = false,
    passCount: EpochValues<Int> = EpochValues(0, 0, 0, 0),
    passCursor: Int = 0,
    browseCursor: Int = 0,
    lastPassDay: EpochValues<Int?> = EpochValues(null, null, null, null),
    waitingEpoch: Int = 0,
    pausedEpoch: Int = 0,
    pausedCursor: Int = 0,
    tail: EpochValues<List<Long>> = EpochValues(emptyList(), emptyList(), emptyList(), emptyList()),
): Deck = Deck(
    id = id,
    name = name,
    activeSection = activeSection,
    currentEpoch = currentEpoch,
    passStarted = passStarted,
    passCount = passCount,
    passCursor = passCursor,
    browseCursor = browseCursor,
    lastPassDay = lastPassDay,
    waitingEpoch = waitingEpoch,
    pausedEpoch = pausedEpoch,
    pausedCursor = pausedCursor,
    tail = tail,
)

fun buildDeckState(deck: Deck, vararg cards: Card): DeckState = DeckState(deck, cards.toList())
