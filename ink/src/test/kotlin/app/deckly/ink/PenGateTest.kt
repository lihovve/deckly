package app.deckly.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenGateTest {
    private val pen = FakePen()
    private val scheduler = FakeScheduler()
    private val gate = PenGate(pen, scheduler)

    @Test
    fun resumeDelayIsOneHundredFiftyMilliseconds() {
        assertEquals(150L, PEN_RESUME_DELAY_MS)
    }

    @Test
    fun openedSessionKeepsPenOnWhenWindowIsActiveAndHostAllows() {
        gate.setWindowActive(true)

        gate.onSessionOpened()

        // Открытие сессии само включает перо: лишних вызовов нет.
        assertEquals(emptyList<String>(), pen.calls)
        assertTrue(gate.isOn)
    }

    @Test
    fun openedSessionSwitchesPenOffWhenWindowIsNotActive() {
        gate.setWindowActive(false)

        gate.onSessionOpened()

        assertEquals(listOf("disable"), pen.calls)
        assertFalse(gate.isOn)
    }

    @Test
    fun windowFocusSwitchesPen() {
        openActiveSession()

        gate.setWindowActive(false)
        gate.setWindowActive(true)

        assertEquals(listOf("disable", "enable"), pen.calls)
    }

    @Test
    fun hostSwitchesPen() {
        openActiveSession()

        gate.setHostEnabled(false)
        gate.setHostEnabled(true)

        assertEquals(listOf("disable", "enable"), pen.calls)
    }

    @Test
    fun repeatedStateDoesNotRepeatCalls() {
        openActiveSession()

        gate.setHostEnabled(false)
        gate.setHostEnabled(false)
        gate.setWindowActive(false)

        assertEquals(listOf("disable"), pen.calls)
    }

    @Test
    fun cycleSwitchesPenOffAtOnceAndOnAfterFrameAndDelay() {
        openActiveSession()

        gate.requestCycle()

        assertEquals(listOf("disable"), pen.calls)
        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS - 1)
        assertEquals(listOf("disable"), pen.calls)
        scheduler.advance(1)
        assertEquals(listOf("disable", "enable"), pen.calls)
    }

    @Test
    fun delayStartsOnlyAfterFrame() {
        openActiveSession()

        gate.requestCycle()
        scheduler.advance(10 * PEN_RESUME_DELAY_MS)

        assertEquals(listOf("disable"), pen.calls)
        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS)
        assertEquals(listOf("disable", "enable"), pen.calls)
    }

    @Test
    fun newCycleRestartsWaiting() {
        openActiveSession()
        gate.requestCycle()
        scheduler.runFrame()
        scheduler.advance(100)

        gate.requestCycle()
        scheduler.advance(60)
        assertEquals(listOf("disable"), pen.calls)
        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS)

        assertEquals(listOf("disable", "enable"), pen.calls)
    }

    @Test
    fun cycleDoesNotSwitchOnPenDisabledByHost() {
        openActiveSession()
        gate.setHostEnabled(false)

        gate.requestCycle()
        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS)

        assertEquals(listOf("disable"), pen.calls)
        assertFalse(gate.isOn)
    }

    @Test
    fun hostEnablingPenDuringCycleWaitsForCycleEnd() {
        openActiveSession()
        gate.setHostEnabled(false)
        gate.requestCycle()

        gate.setHostEnabled(true)
        assertEquals(listOf("disable"), pen.calls)
        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS)

        assertEquals(listOf("disable", "enable"), pen.calls)
    }

    @Test
    fun cycleDoesNotSwitchOnPenWithoutActiveWindow() {
        openActiveSession()
        gate.requestCycle()
        gate.setWindowActive(false)

        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS)

        assertEquals(listOf("disable"), pen.calls)
    }

    @Test
    fun endOfCycleDoesNotSwitchOnPenOfClosedSession() {
        openActiveSession()
        gate.requestCycle()

        gate.onSessionClosed()
        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS)

        assertEquals(listOf("disable"), pen.calls)
        assertFalse(gate.isOn)
    }

    @Test
    fun sessionOpenedDuringCycleKeepsPenOffUntilCycleEnds() {
        openActiveSession()
        gate.requestCycle()
        gate.onSessionClosed()

        // Открытие сессии включает перо само; цикл ещё идёт, поэтому перо тут же выключается.
        gate.onSessionOpened()
        assertEquals(listOf("disable", "disable"), pen.calls)
        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS)

        assertEquals(listOf("disable", "disable", "enable"), pen.calls)
    }

    @Test
    fun cycleRequestedAfterSessionClosedStillHoldsNewSession() {
        openActiveSession()
        gate.onSessionClosed()
        gate.requestCycle()

        gate.onSessionOpened()
        assertEquals(listOf("disable"), pen.calls)
        scheduler.runFrame()
        scheduler.advance(PEN_RESUME_DELAY_MS)

        assertEquals(listOf("disable", "enable"), pen.calls)
    }

    private fun openActiveSession() {
        gate.setWindowActive(true)
        gate.onSessionOpened()
        pen.calls.clear()
    }

    private class FakePen : PenPort {
        val calls = mutableListOf<String>()

        override fun enable() {
            calls += "enable"
        }

        override fun disable() {
            calls += "disable"
        }
    }

    private class FakeScheduler : PenScheduler {
        private val frameActions = ArrayDeque<() -> Unit>()
        private val delayed = mutableListOf<Pair<Long, () -> Unit>>()
        private var now = 0L

        override fun afterNextFrame(action: () -> Unit) {
            frameActions.addLast(action)
        }

        override fun afterDelay(delayMs: Long, action: () -> Unit) {
            delayed += (now + delayMs) to action
        }

        fun runFrame() {
            val actions = frameActions.toList()
            frameActions.clear()
            actions.forEach { it() }
        }

        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = delayed.filter { it.first <= target }.minByOrNull { it.first } ?: break
                delayed.remove(next)
                now = next.first
                next.second()
            }
            now = target
        }
    }
}
