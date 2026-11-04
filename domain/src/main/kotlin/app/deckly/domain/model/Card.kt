package app.deckly.domain.model

/**
 * Карточка без штрихов: алгоритму обучения они не нужны, их хранит [CardContent].
 * Поля — раздел «Модель данных» ТЗ; [excludedFromPass] — решение плана 2: карточка попала в эпоху
 * не на старте её прохода и в текущем проходе этой эпохи не участвует.
 */
data class Card(
    val id: Long,
    val section: Section,
    val position: Int,
    val successes: Int,
    val sectionDay: Int,
    val lastSuccessDay: Int?,
    val createdAt: Long,
    val postponed: Boolean,
    val answerRevealed: Boolean,
    val failed: Boolean,
    val checking: Boolean,
    val excludedFromPass: Boolean,
)
