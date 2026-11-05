package app.deckly.ink

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.deckly.domain.model.Stroke

/**
 * Лист стороны карточки для обучения и просмотра: рисует только штрихи, фон и рамка — дело дизайна.
 * Масштаб — по ширине отведённого места ([CardGeometry.computeScale]), толщина штрихов растёт вместе
 * с ним. Статичная картинка без анимаций; [description] читает экранный диктор. Дизайн ставит описание
 * листа сам через модификатор слота и передаёт `null`: иначе описание хозяина затёрлось бы.
 */
@Composable
fun CardSideImage(strokes: List<Stroke>, modifier: Modifier, description: String?) {
    val labelled = if (description != null) modifier.semantics { contentDescription = description } else modifier
    Canvas(modifier = labelled) {
        val scale = CardGeometry.computeScale(size.width)
        drawIntoCanvas { canvas -> drawStrokes(canvas.nativeCanvas, strokes, scale) }
    }
}
