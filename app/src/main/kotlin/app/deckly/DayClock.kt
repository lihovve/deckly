package app.deckly

import app.deckly.domain.day.computeDayNumber
import java.time.LocalDateTime

/** Часы дней: номер дня по местному времени устройства, день начинается в 4:00. [now] подменяют в тестах. */
class DayClock(private val now: () -> LocalDateTime = LocalDateTime::now) {
    fun computeToday(): Int = computeDayNumber(now())
}
