package app.deckly.design.e

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.ShapeKind
import kotlin.math.roundToInt

/**
 * Палитра фигур поверх экрана (`.shape-overlay` и `.shape-palette` в макете): окно слева над панелью инструментов,
 * заголовок и крест, сетка 5 × 3 из 15 видов (ни один не выделен), толщина контура с образцом и ползунком 1…12. Фон
 * прозрачный и не принимает касаний: касание мимо окна, крест и системная «Назад» закрывают палитру. Значки видов приходят
 * слотом [shapeGlyph].
 */
@Composable
internal fun ShapePalette(
    outlineWidth: Int,
    onAction: (EditorAction) -> Unit,
    shapeGlyph: @Composable (ShapeKind, Int, Modifier) -> Unit,
) {
    BackHandler { onAction(EditorAction.CloseShapePalette) }
    Box(Modifier.fillMaxSize().blockTouches()) {
        // Прозрачный фон закрывает палитру, но ничего из того, что под ним, не получает.
        Box(
            Modifier
                .fillMaxSize()
                .clickSilently(true, Role.Button) { onAction(EditorAction.CloseShapePalette) }
                .semantics { contentDescription = EditorTexts.CLOSE_SHAPES },
        )
        val shape = RoundedCornerShape(Tokens.RadiusLarge)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Tokens.ShapePaletteLeft, bottom = Tokens.ShapePaletteBottom)
                .width(Tokens.ShapePaletteWidth)
                // Окно лежит над фоном и касание мимо его кнопок гасит: закрывает палитру только фон за пределами окна.
                .blockTouches()
                .background(Tokens.White, shape)
                .border(Tokens.BorderMedium, Tokens.Black, shape)
                // Рамка 2 внутри ширины 672 (border-box), поля 24 сверху, 32 по бокам и 28 снизу отсчитываются от неё.
                .padding(
                    start = Tokens.BorderMedium + Tokens.ShapePalettePaddingHorizontal,
                    top = Tokens.BorderMedium + Tokens.ShapePalettePaddingTop,
                    end = Tokens.BorderMedium + Tokens.ShapePalettePaddingHorizontal,
                    bottom = Tokens.BorderMedium + Tokens.ShapePalettePaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(Tokens.ShapePaletteGap),
        ) {
            PaletteHeader(onAction)
            ShapeGrid(shapeGlyph, outlineWidth, onAction)
            OutlineWidthSection(outlineWidth) { onAction(EditorAction.SetOutlineWidth(it)) }
        }
    }
}

@Composable
private fun PaletteHeader(onAction: (EditorAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            EditorTexts.SHAPES,
            modifier = Modifier.weight(1f),
            style = buildTextStyle(Tokens.ShapePaletteTitleText, FontWeight.Bold, letterSpacing = Tokens.ShapePaletteTitleSpacing),
        )
        DecklyButton(
            null, DecklyIcons.Cross, ButtonKind.QUIET, EditorTexts.CLOSE_SHAPES,
            { onAction(EditorAction.CloseShapePalette) }, Modifier.size(Tokens.ControlMin), isSquare = true,
        )
    }
}

// Сетка из пяти колонок равной ширины: 15 видов в порядке `SHAPES`, ни один не выделен (aria-current нет).
@Composable
private fun ShapeGrid(shapeGlyph: @Composable (ShapeKind, Int, Modifier) -> Unit, outlineWidth: Int, onAction: (EditorAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.ShapeGridGap)) {
        for (row in ShapeKind.entries.chunked(Tokens.ShapeGridColumns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.ShapeGridGap)) {
                for (kind in row) {
                    ShapeChoice(kind, outlineWidth, shapeGlyph, Modifier.weight(1f)) { onAction(EditorAction.ChooseShape(kind)) }
                }
            }
        }
    }
}

@Composable
private fun ShapeChoice(
    kind: ShapeKind,
    outlineWidth: Int,
    shapeGlyph: @Composable (ShapeKind, Int, Modifier) -> Unit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.RadiusSmall)
    Box(
        modifier
            .height(Tokens.ShapeChoiceHeight)
            .clickSilently(true, Role.Button, onClick)
            .semantics { contentDescription = EditorTexts.describeShape(kind) }
            // Рамка 2 dp прозрачная: у выбранного вида была бы чёрная, но выбранного вида в палитре нет.
            .border(Tokens.BorderMedium, androidx.compose.ui.graphics.Color.Transparent, shape),
        contentAlignment = Alignment.Center,
    ) {
        shapeGlyph(kind, outlineWidth, Modifier.size(Tokens.ShapeChoiceGlyph))
    }
}

// Подпись, число и образец линии в одной строке сетки `1fr auto 80px` с промежутком 20, ползунок на всю ширину под ними.
@Composable
private fun OutlineWidthSection(outlineWidth: Int, onChange: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            // `border-top:1px solid #000`, затем `padding-top:24px`.
            .drawBehind { drawRect(Tokens.Black, Offset.Zero, Size(size.width, Tokens.BorderThin.toPx())) }
            .padding(top = Tokens.BorderThin + Tokens.ShapeWidthPaddingTop),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.ShapeWidthColumnGap), verticalAlignment = Alignment.CenterVertically) {
            BasicText(EditorTexts.OUTLINE_WIDTH, modifier = Modifier.weight(1f), style = buildTextStyle(Tokens.ShapeWidthText))
            BasicText(outlineWidth.toString(), style = buildTextStyle(Tokens.ShapeWidthText))
            WidthPreview(outlineWidth)
        }
        OutlineWidthSlider(outlineWidth, onChange, Modifier.padding(top = Tokens.ShapeSliderMarginTop))
    }
}

// Образец линии 80 × 24: `M8 12H72` толщиной в выбранную ширину, концы не скруглены.
@Composable
private fun WidthPreview(outlineWidth: Int) {
    Canvas(Modifier.size(Tokens.ShapeWidthPreview, Tokens.ShapeWidthPreviewHeight)) {
        drawLine(Tokens.Black, Offset(8.dp.toPx(), size.height / 2f), Offset(72.dp.toPx(), size.height / 2f), strokeWidth = outlineWidth.dp.toPx())
    }
}

/**
 * Ползунок толщины контура (`input type=range`, 1…12 с шагом 1): высота 64, дорожка 2, бегунок 32 с рамкой 3 и белой
 * серединой. Меняется и пером, и пальцем: касание ставит бегунок под собой, движение ведёт его. Для диктора это
 * регулятор с границами и шагом ([setProgress]).
 */
@Composable
internal fun OutlineWidthSlider(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)
    Box(
        modifier
            .fillMaxWidth()
            .height(Tokens.ShapeSliderHeight)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val thumb = Tokens.ShapeSliderThumb.toPx()
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnChange(chooseWidthAt(down.position.x, size.width.toFloat(), thumb))
                    down.consume()
                    drag(down.id) { change ->
                        currentOnChange(chooseWidthAt(change.position.x, size.width.toFloat(), thumb))
                        change.consume()
                    }
                }
            }
            .semantics {
                contentDescription = EditorTexts.OUTLINE_WIDTH
                role = Role.Button
                progressBarRangeInfo = ProgressBarRangeInfo(
                    value.toFloat(),
                    Tokens.OutlineWidthMin.toFloat()..Tokens.OutlineWidthMax.toFloat(),
                    Tokens.OutlineWidthMax - Tokens.OutlineWidthMin - 1,
                )
                setProgress { target ->
                    currentOnChange(target.roundToInt().coerceIn(Tokens.OutlineWidthMin, Tokens.OutlineWidthMax))
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val thumb = Tokens.ShapeSliderThumb.toPx()
            val border = Tokens.ShapeSliderThumbBorder.toPx()
            val centerY = size.height / 2f
            drawLine(Tokens.Black, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = Tokens.ShapeSliderTrack.toPx())
            // Бегунок: белый круг 32 с чёрной рамкой 3; центр идёт от r до ширина − r пропорционально значению.
            val fraction = (value - Tokens.OutlineWidthMin).toFloat() / (Tokens.OutlineWidthMax - Tokens.OutlineWidthMin)
            val center = Offset(thumb / 2f + (size.width - thumb) * fraction, centerY)
            drawCircle(Tokens.White, thumb / 2f, center)
            drawCircle(Tokens.Black, thumb / 2f - border / 2f, center, style = Stroke(border))
        }
    }
}

/** Значение под пальцем или пером: центр бегунка ходит от r до ширина − r, шаг 1, границы 1 и 12. */
internal fun chooseWidthAt(x: Float, width: Float, thumb: Float): Int {
    val usable = (width - thumb).coerceAtLeast(1f)
    val fraction = ((x - thumb / 2f) / usable).coerceIn(0f, 1f)
    return Tokens.OutlineWidthMin + (fraction * (Tokens.OutlineWidthMax - Tokens.OutlineWidthMin)).roundToInt()
}
