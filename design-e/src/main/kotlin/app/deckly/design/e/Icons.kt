package app.deckly.design.e

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/** Контур значка. [isFilled] — залитый значок без линии (отметка активной эпохи), иначе линия толщиной [Tokens.IconStroke]. */
data class IconShape(val pathData: String, val isFilled: Boolean = false)

/**
 * Значки макета: имена и контуры взяты из `PATHS` в `design/build.py`, сетка 24 × 24. Круги и скруглённый
 * прямоугольник макета записаны теми же контурами в виде дуг. `IconsTest` сверяет состав и контуры с `build.py`.
 */
enum class IconId(val key: String, val shapes: List<IconShape>) {
    MORE("more", listOf(buildCircle(5), buildCircle(12), buildCircle(19))),
    DOWN("down", listOf(IconShape("m6 9 6 6 6-6"))),
    LEFT("left", listOf(IconShape("M20 12H4m6-6-6 6 6 6"))),
    RIGHT("right", listOf(IconShape("M4 12h16m-6-6 6 6-6 6"))),
    FLIP("flip", listOf(IconShape("M4 8h15m-4-4 4 4-4 4M20 16H5m4-4-4 4 4 4"))),
    PLUS("plus", listOf(IconShape("M12 4v16M4 12h16"))),
    CHECK("check", listOf(IconShape("m4 12 5 5L20 6"))),
    CROSS("cross", listOf(IconShape("m6 6 12 12M18 6 6 18"))),
    DEFER("defer", listOf(IconShape("M3 12h13m-5-5 5 5-5 5M21 5v14"))),
    ACTIVE("active", listOf(IconShape("m8 5 11 7-11 7Z", isFilled = true))),
    PEN("pen", listOf(IconShape("m4 16 11-11 4 4L8 20H4v-4Zm9-9 4 4m-1-7 1-1 4 4-1 1"))),
    UNDO("undo", listOf(IconShape("M5 5v6h6M5 11c4-9 15-5 15 2 0 4-3 7-7 7"))),
    TRASH("trash", listOf(IconShape("M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"))),
    FILE("file", listOf(IconShape("M5 3h9l5 5v13H5V3Zm9 0v6h5M8 14h8m-8 3h5"))),
    // rect x=6 y=6 width=14 height=16 rx=2 и контур задней карточки.
    CARDS("cards", listOf(IconShape("M8 6h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z"), IconShape("M16 3H5a2 2 0 0 0-2 2v13"))),
    EXPORT("export", listOf(IconShape("M12 15V3m-4 4 4-4 4 4M4 13v8h16v-8"))),
    // circle cx=7 cy=8 r=4 двумя дугами от самой левой точки и контур треугольника с уголком.
    SHAPES("shapes", listOf(IconShape("M3 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0"), IconShape("m16 5 6 12H10Z M3 16v5h6"))),
}

// Круг радиуса 1 с центром (центр, 12) двумя дугами: так же, как `<circle cx r="1">` в макете.
private fun buildCircle(centerX: Int): IconShape = IconShape("M${centerX - 1} 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0")

object DecklyIcons {
    private val cache = ConcurrentHashMap<Pair<IconId, Float>, ImageVector>()

    /** Значок [id]; [strokeWidth] 1,2 даёт тонкую линию крупных значков пустых состояний (`.empty-icon` в макете). */
    fun get(id: IconId, strokeWidth: Float = Tokens.IconStroke): ImageVector =
        cache.getOrPut(id to strokeWidth) { buildIcon(id, strokeWidth) }

    val More get() = get(IconId.MORE)
    val Down get() = get(IconId.DOWN)
    val Left get() = get(IconId.LEFT)
    val Right get() = get(IconId.RIGHT)
    val Flip get() = get(IconId.FLIP)
    val Plus get() = get(IconId.PLUS)
    val Check get() = get(IconId.CHECK)
    val Cross get() = get(IconId.CROSS)
    val Defer get() = get(IconId.DEFER)
    val Active get() = get(IconId.ACTIVE)
    val Pen get() = get(IconId.PEN)
    val Undo get() = get(IconId.UNDO)
    val Trash get() = get(IconId.TRASH)
    val File get() = get(IconId.FILE)
    val Cards get() = get(IconId.CARDS)
    val Export get() = get(IconId.EXPORT)
    val Shapes get() = get(IconId.SHAPES)
}

// Цвет в векторе чёрный: на экран значок выводится через ColorFilter.tint, чтобы недоступные были серыми.
private fun buildIcon(id: IconId, strokeWidth: Float): ImageVector =
    ImageVector.Builder(id.key, 24.dp, 24.dp, 24f, 24f).apply {
        for (shape in id.shapes) {
            val nodes = PathParser().parsePathString(shape.pathData).toNodes()
            if (shape.isFilled) {
                addPath(nodes, fill = SolidColor(Color.Black))
            } else {
                addPath(
                    nodes,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }.build()
