package app.deckly.startup

import app.deckly.data.timing.DecklyTiming
import app.deckly.navigation.AppScreen

/**
 * Строка о первом экране для проверки запуска на планшете: какой экран выбран и сколько прошло от старта процесса.
 * Идёт через [DecklyTiming]: пока лог выключен, строка не собирается. В строке только тип экрана: ни номеров колод и
 * карточек, ни имён.
 */
object StartTrace {
    /** Пишет строку в лог, если он включён; зовётся, когда загрузка запуска показала экран. */
    fun logFirstScreen(screen: AppScreen) {
        DecklyTiming.logInfo { describeFirstScreen(screen, DecklyTiming.measureProcessAgeMs(System.nanoTime())) }
    }

    fun describeFirstScreen(screen: AppScreen, sinceProcessStartMs: Double): String =
        "start screen=${describeKind(screen)} sinceProcessStart=${DecklyTiming.describeMillis(sinceProcessStartMs)}ms"

    private fun describeKind(screen: AppScreen): String = when (screen) {
        AppScreen.Loading -> "loading"
        is AppScreen.Startup -> "startup"
        AppScreen.DeckList -> "list"
        is AppScreen.DeckName -> "new-deck"
        is AppScreen.Deck -> "deck"
        is AppScreen.Editor -> "editor"
    }
}
