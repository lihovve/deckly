package app.deckly.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.DayClock
import app.deckly.SerialWriter
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.collectDeckChanges
import app.deckly.domain.model.listSectionCards
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.CardContentRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.review.ReviewAction
import app.deckly.domain.review.ReviewEngine
import app.deckly.domain.review.ReviewOutcome
import app.deckly.domain.review.findCurrentCard
import app.deckly.domain.review.isActivePassOpen
import app.deckly.domain.review.listPassCards
import app.deckly.navigation.AppScreen
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.DeckDialog
import app.deckly.ui.contract.DeckScreenState
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Карточка на листе: штрихи видимой стороны. Файл штрихов не открылся — [isDamaged], лист пуст, а с
 * карточкой можно работать дальше.
 */
data class ShownCard(val cardId: Long, val side: Side, val strokes: List<Stroke>, val isDamaged: Boolean)

/**
 * Экран колоды: действие → алгоритм → запись → публикация (поток данных из документа архитектуры).
 *
 * Действия идут по одному и каждое применяется к уже опубликованному состоянию, то есть к тому, что видел
 * пользователь; нажатие, пришедшее во время записи, ждёт её конца. Действие, которое к показанному уже не
 * применимо (двойное «Знал»), пропускается: [isDeckActionApplicable]. Экран показывает новое состояние
 * только после успешной записи, а содержимое новой карточки читается до публикации, чтобы лист не
 * мигал пустым. Ошибка записи не ловится: приложение падает, база остаётся целой.
 *
 * Диалог и экран перехода в базу не пишутся: после перезапуска показывается текущая карточка.
 */
class DeckViewModel(
    private val deckId: Long,
    private val decks: DeckRepository,
    contents: CardContentRepository,
    private val appState: AppStateRepository,
    private val writer: SerialWriter,
    private val clock: DayClock,
    private val random: Random,
    private val navigate: (AppScreen) -> Unit,
    private val initialSection: Section? = null,
    private val initialFocusCardId: Long? = null,
) : ViewModel() {
    private val cache = CardContentCache(contents)
    private val actions = Channel<DeckAction>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<DeckScreenState?>(null)
    private val mutableShownCard = MutableStateFlow<ShownCard?>(null)

    /** `null`, пока колода загружается. */
    val state: StateFlow<DeckScreenState?> = mutableState.asStateFlow()

    /** Карточка на листе; `null`, когда на экране нет карточки (пустая секция, переход). */
    val shownCard: StateFlow<ShownCard?> = mutableShownCard.asStateFlow()

    // То, что видит пользователь. Меняется только в цикле действий и только после записи.
    private lateinit var deckState: DeckState
    private var transition: ReviewOutcome.Transition? = null
    private var dialog: DeckDialog? = null
    // Карточка, о которой спрашивает окно подтверждения; читается, только пока окно открыто.
    private var dialogCardId: Long? = null
    private var prefetchJob: Job? = null

    init {
        viewModelScope.launch {
            deckState = decks.loadDeckState(deckId)
            writer.write { appState.saveCurrentDeckId(deckId) }
            // Из редактора экран возвращается в ту секцию, откуда его вызвали; до этого прежняя не показывается.
            if (initialSection != null) {
                applyReview(ReviewAction.OpenSection(initialSection, initialFocusCardId))
            } else {
                publish()
            }
            for (action in actions) handle(action)
        }
    }

    fun onAction(action: DeckAction) {
        actions.trySend(action)
    }

    private suspend fun handle(action: DeckAction) {
        if (!isDeckActionApplicable(deckState, transition != null, dialog != null, action)) return
        when (action) {
            is DeckAction.OpenSection -> applyReview(ReviewAction.OpenSection(action.section.toSection()))
            DeckAction.Flip -> applyReview(ReviewAction.Flip)
            DeckAction.Knew -> applyReview(ReviewAction.Knew)
            DeckAction.DidNotKnow -> applyReview(ReviewAction.DidNotKnow)
            DeckAction.Postpone -> applyReview(ReviewAction.Postpone)
            DeckAction.ShowPrevious -> applyReview(ReviewAction.Browse(-1))
            DeckAction.ShowNext -> applyReview(ReviewAction.Browse(1))
            DeckAction.StartPass -> startPass()
            is DeckAction.ChooseMenuItem -> chooseMenuItem(action.item)
            DeckAction.ConfirmDialog -> confirmDialog()
            DeckAction.DismissDialog -> closeDialog()
            DeckAction.CreateCard -> createCard()
            DeckAction.OpenDeckList -> navigate(AppScreen.DeckList)
        }
    }

    // Алгоритм → одна запись → публикация. Номер дня и случайность алгоритму даёт приложение.
    private suspend fun applyReview(action: ReviewAction) {
        val before = deckState
        val result = ReviewEngine.applyAction(before, action, clock.computeToday(), random)
        writer.write { decks.saveDeckChanges(collectDeckChanges(before, result.state)) }
        deckState = result.state
        transition = result.outcome as? ReviewOutcome.Transition
        dialog = when (result.outcome) {
            ReviewOutcome.NeedNewCard -> DeckDialog.NEED_NEW_CARD
            ReviewOutcome.DoneForToday -> DeckDialog.DONE_FOR_TODAY
            else -> null
        }
        publish()
    }

    // Проход уже начат алгоритмом до показа перехода; закрытие перехода только показывает карточку.
    private suspend fun startPass() {
        transition = null
        publish()
    }

    // Карточка, показанная в пуле до «+», запоминается: отмена нового черновика возвращает на неё.
    private fun createCard() {
        val shownCardId = findCurrentCard(deckState)?.id
        navigate(AppScreen.Editor(deckId, EditorStart.NewCard(deckState.deck.activeSection, shownCardId)))
    }

    private suspend fun chooseMenuItem(item: CardMenuItem) {
        val cardId = findCurrentCard(deckState)?.id ?: return
        when (item) {
            CardMenuItem.EDIT -> navigate(AppScreen.Editor(deckId, EditorStart.EditCard(cardId, Section.NEW)))
            CardMenuItem.RETURN_TO_NEW -> openDialog(DeckDialog.CONFIRM_RETURN_TO_NEW, cardId)
            CardMenuItem.DELETE -> openDialog(DeckDialog.CONFIRM_DELETE_CARD, cardId)
        }
    }

    private suspend fun openDialog(kind: DeckDialog, cardId: Long) {
        dialog = kind
        dialogCardId = cardId
        publish()
    }

    private suspend fun closeDialog() {
        dialog = null
        publish()
    }

    private suspend fun confirmDialog() {
        when (dialog) {
            DeckDialog.CONFIRM_DELETE_CARD -> confirmCardAction { ReviewAction.DeleteCard(it) }
            DeckDialog.CONFIRM_RETURN_TO_NEW -> confirmCardAction { ReviewAction.ReturnToNew(it) }
            // Алгоритм уже оставил открытым пул: окно только закрывается, писать нечего.
            DeckDialog.NEED_NEW_CARD, DeckDialog.DONE_FOR_TODAY -> closeDialog()
            null -> Unit
        }
    }

    // Пока окно открыто, остальные действия не применяются, так что карточка та же, о которой спросили.
    private suspend fun confirmCardAction(build: (Long) -> ReviewAction) {
        val cardId = checkNotNull(dialogCardId) { "Окно подтверждения открыто без карточки" }
        applyReview(build(cardId))
    }

    // Содержимое читается до публикации, а следующая карточка прохода подгружается после неё.
    private suspend fun publish() {
        val card = if (transition == null) findCurrentCard(deckState) else null
        val shown = card?.let { buildShownCard(it.id, cache.loadContent(it.id)) }
        mutableShownCard.value = shown
        mutableState.value = buildDeckScreenState(deckState, transition, dialog, isContentDamaged = shown?.isDamaged == true)
        prefetchNext()
    }

    private fun buildShownCard(cardId: Long, content: Result<CardContent>): ShownCard {
        val side = deckState.deck.visibleSide
        val loaded = content.getOrNull() ?: return ShownCard(cardId, side, emptyList(), isDamaged = true)
        return ShownCard(cardId, side, if (side == Side.FRONT) loaded.front else loaded.back, isDamaged = false)
    }

    private fun prefetchNext() {
        val cardId = findPrefetchTarget() ?: return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch { cache.loadContent(cardId) }
    }

    // На экране перехода это первая карточка прохода; иначе соседняя в том порядке, в каком идёт показ.
    private fun findPrefetchTarget(): Long? {
        if (transition != null) return findCurrentCard(deckState)?.id
        val deck = deckState.deck
        if (isActivePassOpen(deck)) return listPassCards(deckState, deck.currentEpoch).getOrNull(deck.passCursor + 1)?.id
        val cards = deckState.listSectionCards(deck.activeSection)
        return if (cards.size > 1) cards[(deck.browseCursor + 1) % cards.size].id else null
    }
}
