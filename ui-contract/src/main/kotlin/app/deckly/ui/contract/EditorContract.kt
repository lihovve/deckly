package app.deckly.ui.contract

/** Три толщины пера: тонкая, средняя, толстая. */
enum class Thickness { THIN, MEDIUM, THICK }

/** Окна поверх редактора: отмена черновика, удаление карточки, выход из редактора. */
enum class EditorDialog { DISCARD_DRAFT, DELETE_CARD, SAVE_ON_BACK }

/** Виды фигур в порядке палитры макета (`SHAPES` в `design/build.py`). */
enum class ShapeKind { LINE, ARROW, DOUBLE_ARROW, RECTANGLE, OVAL, TRIANGLE, DIAMOND, HEXAGON, STAR, MOON, SUN, HEART, CLOUD, BOLT, DROP }

/** Ручка выделенной фигуры: четыре угла, концы линии или стрелки и ручка поворота над рамкой. */
enum class ShapeHandleKind { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, START, END, ROTATE }

/**
 * Рамка фигуры в единицах карточки: ширина листа 1000, `x` вправо, `y` вниз. [angle] в градусах по часовой стрелке
 * вокруг центра рамки. У линии и стрелок ([isLinear]) ручек два, по концам оси, и ручки поворота нет.
 */
data class ShapeFrame(val left: Float, val top: Float, val right: Float, val bottom: Float, val angle: Float, val isLinear: Boolean)

/** Выделенная фигура: вид, рамка и толщина контура 1…12 в единицах макета (dp). Контур самой фигуры рисует лист. */
data class SelectedShape(val kind: ShapeKind, val frame: ShapeFrame, val outlineWidth: Int)

/**
 * Состояние редактора. [isNewCard] определяет левое действие шапки: «Отменить» у новой карточки,
 * «Удалить» у правки. [selectedShape] — фигура в руках: пока она есть, вместо обычной панели панель фигуры, а над листом
 * рамка и ручки. [isShapePaletteOpen] — открыта палитра видов и толщины контура, [paletteOutlineWidth] — толщина,
 * которую получит новая фигура. [isCapturingStroke] — касание мимо фигуры собирается в штрих: выделения уже нет, панель
 * обычная, но слой жестов над листом остаётся, пока перо не оторвано, чтобы линия шла с первой точки.
 */
data class EditorScreenState(
    val isNewCard: Boolean,
    val side: CardSideShown,
    val thickness: Thickness,
    val canUndo: Boolean,
    val dialog: EditorDialog?,
    val selectedShape: SelectedShape? = null,
    val isShapePaletteOpen: Boolean = false,
    val paletteOutlineWidth: Int = 3,
    val isCapturingStroke: Boolean = false,
)

sealed interface EditorAction {
    data class SelectSide(val side: CardSideShown) : EditorAction

    data class SelectThickness(val thickness: Thickness) : EditorAction

    data object Undo : EditorAction

    data object Save : EditorAction

    /** Новая карточка: открывает [EditorDialog.DISCARD_DRAFT]. */
    data object Cancel : EditorAction

    /** Правка: открывает [EditorDialog.DELETE_CARD]. */
    data object Delete : EditorAction

    /** Системная «Назад»: открывает [EditorDialog.SAVE_ON_BACK]. */
    data object Back : EditorAction

    data object ConfirmDialog : EditorAction

    data object DismissDialog : EditorAction

    /** Значок «Shapes» (или образец вида на панели фигуры): открыть палитру. */
    data object OpenShapePalette : EditorAction

    /** Крест палитры или касание мимо неё. */
    data object CloseShapePalette : EditorAction

    /** Вид выбран в палитре: фигура вставляется по центру и выделяется. */
    data class ChooseShape(val kind: ShapeKind) : EditorAction

    /** Ползунок толщины контура 1…12: действует на новые фигуры. */
    data class SetOutlineWidth(val width: Int) : EditorAction

    /**
     * Перо коснулось листа при выделенной фигуре. [handle] — ручка, за которую взялись, `null` — любое другое место
     * листа (перенос за контур или касание мимо, это решает модель). [x], [y] в единицах карточки, [pressure] — нажим
     * от 0 до 1: касание мимо становится штрихом, и первая точка штриха несёт нажим. [scale] — пикселей листа в одной
     * единице карточки: по нему модель переводит толщину пера из пикселей в единицы карточки.
     */
    data class StartShapeGesture(
        val handle: ShapeHandleKind?,
        val x: Float,
        val y: Float,
        val pressure: Float = 1f,
        val scale: Float = 1f,
    ) : EditorAction {
        init {
            require(pressure in 0f..1f) { "Нажим должен быть от 0 до 1: $pressure" }
            require(scale.isFinite() && scale > 0f) { "Масштаб листа должен быть конечным и больше нуля: $scale" }
        }
    }

    /** Движение пера в жесте: [x], [y] в единицах карточки, [pressure] от 0 до 1 (у штриха при касании мимо). */
    data class MoveShapeGesture(val x: Float, val y: Float, val pressure: Float = 1f) : EditorAction {
        init {
            require(pressure in 0f..1f) { "Нажим должен быть от 0 до 1: $pressure" }
        }
    }

    data object EndShapeGesture : EditorAction

    /** Жест прерван (отмена касания). */
    data object CancelShapeGesture : EditorAction

    /** Галочка: фигура применена. */
    data object ApplyShape : EditorAction

    /** Крест: новая фигура исчезает, правка возвращается как была. */
    data object CancelShape : EditorAction
}
