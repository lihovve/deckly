package app.deckly.data

import app.deckly.domain.model.Card
import app.deckly.domain.model.Deck
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side

/** Колода, у которой каждое поле не равно значению по умолчанию из `createDeck`. */
fun buildFullDeck(id: Long = 7): Deck = Deck(
    id = id,
    name = "Английский: глаголы",
    activeSection = Section.EPOCH_3,
    currentEpoch = 3,
    passStarted = true,
    passCount = EpochValues(5, 1, 0, 9),
    passCursor = 4,
    browseCursor = 2,
    visibleSide = Side.BACK,
    lastPassDay = EpochValues(null, 120, null, 133),
    waitingEpoch = 2,
    pausedEpoch = 4,
    pausedCursor = 6,
    portionRatedCount = 3,
    tail = EpochValues(listOf(11L, 12L, 13L), emptyList(), listOf(7L), emptyList()),
)

/** Карточка, у которой каждое поле не равно «чистой» новой карточке: все флаги подняты. */
fun buildFlaggedCard(id: Long = 42): Card = Card(
    id = id,
    section = Section.EPOCH_4,
    position = 3,
    successes = 2,
    sectionDay = 118,
    lastSuccessDay = null,
    createdAt = 1_793_800_000_000L,
    postponed = true,
    answerRevealed = true,
    failed = true,
    checking = true,
    excludedFromPass = true,
)

/** Новая карточка пула: нулевые счётчики, флаги сброшены. */
fun buildPlainCard(id: Long, position: Int = 0): Card = Card(
    id = id,
    section = Section.NEW,
    position = position,
    successes = 0,
    sectionDay = 100,
    lastSuccessDay = 99,
    createdAt = 1_793_700_000_000L,
    postponed = false,
    answerRevealed = false,
    failed = false,
    checking = false,
    excludedFromPass = false,
)
