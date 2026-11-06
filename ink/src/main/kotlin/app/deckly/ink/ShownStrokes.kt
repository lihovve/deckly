package app.deckly.ink

import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint

/** Что изменил новый список штрихов хоста относительно того, что уже показано на листе. */
internal sealed interface StrokeChange {
    /** Показанное не меняется: список прежний, хост лишь догнал штрихи листа или заменил штрихи теми же отрезками. */
    data object None : StrokeChange

    /** Штрихи дописаны в конец: на листе дорисовать только их, перо включённым оставить. */
    data class Appended(val added: List<Stroke>) : StrokeChange

    /** Штрих удалён, возвращён, сторона другая: перерисовать изменившееся место и показать перерисованное. */
    data class Replaced(val before: List<Stroke>, val after: List<Stroke>) : StrokeChange
}

/**
 * Штрихи, показанные на листе. Список приходит от хоста с задержкой (штрих ещё записывается), поэтому
 * лист добавляет только что нарисованный штрих к показанным сам ([appendLocal]), не дожидаясь хоста.
 * Иначе штрих пропал бы с поверхности на время записи и появился снова.
 *
 * Лист помнит, какие штрихи он добавил сам и хост ещё не подтвердил. Пока почерк быстрее записи, хост
 * присылает список без последних штрихов листа: это отставание ([StrokeChange.None]), а не удаление. Так же
 * выглядит прежний список хоста, который перекомпозиция по другой причине приносит повторно.
 * Заменой такой список становится, только если в недостающем хвосте есть штрих, который хост уже
 * подтверждал: его убрали по-настоящему.
 */
internal class ShownStrokes {
    var shown: List<Stroke> = emptyList()
        private set

    // Штрихи листа, которых в списках хоста ещё не было. Показанное — это список хоста и эти штрихи в конце.
    private var pending: List<Stroke> = emptyList()

    fun appendLocal(stroke: Stroke) {
        shown = shown + stroke
        pending = pending + stroke
    }

    fun updateFromHost(new: List<Stroke>): StrokeChange {
        if (isLaggingBehindLocalStrokes(new)) {
            pending = pending.filter { it !in new }
            return StrokeChange.None
        }
        // Дальше показанное становится списком хоста, ждать от хоста больше нечего.
        pending = emptyList()
        if (new == shown) return StrokeChange.None

        val before = shown
        shown = new
        val isAppend = new.size > before.size && new.subList(0, before.size) == before
        return when {
            isAppend -> StrokeChange.Appended(new.subList(before.size, new.size))
            // Те же отрезки: картинка прежняя, а список теперь хостовый, и дальше хост дописывает именно к нему.
            listSegments(before) == listSegments(new) -> StrokeChange.None
            else -> StrokeChange.Replaced(before, new)
        }
    }

    // Штрих рисуется цепочкой независимых отрезков ([drawStrokes]), поэтому два списка с одними и теми же отрезками оставляют
    // на листе одно и то же, как бы они ни были разрезаны на штрихи и в каком бы порядке ни шли. Отрезок — ширина штриха и
    // пара соседних точек с нажимом (по ним считается толщина отрезка), штрих из одной точки — своя запись, круг. Так цепочка
    // кусков из двух точек и штрих с теми же точками не меняют картинку. Точки в обратном порядке, другая ширина или другой
    // нажим — уже другие отрезки, это замена.
    private fun listSegments(strokes: List<Stroke>): Set<StrokeSegment> =
        strokes.flatMap { stroke ->
            if (stroke.points.size == 1) {
                listOf(StrokeSegment(stroke.width, stroke.points.single(), null))
            } else {
                stroke.points.zipWithNext { from, to -> StrokeSegment(stroke.width, from, to) }
            }
        }.toSet()

    // Список хоста — начало показанного, а недостающий хвост целиком из штрихов, которые хост не видел.
    private fun isLaggingBehindLocalStrokes(new: List<Stroke>): Boolean =
        new.size < shown.size &&
            shown.subList(0, new.size) == new &&
            shown.subList(new.size, shown.size).all { it in pending }
}

/** Что штрих оставляет на листе: отрезок между двумя точками ([end] == `null` — штрих из одной точки, круг). */
private data class StrokeSegment(val width: Float, val start: StrokePoint, val end: StrokePoint?)
