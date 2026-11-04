package app.deckly.ui.contract

/** Почему приложение не открылось сразу. Экраны запроса доступа и ошибок базы. */
enum class StartupState { NEEDS_FILE_ACCESS, DATABASE_DAMAGED, DATABASE_MISSING }

sealed interface StartupAction {
    /** Открыть системные настройки доступа ко всем файлам (для [StartupState.NEEDS_FILE_ACCESS]). */
    data object OpenSettings : StartupAction

    /** Закрыть приложение (для [StartupState.DATABASE_DAMAGED] и [StartupState.DATABASE_MISSING]). */
    data object Close : StartupAction
}
