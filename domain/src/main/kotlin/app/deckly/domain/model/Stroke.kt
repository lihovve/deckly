package app.deckly.domain.model

/** Точка штриха в системе координат карточки; нажим от 0 до 1. */
data class StrokePoint(val x: Float, val y: Float, val pressure: Float)

data class Stroke(val width: Float, val points: List<StrokePoint>)

enum class Side { FRONT, BACK }

/** Содержимое карточки: штрихи двух сторон. Хранится отдельно от [Card]. */
data class CardContent(val cardId: Long, val front: List<Stroke>, val back: List<Stroke>)
