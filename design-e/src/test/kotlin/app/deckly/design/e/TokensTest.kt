package app.deckly.design.e

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.deckly.design.e.MockupSources.findCssNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Токены берутся из `design/styles.css` один к одному: единица макета — 1 dp, кегль макета — 1 sp. */
class TokensTest {
    @Test
    fun frameAndBarMatchStyles() {
        assertDp(findCssNumber(".screen", "width"), Tokens.FrameWidth)
        assertDp(findCssNumber(".screen", "height"), Tokens.FrameHeight)
        assertDp(findCssNumber(".screen", "padding"), Tokens.FrameMargin)
        assertDp(findCssNumber(".screen", "gap"), Tokens.FrameGap)
        assertDp(findCssNumber(".bar", "height"), Tokens.BarHeight)
        assertDp(findCssNumber(".bar", "gap"), Tokens.BarGap)
    }

    @Test
    fun metricMatchesStyles() {
        assertDp(findCssNumber(".metric", "min-width"), Tokens.MetricMinWidth)
        assertDp(findCssNumber(".metric", "padding-left"), Tokens.MetricPadding)
        assertDp(findCssNumber(".metric", "gap"), Tokens.MetricGap)
        assertSp(findCssNumber(".metric span", "font-size"), Tokens.LabelText)
        assertSp(findCssNumber(".metric strong", "font-size"), Tokens.ValueText)
    }

    @Test
    fun buttonsMatchStyles() {
        assertDp(findCssNumber(".btn", "min-width"), Tokens.ControlMin)
        assertDp(findCssNumber(".btn", "min-height"), Tokens.ControlMin)
        assertDp(findCssNumber(".btn.square", "width"), Tokens.ControlMin)
        assertDp(findCssNumber(".btn", "gap"), Tokens.ButtonGap)
        assertDp(findCssNumber(".btn", "border"), Tokens.BorderMedium)
        assertDp(findCssNumber(".btn.primary", "border-width"), Tokens.BorderThick)
        assertDp(findCssNumber(".btn", "border-radius"), Tokens.RadiusSmall)
        assertDp(findCssNumber(".btn", "padding", index = 0), Tokens.ButtonPaddingVertical)
        assertDp(findCssNumber(".btn", "padding", index = 1), Tokens.ButtonPaddingHorizontal)
        assertSp(findCssNumber(".btn", "font-size"), Tokens.ButtonText)
        assertSp(findCssNumber(".foot .btn", "font-size"), Tokens.FooterButtonText)
    }

    @Test
    fun iconsMatchStyles() {
        assertDp(findCssNumber(".icon", "width"), Tokens.IconDefault)
        assertDp(findCssNumber(".icon-only .icon", "width"), Tokens.IconLarge)
        assertDp(findCssNumber(".cell.active .icon", "width"), Tokens.IconSmall)
        assertDp(findCssNumber(".card .empty-icon", "width"), Tokens.IconEmpty)
        assertEquals(findCssNumber(".icon", "stroke-width"), Tokens.IconStroke, 0f)
        assertEquals(findCssNumber(".card .empty-icon .icon", "stroke-width"), Tokens.IconEmptyStroke, 0f)
    }

    @Test
    fun tabsMatchStyles() {
        assertDp(findCssNumber(".strip", "height"), Tokens.TabsHeight)
        assertDp(findCssNumber(".strip", "gap"), Tokens.TabsGap)
        assertDp(findCssNumber(".strip", "border-bottom"), Tokens.BorderThin)
        assertDp(findCssNumber(".cell", "gap"), Tokens.TabGap)
        assertDp(findCssNumber(".cell", "padding-bottom"), Tokens.TabPaddingBottom)
        assertDp(findCssNumber(".cell", "border-bottom"), Tokens.TabUnderline)
        assertSp(findCssNumber(".cell>span", "font-size"), Tokens.TabLabelText)
        assertSp(findCssNumber(".cell strong", "font-size"), Tokens.TabCountText)
        assertDp(findCssNumber(".cell.active strong>.icon", "left", index = 1), Tokens.ActiveMarkGap)
    }

    @Test
    fun sheetAndFooterMatchStyles() {
        assertDp(findCssNumber(".corner", "width"), Tokens.SheetCorner)
        assertDp(findCssNumber(".tl", "border-width", index = 0), Tokens.BorderMedium)
        assertDp(findCssNumber(".foot", "height"), Tokens.FooterHeight)
        assertDp(findCssNumber(".foot", "gap"), Tokens.FooterGap)
        assertDp(findCssNumber(".foot", "grid-template-columns", index = 0), Tokens.FooterSquare)
        assertDp(findCssNumber(".foot", "grid-template-columns", index = 1), Tokens.FooterSquare)
        assertDp(findCssNumber(".dialog .foot", "height"), Tokens.DialogFooterHeight)
    }

    @Test
    fun dialogAndPopupMatchStyles() {
        assertDp(findCssNumber(".dialog", "width"), Tokens.DialogWidth)
        assertDp(findCssNumber(".dialog", "border"), Tokens.BorderThick)
        assertDp(findCssNumber(".dialog", "border-radius"), Tokens.RadiusLarge)
        assertDp(findCssNumber(".dialog", "padding"), Tokens.DialogPadding)
        assertDp(findCssNumber(".dialog", "gap"), Tokens.DialogGap)
        assertDp(findCssNumber(".dialog", "outline", index = 0), Tokens.DialogRing)
        assertSp(findCssNumber(".dialog h2", "font-size"), Tokens.DialogTitleText)
        assertSp(findCssNumber(".dialog p", "font-size"), Tokens.BodyText)
        assertDp(findCssNumber(".popup", "top"), Tokens.PopupTop)
        assertDp(findCssNumber(".popup", "right"), Tokens.PopupEnd)
        assertDp(findCssNumber(".popup", "width"), Tokens.PopupWidth)
        assertDp(findCssNumber(".popup", "border"), Tokens.BorderMedium)
        assertDp(findCssNumber(".popup", "border-radius"), Tokens.RadiusSmall)
        assertDp(findCssNumber(".popup", "padding", index = 0), Tokens.PopupPaddingVertical)
        assertDp(findCssNumber(".popup", "padding", index = 1), Tokens.PopupPaddingHorizontal)
        assertSp(findCssNumber(".popup h2", "font-size"), Tokens.LabelText)
        assertDp(findCssNumber(".popup a", "min-height"), Tokens.PopupItemHeight)
        assertDp(findCssNumber(".popup a", "gap"), Tokens.PopupItemGap)
        assertSp(findCssNumber(".popup a", "font-size"), Tokens.BodyText)
    }

    @Test
    fun titlesMatchStyles() {
        assertSp(findCssNumber(".heading h1", "font-size"), Tokens.TitleText)
        assertSp(findCssNumber(".deck", "font-size"), Tokens.TitleText)
    }

    @Test
    fun controlsAndTextMeetDeviceMinimums() {
        // ТЗ: цель касания не меньше 64 dp, текст не меньше 18 sp.
        assertTrue(Tokens.ControlMin >= 64.dp)
        val texts = listOf(
            Tokens.LabelText, Tokens.ValueText, Tokens.TitleText, Tokens.ButtonText, Tokens.FooterButtonText,
            Tokens.TabLabelText, Tokens.TabCountText, Tokens.DialogTitleText, Tokens.BodyText,
        )
        assertTrue(texts.all { it >= 18.sp })
    }

    @Test
    fun colorsAreBlackWhiteAndLightGreyForUnavailable() {
        assertEquals(Color.Black, Tokens.Black)
        assertEquals(Color.White, Tokens.White)
        // В макете недоступное #555; владелец просил светлее серого прототипа, цвет проверяется на планшете.
        assertEquals(Color(0xFFA0A0A0), Tokens.Unavailable)
    }

    private fun assertDp(expected: Float, actual: Dp) = assertEquals(expected, actual.value, 0f)

    private fun assertSp(expected: Float, actual: TextUnit) = assertEquals(expected, actual.value, 0f)
}
