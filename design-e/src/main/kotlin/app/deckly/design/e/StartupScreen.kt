package app.deckly.design.e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.deckly.ui.contract.StartupAction
import app.deckly.ui.contract.StartupState

/**
 * Экран, когда приложение не открылось сразу (решение 7 плана 4): окно в стиле диалога варианта E на белом листе, без
 * шапки колоды. Одна главная кнопка: «Открыть настройки» или «Закрыть».
 */
@Composable
fun StartupScreen(state: StartupState, onAction: (StartupAction) -> Unit) {
    Box(Modifier.fillMaxSize().background(Tokens.White)) {
        when (state) {
            StartupState.NEEDS_FILE_ACCESS -> Dialog(
                StartupTexts.ACCESS_TITLE, StartupTexts.ACCESS_TEXT,
                listOf(DialogAction(StartupTexts.OPEN_SETTINGS, isPrimary = true) { onAction(StartupAction.OpenSettings) }),
            )
            StartupState.DATABASE_DAMAGED -> Dialog(
                StartupTexts.DAMAGED_TITLE, StartupTexts.DAMAGED_TEXT,
                listOf(DialogAction(StartupTexts.CLOSE, isPrimary = true) { onAction(StartupAction.Close) }),
            )
            StartupState.DATABASE_MISSING -> Dialog(
                StartupTexts.MISSING_TITLE, StartupTexts.MISSING_TEXT,
                listOf(DialogAction(StartupTexts.CLOSE, isPrimary = true) { onAction(StartupAction.Close) }),
            )
        }
    }
}
