package app.deckly.ui.contract

/**
 * Вкладка секции. [isOpen] — секция открыта, [isActive] — в ней идёт проход (отметка ▶): в ожидании это
 * ожидающая эпоха, при вставном проходе — первая.
 */
data class TabState(val section: SectionTab, val count: Int, val isOpen: Boolean, val isActive: Boolean)

/** Что считает метрика позиции: проход обучения или просмотр секции. */
enum class PositionKind { LEARNING, BROWSING }

/** «14 / 40»: позиция карточки и размер прохода или секции. [position] считается с единицы. */
data class PositionMetric(val kind: PositionKind, val position: Int, val total: Int)

/** «3 / 4»: успехи карточки и порог её эпохи. */
data class SuccessMetric(val successes: Int, val threshold: Int)

/** Пункты меню карточки. */
enum class CardMenuItem { EDIT, RETURN_TO_NEW, DELETE }

/**
 * Шапка экрана колоды. [position] и [successes] — `null`, когда показывать нечего (пустая секция, пул,
 * выученные). [cardMenu] пуст, если карточки нет или идёт экран перехода; открыто ли меню — состояние
 * дизайна.
 */
data class DeckHeader(
    val deckName: String,
    val position: PositionMetric?,
    val successes: SuccessMetric?,
    val canCreateCard: Boolean,
    val cardMenu: List<CardMenuItem>,
)

/** Как начинается проход: обычно, вставной проход первой эпохи или продолжение прерванного. */
enum class TransitionKind { REGULAR, INSERTED, RESUMED }

/** Что показано на листе под шапкой. */
sealed interface DeckBody {
    /** Карточка; лист наполняет слот холста. [isContentDamaged] — файл штрихов не открылся, лист пуст. */
    data class Card(val side: CardSideShown, val isContentDamaged: Boolean) : DeckBody

    /** В секции нет карточек; [isPool] — это пул новых. */
    data class EmptySection(val isPool: Boolean) : DeckBody

    /** Экран перехода: [epoch] — эпоха, [remaining] осталось из [total] карточек. */
    data class Transition(val epoch: Int, val kind: TransitionKind, val remaining: Int, val total: Int) : DeckBody
}

/** Нижняя строка. */
sealed interface DeckFooter {
    /** Обучение: [areRatingsShown] — показать «Не знал» и «Знал» (ответ виден). */
    data class Learning(val postpone: ActionState, val flip: ActionState, val areRatingsShown: Boolean) : DeckFooter

    /** Просмотр секции: листание доступно, когда в секции больше одной карточки. */
    data class Browsing(val canBrowse: Boolean) : DeckFooter

    /** Экран перехода: одна кнопка «Начать проход» или «Продолжить». */
    data class StartPass(val kind: TransitionKind) : DeckFooter

    data object None : DeckFooter
}

/** Окна поверх экрана колоды. */
enum class DeckDialog { CONFIRM_DELETE_CARD, CONFIRM_RETURN_TO_NEW, NEED_NEW_CARD, DONE_FOR_TODAY }

data class DeckScreenState(
    val header: DeckHeader,
    val tabs: List<TabState>,
    val body: DeckBody,
    val footer: DeckFooter,
    val dialog: DeckDialog?,
)

sealed interface DeckAction {
    data class OpenSection(val section: SectionTab) : DeckAction

    data object Flip : DeckAction

    data object Knew : DeckAction

    data object DidNotKnow : DeckAction

    data object Postpone : DeckAction

    data object ShowPrevious : DeckAction

    data object ShowNext : DeckAction

    /** Начать или продолжить проход на экране перехода. */
    data object StartPass : DeckAction

    data class ChooseMenuItem(val item: CardMenuItem) : DeckAction

    data object ConfirmDialog : DeckAction

    data object DismissDialog : DeckAction

    data object CreateCard : DeckAction

    data object OpenDeckList : DeckAction
}
