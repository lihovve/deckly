package app.deckly.design.e

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Размеры и цвета варианта E «Ясный лист» из `design/styles.css`. Единица макета — 1 dp, кегль макета — 1 sp: на
 * Go 10.3 Gen II плотность 1,875, кадр 992 dp равен 1860 px. `TokensTest` сверяет значения с самим файлом стилей.
 *
 * Правила e-ink из ТЗ: цель касания не меньше 64 dp, текст не меньше 18 sp, только чёрный, белый и серый, без теней,
 * градиентов и анимаций.
 */
object Tokens {
    // --- кадр и шапка ---
    val FrameWidth = 992.dp
    val FrameHeight = 1323.dp
    val FrameMargin = 32.dp
    val FrameGap = 16.dp
    val BarHeight = 64.dp
    val BarGap = 24.dp
    val MetricMinWidth = 132.dp
    val MetricPadding = 24.dp
    val MetricGap = 2.dp
    val MetricValueGap = 8.dp

    /** Боковые колонки значения счётчика не уже трёх нулей его шрифта: `minmax(3ch,1fr)` в `.metric-value`. */
    const val MetricSideDigits = 3

    // --- кнопки и значки ---
    val ControlMin = 64.dp
    val ButtonGap = 12.dp
    val ButtonPaddingVertical = 12.dp
    val ButtonPaddingHorizontal = 18.dp
    val IconDefault = 26.dp
    val IconLarge = 32.dp
    val IconSmall = 18.dp
    val IconEmpty = 64.dp
    const val IconStroke = 1.8f
    const val IconEmptyStroke = 1.2f

    // --- вкладки секций ---
    val TabsHeight = 72.dp
    val TabsGap = 8.dp
    val TabGap = 5.dp
    val TabPaddingBottom = 8.dp
    val TabUnderline = 4.dp
    val ActiveMarkGap = 6.dp

    // --- лист и нижняя строка ---
    val SheetCorner = 18.dp
    val FooterHeight = 112.dp
    val FooterGap = 16.dp
    val FooterSquare = 112.dp
    val DialogFooterHeight = 88.dp

    // --- окно и меню ---
    val DialogWidth = 736.dp
    val DialogPadding = 40.dp
    val DialogGap = 24.dp
    val DialogRing = 12.dp
    val DialogFooterGap = 12.dp
    val PopupTop = 108.dp
    val PopupEnd = 32.dp
    val PopupWidth = 432.dp
    val PopupPaddingVertical = 12.dp
    val PopupPaddingHorizontal = 24.dp
    val PopupTitlePaddingTop = 12.dp
    val PopupTitlePaddingBottom = 16.dp
    val PopupItemHeight = 80.dp
    val PopupItemGap = 16.dp

    // --- лист: пустая секция и экран перехода ---
    val SheetPadding = 48.dp
    val SheetGap = 28.dp
    val EmptyIconGap = 4.dp
    val EmptyTitleMaxWidth = 700.dp
    val HintMaxWidth = 580.dp

    // --- название колоды в шапке ---
    val DeckHeadingGap = 10.dp
    val DeckArrow = 20.dp

    // --- редактор: вкладки сторон и инструменты ---
    val SideTabsHeight = 72.dp
    val SideTabsGap = 32.dp
    val ToolsHeight = 112.dp
    val ToolsGap = 16.dp
    val ToolButton = 112.dp
    val ThicknessGap = 4.dp
    val ThicknessMarginVertical = 12.dp
    val ThicknessButtonWidth = 80.dp
    val ThicknessButtonHeight = 72.dp
    val LineSampleWidth = 34.dp
    val LineSampleRadius = 8.dp
    val LineThin = 2.dp
    val LineMedium = 7.dp
    val LineThick = 15.dp

    // --- фигуры: палитра (`.shape-palette` и вложенные правила) ---
    val ShapePaletteLeft = 32.dp
    val ShapePaletteBottom = 160.dp
    val ShapePaletteWidth = 672.dp
    val ShapePalettePaddingTop = 24.dp
    val ShapePalettePaddingHorizontal = 32.dp
    val ShapePalettePaddingBottom = 28.dp
    val ShapePaletteGap = 20.dp
    val ShapePaletteTitleText = 28.sp
    val ShapePaletteTitleSpacing = (-0.6).sp
    const val ShapeGridColumns = 5
    val ShapeGridGap = 8.dp
    val ShapeChoiceHeight = 104.dp
    val ShapeChoiceGlyph = 56.dp
    val ShapeWidthPaddingTop = 24.dp
    val ShapeWidthColumnGap = 20.dp
    val ShapeWidthPreview = 80.dp
    val ShapeWidthPreviewHeight = 24.dp
    val ShapeWidthText = 22.sp
    val ShapeSliderHeight = 64.dp
    val ShapeSliderMarginTop = 8.dp
    val ShapeSliderTrack = 2.dp
    val ShapeSliderThumb = 32.dp
    val ShapeSliderThumbBorder = 3.dp
    const val OutlineWidthMin = 1
    const val OutlineWidthMax = 12

    // --- фигуры: рамка и ручки выделенной фигуры, образец на панели ---
    val ShapeHandleTarget = 64.dp
    val ShapeHandleMark = 16.dp
    val ShapeHandleMarkBorder = 2.dp
    val ShapeHandleOffset = 32.dp
    val ShapeRotateTop = 80.dp
    val ShapeRotateIcon = 24.dp
    val ShapeRotateLineWidth = 1.dp
    val ShapeRotateLineHeight = 16.dp
    val ShapeFrameDash = 1.dp
    val ShapeEditSample = 40.dp

    /** Доли ширины рамки линии, где стоят ручки концов: `.shape-handle.start` и `.end`. */
    const val ShapeStartShare = 0.12f
    const val ShapeEndShare = 0.88f

    // --- список колод и поле ввода ---
    val ListMarginTop = 16.dp
    val ItemMinHeight = 148.dp
    val ItemGap = 24.dp
    val ItemFillMinHeight = 112.dp
    val ItemFillGap = 9.dp
    val ItemFillPaddingVertical = 16.dp
    val ItemNumberWidth = 44.dp
    val ItemNumberIcon = 24.dp
    val ItemInfoGap = 8.dp
    val FieldGap = 16.dp
    val FieldMarginTop = 40.dp
    val InputHeight = 88.dp
    val InputPadding = 16.dp

    // --- рамки и скругления ---
    val BorderThin = 1.dp
    val BorderMedium = 2.dp
    val BorderThick = 3.dp
    val RadiusSmall = 8.dp
    val RadiusLarge = 12.dp

    // --- кегли ---
    val LabelText = 18.sp
    val ValueText = 26.sp
    val TitleText = 28.sp
    val ButtonText = 24.sp
    val FooterButtonText = 26.sp
    val TabLabelText = 20.sp
    val TabCountText = 24.sp
    val BodyText = 24.sp
    val DialogTitleText = 36.sp
    val EmptyTitleText = 48.sp
    val BigNumeralText = 80.sp
    val HintText = 24.sp
    val ItemTitleText = 30.sp
    val ItemInfoText = 20.sp
    val ItemNumberText = 20.sp
    val FieldLabelText = 20.sp
    val InputText = 28.sp

    // --- межбуквенный интервал заголовков ---
    val DeckHeadingSpacing = (-0.5).sp
    val HeadingSpacing = (-0.6).sp
    val DialogTitleSpacing = (-0.7).sp
    val EmptyTitleSpacing = (-1.3).sp
    val BigNumeralSpacing = (-3).sp

    /** Оптический сдвиг цифры эпохи на экране «Next pass»: `.card .next-epoch` в `styles.css`. */
    val NextEpochShift = (-6).dp

    // --- экран загрузки: `.loading-brand h1` ---
    val LoadingBrandText = 64.sp
    val LoadingBrandSpacing = (-2).sp
    const val LoadingBrandLineHeightFactor = 1.1f

    // --- цвета ---
    val Black = Color.Black
    val White = Color.White

    /** Недоступный элемент. В макете #555, но владелец просил светлее серого прототипа; цвет проверяется на планшете. */
    val Unavailable = Color(0xFFA0A0A0)
}
