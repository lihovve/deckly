package app.deckly.editor

import app.deckly.deck.toShown
import app.deckly.domain.editor.EditorSession
import app.deckly.domain.editor.HeldShape
import app.deckly.domain.editor.ShapeHandle
import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Side
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.SelectedShape
import app.deckly.ui.contract.ShapeFrame
import app.deckly.ui.contract.ShapeHandleKind
import app.deckly.ui.contract.ShapeKind
import app.deckly.ui.contract.Thickness
import kotlin.math.roundToInt

/** Состояние редактора для дизайна. Отмена доступна, пока есть что отменять. */
fun buildEditorScreenState(
    session: EditorSession,
    thickness: Thickness,
    dialog: EditorDialog?,
    held: HeldShape? = null,
    isShapePaletteOpen: Boolean = false,
    paletteOutlineWidth: Int = DEFAULT_OUTLINE_WIDTH,
    isCapturingStroke: Boolean = false,
): EditorScreenState = EditorScreenState(
    isNewCard = session.draft.cardId == null,
    side = session.draft.visibleSide.toShown(),
    thickness = thickness,
    canUndo = session.history.isNotEmpty(),
    dialog = dialog,
    selectedShape = held?.shape?.toSelectedShape(),
    isShapePaletteOpen = isShapePaletteOpen,
    paletteOutlineWidth = paletteOutlineWidth,
    isCapturingStroke = isCapturingStroke,
)

/** Толщина пера в пикселях листа (решение владельца 5 ноября: 3, 10 и 25). В единицы карточки её переводит `ink`. */
fun Thickness.toPenWidthPx(): Float = when (this) {
    Thickness.THIN -> 3f
    Thickness.MEDIUM -> 10f
    Thickness.THICK -> 25f
}

fun CardSideShown.toSide(): Side = when (this) {
    CardSideShown.FRONT -> Side.FRONT
    CardSideShown.BACK -> Side.BACK
}

/** Вид фигуры контракта в вид модели: имена совпадают, порядок тот же (палитра макета). */
fun ShapeKind.toDomainKind(): Shape.Kind = Shape.Kind.valueOf(name)

fun Shape.Kind.toContractKind(): ShapeKind = ShapeKind.valueOf(name)

/** Рамка фигуры для дизайна: те же единицы карточки, линия и стрелки помечены. */
fun Shape.toFrame(): ShapeFrame = ShapeFrame(left, top, right, bottom, angle, kind.isLinear)

/**
 * Фигура в руках для дизайна: вид, рамка и толщина контура в единицах макета (ползунок 1…12). Файл мог принести
 * толщину вне ползунка, на панели число всё равно в его границах.
 */
fun Shape.toSelectedShape(): SelectedShape = SelectedShape(
    kind.toContractKind(),
    toFrame(),
    (width / CardUnits.PER_DESIGN_UNIT).roundToInt().coerceIn(MIN_OUTLINE_WIDTH, MAX_OUTLINE_WIDTH),
)

/** Толщина контура из единиц макета (ползунок 1…12) в единицы карточки. */
fun Int.toShapeWidth(): Float = this * CardUnits.PER_DESIGN_UNIT

/** Ручка контракта в ручку модели: имена те же. */
fun ShapeHandleKind.toDomainHandle(): ShapeHandle = ShapeHandle.valueOf(name)
