package app.deckly.domain.model

/** Сторона карточки: штрихи и фигуры. Порядок в списках — порядок появления: поздние лежат выше. */
data class CardSide(val strokes: List<Stroke> = emptyList(), val shapes: List<Shape> = emptyList())

/** Содержимое карточки: две стороны. Хранится отдельно от [Card]. */
data class CardContent(val cardId: Long, val front: CardSide, val back: CardSide)
