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

    // Быстрый почерк: запись черновика идёт дольше, чем рисуется следующий штрих, и хост присылает список,
    // в котором последних штрихов листа ещё нет. Это отставание, а не удаление.
    @Test
    fun hostLaggingBehindLocalStrokesChangesNothing() {
        val book = ShownStrokes()
        book.appendLocal(first)
        book.appendLocal(second)

        assertEquals(StrokeChange.None, book.updateFromHost(listOf(first)))
        assertEquals(listOf(first, second), book.shown)
        assertEquals(StrokeChange.None, book.updateFromHost(listOf(first, second)))
        assertEquals(listOf(first, second), book.shown)
    }

    @Test
    fun hostCatchingUpStepByStepChangesNothing() {
        val book = ShownStrokes()
        book.appendLocal(first)
        book.appendLocal(second)
        book.appendLocal(third)

        assertEquals(StrokeChange.None, book.updateFromHost(listOf(first)))
        assertEquals(StrokeChange.None, book.updateFromHost(listOf(first, second)))
        assertEquals(StrokeChange.None, book.updateFromHost(listOf(first, second, third)))
        assertEquals(listOf(first, second, third), book.shown)
    }

    @Test
    fun realRemovalDuringPendingStillReplaces() {
        // Хост подтвердил A, потом пришла отмена A, а B ещё записывается: A удалён по-настоящему.
        val book = ShownStrokes()
        book.updateFromHost(listOf(first))
        book.appendLocal(second)

        val change = book.updateFromHost(emptyList())

        assertEquals(StrokeChange.Replaced(listOf(first, second), emptyList<Stroke>()), change)
        assertEquals(emptyList<Stroke>(), book.shown)
    }

    @Test
    fun undoAfterHostConfirmedStrokeStillReplaces() {
        val book = ShownStrokes()
        book.appendLocal(first)
        book.appendLocal(second)
        book.updateFromHost(listOf(first, second))

        // Подтверждённый штрих больше не ждёт хоста: его отмена — замена.
        val change = book.updateFromHost(listOf(first))

        assertEquals(StrokeChange.Replaced(listOf(first, second), listOf(first)), change)
    }

    @Test
    fun removalOfConfirmedStrokeDuringLagStillReplaces() {
        // Хост подтвердил A и ещё не видел B и C; потом A убрали. Подтверждённый A ждать хоста не должен.
        val book = ShownStrokes()
        book.appendLocal(first)
        book.appendLocal(second)
        book.appendLocal(third)
        book.updateFromHost(listOf(first))

        val change = book.updateFromHost(emptyList())

        assertEquals(StrokeChange.Replaced(listOf(first, second, third), emptyList<Stroke>()), change)
    }

    @Test
    fun strokesDroppedByReplacementDoNotMakeRemovalLookLikeLag() {
        val book = ShownStrokes()
        book.appendLocal(first)
        book.appendLocal(second)
        book.updateFromHost(listOf(second))

        // Показано [second], и он подтверждён хостом: его отмена — замена, а не отставание.
        val change = book.updateFromHost(emptyList())

        assertEquals(StrokeChange.Replaced(listOf(second), emptyList<Stroke>()), change)
    }

    @Test
    fun hostListThatIsNotPrefixOfLocalStrokesReplaces() {
        val book = ShownStrokes()
        book.appendLocal(first)
        book.appendLocal(second)

        val change = book.updateFromHost(listOf(third))

        assertEquals(StrokeChange.Replaced(listOf(first, second), listOf(third)), change)
    }

    @Test
    fun hostAppendBeyondPendingStrokesAfterLagIsAppended() {
        val book = ShownStrokes()
        book.appendLocal(first)
        book.appendLocal(second)
        book.updateFromHost(listOf(first))

        val change = book.updateFromHost(listOf(first, second, third))

        assertEquals(StrokeChange.Appended(listOf(third)), change)
    }

    // --- замена, после которой на листе та же картинка ------------------------------------------------

    // Штрих рисуется цепочкой независимых отрезков (толщина отрезка по двум его точкам), поэтому цепочка кусков из двух
    // точек и штрих с теми же точками оставляют на листе одно и то же: замена, которую не нужно ни рисовать, ни показывать
    // циклом пера. Отрезок — это ширина штриха и пара соседних точек с нажимом, штрих из одной точки — своя запись (круг).
    private val a = StrokePoint(10f, 10f, 0.4f)
    private val b = StrokePoint(60f, 30f, 0.8f)
    private val c = StrokePoint(110f, 20f, 0.2f)
    private val d = StrokePoint(160f, 90f, 1f)
    private val pieces = listOf(Stroke(6f, listOf(a, b)), Stroke(6f, listOf(b, c)), Stroke(6f, listOf(c, d)))
    private val whole = Stroke(6f, listOf(a, b, c, d))

    @Test
    fun chainOfPiecesReplacedByTheWholeStrokeChangesNothingAndIsRemembered() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(first) + pieces)

        val change = book.updateFromHost(listOf(first, whole))

        assertEquals(StrokeChange.None, change)
        assertEquals(listOf(first, whole), book.shown)
        assertEquals("дальше хост дописывает к новому списку", StrokeChange.Appended(listOf(second)), book.updateFromHost(listOf(first, whole, second)))
    }

    @Test
    fun strokeCutIntoPiecesAndTheOtherWayRoundLeavesTheSameImage() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(whole))

        assertEquals(StrokeChange.None, book.updateFromHost(pieces))
        assertEquals(StrokeChange.None, book.updateFromHost(listOf(whole)))
    }

    @Test
    fun theSameStrokesInAnotherOrderLeaveTheSameImage() {
        val book = ShownStrokes()
        book.updateFromHost(pieces)

        assertEquals(StrokeChange.None, book.updateFromHost(pieces.reversed()))
        assertEquals(pieces.reversed(), book.shown)
    }

    @Test
    fun theSamePointsWithAnotherWidthAreAReplacement() {
        val book = ShownStrokes()
        book.updateFromHost(pieces)

        assertEquals(StrokeChange.Replaced(pieces, listOf(whole.copy(width = 9f))), book.updateFromHost(listOf(whole.copy(width = 9f))))
    }

    @Test
    fun theSamePointsInTheOppositeOrderAreAReplacement() {
        val book = ShownStrokes()
        book.updateFromHost(pieces)
        val reversed = Stroke(6f, listOf(d, c, b, a))

        assertEquals(StrokeChange.Replaced(pieces, listOf(reversed)), book.updateFromHost(listOf(reversed)))
    }

    @Test
    fun anotherPressureOfAPointIsAReplacement() {
        val book = ShownStrokes()
        book.updateFromHost(pieces)
        val other = Stroke(6f, listOf(a, b.copy(pressure = 0.5f), c, d))

        assertEquals(StrokeChange.Replaced(pieces, listOf(other)), book.updateFromHost(listOf(other)))
    }

    @Test
    fun aMissingPieceIsAReplacement() {
        val book = ShownStrokes()
        book.updateFromHost(pieces)
        val shorter = Stroke(6f, listOf(a, b, c))

        assertEquals(StrokeChange.Replaced(pieces, listOf(shorter)), book.updateFromHost(listOf(shorter)))
    }

    @Test
    fun aDotIsNotASegmentOfAStroke() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(Stroke(6f, listOf(a))))
        val segment = Stroke(6f, listOf(a, b))

        assertEquals(StrokeChange.Replaced(listOf(Stroke(6f, listOf(a))), listOf(segment)), book.updateFromHost(listOf(segment)))
        val dots = listOf(Stroke(6f, listOf(a)), Stroke(6f, listOf(b)))
        assertEquals(StrokeChange.Replaced(listOf(segment), dots), book.updateFromHost(dots))
    }

    @Test
    fun aDotOfAnotherWidthIsAReplacement() {
        val book = ShownStrokes()
        book.updateFromHost(listOf(Stroke(6f, listOf(a))))
        val wider = Stroke(9f, listOf(a))

        assertEquals(StrokeChange.Replaced(listOf(Stroke(6f, listOf(a))), listOf(wider)), book.updateFromHost(listOf(wider)))
    }

    private fun stroke(x: Float) = Stroke(6f, listOf(StrokePoint(x, x, 1f)))
}
