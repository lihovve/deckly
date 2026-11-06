package app.deckly.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.DayClock
import app.deckly.SerialWriter
import app.deckly.domain.editor.EditorSession
import app.deckly.domain.editor.addStroke
import app.deckly.domain.editor.eraseAlong
import app.deckly.domain.editor.resumeDraft
import app.deckly.domain.editor.saveDraft
import app.deckly.domain.editor.startEditing
import app.deckly.domain.editor.startNewCard
import app.deckly.domain.editor.switchSide
import app.deckly.domain.editor.undoLastStep
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import app.deckly.domain.model.collectDeckChanges
import app.deckly.domain.port.CardContentRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DraftRepository
import app.deckly.domain.port.StoredDraft
import app.deckly.domain.review.ReviewAction
import app.deckly.domain.review.ReviewEngine
import app.deckly.navigation.AppScreen
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.Thickness
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Что нужно листу рисования ([app.deckly.ink.DrawingSheet]). [uiRevision] растёт, когда вне холста изменилось
 * что-то видимое, и пока перо включено, прошивка этого не показывает: лист выключает перо на время обновления.
 */
data class CanvasState(
    val strokes: List<Stroke>,
    val penWidthPx: Float,
    val isPenEnabled: Boolean,
    val uiRevision: Int,
)

/**
 * Редактор карточки. Штрихи, стирания и отмены приходят от листа, действия — от дизайна; всё идёт по одному
 * в порядке прихода. Черновик появляется с первого изменения (до него в базу ничего не пишется) и после
 * каждого штриха, стирания, отмены и смены стороны записывается через [SerialWriter]. Новое состояние
 * публикуется только после записи. Ошибка записи не ловится: приложение падает, база остаётся целой.
 *
 * Пока открыто окно или идёт выход, холст не принимает ни штрихов, ни действий, а перо выключено.
 */
class EditorViewModel(
    private val deckId: Long,
    private val start: EditorStart,
    private val decks: DeckRepository,
    private val contents: CardContentRepository,
    private val drafts: DraftRepository,
    private val writer: SerialWriter,
    private val clock: DayClock,
    private val random: Random,
    private val nowMillis: () -> Long,
    private val navigate: (AppScreen) -> Unit,
) : ViewModel() {
    private sealed interface Input {
        data class Action(val action: EditorAction) : Input
        data class Stroked(val stroke: Stroke) : Input
        data class Erased(val path: List<StrokePoint>, val radius: Float) : Input
    }

    // Что вне черновика и не пишется в базу: толщина, окно. `null` в session — редактор ещё грузится.
    private var session: EditorSession? = (start as? EditorStart.NewCard)?.let { startNewCard(it.returnSection) }
    private var hasDraft = false
    private var thickness = Thickness.MEDIUM
    private var dialog: EditorDialog? = null
    private var isLeaving = false

    private data class UiSignature(
        val thickness: Thickness,
        val canUndo: Boolean,
        val dialog: EditorDialog?,
        val side: Side,
    )

    private var uiSignature = buildUiSignature()
    private var uiRevision = 0

    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(buildState())
    private val mutableCanvas = MutableStateFlow(buildCanvas())

    /**
     * `null`, пока сессия (черновик, правленая карточка) не прочитана: заглушка с неверной шапкой и стороной
     * показалась бы на кадр. Новая карточка готова сразу, у неё `null` не бывает.
     */
    val state: StateFlow<EditorScreenState?> = mutableState.asStateFlow()
    val canvas: StateFlow<CanvasState> = mutableCanvas.asStateFlow()

    private val current: EditorSession get() = checkNotNull(session) { "Редактор ещё не загружен" }

    init {
        viewModelScope.launch {
            if (session == null) {
                session = loadSession()
                hasDraft = start is EditorStart.ResumeDraft
                publish()
            }
            for (input in inputs) handle(input)
        }
    }

    fun onAction(action: EditorAction) {
        inputs.trySend(Input.Action(action))
    }

    fun onStroke(stroke: Stroke) {
        inputs.trySend(Input.Stroked(stroke))
    }

    fun onErase(path: List<StrokePoint>, radius: Float) {
        inputs.trySend(Input.Erased(path, radius))
    }

    private suspend fun loadSession(): EditorSession = when (start) {
        is EditorStart.NewCard -> error("Новая карточка начинается без загрузки")
        is EditorStart.EditCard -> {
            val card = checkNotNull(decks.loadDeckState(deckId).cards.find { it.id == start.cardId }) {
                "Карточки ${start.cardId} нет в колоде $deckId"
            }
            startEditing(card, loadContentOrEmpty(card.id), start.returnSection)
        }
        EditorStart.ResumeDraft -> resumeDraft(checkNotNull(drafts.loadDraft()) { "Черновика нет в хранилище" }.draft)
    }

    // Файл штрихов правимой карточки пропал или повреждён: редактор открывается пустым, как и экран колоды.
    private suspend fun loadContentOrEmpty(cardId: Long): CardContent =
        try {
            contents.loadContent(cardId)
        } catch (failure: IOException) {
            CardContent(cardId, CardSide(), CardSide())
        }

    private suspend fun handle(input: Input) {
        if (isLeaving) return
        when (input) {
            is Input.Stroked -> if (dialog == null) applyChange(current.addStroke(input.stroke), startsDraft = true)
            is Input.Erased ->
                if (dialog == null) applyChange(current.eraseAlong(input.path, input.radius), startsDraft = true)
            is Input.Action -> handleAction(input.action)
        }
    }

    private suspend fun handleAction(action: EditorAction) {
        if (dialog != null) {
            when (action) {
                EditorAction.ConfirmDialog -> confirmDialog()
                EditorAction.DismissDialog -> closeDialog()
                else -> Unit
            }
            return
        }
        when (action) {
            is EditorAction.SelectSide -> applyChange(current.switchSide(action.side.toSide()), startsDraft = false)
            is EditorAction.SelectThickness -> {
                thickness = action.thickness
                publish()
            }
            EditorAction.Undo -> applyChange(current.undoLastStep(), startsDraft = false)
            EditorAction.Save -> saveAndLeave()
            EditorAction.Cancel -> if (current.draft.cardId == null) openDialog(EditorDialog.DISCARD_DRAFT)
            EditorAction.Delete -> if (current.draft.cardId != null) openDialog(EditorDialog.DELETE_CARD)
            EditorAction.Back -> openDialog(EditorDialog.SAVE_ON_BACK)
            EditorAction.ConfirmDialog, EditorAction.DismissDialog -> Unit
            // Палитра, выделение и жесты фигур: дизайн их уже присылает, модель получит их в задаче 6 плана 4.3.
            EditorAction.OpenShapePalette, EditorAction.CloseShapePalette, is EditorAction.ChooseShape,
            is EditorAction.SetOutlineWidth, is EditorAction.StartShapeGesture, is EditorAction.MoveShapeGesture,
            EditorAction.EndShapeGesture, EditorAction.CancelShapeGesture, EditorAction.ApplyShape, EditorAction.CancelShape,
            -> Unit
        }
    }

    // Изменение черновика: запись, затем показ. Ничего не изменилось (ластик мимо, нечего отменять) — ничего не делаем.
    // Смена стороны черновик не создаёт: он появляется только с изменением штрихов.
    private suspend fun applyChange(next: EditorSession, startsDraft: Boolean) {
        if (next == current) return
        session = next
        if (startsDraft) hasDraft = true
        if (hasDraft) {
            val stored = StoredDraft(deckId, next.draft)
            writer.write { drafts.storeDraft(stored) }
        }
        publish()
    }

    private fun openDialog(kind: EditorDialog) {
        dialog = kind
        publish()
    }

    private fun closeDialog() {
        dialog = null
        publish()
    }

    private suspend fun confirmDialog() {
        when (dialog) {
            EditorDialog.DISCARD_DRAFT -> discardAndLeave()
            EditorDialog.DELETE_CARD -> deleteAndLeave()
            EditorDialog.SAVE_ON_BACK -> saveAndLeave()
            null -> Unit
        }
    }

    // Выход запрещает любой дальнейший ввод и выключает перо: штрих, нарисованный посреди сохранения, потерялся бы.
    private fun startLeaving() {
        isLeaving = true
        publish()
    }

    // Новая карточка встаёт в конец пула, у правки меняется только содержимое. Черновик удаляет сама запись.
    private suspend fun saveAndLeave() {
        startLeaving()
        val draft = current.draft
        val before = decks.loadDeckState(deckId)
        val newCardId = if (draft.cardId == null) writer.write { drafts.allocateCardId() } else 0L
        val (after, content) = saveDraft(before, draft, newCardId, clock.computeToday(), nowMillis())
        writer.write { drafts.commitDraft(collectDeckChanges(before, after), content) }
        // Экран колоды покажет правленую или созданную карточку, а не первую в секции.
        navigate(AppScreen.Deck(deckId, draft.returnSection, focusCardId = content.cardId))
    }

    // Отменённая карточка не создана: экран колоды показывает ту, что стояла перед «+» (после перезапуска её нет).
    private suspend fun discardAndLeave() {
        startLeaving()
        writer.write { drafts.discardDraft() }
        val shownBefore = (start as? EditorStart.NewCard)?.returnFocusCardId
        navigate(AppScreen.Deck(deckId, current.draft.returnSection, focusCardId = shownBefore))
    }

    // Сначала карточка, потом черновик: убийство между ними оставляет черновик правки несуществующей карточки,
    // а хранилище при загрузке такой черновик отбрасывает.
    private suspend fun deleteAndLeave() {
        startLeaving()
        val cardId = checkNotNull(current.draft.cardId) { "Удалять нечего: карточка ещё не создана" }
        val before = decks.loadDeckState(deckId)
        val deleted = ReviewEngine.applyAction(before, ReviewAction.DeleteCard(cardId), clock.computeToday(), random)
        writer.write { decks.saveDeckChanges(collectDeckChanges(before, deleted.state)) }
        writer.write { drafts.discardDraft() }
        navigate(AppScreen.Deck(deckId, current.draft.returnSection))
    }

    private fun publish() {
        val signature = buildUiSignature()
        if (signature != uiSignature) {
            uiSignature = signature
            uiRevision++
        }
        mutableState.value = buildState()
        mutableCanvas.value = buildCanvas()
    }

    // Видимое вне холста: толщина, кнопка отмены, окно и вкладка стороны. Вкладка стороны тут нужна,
    // потому что у пустых сторон список штрихов не меняется, и лист сам смену стороны не заметит.
    private fun buildUiSignature() = UiSignature(
        thickness,
        canUndo = session?.history?.isNotEmpty() == true,
        dialog,
        side = session?.draft?.visibleSide ?: Side.FRONT,
    )

    private fun buildState(): EditorScreenState? = session?.let { buildEditorScreenState(it, thickness, dialog) }

    private fun buildCanvas() = CanvasState(
        strokes = session?.let { listVisibleStrokes(it) } ?: emptyList(),
        penWidthPx = thickness.toPenWidthPx(),
        isPenEnabled = session != null && dialog == null && !isLeaving,
        uiRevision = uiRevision,
    )
}
