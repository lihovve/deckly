package app.deckly.ink

import app.deckly.domain.model.Stroke

/** Что изменил новый список штрихов хоста относительно того, что уже показано на листе. */
internal sealed interface StrokeChange {
    /** Показанное не меняется: список прежний или хост лишь догнал штрихи, нарисованные листом сразу. */
    data object None : StrokeChange

    /** Штрихи дописаны в конец: на листе дорисовать только их, перо включённым оставить. */
    data class Appended(val added: List<Stroke>) : StrokeChange

    /** Штрих удалён, возвращён, сторона другая: лист перерисовать целиком и показать перерисованное. */
    data class Replaced(val before: List<Stroke>, val after: List<Stroke>) : StrokeChange
}

/**
 * Штрихи, показанные на листе. Список приходит от хоста с задержкой (штрих ещё записывается), поэтому
 * лист добавляет только что нарисованный штрих к показанным сам ([appendLocal]), не дожидаясь хоста.
 * Иначе штрих пропал бы с поверхности на время записи и появился снова.
 *
 * Значение хоста, которое не менялось, показанное не трогает: перекомпозиция по другой причине приносит
 * прежний список, в котором нового штриха ещё нет.
 */
internal class ShownStrokes {
    var shown: List<Stroke> = emptyList()
        private set

    private var host: List<Stroke> = emptyList()

    fun appendLocal(stroke: Stroke) {
        shown = shown + stroke
    }

    fun updateFromHost(new: List<Stroke>): StrokeChange {
        if (new == host) return StrokeChange.None
        host = new
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
}
