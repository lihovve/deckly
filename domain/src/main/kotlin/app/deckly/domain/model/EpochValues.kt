package app.deckly.domain.model

/** Значение для каждой из четырёх эпох; доступ по номеру эпохи 1–4. */
data class EpochValues<T>(val epoch1: T, val epoch2: T, val epoch3: T, val epoch4: T) {

    operator fun get(epoch: Int): T = when (epoch) {
        1 -> epoch1
        2 -> epoch2
        3 -> epoch3
        4 -> epoch4
        else -> throw IllegalArgumentException("Эпоха должна быть от 1 до 4: $epoch")
    }

    fun replace(epoch: Int, value: T): EpochValues<T> = when (epoch) {
        1 -> copy(epoch1 = value)
        2 -> copy(epoch2 = value)
        3 -> copy(epoch3 = value)
        4 -> copy(epoch4 = value)
        else -> throw IllegalArgumentException("Эпоха должна быть от 1 до 4: $epoch")
    }
}
