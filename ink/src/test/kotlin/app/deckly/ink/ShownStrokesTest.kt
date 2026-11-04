package app.deckly.ink

import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ShownStrokesTest {
    private val first = stroke(10f)
    private val second = stroke(20f)
    private val third = stroke(30f)

    @Test
    fun firstHostListIsAppended() {
        val book = ShownStrokes()

        val change = book.updateFromHost(listOf(first))

        assertEquals(StrokeChange.Appended(listOf(first)), change)
        assertEquals(listOf(first), book.shown)
    }

    @Test
    fun sameHostListChangesNothing() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(first))

        assertEquals(StrokeChange.None, book.updateFromHost(listOf(first)))
        assertEquals(StrokeChange.None, book.updateFromHost(listOf(first.copy())))
    }

    @Test
    fun localStrokeIsShownBeforeHostSendsIt() {
        val book = ShownStrokes()

        book.appendLocal(first)

        assertEquals(listOf(first), book.shown)
    }

    @Test
    fun hostCatchingUpWithLocalStrokeChangesNothing() {
        val book = ShownStrokes()
        book.appendLocal(first)

        val change = book.updateFromHost(listOf(first))

        assertEquals(StrokeChange.None, change)
        assertEquals(listOf(first), book.shown)
    }

    @Test
    fun staleHostListDoesNotRemoveLocalStroke() {
        // Перекомпозиция по другой причине приносит прежний список хоста, в котором нового штриха ещё нет.
        val book = ShownStrokes()
        book.updateFromHost(listOf(first))
        book.appendLocal(second)

        val change = book.updateFromHost(listOf(first))

        assertEquals(StrokeChange.None, change)
        assertEquals(listOf(first, second), book.shown)
    }

    @Test
    fun hostAppendBeyondLocalStrokesIsAppended() {
        val book = ShownStrokes()
        book.appendLocal(first)

        val change = book.updateFromHost(listOf(first, second))

        assertEquals(StrokeChange.Appended(listOf(second)), change)
        assertEquals(listOf(first, second), book.shown)
    }

    @Test
    fun removalIsReplacement() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(first, second))

        val change = book.updateFromHost(listOf(first))

        assertEquals(StrokeChange.Replaced(listOf(first, second), listOf(first)), change)
        assertEquals(listOf(first), book.shown)
    }

    @Test
    fun otherSideIsReplacement() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(first, second))

        val change = book.updateFromHost(listOf(third))

        assertEquals(StrokeChange.Replaced(listOf(first, second), listOf(third)), change)
    }

    @Test
    fun longerListWithOtherStrokesIsReplacement() {
        // Другая сторона может оказаться длиннее прежней: дописанным такой список считать нельзя.
        val book = ShownStrokes()
        book.updateFromHost(listOf(first))

        val change = book.updateFromHost(listOf(second, third))

        assertEquals(StrokeChange.Replaced(listOf(first), listOf(second, third)), change)
    }

    @Test
    fun clearedListIsReplacement() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(first))

        val change = book.updateFromHost(emptyList())

        assertEquals(StrokeChange.Replaced(listOf(first), emptyList<Stroke>()), change)
        assertEquals(emptyList<Stroke>(), book.shown)
    }

    @Test
    fun hostListAfterReplacementIsAppendedToNewBase() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(first, second))
        book.updateFromHost(listOf(first))

        val change = book.updateFromHost(listOf(first, third))

        assertEquals(StrokeChange.Appended(listOf(third)), change)
    }

    private fun stroke(x: Float) = Stroke(6f, listOf(StrokePoint(x, x, 1f)))
}
