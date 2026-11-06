package app.deckly.domain.model

/**
 * Контурная фигура на стороне карточки. Рамка `left..right`, `top..bottom` и [width] контура заданы в единицах
 * карточки ([CardUnits]), [angle] — поворот в градусах по часовой стрелке вокруг центра рамки. Заливки нет. Рамка
 * непустая, числа конечные, толщина контура больше нуля; угол любой.
 */
data class Shape(
    val kind: Kind,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val angle: Float,
    val width: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "Рамка фигуры должна быть конечной: ($left, $top, $right, $bottom)"
        }
        require(left < right && top < bottom) { "Рамка фигуры не может быть пустой: ($left, $top, $right, $bottom)" }
        require(angle.isFinite()) { "Угол фигуры должен быть конечным: $angle" }
        require(width.isFinite() && width > 0f) { "Толщина контура должна быть конечной и больше нуля: $width" }
    }

    /**
     * Вид фигуры. Порядок, [key] и [svg] те же, что в словаре `SHAPES` в `design/build.py` (разметка значка в сетке
     * 100 × 100), [code] закреплён: он лежит в файле штрихов.
     */
    enum class Kind(val code: Int, val key: String, val svg: String) {
        LINE(1, "line", """<path d="M12 50H88"/>"""),
        ARROW(2, "arrow", """<path d="M12 50H88L68 30M88 50 68 70"/>"""),
        DOUBLE_ARROW(3, "double-arrow", """<path d="M12 50H88M32 30 12 50 32 70M68 30 88 50 68 70"/>"""),
        RECTANGLE(4, "rectangle", """<rect x="14" y="20" width="72" height="60"/>"""),
        OVAL(5, "oval", """<ellipse cx="50" cy="50" rx="38" ry="30"/>"""),
        TRIANGLE(6, "triangle", """<path d="M50 12 89 85H11Z"/>"""),
        DIAMOND(7, "diamond", """<path d="M50 10 86 50 50 90 14 50Z"/>"""),
        HEXAGON(8, "hexagon", """<path d="M30 15H70L90 50 70 85H30L10 50Z"/>"""),
        STAR(9, "star", """<path d="m50 10 12 27 29 3-22 20 6 30-25-15-25 15 6-30L9 40l29-3Z"/>"""),
        MOON(10, "moon", """<path d="M65 12A39 39 0 1 0 87 70 38 38 0 0 1 65 12Z"/>"""),
        SUN(11, "sun", """<circle cx="50" cy="50" r="23"/><path d="M50 7V16M50 84V93M7 50H16M84 50H93M20 20 27 27M73 73 80 80M20 80 27 73M73 27 80 20"/>"""),
        HEART(12, "heart", """<path d="M50 86 16 53C-5 28 25 4 50 29 75 4 105 28 84 53Z"/>"""),
        CLOUD(13, "cloud", """<path d="M25 77A20 20 0 0 1 19 38 27 27 0 0 1 71 32a23 23 0 0 1 8 45H25Z"/>"""),
        BOLT(14, "bolt", """<path d="M57 8 20 56H46L39 92 82 40H55Z"/>"""),
        DROP(15, "drop", """<path d="M50 10C43 25 20 48 20 62A30 30 0 0 0 80 62C80 48 57 25 50 10Z"/>""");

        /** Линия и стрелки: рамка у них только ограничивает ось, а не растягивает контур. */
        val isLinear: Boolean get() = this == LINE || this == ARROW || this == DOUBLE_ARROW

        companion object {
            fun findByCode(code: Int): Kind? = entries.find { it.code == code }

            fun findByKey(key: String): Kind? = entries.find { it.key == key }
        }
    }
}
