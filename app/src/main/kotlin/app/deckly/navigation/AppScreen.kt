package app.deckly.navigation

import app.deckly.domain.model.Section
import app.deckly.ui.contract.StartupState

/** Текущий экран приложения. Переходы — состояние в [AppViewModel], библиотеки навигации нет. */
sealed interface AppScreen {
    /** Хранение открывается: проверка базы может занять секунды. Пустой экран, дизайн его не рисует. */
    data object Loading : AppScreen

    data class Startup(val state: StartupState) : AppScreen

    data object DeckList : AppScreen

    /** Форма названия колоды: новой, а если задано [rename], существующей, которую переименовывают. */
    data class DeckName(val rename: DeckToRename? = null) : AppScreen

    /**
     * [openSection] — секция, которую открыть после загрузки, а [focusCardId] — карточка, на которой её показать:
     * так экран колоды возвращается из редактора на правленую или созданную карточку.
     */
    data class Deck(val deckId: Long, val openSection: Section? = null, val focusCardId: Long? = null) : AppScreen

    data class Editor(val deckId: Long, val start: EditorStart) : AppScreen
}

/** Колода, которую переименовывают: её [id] и название на момент нажатия «Rename». */
data class DeckToRename(val id: Long, val name: String)

/** Как открывается редактор; [returnSection] — секция, в которую вернётся экран колоды. */
sealed interface EditorStart {
    /**
     * [returnFocusCardId] — карточка, которая была показана до «+». Отмена черновика возвращает на неё. После
     * перезапуска (черновик в хранилище) она не известна: черновик её не хранит.
     */
    data class NewCard(val returnSection: Section, val returnFocusCardId: Long? = null) : EditorStart

    data class EditCard(val cardId: Long, val returnSection: Section) : EditorStart

    /** После перезапуска: черновик берётся из хранилища. */
    data object ResumeDraft : EditorStart
}
