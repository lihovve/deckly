package app.deckly.design.e

import app.deckly.ui.contract.Thickness

/** Тексты редактора из `design/build.py`: функция `editor`, окна `dlg-discard`, `dlg-delete-edit`, `dlg-editor-back`. */
internal object EditorTexts {
    const val TITLE_NEW = "Новая карточка"
    const val TITLE_EDIT = "Редактирование"
    const val CANCEL = "Отменить"
    const val CANCEL_DESCRIPTION = "Отменить создание"
    const val DELETE = "Удалить"
    const val DELETE_DESCRIPTION = "Удалить карточку"
    const val SAVE = "Сохранить"
    const val SIDES = "Сторона карточки"
    const val FRONT = "Вопрос"
    const val BACK = "Ответ"
    const val CANVAS = "Холст. Рисование только пером"
    const val TOOLS = "Инструменты рисования"
    const val PEN = "Перо"
    const val ERASER = "Ластик"
    const val THICKNESS = "Толщина пера"
    const val THIN = "Тонкая"
    const val MEDIUM = "Средняя"
    const val THICK = "Толстая"
    const val LINE = "линия"
    const val UNDO_STROKE = "Отменить штрих"

    // --- окна ---
    const val KEEP_EDITING = "Продолжить"
    const val DISCARD_TITLE = "Удалить черновик?"
    const val DISCARD_TEXT = "Рисунки на обеих сторонах не сохранятся."
    const val DISCARD = "Удалить черновик"
    const val DELETE_CARD_TITLE = "Удалить карточку?"
    const val DELETE_CARD_TEXT = "Вопрос и ответ будут удалены безвозвратно."
    const val SAVE_EXIT_TITLE = "Сохранить и выйти?"
    const val SAVE_EXIT = "Сохранить и выйти"

    /** «Тонкая линия», «Средняя линия», «Толстая линия»: `aria-label="{name} линия"`. */
    fun describeThickness(thickness: Thickness): String {
        val name = when (thickness) {
            Thickness.THIN -> THIN
            Thickness.MEDIUM -> MEDIUM
            Thickness.THICK -> THICK
        }
        return "$name $LINE"
    }
}

/** Тексты списка колод из `design/build.py`: `deck_header`, `deck_row`, `deck_actions`, `decks-empty`. */
internal object DeckListTexts {
    const val TITLE = "Колоды"
    const val BACK_TO_CARDS = "К карточкам"
    const val NEW_DECK = "Новая колода"
    const val NO_DECKS = "Нет колод"
    const val LEARNED = "выучены"
    const val SEPARATOR = "·"

    /** Номер строки с нулём впереди: `{number:02}`. */
    fun formatDeckNumber(number: Int): String = number.toString().padStart(2, '0')

    /** «245 карточек ·»: подпись перед значком ▶ и эпохой. */
    fun formatCaptionBeforeEpoch(cardCount: Int): String = "${formatCardCount(cardCount)} $SEPARATOR"

    /** «18 карточек · выучены». */
    fun formatLearnedCaption(cardCount: Int): String = "${formatCardCount(cardCount)} $SEPARATOR $LEARNED"
}

/** Тексты формы новой колоды из `design/build.py` (`deck-new`). */
internal object DeckNameTexts {
    const val TITLE = "Новая колода"
    const val CANCEL = "Отмена"
    const val CREATE = "Создать"
    const val FIELD_LABEL = "Название колоды"
    const val PLACEHOLDER = "Немецкий A2"
}

/**
 * Тексты экрана запуска. В макетах таких экранов нет; тексты — таблица решения 7 плана 4. Из них в `build.py` есть
 * только «Закрыть» (окно ошибки импорта); `ScreenTextsTest` фиксирует, что остальные фразы вне макета и их ровно столько.
 */
internal object StartupTexts {
    const val ACCESS_TITLE = "Нужен доступ к файлам"
    const val ACCESS_TEXT = "Колоды хранятся в папке Deckly, без доступа их не открыть."
    const val OPEN_SETTINGS = "Открыть настройки"
    const val DAMAGED_TITLE = "База повреждена"
    const val DAMAGED_TEXT = "Файл Deckly/deckly.db не открылся, ничего не удалено."
    const val MISSING_TITLE = "База не найдена"
    const val MISSING_TEXT = "В папке Deckly нет файла deckly.db, рисунки карточек не тронуты."
    const val CLOSE = "Закрыть"
}
