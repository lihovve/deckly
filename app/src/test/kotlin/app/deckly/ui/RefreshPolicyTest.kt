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

/** Правило полного обновления экрана (решение 4 плана 4) живёт в одном месте и проверяется без устройства. */
class RefreshPolicyTest {
    private val deck = RefreshKey(screenToken = 5, cardId = 10, editorSide = null)

    @Test
    fun screenChangeRefreshesFully() {
        val next = RefreshKey(screenToken = 6, cardId = null, editorSide = null)

        assertTrue(isFullRefreshNeeded(deck, next))
    }

    @Test
    fun returnToEqualScreenRefreshesFully() {
        // Тот же экран и та же карточка, но переход состоялся (из редактора в ту же колоду): номер перехода другой.
        assertTrue(isFullRefreshNeeded(deck, deck.copy(screenToken = 6)))
    }

    @Test
    fun firstScreenRefreshesFully() {
        assertTrue(isFullRefreshNeeded(null, deck))
    }

    @Test
    fun newCardRefreshesFully() {
        assertTrue(isFullRefreshNeeded(deck, deck.copy(cardId = 11)))
        assertTrue(isFullRefreshNeeded(deck.copy(cardId = null), deck))
        assertTrue(isFullRefreshNeeded(deck, deck.copy(cardId = null)))
    }

    @Test
    fun flipRefreshesPartially() {
        // Переворот меняет сторону карточки, но не ключ: на экране остаётся то же обновление, что делает прошивка.
        assertFalse(isFullRefreshNeeded(deck, deck.copy()))
    }

    @Test
    fun editorSideSwitchRefreshesFully() {
        val editor = RefreshKey(screenToken = 7, cardId = null, editorSide = CardSideShown.FRONT)

        assertTrue(isFullRefreshNeeded(editor, editor.copy(editorSide = CardSideShown.BACK)))
        assertTrue(isFullRefreshNeeded(editor.copy(editorSide = CardSideShown.BACK), editor))
    }

    @Test
    fun sameStateDoesNotRefresh() {
        val editor = RefreshKey(screenToken = 7, cardId = null, editorSide = CardSideShown.BACK)

        assertFalse(isFullRefreshNeeded(deck, deck))
        assertFalse(isFullRefreshNeeded(editor, editor))
    }

    @Test
    fun refresherCountsOnlyFinishedRefreshes() {
        val refresher = ScreenRefresher()

        assertTrue(refresher.isNeeded(deck))
        // Обновление не закончено (эффект отменили): ключ ещё не показан, обновление нужно снова.
        assertTrue(refresher.isNeeded(deck))
        refresher.markRefreshed(deck)
        assertFalse(refresher.isNeeded(deck))
        assertTrue(refresher.isNeeded(deck.copy(cardId = 11)))
        refresher.markRefreshed(deck.copy(cardId = 11))
        // Возврат к прежнему ключу — снова изменение, а не «так уже было».
        assertTrue(refresher.isNeeded(deck))
    }

    @Test
    fun refreshIsDoneOnceForAKeyAndSkippedAfterwards() = runTest {
        val refresher = ScreenRefresher()
        val events = mutableListOf<String>()

        repeat(2) { refreshScreenIfNeeded(refresher, deck, null, { events += "frame" }, { events += "refresh" }) }

        assertEquals(listOf("frame", "frame", "refresh"), events)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancelledRefreshIsDoneAgainForTheSameKey() = runTest(StandardTestDispatcher()) {
        val refresher = ScreenRefresher()
        val events = mutableListOf<String>()
        val frame = CompletableDeferred<Unit>()
        val job = launch { refreshScreenIfNeeded(refresher, deck, null, { frame.await() }, { events += "refresh" }) }
        runCurrent()
        job.cancel(CancellationException("экран ушёл из композиции"))
        runCurrent()

        // Тот же ключ вернулся (экран собрался заново): обновление, которое не успело, выполняется.
        refreshScreenIfNeeded(refresher, deck, null, { }, { events += "refresh" })

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
    fun cancelledRefreshStillReturnsThePen() = runTest(StandardTestDispatcher()) {
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
}
