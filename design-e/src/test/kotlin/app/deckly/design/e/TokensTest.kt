package app.deckly.design.e

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.deckly.design.e.MockupSources.buildScript
import app.deckly.design.e.MockupSources.findCssNumber
import app.deckly.design.e.MockupSources.findCssValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        assertSp(findCssNumber(".metric>span", "font-size"), Tokens.LabelText)
        assertSp(findCssNumber(".metric-value", "font-size"), Tokens.ValueText)
    }

    @Test
    fun metricValueGridMatchesStyles() {
        assertDp(findCssNumber(".metric-value", "column-gap"), Tokens.MetricValueGap)
        assertEquals("minmax(3ch,1fr) auto minmax(3ch,1fr)", findCssValue(".metric-value", "grid-template-columns"))
        assertEquals(3, Tokens.MetricSideDigits)
        assertEquals("500", findCssValue(".metric-value", "font-weight"))
        assertEquals("right", findCssValue(".metric-value>span:first-child", "text-align"))
        assertEquals("left", findCssValue(".metric-value>span:last-child", "text-align"))
        assertEquals("center", findCssValue(".metric>span", "text-align"))
    }

    @Test
    fun nextEpochShiftAndLoadingBrandMatchStyles() {
        assertEquals("translateX(-6px)", findCssValue(".card .next-epoch", "transform"))
        assertDp(findCssNumber(".card .next-epoch", "transform"), Tokens.NextEpochShift)
        assertSp(findCssNumber(".loading-brand h1", "font-size"), Tokens.LoadingBrandText)
        assertSp(findCssNumber(".loading-brand h1", "letter-spacing"), Tokens.LoadingBrandSpacing)
        assertEquals(findCssNumber(".loading-brand h1", "line-height"), Tokens.LoadingBrandLineHeightFactor, 0f)
        assertEquals("700", findCssValue(".loading-brand h1", "font-weight"))
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
    fun sheetMessagesAndDeckHeadingMatchStyles() {
        assertDp(findCssNumber(".card", "padding"), Tokens.SheetPadding)
        assertDp(findCssNumber(".card", "gap"), Tokens.SheetGap)
        assertDp(findCssNumber(".card .empty-icon", "margin-bottom"), Tokens.EmptyIconGap)
        assertDp(findCssNumber(".card h2", "max-width"), Tokens.EmptyTitleMaxWidth)
        assertDp(findCssNumber(".card .hint", "max-width"), Tokens.HintMaxWidth)
        assertSp(findCssNumber(".card h2", "font-size"), Tokens.EmptyTitleText)
        assertSp(findCssNumber(".card .big", "font-size"), Tokens.BigNumeralText)
        assertSp(findCssNumber(".card .hint", "font-size"), Tokens.HintText)
        assertDp(findCssNumber(".deck", "gap"), Tokens.DeckHeadingGap)
        assertDp(findCssNumber(".deck svg", "width"), Tokens.DeckArrow)
    }

    @Test
    fun letterSpacingsMatchStyles() {
        assertSp(findCssNumber(".deck", "letter-spacing"), Tokens.DeckHeadingSpacing)
        assertSp(findCssNumber(".dialog h2", "letter-spacing"), Tokens.DialogTitleSpacing)
        assertSp(findCssNumber(".card h2", "letter-spacing"), Tokens.EmptyTitleSpacing)
        assertSp(findCssNumber(".card .big", "letter-spacing"), Tokens.BigNumeralSpacing)
    }

    @Test
    fun editorTokensMatchStyles() {
        assertDp(findCssNumber(".side-tabs", "height"), Tokens.SideTabsHeight)
        assertDp(findCssNumber(".side-tabs", "gap"), Tokens.SideTabsGap)
        assertDp(findCssNumber(".side-tab", "min-height"), Tokens.ControlMin)
        assertDp(findCssNumber(".side-tab", "border-bottom"), Tokens.TabUnderline)
        assertSp(findCssNumber(".side-tab", "font-size"), Tokens.BodyText)
        assertDp(findCssNumber(".tools", "height"), Tokens.ToolsHeight)
        assertDp(findCssNumber(".tools", "gap"), Tokens.ToolsGap)
        assertDp(findCssNumber(".tools>.btn", "width"), Tokens.ToolButton)
        assertDp(findCssNumber(".thickness", "gap"), Tokens.ThicknessGap)
        assertDp(findCssNumber(".thickness", "margin", 0), Tokens.ThicknessMarginVertical)
        assertDp(findCssNumber(".thickness .btn", "width"), Tokens.ThicknessButtonWidth)
        assertDp(findCssNumber(".thickness .btn", "height"), Tokens.ThicknessButtonHeight)
        assertDp(findCssNumber(".line-sample", "width"), Tokens.LineSampleWidth)
        assertDp(findCssNumber(".line-sample", "border-radius"), Tokens.LineSampleRadius)
        assertSp(findCssNumber(".heading h1", "letter-spacing"), Tokens.HeadingSpacing)
        // Высоты образцов линии заданы в build.py, а не в стилях.
        assertTrue(buildScript.contains("[('Thin', 2), ('Medium', 7), ('Thick', 15)]"))
        assertEquals(listOf(2f, 7f, 15f), listOf(Tokens.LineThin, Tokens.LineMedium, Tokens.LineThick).map { it.value })
    }

    @Test
    fun thicknessGroupOfMockupHasNoSideLinesOrPadding() {
        // Переключателя инструмента нет, группа толщин стоит первой слева: разделять её не от чего.
        for (property in listOf("border-left", "border-right", "padding")) {
            assertThrows(property, IllegalStateException::class.java) { findCssValue(".thickness", property) }
        }
        assertFalse(buildScript.contains("icon('eraser')"))
        assertFalse(buildScript.contains("aria-label=\"Eraser\""))
        assertFalse(buildScript.contains("aria-label=\"Pen\""))
    }

    @Test
    fun deckListAndFieldTokensMatchStyles() {
        assertDp(findCssNumber(".list", "margin-top"), Tokens.ListMarginTop)
        assertDp(findCssNumber(".item", "min-height"), Tokens.ItemMinHeight)
        assertDp(findCssNumber(".item", "gap"), Tokens.ItemGap)
        assertDp(findCssNumber(".item .fill", "min-height"), Tokens.ItemFillMinHeight)
        assertDp(findCssNumber(".item .fill", "gap"), Tokens.ItemFillGap)
        assertDp(findCssNumber(".item .fill", "padding"), Tokens.ItemFillPaddingVertical)
        assertSp(findCssNumber(".item h2", "font-size"), Tokens.ItemTitleText)
        assertSp(findCssNumber(".item p", "font-size"), Tokens.ItemInfoText)
        assertDp(findCssNumber(".item p", "gap"), Tokens.ItemInfoGap)
        assertDp(findCssNumber(".item p .icon", "width"), Tokens.IconSmall)
        assertSp(findCssNumber(".item .number", "font-size"), Tokens.ItemNumberText)
        assertDp(findCssNumber(".item .number", "width"), Tokens.ItemNumberWidth)
        assertDp(findCssNumber(".item .number .icon", "width"), Tokens.ItemNumberIcon)
        assertDp(findCssNumber(".field", "gap"), Tokens.FieldGap)
        assertDp(findCssNumber(".field", "margin-top"), Tokens.FieldMarginTop)
        assertSp(findCssNumber(".field", "font-size"), Tokens.FieldLabelText)
        assertDp(findCssNumber(".input", "height"), Tokens.InputHeight)
        assertDp(findCssNumber(".input", "border"), Tokens.BorderMedium)
        assertDp(findCssNumber(".input", "border-radius"), Tokens.RadiusSmall)
        assertDp(findCssNumber(".input", "padding"), Tokens.InputPadding)
        assertSp(findCssNumber(".input", "font-size"), Tokens.InputText)
    }

    @Test
    fun titlesMatchStyles() {
        assertSp(findCssNumber(".heading h1", "font-size"), Tokens.TitleText)
        assertSp(findCssNumber(".deck", "font-size"), Tokens.TitleText)
    }

    @Test
    fun shapePaletteMatchesStyles() {
        assertDp(findCssNumber(".shape-palette", "left"), Tokens.ShapePaletteLeft)
        assertDp(findCssNumber(".shape-palette", "bottom"), Tokens.ShapePaletteBottom)
        assertDp(findCssNumber(".shape-palette", "width"), Tokens.ShapePaletteWidth)
        assertDp(findCssNumber(".shape-palette", "padding", index = 0), Tokens.ShapePalettePaddingTop)
        assertDp(findCssNumber(".shape-palette", "padding", index = 1), Tokens.ShapePalettePaddingHorizontal)
        assertDp(findCssNumber(".shape-palette", "padding", index = 2), Tokens.ShapePalettePaddingBottom)
        assertDp(findCssNumber(".shape-palette", "border"), Tokens.BorderMedium)
        assertDp(findCssNumber(".shape-palette", "border-radius"), Tokens.RadiusLarge)
        assertDp(findCssNumber(".shape-palette", "gap"), Tokens.ShapePaletteGap)
        assertSp(findCssNumber(".shape-palette h2", "font-size"), Tokens.ShapePaletteTitleText)
        assertSp(findCssNumber(".shape-palette h2", "letter-spacing"), Tokens.ShapePaletteTitleSpacing)
    }

    @Test
    fun shapeGridAndChoicesMatchStyles() {
        assertEquals("repeat(5,1fr)", findCssValue(".shape-grid", "grid-template-columns"))
        assertEquals(5, Tokens.ShapeGridColumns)
        assertDp(findCssNumber(".shape-grid", "gap"), Tokens.ShapeGridGap)
        assertDp(findCssNumber(".shape-choice", "height"), Tokens.ShapeChoiceHeight)
        assertDp(findCssNumber(".shape-choice", "border"), Tokens.BorderMedium)
        assertEquals("2px solid transparent", findCssValue(".shape-choice", "border"))
        assertDp(findCssNumber(".shape-choice", "border-radius"), Tokens.RadiusSmall)
        assertDp(findCssNumber(".shape-choice .shape-graphic", "width"), Tokens.ShapeChoiceGlyph)
        assertDp(findCssNumber(".shape-choice .shape-graphic", "height"), Tokens.ShapeChoiceGlyph)
        // Выбранный вид в рамке, но в палитре выбранного нет (проверяют тесты палитры).
        assertEquals("#000", findCssValue(".shape-choice[aria-current=\"true\"]", "border-color"))
    }

    @Test
    fun outlineWidthSectionAndSliderMatchStyles() {
        assertEquals("1px solid #000", findCssValue(".shape-width", "border-top"))
        assertDp(findCssNumber(".shape-width", "border-top"), Tokens.BorderThin)
        assertDp(findCssNumber(".shape-width", "padding-top"), Tokens.ShapeWidthPaddingTop)
        assertEquals("1fr auto 80px", findCssValue(".shape-width", "grid-template-columns"))
        assertDp(findCssNumber(".shape-width", "grid-template-columns", index = 1), Tokens.ShapeWidthPreview)
        assertDp(findCssNumber(".shape-width", "column-gap"), Tokens.ShapeWidthColumnGap)
        assertSp(findCssNumber(".shape-width", "font-size"), Tokens.ShapeWidthText)
        assertDp(findCssNumber(".stroke-preview", "width"), Tokens.ShapeWidthPreview)
        assertDp(findCssNumber(".stroke-preview", "height"), Tokens.ShapeWidthPreviewHeight)
        assertDp(findCssNumber(".shape-width input", "height"), Tokens.ShapeSliderHeight)
        assertDp(findCssNumber(".shape-width input", "margin", index = 0), Tokens.ShapeSliderMarginTop)
        assertDp(findCssNumber(".shape-width input::-webkit-slider-runnable-track", "height"), Tokens.ShapeSliderTrack)
        assertDp(findCssNumber(".shape-width input::-webkit-slider-thumb", "width"), Tokens.ShapeSliderThumb)
        assertDp(findCssNumber(".shape-width input::-webkit-slider-thumb", "height"), Tokens.ShapeSliderThumb)
        assertEquals("3px solid #000", findCssValue(".shape-width input::-webkit-slider-thumb", "border"))
        assertDp(findCssNumber(".shape-width input::-webkit-slider-thumb", "border"), Tokens.ShapeSliderThumbBorder)
        assertEquals("#fff", findCssValue(".shape-width input::-webkit-slider-thumb", "background"))
        assertTrue(buildScript.contains("type=\"range\" min=\"${Tokens.OutlineWidthMin}\" max=\"${Tokens.OutlineWidthMax}\" step=\"1\""))
    }

    @Test
    fun shapeHandlesAndFrameMatchStyles() {
        assertDp(findCssNumber(".shape-handle", "width"), Tokens.ShapeHandleTarget)
        assertDp(findCssNumber(".shape-handle", "height"), Tokens.ShapeHandleTarget)
        assertDp(findCssNumber(".shape-handle::after", "width"), Tokens.ShapeHandleMark)
        assertDp(findCssNumber(".shape-handle::after", "height"), Tokens.ShapeHandleMark)
        assertEquals("2px solid #000", findCssValue(".shape-handle::after", "border"))
        assertDp(findCssNumber(".shape-handle::after", "border"), Tokens.ShapeHandleMarkBorder)
        // Знак стоит в середине цели: (64 − 16) / 2 с каждой стороны.
        assertDp(findCssNumber(".shape-handle::after", "left"), (Tokens.ShapeHandleTarget - Tokens.ShapeHandleMark) / 2)
        assertDp(findCssNumber(".shape-handle::after", "top"), (Tokens.ShapeHandleTarget - Tokens.ShapeHandleMark) / 2)
        // Цель выступает за угол рамки наполовину.
        val sides = mapOf("nw" to listOf("top", "left"), "ne" to listOf("top", "right"), "sw" to listOf("bottom", "left"), "se" to listOf("bottom", "right"))
        for ((corner, properties) in sides) {
            for (property in properties) assertDp(findCssNumber(".shape-handle.$corner", property), -Tokens.ShapeHandleOffset)
        }
        assertEquals("1px dashed #000", findCssValue(".shape-object.is-selected", "outline"))
        assertDp(findCssNumber(".shape-object.is-selected", "outline"), Tokens.ShapeFrameDash)
        assertEquals(findCssNumber(".shape-handle.start", "left"), Tokens.ShapeStartShare * 100f, 0.01f)
        assertEquals(findCssNumber(".shape-handle.end", "left"), Tokens.ShapeEndShare * 100f, 0.01f)
        assertEquals("50%", findCssValue(".shape-handle.start", "top").substringAfter("calc(").substringBefore(" "))
        assertEquals("border-radius", "50%", findCssValue(".shape-handle.start::after,.shape-handle.end::after", "border-radius"))
    }

    @Test
    fun rotateHandleAndEditToolsMatchStyles() {
        assertDp(findCssNumber(".shape-rotate", "width"), Tokens.ShapeHandleTarget)
        assertDp(findCssNumber(".shape-rotate", "height"), Tokens.ShapeHandleTarget)
        assertDp(findCssNumber(".shape-rotate", "top"), -Tokens.ShapeRotateTop)
        assertEquals("calc(50% - 32px)", findCssValue(".shape-rotate", "left"))
        assertDp(findCssNumber(".shape-rotate .icon", "width"), Tokens.ShapeRotateIcon)
        assertDp(findCssNumber(".shape-rotate .icon", "height"), Tokens.ShapeRotateIcon)
        assertDp(findCssNumber(".shape-rotate::before", "top"), Tokens.ShapeHandleTarget)
        assertDp(findCssNumber(".shape-rotate::before", "left"), Tokens.ShapeHandleTarget / 2 - Tokens.ShapeRotateLineWidth)
        assertDp(findCssNumber(".shape-rotate::before", "width"), Tokens.ShapeRotateLineWidth)
        assertDp(findCssNumber(".shape-rotate::before", "height"), Tokens.ShapeRotateLineHeight)
        assertDp(findCssNumber(".shape-edit-tools .shape-graphic", "width"), Tokens.ShapeEditSample)
        assertDp(findCssNumber(".shape-edit-tools .shape-graphic", "height"), Tokens.ShapeEditSample)
    }

    @Test
    fun controlsAndTextMeetDeviceMinimums() {
        // ТЗ: цель касания не меньше 64 dp, текст не меньше 18 sp.
        assertTrue(Tokens.ControlMin >= 64.dp)
        val texts = listOf(
            Tokens.LabelText, Tokens.ValueText, Tokens.TitleText, Tokens.ButtonText, Tokens.FooterButtonText,
            Tokens.TabLabelText, Tokens.TabCountText, Tokens.DialogTitleText, Tokens.BodyText, Tokens.EmptyTitleText,
            Tokens.BigNumeralText, Tokens.HintText,
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
