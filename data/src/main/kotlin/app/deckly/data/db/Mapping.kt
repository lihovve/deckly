package app.deckly.data.db

import app.deckly.domain.model.Card
import app.deckly.domain.model.Deck
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side

fun mapDeckEntity(deck: Deck): DeckEntity = DeckEntity(
    id = deck.id,
    name = deck.name,
    activeSection = deck.activeSection.name,
    currentEpoch = deck.currentEpoch,
    passStarted = deck.passStarted,
    passCount1 = deck.passCount.epoch1,
    passCount2 = deck.passCount.epoch2,
    passCount3 = deck.passCount.epoch3,
    passCount4 = deck.passCount.epoch4,
    passCursor = deck.passCursor,
    browseCursor = deck.browseCursor,
    visibleSide = deck.visibleSide.name,
    lastPassDay1 = deck.lastPassDay.epoch1,
    lastPassDay2 = deck.lastPassDay.epoch2,
    lastPassDay3 = deck.lastPassDay.epoch3,
    lastPassDay4 = deck.lastPassDay.epoch4,
    waitingEpoch = deck.waitingEpoch,
    pausedEpoch = deck.pausedEpoch,
    pausedCursor = deck.pausedCursor,
    portionRatedCount = deck.portionRatedCount,
    tail1 = joinIds(deck.tail.epoch1),
    tail2 = joinIds(deck.tail.epoch2),
    tail3 = joinIds(deck.tail.epoch3),
    tail4 = joinIds(deck.tail.epoch4),
)

fun mapDeck(entity: DeckEntity): Deck = Deck(
    id = entity.id,
    name = entity.name,
    activeSection = Section.valueOf(entity.activeSection),
    currentEpoch = entity.currentEpoch,
    passStarted = entity.passStarted,
    passCount = EpochValues(entity.passCount1, entity.passCount2, entity.passCount3, entity.passCount4),
    passCursor = entity.passCursor,
    browseCursor = entity.browseCursor,
    visibleSide = Side.valueOf(entity.visibleSide),
    lastPassDay = EpochValues(
        entity.lastPassDay1,
        entity.lastPassDay2,
        entity.lastPassDay3,
        entity.lastPassDay4,
    ),
    waitingEpoch = entity.waitingEpoch,
    pausedEpoch = entity.pausedEpoch,
    pausedCursor = entity.pausedCursor,
    portionRatedCount = entity.portionRatedCount,
    tail = EpochValues(
        splitIds(entity.tail1),
        splitIds(entity.tail2),
        splitIds(entity.tail3),
        splitIds(entity.tail4),
    ),
)

fun mapCardEntity(card: Card, deckId: Long, contentFile: String): CardEntity = CardEntity(
    id = card.id,
    deckId = deckId,
    section = card.section.name,
    position = card.position,
    successes = card.successes,
    sectionDay = card.sectionDay,
    lastSuccessDay = card.lastSuccessDay,
    createdAt = card.createdAt,
    postponed = card.postponed,
    answerRevealed = card.answerRevealed,
    failed = card.failed,
    checking = card.checking,
    excludedFromPass = card.excludedFromPass,
    contentFile = contentFile,
)

fun mapCard(entity: CardEntity): Card = Card(
    id = entity.id,
    section = Section.valueOf(entity.section),
    position = entity.position,
    successes = entity.successes,
    sectionDay = entity.sectionDay,
    lastSuccessDay = entity.lastSuccessDay,
    createdAt = entity.createdAt,
    postponed = entity.postponed,
    answerRevealed = entity.answerRevealed,
    failed = entity.failed,
    checking = entity.checking,
    excludedFromPass = entity.excludedFromPass,
)

fun mapCardState(card: Card): CardStateUpdate = CardStateUpdate(
    id = card.id,
    section = card.section.name,
    position = card.position,
    successes = card.successes,
    sectionDay = card.sectionDay,
    lastSuccessDay = card.lastSuccessDay,
    createdAt = card.createdAt,
    postponed = card.postponed,
    answerRevealed = card.answerRevealed,
    failed = card.failed,
    checking = card.checking,
    excludedFromPass = card.excludedFromPass,
)

private fun joinIds(ids: List<Long>): String = ids.joinToString(",")

private fun splitIds(text: String): List<Long> =
    if (text.isEmpty()) emptyList() else text.split(",").map { it.toLong() }
