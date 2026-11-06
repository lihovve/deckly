package app.deckly.ink

import app.deckly.domain.model.CardUnits

/**
 * Система координат карточки: ширина 1000 единиц, `x` вправо, `y` вниз. Высота — по пропорции листа
 * макета 928 × 963. Штрихи хранятся и приходят из холста в этих единицах, на экран их переводит масштаб
 * листа ([computeScale]), поэтому рисунок не зависит от размера листа на конкретном экране.
 */
object CardGeometry {
    const val WIDTH = CardUnits.WIDTH
    val HEIGHT = CardUnits.HEIGHT

    /** Пикселей экрана в одной единице карточки при заданной ширине листа. */
    fun computeScale(sheetWidthPx: Float): Float = sheetWidthPx / WIDTH

    fun toCardUnits(px: Float, scale: Float): Float = px / scale

    fun toPixels(units: Float, scale: Float): Float = units * scale
}

/**
 * Толщина точки штриха в пикселях экрана: `2 + (w + 1) · p²`, где `w` — толщина пера в пикселях листа
 * (`strokeWidth · scale`), `p` — нажим 0..1. Это формула ручки прошивки Onyx, подогнанная по замерам
 * прототипа: минимум 2 px при любой толщине, максимум `w + 3`.
 */
fun computePointWidthPx(strokeWidth: Float, pressure: Float, scale: Float): Float =
    2f + (strokeWidth * scale + 1f) * pressure * pressure
