package app.deckly.domain.model

/**
 * Колода. Поле `cursor` из ТЗ разделено на [passCursor] (позиция в проходе активной эпохи) и
 * [browseCursor] (позиция при листании открытой неактивной секции); [passStarted] — начат ли проход
 * активной эпохи. Оба решения плана 2.
 *
 * [visibleSide] — какой стороной показана текущая карточка. Это состояние модели, а не интерфейса: правило
 * «пока видно лицо, оценка недоступна» держит движок. Любое действие, кроме переворота, возвращает [Side.FRONT].
 *
 * [portionRatedCount] — сколько карточек оценено в текущей порции прохода эпохи 2–4. Границу порции
 * (40 оценок) задаёт этот счётчик, а не [passCursor]: удаление оценённой карточки сдвигает курсоры,
 * но не отменяет оценку.
 */
data class Deck(
    val id: Long,
    val name: String,
    val activeSection: Section,
    val currentEpoch: Int,
    val passStarted: Boolean,
    val passCount: EpochValues<Int>,
    val passCursor: Int,
    val browseCursor: Int,
    val visibleSide: Side,
    val lastPassDay: EpochValues<Int?>,
    val waitingEpoch: Int,
    val pausedEpoch: Int,
    val pausedCursor: Int,
    val portionRatedCount: Int,
    val tail: EpochValues<List<Long>>,
)

/** Новая колода: открыт пул новых, проход эпохи 1 не начат, счётчики нулевые. */
fun createDeck(id: Long, name: String): Deck = Deck(
    id = id,
    name = name,
    activeSection = Section.NEW,
    currentEpoch = 1,
    passStarted = false,
    passCount = EpochValues(0, 0, 0, 0),
    passCursor = 0,
    browseCursor = 0,
    visibleSide = Side.FRONT,
    lastPassDay = EpochValues(null, null, null, null),
    waitingEpoch = 0,
    pausedEpoch = 0,
    pausedCursor = 0,
    portionRatedCount = 0,
    tail = EpochValues(emptyList(), emptyList(), emptyList(), emptyList()),
)
