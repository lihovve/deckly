package app.deckly.domain.day

import java.time.LocalDateTime

private const val DAY_START_HOUR = 4L

/** Порядковый номер дня; новый день начинается в 4:00, чтобы занятие после полуночи шло в прошлый. */
fun computeDayNumber(dateTime: LocalDateTime): Int =
    dateTime.minusHours(DAY_START_HOUR).toLocalDate().toEpochDay().toInt()
