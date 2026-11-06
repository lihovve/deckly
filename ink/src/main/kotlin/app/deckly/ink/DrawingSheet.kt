package app.deckly.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint

/**
 * Лист для рисования в редакторе: сырое перо Onyx при [OnyxSdk.isPenAvailable], иначе запасной `Canvas`.
 *
 * Координаты в колбэках — в единицах карточки ([CardGeometry]). Толщина штриха в [onStroke] тоже в
 * единицах карточки; [penWidthPx] — толщина пера в пикселях листа. [onErase] получает траекторию ластика и
 * его радиус (16 px листа) в единицах карточки. Пишет кончик пера, стирает боковая кнопка стилуса. Короткое касание пера у
 * контура фигуры не штрих: [onShapeTap] получает номер фигуры в [side]. Внутренность пустой фигуры не выбирает, поэтому там
 * можно писать.
 *
 * [uiRevision] растёт, когда на экране изменилось что-то вне холста: пока перо включено, прошивка этого не
 * показывает, и лист выключает перо на время обновления. То же лист делает сам, когда [side] заменена
 * (удаление, возврат, другая сторона, другие фигуры), а не дополнена штрихами. [isPenEnabled] = `false` (диалог) выключает перо.
 *
 * Лист не проверялся на планшете: на JVM работает только запасной холст, см. [OnyxDrawingView].
 */
@Composable
fun DrawingSheet(
    side: CardSide,
    penWidthPx: Float,
    isPenEnabled: Boolean,
    uiRevision: Int,
    onStroke: (Stroke) -> Unit,
    onErase: (List<StrokePoint>, Float) -> Unit,
    onShapeTap: (Int) -> Unit,
    modifier: Modifier,
) {
    // Выбор холста делается один раз: SDK поднимается в Application.onCreate и дальше не меняется.
    val isOnyx = remember { OnyxSdk.isPenAvailable }
    AndroidView(
        modifier = modifier,
        factory = { context -> if (isOnyx) OnyxDrawingView(context) else CanvasDrawingView(context) },
        update = { view ->
            (view as DrawingSurface).bind(
                DrawingState(
                    side = side,
                    penWidthPx = penWidthPx,
                    isPenEnabled = isPenEnabled,
                    uiRevision = uiRevision,
                    onStroke = onStroke,
                    onErase = onErase,
                    onShapeTap = onShapeTap,
                ),
            )
        },
    )
}

/** Всё, что хозяин задаёт листу; лист сам находит, что изменилось. Координаты в колбэках — в единицах карточки. */
internal data class DrawingState(
    val side: CardSide,
    val penWidthPx: Float,
    val isPenEnabled: Boolean,
    val uiRevision: Int,
    val onStroke: (Stroke) -> Unit,
    val onErase: (List<StrokePoint>, Float) -> Unit,
    val onShapeTap: (Int) -> Unit,
)

/** Холст за [DrawingSheet]: сырое перо Onyx или запасной `Canvas`. */
internal interface DrawingSurface {
    fun bind(state: DrawingState)
}
