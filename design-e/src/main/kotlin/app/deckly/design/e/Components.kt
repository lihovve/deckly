package app.deckly.design.e

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.deckly.ui.contract.SectionTab
import app.deckly.ui.contract.TabState

/** Вид кнопки: основная (рамка 3), обычная (рамка 2), тихая (без рамки, шрифт тоньше), недоступная (серая, не нажимается). */
enum class ButtonKind { PRIMARY, NORMAL, QUIET, OFF }

internal fun buildTextStyle(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Tokens.Black,
    lineHeightFactor: Float = 1.25f,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    align: TextAlign = TextAlign.Unspecified,
): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = weight,
    fontFamily = FontFamily.SansSerif,
    lineHeight = (size.value * lineHeightFactor).sp,
    letterSpacing = letterSpacing,
    textAlign = align,
)

/**
 * Нажимается без обратной связи: ни ripple, ни подсветки, ни анимации (ТЗ). Индикацию из окружения не берёт, даже если
 * корень приложения её не отключил.
 */
internal fun Modifier.clickSilently(isEnabled: Boolean, role: Role, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, enabled = isEnabled, role = role, onClick = onClick)

internal fun requireButtonContent(label: String?, icon: ImageVector?, description: String?) {
    require(label != null || icon != null) { "У кнопки нет ни подписи, ни значка" }
    require(label != null || description != null) { "Кнопка-значок без подписи должна иметь описание для доступности" }
}

/**
 * Кнопка макета. Минимум 64 × 64 dp. [label] и [icon] можно сочетать; кнопка только со значком обязана иметь
 * [description] (в макете это `aria-label`). Описание есть и у кнопки с подписью, если причина недоступности или
 * смысл шире подписи: оно читается вместо неё. Размер задаёт [modifier] (квадратные кнопки шапки и нижней строки).
 */
@Composable
fun DecklyButton(
    label: String?,
    icon: ImageVector?,
    kind: ButtonKind,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textSize: TextUnit = Tokens.ButtonText,
) {
    requireButtonContent(label, icon, description)
    val isEnabled = kind != ButtonKind.OFF
    val contentColor = if (isEnabled) Tokens.Black else Tokens.Unavailable
    val borderWidth = if (kind == ButtonKind.PRIMARY) Tokens.BorderThick else Tokens.BorderMedium
    val borderColor = if (kind == ButtonKind.PRIMARY || kind == ButtonKind.NORMAL) Tokens.Black else Color.Transparent
    val shape = RoundedCornerShape(Tokens.RadiusSmall)
    Row(
        modifier = modifier
            .defaultMinSize(Tokens.ControlMin, Tokens.ControlMin)
            .clickSilently(isEnabled, Role.Button, onClick)
            .semantics { if (description != null) contentDescription = description }
            .background(Tokens.White, shape)
            .border(borderWidth, borderColor, shape)
            // Рамка занимает место, как в макете (border-box): у тихой и недоступной она прозрачна.
            .padding(borderWidth)
            .padding(horizontal = Tokens.ButtonPaddingHorizontal, vertical = Tokens.ButtonPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(if (label != null && icon != null) Tokens.ButtonGap else 0.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val iconSize = if (label == null) Tokens.IconLarge else Tokens.IconDefault
            Image(icon, contentDescription = null, modifier = Modifier.size(iconSize), colorFilter = ColorFilter.tint(contentColor))
        }
        if (label != null) {
            val weight = if (kind == ButtonKind.QUIET) FontWeight.Normal else FontWeight.SemiBold
            BasicText(label, style = buildTextStyle(textSize, weight, contentColor, lineHeightFactor = 1.2f, align = TextAlign.Center))
        }
    }
}

/** Шапка экрана: ряд высотой 64 dp с промежутком 24 dp. Название, счётчики и кнопки кладёт экран. */
@Composable
fun TopBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(Tokens.BarHeight),
        horizontalArrangement = Arrangement.spacedBy(Tokens.BarGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Счётчик шапки: подпись и значение; слева линия в 1 dp, отступ 24 dp, минимум 132 dp. */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(min = Tokens.MetricMinWidth)
            .drawWithContent {
                drawContent()
                drawRect(Tokens.Black, Offset.Zero, Size(Tokens.BorderThin.toPx(), size.height))
            }
            .padding(start = Tokens.BorderThin + Tokens.MetricPadding),
        verticalArrangement = Arrangement.spacedBy(Tokens.MetricGap),
    ) {
        BasicText(label, style = buildTextStyle(Tokens.LabelText))
        BasicText(value, style = buildTextStyle(Tokens.ValueText, FontWeight.Medium), softWrap = false)
    }
}

/**
 * Полоса из шести вкладок секций. Открытая подчёркнута и жирная, активная эпоха отмечена ▶ справа от числа, число
 * при этом остаётся по центру вкладки. Открытие — нажатие на вкладку.
 */
@Composable
fun SectionTabs(tabs: List<TabState>, onOpen: (SectionTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Tokens.TabsHeight)
            .drawWithContent {
                drawContent()
                val line = Tokens.BorderThin.toPx()
                drawRect(Tokens.Black, Offset(0f, size.height - line), Size(size.width, line))
            }
            .padding(bottom = Tokens.BorderThin),
        horizontalArrangement = Arrangement.spacedBy(Tokens.TabsGap),
    ) {
        for (tab in tabs) SectionTabCell(tab, onOpen, Modifier.weight(1f))
    }
}

@Composable
private fun SectionTabCell(tab: TabState, onOpen: (SectionTab) -> Unit, modifier: Modifier) {
    val description = buildString {
        append(tab.section.toLabel()).append(", ").append(formatCardCount(tab.count))
        if (tab.isActive) append(", сейчас обучение")
    }
    val weight = if (tab.isOpen) FontWeight.Bold else FontWeight.Normal
    Column(
        modifier
            .fillMaxHeight()
            .clickSilently(true, Role.Tab) { onOpen(tab.section) }
            .semantics {
                selected = tab.isOpen
                contentDescription = description
            },
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = Tokens.TabPaddingBottom),
            verticalArrangement = Arrangement.spacedBy(Tokens.TabGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(tab.section.toLabel(), style = buildTextStyle(Tokens.TabLabelText, weight), softWrap = false)
            CountWithActiveMark(tab.count.toString(), tab.isActive, weight)
        }
        // Подчёркивание открытой вкладки занимает место и у остальных: полоса остаётся ровной.
        Box(Modifier.fillMaxWidth().height(Tokens.TabUnderline).background(if (tab.isOpen) Tokens.Black else Color.Transparent))
    }
}

// Число по центру вкладки, отметка ▶ приставлена справа и размер числа не меняет (в макете она position:absolute).
@Composable
private fun CountWithActiveMark(count: String, isActive: Boolean, weight: FontWeight) {
    Layout(
        content = {
            BasicText(count, style = buildTextStyle(Tokens.TabCountText, weight), softWrap = false)
            if (isActive) {
                Image(DecklyIcons.Active, contentDescription = null, modifier = Modifier.size(Tokens.IconSmall), colorFilter = ColorFilter.tint(Tokens.Black))
            }
        },
    ) { measurables, constraints ->
        val text = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val mark = measurables.getOrNull(1)?.measure(Constraints())
        layout(text.width, text.height) {
            text.place(0, 0)
            mark?.place(text.width + Tokens.ActiveMarkGap.roundToPx(), (text.height - mark.height) / 2)
        }
    }
}

/**
 * Лист карточки: четыре угловые метки (две линии по 18 dp толщиной 2 dp) обозначают границу без рамки. Содержимое лежит
 * в центре, полей нет: холст рисования занимает лист целиком.
 */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier.drawWithContent {
            drawContent()
            val length = Tokens.SheetCorner.toPx()
            val thickness = Tokens.BorderMedium.toPx()
            val right = size.width
            val bottom = size.height
            val black = Tokens.Black
            fun mark(x: Float, y: Float, width: Float, height: Float) = drawRect(black, Offset(x, y), Size(width, height))
            mark(0f, 0f, length, thickness)
            mark(0f, 0f, thickness, length)
            mark(right - length, 0f, length, thickness)
            mark(right - thickness, 0f, thickness, length)
            mark(0f, bottom - thickness, length, thickness)
            mark(0f, bottom - length, thickness, length)
            mark(right - length, bottom - thickness, length, thickness)
            mark(right - thickness, bottom - length, thickness, length)
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

internal sealed interface FooterColumn {
    data class Fixed(val width: Dp) : FooterColumn

    data object Flexible : FooterColumn
}

/** Сетки нижней строки макета: `112 112 1fr 1fr`, `112 112 1fr 112` (просмотр), `1fr 1fr` и `1fr`. */
enum class FooterLayout(internal val columns: List<FooterColumn>) {
    LEARNING(listOf(FooterColumn.Fixed(Tokens.FooterSquare), FooterColumn.Fixed(Tokens.FooterSquare), FooterColumn.Flexible, FooterColumn.Flexible)),
    NAVIGATION(listOf(FooterColumn.Fixed(Tokens.FooterSquare), FooterColumn.Fixed(Tokens.FooterSquare), FooterColumn.Flexible, FooterColumn.Fixed(Tokens.FooterSquare))),
    TWO(listOf(FooterColumn.Flexible, FooterColumn.Flexible)),
    ONE(listOf(FooterColumn.Flexible)),
}

internal fun requireFooterCells(layout: FooterLayout, cellCount: Int) {
    require(cellCount <= layout.columns.size) { "В раскладке $layout ${layout.columns.size} колонок, а ячеек $cellCount" }
}

/**
 * Нижняя строка. [cells] идут по колонкам раскладки; `null` — пустая колонка, остальные остаются на своих местах
 * (в обучении на вопросе нет оценок, но переворот не сдвигается). Ячейка получает модификатор, заполняющий колонку.
 */
@Composable
fun Footer(
    layout: FooterLayout,
    cells: List<(@Composable (Modifier) -> Unit)?>,
    modifier: Modifier = Modifier,
    height: Dp = Tokens.FooterHeight,
) {
    requireFooterCells(layout, cells.size)
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(Tokens.FooterGap)) {
        layout.columns.forEachIndexed { index, column ->
            val columnModifier = when (column) {
                is FooterColumn.Fixed -> Modifier.width(column.width).fillMaxHeight()
                FooterColumn.Flexible -> Modifier.weight(1f).fillMaxHeight()
            }
            Box(columnModifier) { cells.getOrNull(index)?.invoke(Modifier.fillMaxSize()) }
        }
    }
}
