package app.deckly.ui

import app.deckly.ui.contract.CardSideShown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило полного обновления экрана (решение владельца 5 ноября: первый экран после запуска и затем каждая 30-я
 * смена) живёт в одном месте и проверяется без устройства.
 */
class RefreshPolicyTest {
    private val deck = RefreshKey(screenToken = 5, cardId = 10, editorSide = null)

    // Ключи, каждый из которых отличается от соседних: смена экрана с номером [change].
    private fun keyAt(change: Int) = RefreshKey(screenToken = change.toLong(), cardId = null, editorSide = null)

    // Первый экран после запуска обновлён, счётчик смен обнулён.
    private fun buildRefresherAfterFirstRefresh(): ScreenRefresher {
        val refresher = ScreenRefresher()
        assertTrue(refresher.isRefreshDue(keyAt(1)))
        refresher.markRefreshed()
        return refresher
    }

    @Test
    fun firstKeyIsDue() {
        assertTrue(ScreenRefresher().isRefreshDue(deck))
    }

    @Test
    fun thirtiethChangeIsDueAndNotEarlier() {
        val refresher = buildRefresherAfterFirstRefresh()

        for (change in 2..30) assertFalse("смена ${change - 1} после обновления", refresher.isRefreshDue(keyAt(change)))
        assertTrue("30-я смена после обновления", refresher.isRefreshDue(keyAt(31)))
        refresher.markRefreshed()

        // Период повторяется: снова 29 смен без обновления и 30-я с ним.
        for (change in 32..60) assertFalse("смена ${change - 31} второго круга", refresher.isRefreshDue(keyAt(change)))
        assertTrue(refresher.isRefreshDue(keyAt(61)))
    }

    @Test
    fun sameKeyIsNotCountedTwice() {
        val refresher = buildRefresherAfterFirstRefresh()

        // Эффект перезапускается, пока экран остаётся тем же: сколько бы раз ни спросили, это одна смена.
        repeat(100) { assertFalse(refresher.isRefreshDue(keyAt(2))) }
        for (change in 3..30) assertFalse("смена ${change - 1} после обновления", refresher.isRefreshDue(keyAt(change)))

        assertTrue(refresher.isRefreshDue(keyAt(31)))
    }

    @Test
    fun screenCardAndEditorSideChangesAreCountedTogether() {
        val refresher = buildRefresherAfterFirstRefresh()
        // Смена экрана, смена карточки и смена стороны в редакторе чередуются; соседние ключи всегда разные.
        val keys = (1..30).map { number ->
            when (number % 3) {
                0 -> RefreshKey(screenToken = 100L + number, cardId = null, editorSide = null)
                1 -> RefreshKey(screenToken = 1, cardId = number.toLong(), editorSide = null)
                else -> RefreshKey(screenToken = 2, cardId = null, editorSide = if (number % 2 == 0) CardSideShown.FRONT else CardSideShown.BACK)
            }
        }

        for (key in keys.take(29)) assertFalse("$key", refresher.isRefreshDue(key))
        assertTrue(refresher.isRefreshDue(keys.last()))
    }

    @Test
    fun returnToEarlierKeyIsAnotherChange() {
        val refresher = buildRefresherAfterFirstRefresh()
        val first = keyAt(2)
        val second = keyAt(3)

        // Туда и обратно: каждый показ ключа после другого — смена, а не «так уже было».
        for (change in 1..29) assertFalse("смена $change", refresher.isRefreshDue(if (change % 2 == 1) first else second))

        assertTrue(refresher.isRefreshDue(second))
    }

    @Test
    fun cancelledRefreshStaysDue() {
        val refresher = ScreenRefresher()
        assertTrue(refresher.isRefreshDue(deck))

        // Обновление не закончено (эффект отменили, markRefreshed не вызван): на том же ключе оно должно снова…
        assertTrue(refresher.isRefreshDue(deck))
        // …и на следующем ключе тоже, пока какое-то обновление не дойдёт до конца.
        assertTrue(refresher.isRefreshDue(deck.copy(cardId = 11)))
        refresher.markRefreshed()
        assertFalse(refresher.isRefreshDue(deck.copy(cardId = 11)))
        assertFalse(refresher.isRefreshDue(deck.copy(cardId = 12)))
    }

    @Test
    fun refreshIsDoneOnceForAKeyAndSkippedAfterwards() = runTest {
        val refresher = ScreenRefresher()
        val events = mutableListOf<String>()

        repeat(2) { refreshScreenIfDue(refresher, deck, null, { events += "frame" }, { events += "refresh" }) }

        assertEquals(listOf("frame", "frame", "refresh"), events)
    }

    @Test
    fun penIsNotPausedWithoutRefresh() = runTest {
        val refresher = buildRefresherAfterFirstRefresh()
        val events = mutableListOf<String>()

        // Недолжная смена: ни кадров, ни паузы пера, ни обновления.
        refreshScreenIfDue(refresher, keyAt(2), { events += "pen paused=$it" }, { events += "frame" }, { events += "refresh" })

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun dueRefreshPausesPenAndStartsNewPeriod() = runTest {
        val refresher = buildRefresherAfterFirstRefresh()
        for (change in 2..30) refresher.isRefreshDue(keyAt(change))
        val events = mutableListOf<String>()

        refreshScreenIfDue(refresher, keyAt(31), { events += "pen paused=$it" }, { events += "frame" }, { events += "refresh" })

        assertEquals(listOf("pen paused=true", "frame", "frame", "refresh", "pen paused=false"), events)
        assertFalse(refresher.isRefreshDue(keyAt(32)))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancelledRefreshIsDoneAgainForTheSameKey() = runTest(StandardTestDispatcher()) {
        val refresher = ScreenRefresher()
        val events = mutableListOf<String>()
        val frame = CompletableDeferred<Unit>()
        val job = launch { refreshScreenIfDue(refresher, deck, null, { frame.await() }, { events += "refresh" }) }
        runCurrent()
        job.cancel(CancellationException("экран ушёл из композиции"))
        runCurrent()

        // Тот же ключ вернулся (экран собрался заново): обновление, которое не успело, выполняется.
        refreshScreenIfDue(refresher, deck, null, { }, { events += "refresh" })

        assertEquals(listOf("refresh"), events)
    }

    @Test
    fun fullRefreshWaitsTwoFramesAndPausesPenAroundIt() = runTest {
        val events = mutableListOf<String>()

        performFullRefresh(
            onPenPause = { events += "pen paused=$it" },
            awaitFrame = { events += "frame" },
            refresh = { events += "refresh" },
        )

        assertEquals(listOf("pen paused=true", "frame", "frame", "refresh", "pen paused=false"), events)
    }

    @Test
    fun fullRefreshWithoutPenPauseOnlyWaitsFramesAndRefreshes() = runTest {
        val events = mutableListOf<String>()

        performFullRefresh(onPenPause = null, awaitFrame = { events += "frame" }, refresh = { events += "refresh" })

        assertEquals(listOf("frame", "frame", "refresh"), events)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun penReturnsAfterCancelledRefresh() = runTest(StandardTestDispatcher()) {
        val events = mutableListOf<String>()
        val frame = CompletableDeferred<Unit>()
        val job = launch {
            performFullRefresh(
                onPenPause = { events += "pen paused=$it" },
                awaitFrame = { frame.await() },
                refresh = { events += "refresh" },
            )
        }
        runCurrent()

        job.cancel(CancellationException("новый ключ"))
        runCurrent()

        // Эффект перезапустился на полпути: перо не остаётся выключенным, обновление не выполнено.
        assertEquals(listOf("pen paused=true", "pen paused=false"), events)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun penReturnsAfterCancelledDueRefreshAndRefreshStaysDue() = runTest(StandardTestDispatcher()) {
        val refresher = buildRefresherAfterFirstRefresh()
        for (change in 2..30) refresher.isRefreshDue(keyAt(change))
        val events = mutableListOf<String>()
        val frame = CompletableDeferred<Unit>()
        val job = launch {
            refreshScreenIfDue(refresher, keyAt(31), { events += "pen paused=$it" }, { frame.await() }, { events += "refresh" })
        }
        runCurrent()

        job.cancel(CancellationException("новый ключ"))
        runCurrent()

        // Должное обновление отменено на полпути: перо вернулось, обновление не сделано и остаётся должным.
        assertEquals(listOf("pen paused=true", "pen paused=false"), events)
        assertTrue(refresher.isRefreshDue(keyAt(31)))
        assertTrue(refresher.isRefreshDue(keyAt(32)))
    }
}
