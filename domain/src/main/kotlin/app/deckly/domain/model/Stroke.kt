package app.deckly.domain.model

/**
 * Точка штриха в системе координат карточки; нажим от 0 до 1. Координаты конечные, нажим в 0..1:
 * недопустимую точку нельзя создать, поэтому её нельзя ни сохранить, ни экспортировать.
 */
data class StrokePoint(val x: Float, val y: Float, val pressure: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "Координаты точки должны быть конечными: ($x, $y)" }
        require(pressure in 0f..1f) { "Нажим должен быть от 0 до 1: $pressure" }
    }
}

/** Штрих: толщина конечная и больше нуля. Штрих без точек допустим. */
data class Stroke(val width: Float, val points: List<StrokePoint>) {
    init {
        require(width.isFinite() && width > 0f) { "Толщина штриха должна быть конечной и больше нуля: $width" }
    }
}

enum class Side { FRONT, BACK }

/** Содержимое карточки: штрихи двух сторон. Хранится отдельно от [Card]. */
data class CardContent(val cardId: Long, val front: List<Stroke>, val back: List<Stroke>)
