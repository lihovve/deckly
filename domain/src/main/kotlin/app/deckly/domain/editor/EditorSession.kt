package app.deckly.domain.editor

import app.deckly.domain.model.Card
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Section
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint

/** Сколько последних шагов помнит отмена (ТЗ требует не меньше 20). */
const val UNDO_DEPTH = 100

/** Штрих, снятый ластиком, и место, где он стоял на своей стороне. */
data class ErasedStroke(val index: Int, val stroke: Stroke)

/** Фигура, снятая ластиком, и место, где она стояла на своей стороне. */
data class ErasedShape(val index: Int, val shape: Shape)

/** Шаг, который снимает отмена: на какой стороне он был и что изменил. */
sealed interface EditorStep {
    val side: Side

    data class StrokeAdded(override val side: Side) : EditorStep

    data class ShapeAdded(override val side: Side) : EditorStep

    /** Фигура на месте [index] стала другой; [before] — какой была. */
    data class ShapeChanged(override val side: Side, val index: Int, val before: Shape) : EditorStep

    /** Один проход ластика снял штрихи и фигуры вместе: отмена возвращает всё одним шагом. */
    data class ContentErased(
        override val side: Side,
        val strokes: List<ErasedStroke>,
        val shapes: List<ErasedShape>,
    ) : EditorStep
}

/** Черновик и история отмены. История хранится только в памяти и перезапуск не переживает. */
data class EditorSession(val draft: Draft, val history: List<EditorStep>)

fun startNewCard(returnSection: Section): EditorSession =
    EditorSession(Draft(null, CardSide(), CardSide(), Side.FRONT, returnSection), emptyList())

/** Правка карточки пула: черновик начинается с её штрихов и фигур. */
fun startEditing(card: Card, content: CardContent, returnSection: Section): EditorSession {
    require(card.section == Section.NEW) { "Редактировать можно только карточку пула новых: ${card.section}" }
    require(content.cardId == card.id) { "Содержимое карточки ${content.cardId} не относится к карточке ${card.id}" }
    return EditorSession(Draft(card.id, content.front, content.back, Side.FRONT, returnSection), emptyList())
}

/** Продолжение после перезапуска: черновик на той же стороне, история пустая. */
fun resumeDraft(draft: Draft): EditorSession = EditorSession(draft, emptyList())

/** Добавляет штрих на видимую сторону. */
fun EditorSession.addStroke(stroke: Stroke): EditorSession {
    val side = draft.visibleSide
    return copy(
        draft = draft.replaceSide(side) { it.copy(strokes = it.strokes + stroke) },
        history = pushStep(EditorStep.StrokeAdded(side)),
    )
}

/** Добавляет фигуру на видимую сторону, поверх прежних. */
fun EditorSession.addShape(shape: Shape): EditorSession {
    val side = draft.visibleSide
    return copy(
        draft = draft.replaceSide(side) { it.copy(shapes = it.shapes + shape) },
        history = pushStep(EditorStep.ShapeAdded(side)),
    )
}

/** Заменяет фигуру на месте [index] видимой стороны. Та же фигура — не шаг. */
fun EditorSession.changeShape(index: Int, shape: Shape): EditorSession {
    val side = draft.visibleSide
    val before = draft.selectSide(side).shapes[index]
    if (before == shape) return this
    return copy(
        draft = draft.replaceSide(side) { it.copy(shapes = it.shapes.replaceAt(index, shape)) },
        history = pushStep(EditorStep.ShapeChanged(side, index, before)),
    )
}

/** Применяет фигуру из рук: новую добавляет, старую ставит на её место; без изменений шага нет. */
fun EditorSession.applyHeldShape(held: HeldShape): EditorSession {
    val index = held.editedIndex
    return if (index == null) addShape(held.shape) else changeShape(index, held.shape)
}

/** Что видно на листе: видимая сторона, а фигура в руках стоит поверх (новая) или вместо прежней (выбранная). */
fun EditorSession.listVisibleContent(held: HeldShape?): CardSide {
    val side = draft.selectSide(draft.visibleSide)
    if (held == null) return side
    val index = held.editedIndex
    val shapes = if (index == null) side.shapes + held.shape else side.shapes.replaceAt(index, held.shape)
    return side.copy(shapes = shapes)
}

/**
 * Удаляет целиком все штрихи и фигуры видимой стороны, которых коснулся ластик; если не задел ни одного, шага нет.
 * Внутренность пустой фигуры ластик не задевает, только её контур.
 */
fun EditorSession.eraseAlong(path: List<StrokePoint>, radius: Float): EditorSession {
    val side = draft.visibleSide
    val content = draft.selectSide(side)
    val erasedStrokes = content.strokes.withIndex()
        .filter { isStrokeTouched(it.value, path, radius) }
        .map { ErasedStroke(it.index, it.value) }
    val erasedShapes = content.shapes.withIndex()
        .filter { isShapeTouched(it.value, path, radius) }
        .map { ErasedShape(it.index, it.value) }
    if (erasedStrokes.isEmpty() && erasedShapes.isEmpty()) return this
    val strokeIndexes = erasedStrokes.map { it.index }.toSet()
    val shapeIndexes = erasedShapes.map { it.index }.toSet()
    return copy(
        draft = draft.replaceSide(side) {
            CardSide(
                it.strokes.filterIndexed { index, _ -> index !in strokeIndexes },
                it.shapes.filterIndexed { index, _ -> index !in shapeIndexes },
            )
        },
        history = pushStep(EditorStep.ContentErased(side, erasedStrokes, erasedShapes)),
    )
}

/** Снимает последний шаг. Если он был на другой стороне, показывается она, чтобы изменение было видно. */
fun EditorSession.undoLastStep(): EditorSession {
    val step = history.lastOrNull() ?: return this
    val restored = draft.replaceSide(step.side) { content ->
        when (step) {
            is EditorStep.StrokeAdded -> content.copy(strokes = content.strokes.dropLast(1))
            is EditorStep.ShapeAdded -> content.copy(shapes = content.shapes.dropLast(1))
            is EditorStep.ShapeChanged -> content.copy(shapes = content.shapes.replaceAt(step.index, step.before))
            // Всё возвращается по возрастанию индекса, поэтому каждое встаёт на своё прежнее место.
            is EditorStep.ContentErased -> CardSide(
                step.strokes.fold(content.strokes) { acc, erased -> acc.insertAt(erased.index, erased.stroke) },
                step.shapes.fold(content.shapes) { acc, erased -> acc.insertAt(erased.index, erased.shape) },
            )
        }
    }
    return EditorSession(draft = restored.copy(visibleSide = step.side), history = history.dropLast(1))
}

/** Переключает видимую сторону; шагом отмены это не считается. */
fun EditorSession.switchSide(side: Side): EditorSession = copy(draft = draft.copy(visibleSide = side))

private fun EditorSession.pushStep(step: EditorStep): List<EditorStep> = (history + step).takeLast(UNDO_DEPTH)

private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> = mapIndexed { i, old -> if (i == index) item else old }

private fun <T> List<T>.insertAt(index: Int, item: T): List<T> = toMutableList().apply { add(index, item) }

private fun Draft.selectSide(side: Side): CardSide = if (side == Side.FRONT) front else back

private fun Draft.replaceSide(side: Side, change: (CardSide) -> CardSide): Draft =
    if (side == Side.FRONT) copy(front = change(front)) else copy(back = change(back))
