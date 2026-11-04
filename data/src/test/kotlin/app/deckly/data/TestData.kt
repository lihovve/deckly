package app.deckly.data

import app.deckly.domain.model.Card
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import app.deckly.domain.model.createDeck
import app.deckly.domain.review.ReviewAction
import app.deckly.domain.review.ReviewEngine
import kotlin.random.Random

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

/** Карточка эпохи [epoch] (1–4) с позицией [position]. */
fun buildEpochCard(id: Long, section: Section, position: Int, successes: Int = 0): Card =
    buildPlainCard(id, position).copy(section = section, successes = successes, sectionDay = 150)

/** Карточки в порядке id: так их отдаёт `loadDeckState`. */
fun DeckState.sortCardsById(): DeckState = copy(cards = cards.sortedBy { it.id })

/** Штрих, который зависит от [seed]: разные штрихи не равны, нажим не одинаков по точкам. */
fun buildTestStroke(seed: Int): Stroke = Stroke(
    width = 2f + seed,
    points = listOf(
        StrokePoint(seed.toFloat(), -seed.toFloat(), 0.5f),
        StrokePoint(seed + 1.25f, seed + 2.5f, 1f),
    ),
)

/** Содержимое, которое [seedDeck] кладёт в файл карточки: один штрих на лице, пустой оборот. */
fun buildSeedContent(cardId: Long): CardContent =
    CardContent(cardId, listOf(buildTestStroke(cardId.toInt())), emptyList())

/** День, на который рассчитаны сценарии ниже. */
const val SCENARIO_DAY = 200

/** Состояние колоды до шага движка и после него: из них строится `collectDeckChanges`. */
data class Scenario(val before: DeckState, val after: DeckState)

private fun flipAndRateKnew(state: DeckState, random: Random): DeckState {
    val flipped = ReviewEngine.applyAction(state, ReviewAction.Flip, SCENARIO_DAY, random).state
    return ReviewEngine.applyAction(flipped, ReviewAction.Knew, SCENARIO_DAY, random).state
}

private fun applyEngineAction(state: DeckState, action: ReviewAction, random: Random): DeckState =
    ReviewEngine.applyAction(state, action, SCENARIO_DAY, random).state

/**
 * Прерванный проход: в эпохе 4 оценены 40 карточек из 41, порция полна, начался вставной проход
 * эпохи 1 (3 карточки). В нём одна оценена, следующая отложена, текущая перевёрнута: показан оборот.
 * Карточки `before` и `after` отсортированы по id.
 */
fun buildInterruptedPassScenario(deckId: Long = 1): Scenario {
    val random = Random(1)
    val deck = createDeck(deckId, "Прерванный проход").copy(
        activeSection = Section.EPOCH_4,
        currentEpoch = 4,
        passStarted = true,
        passCount = EpochValues(7, 2, 1, 2),
        lastPassDay = EpochValues(null, 190, 185, 160),
        tail = EpochValues(emptyList(), emptyList(), emptyList(), listOf(37L, 38L, 39L, 40L, 41L)),
    )
    val epochFour = (1L..41L).map { buildEpochCard(it, Section.EPOCH_4, position = it.toInt() - 1) }
    val epochOne = (101L..103L).map { buildEpochCard(it, Section.EPOCH_1, position = (it - 101).toInt()) }
    val learned = buildEpochCard(201, Section.LEARNED, position = 0, successes = 4).copy(sectionDay = 120)
    val before = DeckState(deck, epochFour + epochOne + learned).sortCardsById()

    var state = before
    repeat(40) { state = flipAndRateKnew(state, random) }
    state = flipAndRateKnew(state, random)
    state = applyEngineAction(state, ReviewAction.Postpone, random)
    state = applyEngineAction(state, ReviewAction.Flip, random)
    return Scenario(before, state.sortCardsById())
}

/**
 * Ожидающая эпоха: эпоха 2 не созрела, ход у эпохи 1. Проход эпохи 1 из трёх карточек пройден до
 * конца, `tail` записан, начался следующий проход эпохи 1. Карточки отсортированы по id.
 */
fun buildWaitingEpochScenario(deckId: Long = 1): Scenario {
    val random = Random(2)
    val deck = createDeck(deckId, "Ожидающая эпоха").copy(
        currentEpoch = 2,
        passCount = EpochValues(2, 1, 0, 0),
        lastPassDay = EpochValues(null, SCENARIO_DAY, 195, null),
    )
    val epochTwo = (1L..3L).map { buildEpochCard(it, Section.EPOCH_2, position = (it - 1).toInt(), successes = 1) }
    val epochOne = (101L..103L).map { buildEpochCard(it, Section.EPOCH_1, position = (it - 101).toInt()) }
    val before = DeckState(deck, epochTwo + epochOne).sortCardsById()

    var state = applyEngineAction(before, ReviewAction.OpenSection(Section.EPOCH_2), random)
    repeat(3) { state = flipAndRateKnew(state, random) }
    return Scenario(before, state.sortCardsById())
}
