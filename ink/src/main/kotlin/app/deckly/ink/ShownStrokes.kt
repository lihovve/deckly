package app.deckly.ink

import app.deckly.domain.model.Stroke

/** Что изменил новый список штрихов хоста относительно того, что уже показано на листе. */
internal sealed interface StrokeChange {
    /** Показанное не меняется: список прежний или хост лишь догнал штрихи, нарисованные листом сразу. */
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
        return if (isAppend) {
            StrokeChange.Appended(new.subList(before.size, new.size))
        } else {
            StrokeChange.Replaced(before, new)
        }
    }

    // Список хоста — начало показанного, а недостающий хвост целиком из штрихов, которые хост не видел.
    private fun isLaggingBehindLocalStrokes(new: List<Stroke>): Boolean =
        new.size < shown.size &&
            shown.subList(0, new.size) == new &&
            shown.subList(new.size, shown.size).all { it in pending }
}
