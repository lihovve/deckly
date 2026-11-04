package app.deckly.domain.model

/** Шесть секций колоды в порядке движения карточки: пул новых, эпохи 1–4, стек выученных. */
enum class Section { NEW, EPOCH_1, EPOCH_2, EPOCH_3, EPOCH_4, LEARNED }

/** Номер эпохи 1–4 для секций эпох, `null` для пула новых и стека выученных. */
val Section.epochNumber: Int?
    get() = when (this) {
        Section.EPOCH_1 -> 1
        Section.EPOCH_2 -> 2
        Section.EPOCH_3 -> 3
        Section.EPOCH_4 -> 4
        Section.NEW, Section.LEARNED -> null
    }

fun findEpochSection(epoch: Int): Section = when (epoch) {
    1 -> Section.EPOCH_1
    2 -> Section.EPOCH_2
    3 -> Section.EPOCH_3
    4 -> Section.EPOCH_4
    else -> throw IllegalArgumentException("Эпоха должна быть от 1 до 4: $epoch")
}
