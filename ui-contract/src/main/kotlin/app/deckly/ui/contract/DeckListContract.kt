package app.deckly.ui.contract

/**
 * Строка списка колод. [activeEpoch] — эпоха, в которой идёт проход (`null`, если колода пуста или
 * всё выучено), [isAllLearned] — все карточки выучены, [isCurrent] — колода открыта по умолчанию.
 */
data class DeckRow(
    val id: Long,
    val name: String,
    val cardCount: Int,
    val activeEpoch: Int?,
    val isAllLearned: Boolean,
    val isCurrent: Boolean,
)

/**
 * [canGoBack] — есть текущая колода, к которой можно вернуться. [deleteDialog] — открытое окно удаления колоды,
 * [isImportErrorShown] — окно «Couldn't open file»; меню строки открыто или нет, знает только дизайн.
 */
data class DeckListState(
    val decks: List<DeckRow>,
    val canGoBack: Boolean,
    val deleteDialog: DeleteDeckDialog? = null,
    val isImportErrorShown: Boolean = false,
)

/** Окно «Delete “[name]”?»: с колодой пропадут [cardCount] карточек. Название и число — как на строке, когда нажали «Delete deck». */
data class DeleteDeckDialog(val id: Long, val name: String, val cardCount: Int)

sealed interface DeckListAction {
    data class OpenDeck(val id: Long) : DeckListAction

    data object CreateDeck : DeckListAction

    data object Back : DeckListAction

    /** «Rename» в меню строки: форма с названием этой колоды. */
    data class RenameDeck(val id: Long) : DeckListAction

    /** «Delete deck» в меню строки: показать окно подтверждения. */
    data class AskDeleteDeck(val id: Long) : DeckListAction

    /** «Delete» в окне: колода удаляется вместе с карточками. */
    data object ConfirmDeleteDeck : DeckListAction

    /** «Keep» в окне и системная «Назад», пока оно открыто. */
    data object DismissDeleteDialog : DeckListAction

    /** «Export to file» в меню строки: колода уходит в файл, который выберет пользователь. */
    data class ExportDeck(val id: Long) : DeckListAction

    /** «Import from file» в нижней строке: файл колоды выбирается системным окном. */
    data object ImportDeck : DeckListAction

    /** «Close» в окне ошибки импорта и системная «Назад», пока оно открыто. */
    data object DismissImportError : DeckListAction
}

/** Форма названия колоды: [canSave] — имя не пустое, [isRename] — переименование существующей колоды, а не новая колода. */
data class DeckNameState(val name: String, val canSave: Boolean, val isRename: Boolean = false)

sealed interface DeckNameAction {
    data class ChangeName(val text: String) : DeckNameAction

    data object Save : DeckNameAction

    data object Cancel : DeckNameAction
}
