package app.deckly.navigation

import app.deckly.domain.model.Section
import app.deckly.ui.contract.StartupState

/** Текущий экран приложения. Переходы — состояние в [AppViewModel], библиотеки навигации нет. */
sealed interface AppScreen {
    /** Хранение открывается: проверка базы может занять секунды. Пустой экран, дизайн его не рисует. */
    data object Loading : AppScreen

    data class Startup(val state: StartupState) : AppScreen

    data object DeckList : AppScreen

    /** Форма новой колоды. */
    data object DeckName : AppScreen

    /**
     * [openSection] — секция, которую открыть после загрузки, а [focusCardId] — карточка, на которой её показать:
     * так экран колоды возвращается из редактора на правленую или созданную карточку.
     */
    data class Deck(val deckId: Long, val openSection: Section? = null, val focusCardId: Long? = null) : AppScreen

    data class Editor(val deckId: Long, val start: EditorStart) : AppScreen
}

/** Как открывается редактор; [returnSection] — секция, в которую вернётся экран колоды. */
sealed interface EditorStart {
    data class NewCard(val returnSection: Section) : EditorStart

    data class EditCard(val cardId: Long, val returnSection: Section) : EditorStart

    /** После перезапуска: черновик берётся из хранилища. */
    data object ResumeDraft : EditorStart
}
