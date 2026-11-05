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

    // --- межбуквенный интервал заголовков ---
    val DeckHeadingSpacing = (-0.5).sp
    val DialogTitleSpacing = (-0.7).sp
    val EmptyTitleSpacing = (-1.3).sp
    val BigNumeralSpacing = (-3).sp

    // --- цвета ---
    val Black = Color.Black
    val White = Color.White

    /** Недоступный элемент. В макете #555, но владелец просил светлее серого прототипа; цвет проверяется на планшете. */
    val Unavailable = Color(0xFFA0A0A0)
}
