package app.deckly.domain.review

import app.deckly.domain.model.Deck
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.findEpochSection
import app.deckly.domain.model.listSectionCards
import kotlin.random.Random

private const val MAX_CHAIN_ITERATIONS = 100
private const val PORTION_SIZE = 40

sealed interface ReviewAction {
    /**
     * [focusCardId] — карточка, с которой начать просмотр секции (после правки карточки в редакторе). Нет такой в
     * секции — первая. Для активной эпохи игнорируется: там позицию ведёт курсор прохода.
     */
    data class OpenSection(val section: Section, val focusCardId: Long? = null) : ReviewAction
    data class Browse(val step: Int) : ReviewAction
    data object Flip : ReviewAction
    data object Knew : ReviewAction
    data object DidNotKnow : ReviewAction
    data object Postpone : ReviewAction
    data class DeleteCard(val cardId: Long) : ReviewAction
    data class ReturnToNew(val cardId: Long) : ReviewAction
}

enum class TransitionKind { REGULAR, INSERTED, RESUMED }

sealed interface ReviewOutcome {
    data class ShowCard(val cardId: Long) : ReviewOutcome
    data object EmptySection : ReviewOutcome
    data class Transition(val epoch: Int, val remaining: Int, val kind: TransitionKind) : ReviewOutcome
    data object NeedNewCard : ReviewOutcome
    data object DoneForToday : ReviewOutcome
}

data class ReviewResult(val state: DeckState, val outcome: ReviewOutcome)

/**
 * Весь алгоритм обучения: старт и конец проходов, расписание, ожидающая эпоха, порции и вставные
 * проходы. Чистая функция состояния: ничего не читает и не пишет, номер дня и случайность приходят
 * параметрами. Порядок решений зафиксирован в плане 2, задача 6.
 */
object ReviewEngine {

    /** Любое действие, кроме переворота, оставляет карточку лицом: после него показывается лицевая сторона. */
    fun applyAction(state: DeckState, action: ReviewAction, today: Int, random: Random): ReviewResult {
        val result = applyActionOnCurrentSide(state, action, today, random)
        if (action == ReviewAction.Flip) return result
        return result.copy(state = result.state.copy(deck = result.state.deck.copy(visibleSide = Side.FRONT)))
    }

    private fun applyActionOnCurrentSide(
        state: DeckState,
        action: ReviewAction,
        today: Int,
        random: Random,
    ): ReviewResult =
        when (action) {
            is ReviewAction.OpenSection -> openSection(state, action.section, action.focusCardId, today, random)
            is ReviewAction.Browse -> browse(state, action.step)
            ReviewAction.Flip -> showCurrent(flipCard(state))
            ReviewAction.Knew -> continueAfterRating(rateKnew(state, today), today, random)
            ReviewAction.DidNotKnow -> continueAfterRating(rateDidNotKnow(state), today, random)
            ReviewAction.Postpone -> showCurrent(postponeCard(state))
            is ReviewAction.DeleteCard -> continueAfterRemoval(deleteCard(state, action.cardId), today, random)
            is ReviewAction.ReturnToNew -> returnCardToPool(state, action.cardId, today)
        }

    /** Что показать после перезапуска приложения: текущую карточку или пустую секцию. */
    fun computeCurrentOutcome(state: DeckState): ReviewOutcome =
        findCurrentCard(state)?.let { ReviewOutcome.ShowCard(it.id) } ?: ReviewOutcome.EmptySection

    private fun showCurrent(state: DeckState): ReviewResult = ReviewResult(state, computeCurrentOutcome(state))

    /**
     * Открытие активной эпохи при неначатом проходе запускает цепочку старта; при начатом — возвращает
     * к текущей карточке. Любая другая секция открывается для просмотра и проход не двигает.
     */
    private fun openSection(
        state: DeckState,
        section: Section,
        focusCardId: Long?,
        today: Int,
        random: Random,
    ): ReviewResult {
        val deck = state.deck
        val isActiveEpoch = section == findEpochSection(deck.currentEpoch)
        val browseCursor = if (isActiveEpoch) 0 else findFocusCursor(state, section, focusCardId)
        val opened = state.copy(deck = deck.copy(activeSection = section, browseCursor = browseCursor))
        if (!isActiveEpoch) return showCurrent(opened)
        return if (deck.passStarted) showCurrent(opened) else runStartChain(opened, today, random)
    }

    /** Индекс карточки [focusCardId] в порядке секции; без фокуса или без такой карточки в секции — начало. */
    private fun findFocusCursor(state: DeckState, section: Section, focusCardId: Long?): Int =
        state.listSectionCards(section).indexOfFirst { it.id == focusCardId }.coerceAtLeast(0)

    private fun browse(state: DeckState, step: Int): ReviewResult {
        require(!isActivePassOpen(state.deck)) { "В активном проходе листать нельзя" }
        return showCurrent(browseSection(state, step))
    }

    /**
     * После оценки: оценка идёт в счётчик порции; проход исчерпан — его конец; порция полна — вставной
     * проход, а если вставлять нечего, следующая порция начинается сразу; иначе следующая карточка.
     */
    private fun continueAfterRating(rated: DeckState, today: Int, random: Random): ReviewResult {
        val state = countRatingInPortion(rated)
        if (isPassExhausted(state)) return endPass(state, today, random)
        if (!isPortionFull(state.deck)) return showCurrent(state)
        if (hasFirstEpochWork(state, today)) return startInsertedPass(state, today, random)
        return showCurrent(state.copy(deck = state.deck.copy(portionRatedCount = 0)))
    }

    /** После удаления карточки: оно ничего не оценивает, поэтому границу порции не проверяем. */
    private fun continueAfterRemoval(state: DeckState, today: Int, random: Random): ReviewResult {
        if (isActivePassOpen(state.deck) && isPassExhausted(state)) return endPass(state, today, random)
        return showCurrent(state)
    }

    private fun isPassExhausted(state: DeckState): Boolean =
        state.deck.passCursor >= listPassCards(state, state.deck.currentEpoch).size

    /** Оценки считаются в порцию только в проходе эпохи 2–4 без прерывания. */
    private fun isPortionCounting(deck: Deck): Boolean = deck.pausedEpoch == 0 && deck.currentEpoch in 2..4

    private fun countRatingInPortion(state: DeckState): DeckState {
        val deck = state.deck
        if (!isPortionCounting(deck)) return state
        return state.copy(deck = deck.copy(portionRatedCount = deck.portionRatedCount + 1))
    }

    /** Порция полна после каждых 40 оценённых карточек. Курсор прохода здесь ни при чём: удаления его сдвигают. */
    private fun isPortionFull(deck: Deck): Boolean = isPortionCounting(deck) && deck.portionRatedCount == PORTION_SIZE

    /** Прерывает проход эпохи 2–4 и стартует вставной проход эпохи 1; пустую вставку проскакивает молча. */
    private fun startInsertedPass(state: DeckState, today: Int, random: Random): ReviewResult {
        val deck = state.deck
        val paused = state.copy(
            deck = deck.copy(
                pausedEpoch = deck.currentEpoch,
                pausedCursor = deck.passCursor,
                currentEpoch = 1,
                passCursor = 0,
                browseCursor = 0,
                activeSection = Section.EPOCH_1,
            ),
        )
        val started = startPass(paused, 1, today, random)
        val size = listPassCards(started, 1).size
        if (size > 0) return ReviewResult(started, ReviewOutcome.Transition(1, size, TransitionKind.INSERTED))
        return returnToPausedPass(finishPass(started, 1, today), today, random, announce = false)
    }

    /**
     * Конец прохода, в котором исчерпаны карточки. Вставной проход завершается одним `tail` и
     * возвращает к прерванному; обычный проход считается, выбирает следующую эпоху и запускает цепочку.
     */
    private fun endPass(state: DeckState, today: Int, random: Random): ReviewResult {
        if (isInsertedPass(state.deck)) {
            return returnToPausedPass(finishPass(state, 1, today), today, random, announce = true)
        }
        return runStartChain(completePass(state, state.deck.currentEpoch, today), today, random)
    }

    /** Идёт ли вставной проход эпохи 1: он прервал проход эпохи 2–4, и тот ждёт в `pausedEpoch`. */
    private fun isInsertedPass(deck: Deck): Boolean = deck.pausedEpoch != 0 && deck.currentEpoch == 1

    /**
     * Возврат карточки в пул (ТЗ, «Действия с карточкой»): открывается пул новых на возвращённой карточке. Если возврат
     * исчерпал открытый проход, проход завершается, но цепочка старта не идёт: добор эпохи 1 сразу забрал бы карточку
     * из пула обратно. Следующий проход стартует, когда пользователь сам откроет активную эпоху.
     */
    private fun returnCardToPool(state: DeckState, cardId: Long, today: Int): ReviewResult {
        val returned = returnToNew(state, cardId, today)
        val isExhausted = isActivePassOpen(returned.deck) && isPassExhausted(returned)
        val settled = if (isExhausted) finishExhaustedPass(returned, today) else returned
        val cursor = settled.listSectionCards(Section.NEW).indexOfFirst { it.id == cardId }
        val poolDeck = settled.deck.copy(activeSection = Section.NEW, browseCursor = cursor)
        return showCurrent(settled.copy(deck = poolDeck))
    }

    /**
     * Конец исчерпанного прохода без цепочки старта, парный к [endPass]. Вставной проход возвращает к прерванному, а
     * если и в нём ничего не осталось, тот завершается здесь же; обычный проход считается и выбирает следующую эпоху.
     */
    private fun finishExhaustedPass(state: DeckState, today: Int): DeckState {
        if (!isInsertedPass(state.deck)) return completePass(state, state.deck.currentEpoch, today)
        val resumed = resumePausedPass(finishPass(state, 1, today))
        return if (isPassExhausted(resumed)) completePass(resumed, resumed.deck.currentEpoch, today) else resumed
    }

    /**
     * Возврат к прерванному проходу со следующей карточки. Если в нём ничего не осталось (все
     * удалены во время вставки), он завершается здесь же.
     */
    private fun returnToPausedPass(state: DeckState, today: Int, random: Random, announce: Boolean): ReviewResult {
        val resumed = resumePausedPass(state)
        val epoch = resumed.deck.currentEpoch
        val remaining = listPassCards(resumed, epoch).size - resumed.deck.passCursor
        if (remaining <= 0) return endPass(resumed, today, random)
        if (!announce) return showCurrent(resumed)
        return ReviewResult(resumed, ReviewOutcome.Transition(epoch, remaining, TransitionKind.RESUMED))
    }

    /** Прерванная эпоха снова активна, её курсор на месте, порция в 40 оценок начинается заново. */
    private fun resumePausedPass(state: DeckState): DeckState {
        val deck = state.deck
        val epoch = deck.pausedEpoch
        return state.copy(
            deck = deck.copy(
                currentEpoch = epoch,
                passCursor = deck.pausedCursor,
                pausedEpoch = 0,
                pausedCursor = 0,
                portionRatedCount = 0,
                browseCursor = 0,
                activeSection = findEpochSection(epoch),
            ),
        )
    }
}

/**
 * Цепочка старта (`OpenSection` активной эпохи при `passStarted = false` и после каждого конца
 * прохода). Не больше [maxIterations] итераций, потом `IllegalStateException`: это страховка от
 * бага, а не часть логики.
 *
 * Шаги одной итерации:
 * 1. пул, эпохи 1–4, готовые выученные и ждущие с прошедшей ночью пусты — проход не начинается: если есть ждущие
 *    карточки, `DoneForToday`, иначе `NeedNewCard`;
 * 2. ожидающая эпоха: созрела — получает ход; не созрела, а эпоха 1, пул и ждущие с прошедшей ночью пусты —
 *    `DoneForToday`; иначе ход у эпохи 1;
 * 3. выбранная эпоха 2–4 не созрела — она становится ожидающей, ход у эпохи 1, снова шаг 2;
 * 4. старт прохода: непустой — `Transition`; пустой — его завершение, счётчик и выбор следующей эпохи.
 */
internal fun runStartChain(
    initial: DeckState,
    today: Int,
    random: Random,
    maxIterations: Int = MAX_CHAIN_ITERATIONS,
): ReviewResult {
    var state = initial
    repeat(maxIterations) {
        findIdleOutcome(state, today)?.let { return buildIdleResult(state, it) }

        when (val step = settleWaitingEpoch(state, today)) {
            is WaitingStep.Finish -> return step.result
            is WaitingStep.Proceed -> state = step.state
        }

        val deck = state.deck
        if (deck.currentEpoch in 2..4 && !isEpochMature(state, deck.currentEpoch, today)) {
            state = state.copy(deck = deck.copy(waitingEpoch = deck.currentEpoch, currentEpoch = 1))
            when (val step = settleWaitingEpoch(state, today)) {
                is WaitingStep.Finish -> return step.result
                is WaitingStep.Proceed -> state = step.state
            }
        }

        val epoch = state.deck.currentEpoch
        val started = startPass(state, epoch, today, random)
        val size = listPassCards(started, epoch).size
        if (size > 0) {
            val activeDeck = started.deck.copy(
                activeSection = findEpochSection(epoch),
                passStarted = true,
                passCursor = 0,
                portionRatedCount = 0,
                browseCursor = 0,
            )
            return ReviewResult(
                started.copy(deck = activeDeck),
                ReviewOutcome.Transition(epoch, size, TransitionKind.REGULAR),
            )
        }
        state = completePass(started, epoch, today)
    }
    throw IllegalStateException("Цепочка пустых проходов не закончилась за $maxIterations итераций")
}

private sealed interface WaitingStep {
    data class Proceed(val state: DeckState) : WaitingStep
    data class Finish(val result: ReviewResult) : WaitingStep
}

/** Шаг 2 цепочки: что делать с ожидающей эпохой. */
private fun settleWaitingEpoch(state: DeckState, today: Int): WaitingStep {
    val deck = state.deck
    val waiting = deck.waitingEpoch
    if (waiting == 0) return WaitingStep.Proceed(state)
    if (isEpochMature(state, waiting, today)) {
        return WaitingStep.Proceed(state.copy(deck = deck.copy(currentEpoch = waiting, waitingEpoch = 0)))
    }
    if (!hasFirstEpochWork(state, today)) {
        val idle = state.copy(deck = deck.copy(currentEpoch = waiting))
        return WaitingStep.Finish(buildIdleResult(idle, ReviewOutcome.DoneForToday))
    }
    return WaitingStep.Proceed(state.copy(deck = deck.copy(currentEpoch = 1)))
}

/** Проход не начинается: открыт пул новых, активной остаётся выбранная эпоха. */
private fun buildIdleResult(state: DeckState, outcome: ReviewOutcome): ReviewResult {
    val idle = state.deck.copy(activeSection = Section.NEW, passStarted = false, passCursor = 0, browseCursor = 0)
    return ReviewResult(state.copy(deck = idle), outcome)
}

/**
 * Исход без прохода, если учить нечего, и `null`, если есть что: карточка в пуле или эпохе, готовая к проверке
 * выученная или ждущая, у которой прошла ночь. Ждущие карточки дают «На сегодня всё»: колода не пуста, просто ночь
 * ещё не прошла. Совсем пустая колода просит создать карточку.
 */
private fun findIdleOutcome(state: DeckState, today: Int): ReviewOutcome? {
    val hasWork = state.cards.any {
        when (it.section) {
            Section.LEARNED -> isReadyForCheck(it, today)
            Section.RESTING -> isRestOver(it, today)
            else -> true
        }
    }
    if (hasWork) return null
    val hasResting = state.cards.any { it.section == Section.RESTING }
    return if (hasResting) ReviewOutcome.DoneForToday else ReviewOutcome.NeedNewCard
}

/**
 * Будет ли у эпохи 1 работа: карточка в пуле (добор) или в самой эпохе, либо ждущая, которую её старт выпустит.
 * Пока работы нет, ожидающая эпоха не уступает ей ход, а порция эпохи 2–4 не прерывается вставным проходом.
 */
private fun hasFirstEpochWork(state: DeckState, today: Int): Boolean =
    state.cards.any { it.section == Section.NEW || it.section == Section.EPOCH_1 || isRestOver(it, today) }

/** Завершение обычного прохода: шаги 1–5 `finishPass`, затем счётчик эпохи и выбор следующей. */
private fun completePass(state: DeckState, epoch: Int, today: Int): DeckState {
    val finished = finishPass(state, epoch, today)
    val deck = finished.deck
    val passCountAfter = deck.passCount[epoch] + 1
    return finished.copy(
        deck = deck.copy(
            passCount = deck.passCount.replace(epoch, passCountAfter),
            currentEpoch = chooseNextEpoch(epoch, passCountAfter, deck.waitingEpoch),
            passStarted = false,
            passCursor = 0,
        ),
    )
}
