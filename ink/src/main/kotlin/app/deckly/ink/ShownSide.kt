package app.deckly.ink

import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke

/** Что изменил новая сторона от хоста относительно показанной на листе. */
internal sealed interface SideChange {
    /** Показанное не меняется: сторона прежняя или хост лишь догнал штрихи, нарисованные листом сразу. */
    data object None : SideChange

    /** Только штрихи дописаны в конец: дорисовать их, перо включённым оставить. */
    data class Appended(val added: List<Stroke>) : SideChange

    /** Фигуры другие или штрих удалён, возвращён, сторона другая: лист перерисовать целиком. */
    data class Replaced(val before: CardSide, val after: CardSide) : SideChange
}

/**
 * Сторона карточки, показанная на листе: штрихи с отставанием хоста ([ShownStrokes]) и фигуры. Фигуры хост присылает
 * целиком и сразу, лист сам их не добавляет, поэтому у них отставания нет. Они заменяются независимо от штрихов:
 * штрихи, нарисованные листом и ещё не подтверждённые хостом, остаются, даже если в этот момент пришли новые фигуры со
 * старым списком штрихов.
 */
internal class ShownSide {
    private val strokes = ShownStrokes()

    var shapes: List<Shape> = emptyList()
        private set

    val shown: CardSide get() = CardSide(strokes.shown, shapes)

    fun appendLocal(stroke: Stroke) = strokes.appendLocal(stroke)

    fun updateFromHost(new: CardSide): SideChange {
        val before = shown
        val strokeChange = strokes.updateFromHost(new.strokes)
        val isShapesChanged = new.shapes != shapes
        shapes = new.shapes
        return when {
            isShapesChanged || strokeChange is StrokeChange.Replaced -> SideChange.Replaced(before, shown)
            strokeChange is StrokeChange.Appended -> SideChange.Appended(strokeChange.added)
            else -> SideChange.None
        }
    }
}
