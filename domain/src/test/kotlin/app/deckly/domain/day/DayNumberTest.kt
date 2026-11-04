package app.deckly.domain.day

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DayNumberTest {

    @Test
    fun dayStartsAtFourInTheMorning() {
        val previousNoon = computeDayNumber(LocalDateTime.of(2026, 11, 3, 12, 0))
        val justBeforeFour = computeDayNumber(LocalDateTime.of(2026, 11, 4, 3, 59))
        val atFour = computeDayNumber(LocalDateTime.of(2026, 11, 4, 4, 0))

        assertEquals(previousNoon, justBeforeFour)
        assertEquals(previousNoon + 1, atFour)
    }
}
