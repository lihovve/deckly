package app.deckly

import app.deckly.domain.day.computeDayNumber
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayClockTest {
    @Test
    fun todayIsDayNumberOfCurrentTime() {
        val now = LocalDateTime.of(2026, 11, 4, 12, 30)

        assertEquals(computeDayNumber(now), DayClock { now }.computeToday())
    }

    @Test
    fun dayChangesAtFourInTheMorning() {
        val beforeFour = DayClock { LocalDateTime.of(2026, 11, 5, 3, 59) }.computeToday()
        val atFour = DayClock { LocalDateTime.of(2026, 11, 5, 4, 0) }.computeToday()
        val previousDayNoon = DayClock { LocalDateTime.of(2026, 11, 4, 12, 0) }.computeToday()

        assertEquals(previousDayNoon, beforeFour)
        assertEquals(previousDayNoon + 1, atFour)
    }

    @Test
    fun clockFollowsTimeSourceOnEveryCall() {
        var now = LocalDateTime.of(2026, 11, 4, 12, 0)
        val clock = DayClock { now }
        val first = clock.computeToday()

        now = now.plusDays(1)

        assertEquals(first + 1, clock.computeToday())
    }
}
