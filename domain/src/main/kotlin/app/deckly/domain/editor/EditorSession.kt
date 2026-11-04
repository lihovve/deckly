package app.deckly.domain.editor

import app.deckly.domain.model.Card
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint

/** Сколько последних шагов помнит отмена (ТЗ требует не меньше 20). */
const val UNDO_DEPTH = 100

/** Штрих, снятый ластиком, и место, где он стоял на своей стороне. */
data class ErasedStroke(val index: Int, val stroke: Stroke)

/** Шаг, который снимает отмена: на какой стороне он был и что изменил. */
sealed interface EditorStep {
    val side: Side

    data class StrokeAdded(override val side: Side) : EditorStep

    data class StrokesErased(override val side: Side, val erased: List<ErasedStroke>) : EditorStep
}

/** Черновик и история отмены. История хранится только в памяти и перезапуск не переживает. */
data class EditorSession(val draft: Draft, val history: List<EditorStep>)

fun startNewCard(returnSection: Section): EditorSession =
    EditorSession(Draft(null, emptyList(), emptyList(), Side.FRONT, returnSection), emptyList())

/** Правка карточки пула: черновик начинается с её штрихов. */
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
        draft = draft.replaceStrokes(side, draft.listStrokes(side) + stroke),
        history = pushStep(EditorStep.StrokeAdded(side)),
    )
}

/** Удаляет целиком все штрихи видимой стороны, которых коснулся ластик; если не задел ни одного, шага нет. */
fun EditorSession.eraseAlong(path: List<StrokePoint>, radius: Float): EditorSession {
    val side = draft.visibleSide
    val strokes = draft.listStrokes(side)
    val erased = strokes.withIndex()
        .filter { isStrokeTouched(it.value, path, radius) }
        .map { ErasedStroke(it.index, it.value) }
    if (erased.isEmpty()) return this
    val erasedIndexes = erased.map { it.index }.toSet()
    val remaining = strokes.filterIndexed { index, _ -> index !in erasedIndexes }
    return copy(
        draft = draft.replaceStrokes(side, remaining),
        history = pushStep(EditorStep.StrokesErased(side, erased)),
    )
}

/** Снимает последний шаг. Если он был на другой стороне, показывается она, чтобы изменение было видно. */
fun EditorSession.undoLastStep(): EditorSession {
    val step = history.lastOrNull() ?: return this
    val strokes = draft.listStrokes(step.side)
    val restored = when (step) {
        is EditorStep.StrokeAdded -> strokes.dropLast(1)
        // Штрихи возвращаются по возрастанию индекса, поэтому каждый встаёт на своё прежнее место.
        is EditorStep.StrokesErased -> step.erased.fold(strokes) { acc, erased ->
            acc.toMutableList().apply { add(erased.index, erased.stroke) }
        }
    }
    return EditorSession(
        draft = draft.replaceStrokes(step.side, restored).copy(visibleSide = step.side),
        history = history.dropLast(1),
    )
}

/** Переключает видимую сторону; шагом отмены это не считается. */
fun EditorSession.switchSide(side: Side): EditorSession = copy(draft = draft.copy(visibleSide = side))

private fun EditorSession.pushStep(step: EditorStep): List<EditorStep> = (history + step).takeLast(UNDO_DEPTH)

private fun Draft.listStrokes(side: Side): List<Stroke> = if (side == Side.FRONT) front else back

private fun Draft.replaceStrokes(side: Side, strokes: List<Stroke>): Draft =
    if (side == Side.FRONT) copy(front = strokes) else copy(back = strokes)
