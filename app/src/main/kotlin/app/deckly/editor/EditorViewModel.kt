package app.deckly.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.DayClock
import app.deckly.SerialWriter
import app.deckly.domain.editor.ActiveShapeGesture
import app.deckly.domain.editor.EditorSession
import app.deckly.domain.editor.HeldShape
import app.deckly.domain.editor.addStroke
import app.deckly.domain.editor.applyHeldShape
import app.deckly.domain.editor.eraseAlong
import app.deckly.domain.editor.findShapeAt
import app.deckly.domain.editor.insertShape
import app.deckly.domain.editor.listVisibleContent
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
import app.deckly.ui.contract.ShapeKind
import app.deckly.ui.contract.Thickness
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Что нужно листу рисования ([app.deckly.ink.DrawingSheet]): штрихи и фигуры видимой стороны, фигура в руках среди
 * них. [uiRevision] растёт, когда вне холста изменилось что-то видимое, и пока перо включено, прошивка этого не
 * показывает: лист выключает перо на время обновления.
 */
data class CanvasState(
    val side: CardSide,
    val penWidthPx: Float,
    val isPenEnabled: Boolean,
    val uiRevision: Int,
)

/**
 * Редактор карточки. Штрихи, стирания и отмены приходят от листа, действия — от дизайна; всё идёт по одному
 * в порядке прихода. Черновик появляется с первого изменения (до него в базу ничего не пишется) и после
 * каждого штриха, стирания, отмены, смены стороны и применения фигуры записывается через [SerialWriter]. Новое
 * состояние публикуется только после записи. Ошибка записи не ловится: приложение падает, база остаётся целой.
 *
 * Фигура в руках (новая из палитры или выбранная касанием у контура) в черновик не входит: движения, повороты и
 * растяжение живут только в памяти, пишется применённое. Применяют её галочка, касание мимо, выбор другой фигуры,
 * палитра, «Сохранить» и смена стороны; крест возвращает фигуру, какой она была. Пока открыто окно, палитра или фигура
 * в руках, перо выключено, а пока открыто окно или идёт выход, холст не принимает ни штрихов, ни действий.
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
    private val outlineWidths: OutlineWidthStore,
    private val navigate: (AppScreen) -> Unit,
) : ViewModel() {
    private sealed interface Input {
        data class Action(val action: EditorAction) : Input
        data class Stroked(val stroke: Stroke) : Input
        data class Erased(val path: List<StrokePoint>, val radius: Float) : Input
        data class ShapeTapped(val index: Int) : Input
    }

    // Что вне черновика и не пишется в базу: толщина, окно. `null` в session — редактор ещё грузится.
    private var session: EditorSession? = (start as? EditorStart.NewCard)?.let { startNewCard(it.returnSection) }
    private var hasDraft = false
    private var thickness = Thickness.MEDIUM
    private var dialog: EditorDialog? = null
    private var isLeaving = false

    // Фигура в руках и жест над ней; палитра и запомненная толщина контура новых фигур (в единицах макета).
    private var held: HeldShape? = null
    private var gesture: ActiveShapeGesture? = null
    private var isPaletteOpen = false
    private var outlineWidth = outlineWidths.readWidth().coerceIn(MIN_OUTLINE_WIDTH, MAX_OUTLINE_WIDTH)

    private data class UiSignature(
        val thickness: Thickness,
        val canUndo: Boolean,
        val dialog: EditorDialog?,
        val side: Side,
        val isPaletteOpen: Boolean,
        val hasSelection: Boolean,
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

    /** Лист сообщает, что короткое касание у контура выбрало фигуру [index] видимой стороны. */
    fun onShapeTap(index: Int) {
        inputs.trySend(Input.ShapeTapped(index))
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
            // Стирание из-под руки сняло бы фигуру, место (индекс) которой держат в руках: пока она в руках, не стираем.
            is Input.Erased ->
                if (dialog == null && held == null) applyChange(current.eraseAlong(input.path, input.radius), startsDraft = true)
            is Input.ShapeTapped -> selectPlacedShape(input.index)
            is Input.Action -> handleAction(input.action)
        }
    }

    // Состояния взаимоисключающие, сверху вниз по строгости: окно закрывает всё, палитра закрывает лист, фигура в руках
    // закрывает обычные инструменты.
    private suspend fun handleAction(action: EditorAction) {
        val inHand = held
        when {
            dialog != null -> when (action) {
                EditorAction.ConfirmDialog -> confirmDialog()
                EditorAction.DismissDialog -> closeDialog()
                else -> Unit
            }
            isPaletteOpen -> handlePaletteAction(action)
            inHand != null -> handleSelectionAction(action, inHand)
            else -> handleOrdinaryAction(action)
        }
    }

    private suspend fun handleOrdinaryAction(action: EditorAction) {
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
            EditorAction.OpenShapePalette -> openPalette()
            // Остальное без фигуры в руках и без палитры не имеет смысла (дизайн такого не присылает): поздний жест, галочка.
            else -> Unit
        }
    }

    // Палитра закрывает лист: от неё идут только её действия. Системная «Назад» закрывает её, а не предлагает выйти.
    private fun handlePaletteAction(action: EditorAction) {
        when (action) {
            is EditorAction.SetOutlineWidth -> rememberOutlineWidth(action.width)
            // Новая фигура встаёт по центру листа с запомненным контуром, выделена и в руках; в черновик не входит.
            is EditorAction.ChooseShape -> {
                val shape = insertShape(action.kind.toDomainKind(), outlineWidth.toShapeWidth())
                isPaletteOpen = false
                held = HeldShape(shape, editedIndex = null, original = null)
                publish()
            }
            EditorAction.CloseShapePalette, EditorAction.Back -> {
                isPaletteOpen = false
                publish()
            }
            else -> Unit
        }
    }

    private suspend fun handleSelectionAction(action: EditorAction, inHand: HeldShape) {
        when (action) {
            is EditorAction.StartShapeGesture -> startShapeGesture(action, inHand)
            // `Move` и `Cancel` без жеста (его оборвали: касание мимо убирает слой жестов вместе с корутиной) ничего не меняют.
            is EditorAction.MoveShapeGesture -> gesture?.let { active ->
                held = inHand.copy(shape = active.applyTo(action.x, action.y))
                publish()
            }
            EditorAction.EndShapeGesture -> gesture = null
            EditorAction.CancelShapeGesture -> gesture?.let { active ->
                gesture = null
                held = inHand.copy(shape = active.startShape)
                publish()
            }
            EditorAction.ApplyShape -> {
                applyHeldShape()
                publish()
            }
            EditorAction.CancelShape -> {
                held = null
                gesture = null
                publish()
            }
            EditorAction.OpenShapePalette -> {
                applyHeldShape()
                openPalette()
            }
            is EditorAction.SelectSide -> {
                applyHeldShape()
                publish()
                handleOrdinaryAction(action)
            }
            EditorAction.Save -> {
                applyHeldShape()
                saveAndLeave()
            }
            // Окно открывается поверх выделения; «Keep editing» вернёт к нему, «Save and exit» применит фигуру.
            EditorAction.Back, EditorAction.Cancel, EditorAction.Delete -> handleOrdinaryAction(action)
            // Отмена и толщина пера при фигуре в руках не работают: их кнопок на панели фигуры нет.
            else -> Unit
        }
    }

    private fun openPalette() {
        isPaletteOpen = true
        publish()
    }

    // Одно значение подряд не пишем и не публикуем: ползунок шлёт его на каждое движение пера внутри деления.
    private fun rememberOutlineWidth(width: Int) {
        val next = width.coerceIn(MIN_OUTLINE_WIDTH, MAX_OUTLINE_WIDTH)
        if (next == outlineWidth) return
        outlineWidth = next
        outlineWidths.writeWidth(next)
        publish()
    }

    // Фигура, выбранная касанием у контура: в руках, запомнена как была. Устаревший номер (фигуру успели снять) молча теряется.
    private fun selectPlacedShape(index: Int) {
        if (dialog != null || isPaletteOpen || held != null) return
        val shape = current.listVisibleContent(null).shapes.getOrNull(index) ?: return
        held = HeldShape(shape, index, shape)
        publish()
    }

    /**
     * Касание листа при выделенной фигуре. С ручкой: жест ручки. Без неё решает, чья лента выбора задета (верхней из
     * задетых): своя фигура двигается за контур; другая применяет текущую, выделяется и тем же касанием двигается, как
     * в демо; мимо всех применяет фигуру и возвращает перо, а касание дальше станет штрихом.
     */
    private suspend fun startShapeGesture(action: EditorAction.StartShapeGesture, inHand: HeldShape) {
        val handle = action.handle
        if (handle != null) {
            gesture = ActiveShapeGesture(handle.toDomainHandle(), action.x, action.y, inHand.shape)
            return
        }
        val shapes = current.listVisibleContent(inHand).shapes
        val inHandIndex = inHand.editedIndex ?: shapes.lastIndex
        when (val hit = findShapeAt(shapes, action.x, action.y)) {
            inHandIndex -> gesture = ActiveShapeGesture(null, action.x, action.y, inHand.shape)
            null -> {
                applyHeldShape()
                publish()
            }
            else -> {
                val other = shapes[hit]
                applyHeldShape()
                held = HeldShape(other, hit, other)
                gesture = ActiveShapeGesture(null, action.x, action.y, other)
                publish()
            }
        }
    }

    // Применение фигуры из рук: запись (если что-то изменилось) и снятие выделения; показывает вызывающий. Новое
    // состояние публикуется только после записи, поэтому выделение снимается до неё, а публикуется после.
    private suspend fun applyHeldShape() {
        val inHand = held ?: return
        held = null
        gesture = null
        commit(current.applyHeldShape(inHand), startsDraft = true)
    }

    // Изменение черновика: запись, затем показ. Ничего не изменилось (ластик мимо, нечего отменять) — ничего не делаем.
    // Смена стороны черновик не создаёт: он появляется только с изменением штрихов или фигур.
    private suspend fun applyChange(next: EditorSession, startsDraft: Boolean) {
        if (next == current) return
        commit(next, startsDraft)
        publish()
    }

    // Запись без показа: вызывающий публикует сам, когда закончит все изменения одного действия.
    private suspend fun commit(next: EditorSession, startsDraft: Boolean) {
        if (next == current) return
        session = next
        if (startsDraft) hasDraft = true
        if (hasDraft) {
            val stored = StoredDraft(deckId, next.draft)
            writer.write { drafts.storeDraft(stored) }
        }
    }

    private fun openDialog(kind: EditorDialog) {
        dialog = kind
        // Под окном жест не продолжается: его движения и конец окно не пропустит. Фигура остаётся там, где её оставили.
        gesture = null
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
            EditorDialog.SAVE_ON_BACK -> {
                applyHeldShape()
                saveAndLeave()
            }
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

    // Видимое вне холста: толщина, кнопка отмены, окно, вкладка стороны, палитра и панель фигуры. Вкладка стороны тут
    // нужна, потому что у пустых сторон список штрихов не меняется, и лист сам смену стороны не заметит. Сами движения
    // фигуры в подпись не входят: пока она в руках, перо выключено, и рамка с ручками показывается обычным путём.
    private fun buildUiSignature() = UiSignature(
        thickness,
        canUndo = session?.history?.isNotEmpty() == true,
        dialog,
        side = session?.draft?.visibleSide ?: Side.FRONT,
        isPaletteOpen,
        hasSelection = held != null,
    )

    private fun buildState(): EditorScreenState? =
        session?.let { buildEditorScreenState(it, thickness, dialog, held, isPaletteOpen, outlineWidth) }

    private fun buildCanvas() = CanvasState(
        side = session?.listVisibleContent(held) ?: CardSide(),
        penWidthPx = thickness.toPenWidthPx(),
        isPenEnabled = session != null && dialog == null && !isLeaving && !isPaletteOpen && held == null,
        uiRevision = uiRevision,
    )
}
