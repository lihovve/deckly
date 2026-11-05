// Копия строителей из domain/src/test (Fixtures.kt): тесты domain из app недоступны.
package app.deckly.fixtures

import app.deckly.domain.model.Card
import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import app.deckly.domain.model.findEpochSection

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
    visibleSide: Side = Side.FRONT,
    lastPassDay: EpochValues<Int?> = EpochValues(null, null, null, null),
    waitingEpoch: Int = 0,
    pausedEpoch: Int = 0,
    pausedCursor: Int = 0,
    portionRatedCount: Int = 0,
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
    visibleSide = visibleSide,
    lastPassDay = lastPassDay,
    waitingEpoch = waitingEpoch,
    pausedEpoch = pausedEpoch,
    pausedCursor = pausedCursor,
    portionRatedCount = portionRatedCount,
    tail = tail,
)

fun buildDeckState(deck: Deck, vararg cards: Card): DeckState = DeckState(deck, cards.toList())

fun buildDeckState(deck: Deck, cards: List<Card>): DeckState = DeckState(deck, cards)

fun DeckState.findCard(id: Long): Card = cards.single { it.id == id }

/** Колода в начатом проходе эпохи [epoch]: эта эпоха открыта и активна, курсор на [passCursor]. */
fun buildPassDeck(epoch: Int, passCursor: Int = 0, visibleSide: Side = Side.FRONT): Deck = buildDeck(
    activeSection = findEpochSection(epoch),
    currentEpoch = epoch,
    passStarted = true,
    passCursor = passCursor,
    visibleSide = visibleSide,
)

/** Карточки эпохи с позициями 0, 1, … в порядке [ids]. */
fun buildEpochCards(epoch: Int, vararg ids: Long): List<Card> =
    ids.mapIndexed { index, id -> buildCard(id, findEpochSection(epoch), position = index) }

/** [count] карточек эпохи с id от [firstId] и позициями 0, 1, … */
fun buildEpochRange(epoch: Int, firstId: Long, count: Int): List<Card> =
    (0 until count).map { buildCard(firstId + it, findEpochSection(epoch), position = it) }

/** Штрих по точкам `x to y`, толщина 2, нажим 0.5. */
fun buildStroke(vararg points: Pair<Float, Float>): Stroke =
    Stroke(2f, points.map { StrokePoint(it.first, it.second, 0.5f) })

/** Траектория ластика по точкам `x to y`. */
fun buildPath(vararg points: Pair<Float, Float>): List<StrokePoint> =
    points.map { StrokePoint(it.first, it.second, 0.5f) }
