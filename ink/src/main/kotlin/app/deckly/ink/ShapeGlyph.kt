package app.deckly.ink

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import app.deckly.domain.editor.listGridOutlines
import app.deckly.domain.model.Shape

/**
 * Значок вида фигуры (палитра, образец на панели): контуры вида в сетке 100 × 100, растянутой на всю область
 * независимо по осям. Толщина контура [outlineDesignUnits] задана в единицах макета (1 dp) и не растёт вместе с сеткой,
 * как `vector-effect: non-scaling-stroke` в макете. Те же круглые концы и стыки, что у фигур на листе.
 */
@Composable
fun ShapeGlyph(kind: Shape.Kind, outlineDesignUnits: Int, modifier: Modifier) {
    val outlines = remember(kind) { kind.listGridOutlines() }
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            drawGlyph(canvas.nativeCanvas, outlines, size.width / GRID_SIZE, size.height / GRID_SIZE, outlineDesignUnits.dp.toPx())
        }
    }
}

private const val GRID_SIZE = 100f
