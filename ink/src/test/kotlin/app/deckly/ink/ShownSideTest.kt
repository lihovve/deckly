package app.deckly.ink

import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test

/** Сторона на листе: штрихи с отставанием хоста ([ShownStrokes]) и фигуры, которые хост присылает целиком. */
class ShownSideTest {
    private val first = stroke(10f)
    private val second = stroke(20f)
    private val star = Shape(Shape.Kind.STAR, 100f, 100f, 300f, 300f, 0f, 3f)
    private val oval = Shape(Shape.Kind.OVAL, 400f, 100f, 700f, 300f, 0f, 3f)

    @Test
    fun sameSideChangesNothing() {
        val book = ShownSide()
        book.updateFromHost(CardSide(listOf(first), listOf(star)))

        assertEquals(SideChange.None, book.updateFromHost(CardSide(listOf(first), listOf(star))))
        assertEquals(SideChange.None, book.updateFromHost(CardSide(listOf(first.copy()), listOf(star.copy()))))
    }

    @Test
    fun appendedStrokesAreAppendedWhenShapesStay() {
        val book = ShownSide()
        book.updateFromHost(CardSide(listOf(first), listOf(star)))

        val change = book.updateFromHost(CardSide(listOf(first, second), listOf(star)))

        assertEquals(SideChange.Appended(listOf(second)), change)
    }

    @Test
    fun newShapesAreAReplacementOfTheSheet() {
        val book = ShownSide()
        book.updateFromHost(CardSide(listOf(first)))

        val change = book.updateFromHost(CardSide(listOf(first), listOf(star)))

        assertEquals(SideChange.Replaced(CardSide(listOf(first)), CardSide(listOf(first), listOf(star))), change)
        assertEquals(CardSide(listOf(first), listOf(star)), book.shown)
    }

    @Test
    fun movedShapeIsAReplacementWithBothViews() {
        val book = ShownSide()
        book.updateFromHost(CardSide(emptyList(), listOf(star, oval)))
        val moved = star.copy(left = 150f, right = 350f)

        val change = book.updateFromHost(CardSide(emptyList(), listOf(moved, oval)))

        assertEquals(SideChange.Replaced(CardSide(emptyList(), listOf(star, oval)), CardSide(emptyList(), listOf(moved, oval))), change)
    }

    @Test
    fun removedShapeIsAReplacement() {
        val book = ShownSide()
        book.updateFromHost(CardSide(emptyList(), listOf(star, oval)))

        val change = book.updateFromHost(CardSide(emptyList(), listOf(oval)))

        assertEquals(SideChange.Replaced(CardSide(emptyList(), listOf(star, oval)), CardSide(emptyList(), listOf(oval))), change)
    }

    @Test
    fun replacedStrokesAreAReplacementToo() {
        val book = ShownSide()
        book.updateFromHost(CardSide(listOf(first, second), listOf(star)))

        val change = book.updateFromHost(CardSide(listOf(second), listOf(star)))

        assertEquals(SideChange.Replaced(CardSide(listOf(first, second), listOf(star)), CardSide(listOf(second), listOf(star))), change)
    }

    @Test
    fun pendingLocalStrokeSurvivesNewShapesFromHost() {
        // Штрих нарисован листом, хост его ещё не записал; в этот момент пришли новые фигуры со старым списком штрихов.
        val book = ShownSide()
        book.updateFromHost(CardSide(listOf(first)))
        book.appendLocal(second)

        val change = book.updateFromHost(CardSide(listOf(first), listOf(star)))

        assertEquals(CardSide(listOf(first, second), listOf(star)), book.shown)
        assertEquals(
            SideChange.Replaced(CardSide(listOf(first, second)), CardSide(listOf(first, second), listOf(star))),
            change,
        )
    }

    @Test
    fun hostConfirmingTheLocalStrokeAfterShapesChangesNothing() {
        val book = ShownSide()
        book.updateFromHost(CardSide(listOf(first)))
        book.appendLocal(second)
        book.updateFromHost(CardSide(listOf(first), listOf(star)))

        val change = book.updateFromHost(CardSide(listOf(first, second), listOf(star)))

        assertEquals(SideChange.None, change)
        assertEquals(CardSide(listOf(first, second), listOf(star)), book.shown)
    }

    @Test
    fun staleHostSideWithSameShapesDoesNotRemoveLocalStroke() {
        val book = ShownSide()
        book.updateFromHost(CardSide(listOf(first), listOf(star)))
        book.appendLocal(second)

        val change = book.updateFromHost(CardSide(listOf(first), listOf(star)))

        assertEquals(SideChange.None, change)
        assertEquals(listOf(first, second), book.shown.strokes)
    }

    @Test
    fun localStrokeWithShapesRemovedByHostStaysUntilConfirmedOrReplaced() {
        val book = ShownSide()
        book.updateFromHost(CardSide(listOf(first), listOf(star)))
        book.appendLocal(second)

        book.updateFromHost(CardSide(listOf(first), emptyList()))

        assertEquals(CardSide(listOf(first, second), emptyList()), book.shown)
    }

    private fun stroke(x: Float) = Stroke(6f, listOf(StrokePoint(x, x, 1f)))
}
