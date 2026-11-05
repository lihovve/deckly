package app.deckly.design.e

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.down
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.deckly.ui.contract.SectionTab
import app.deckly.ui.contract.TabState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    // --- кнопки --------------------------------------------------------------------------------

    @Test
    fun buttonsAreAtLeast64dp() {
        showOnWhite {
            Column {
                DecklyButton("Ок", null, ButtonKind.NORMAL, null, {}, Modifier.testTag("text"))
                DecklyButton(null, DecklyIcons.Plus, ButtonKind.QUIET, "Создать карточку", {}, Modifier.testTag("icon"))
                DecklyButton("Знал", DecklyIcons.Check, ButtonKind.PRIMARY, null, {}, Modifier.testTag("labelled"))
                DecklyButton(null, DecklyIcons.Defer, ButtonKind.OFF, "Недоступно", {}, Modifier.testTag("off"))
            }
        }

        for (tag in listOf("text", "icon", "labelled", "off")) {
            composeRule.onNodeWithTag(tag).assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp)
        }
    }

    @Test
    fun iconButtonsFollowBorderAndPaddingOfMockup() {
        showOnWhite {
            Column {
                for (kind in ButtonKind.entries) {
                    DecklyButton(null, DecklyIcons.Plus, kind, "Создать", {}, Modifier.testTag(kind.name))
                }
            }
        }

        // Значок 32 + поля 2 × 18 + рамка с обеих сторон: 72 при рамке 2 (рамка занимает место и у прозрачной) и 74 при рамке 3.
        composeRule.onNodeWithTag("NORMAL").assertWidthIsEqualTo(72.dp).assertHeightIsEqualTo(64.dp)
        composeRule.onNodeWithTag("PRIMARY").assertWidthIsEqualTo(74.dp).assertHeightIsEqualTo(64.dp)
        composeRule.onNodeWithTag("QUIET").assertWidthIsEqualTo(72.dp).assertHeightIsEqualTo(64.dp)
        composeRule.onNodeWithTag("OFF").assertWidthIsEqualTo(72.dp).assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun iconBesideLabelIs26dpAndSeparatedBy12dp() {
        showOnWhite {
            Column {
                DecklyButton("Знал", DecklyIcons.Check, ButtonKind.NORMAL, null, {}, Modifier.testTag("with"))
                DecklyButton("Знал", null, ButtonKind.NORMAL, null, {}, Modifier.testTag("without"))
            }
        }

        val withIcon = composeRule.onNodeWithTag("with").getUnclippedBoundsInRoot()
        val without = composeRule.onNodeWithTag("without").getUnclippedBoundsInRoot()
        val widthDifference = (withIcon.right.value - withIcon.left.value) - (without.right.value - without.left.value)
        assertEquals(26f + 12f, widthDifference, 0.05f)
    }

    @Test
    fun iconButtonsHaveDescriptions() {
        showOnWhite {
            Column {
                for (id in IconId.entries) {
                    DecklyButton(null, DecklyIcons.get(id), ButtonKind.NORMAL, "Значок ${id.key}", {})
                }
            }
        }

        for (id in IconId.entries) {
            composeRule.onNodeWithContentDescription("Значок ${id.key}")
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test
    fun buttonWithoutTextNeedsDescription() {
        assertTrue(runCatching { requireButtonContent(null, DecklyIcons.Plus, null) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { requireButtonContent(null, null, "x") }.exceptionOrNull() is IllegalArgumentException)
        requireButtonContent(null, DecklyIcons.Plus, "Создать")
        requireButtonContent("Знал", null, null)
        requireButtonContent("Знал", DecklyIcons.Check, null)
    }

    @Test
    fun descriptionOfLabelledButtonReplacesText() {
        showOnWhite {
            DecklyButton("В конец", DecklyIcons.Defer, ButtonKind.OFF, "В конец прохода: ответ уже открыт", {})
        }

        composeRule.onNodeWithContentDescription("В конец прохода: ответ уже открыт").assertIsNotEnabled()
    }

    @Test
    fun enabledButtonsReportClicks() {
        var clicks = 0
        showOnWhite {
            Column {
                DecklyButton("Знал", null, ButtonKind.PRIMARY, null, { clicks++ }, Modifier.testTag("primary"))
                DecklyButton("Оставить", null, ButtonKind.NORMAL, null, { clicks += 10 }, Modifier.testTag("normal"))
                DecklyButton("Отмена", null, ButtonKind.QUIET, null, { clicks += 100 }, Modifier.testTag("quiet"))
            }
        }

        composeRule.onNodeWithTag("primary").performClick()
        composeRule.onNodeWithTag("normal").performClick()
        composeRule.onNodeWithTag("quiet").performClick()

        assertEquals(111, clicks)
    }

    @Test
    fun offButtonIsDisabledAndGrey() {
        var clicks = 0
        showOnWhite {
            DecklyButton(null, DecklyIcons.Defer, ButtonKind.OFF, "В конец прохода: ответ уже открыт", { clicks++ }, Modifier.testTag("off"))
        }

        composeRule.onNodeWithTag("off").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)

        val inked = capture("off").listPixels().filter { it != AndroidColor.WHITE }
        assertTrue(inked.isNotEmpty())
        // Светлее серого прототипа: нигде не чернее #909090, а середина линии — ровно #A0A0A0.
        assertTrue(inked.all { AndroidColor.red(it) >= 0x90 })
        assertTrue(inked.any { it == AndroidColor.rgb(0xA0, 0xA0, 0xA0) })
    }

    @Test
    fun borderThicknessFollowsButtonKind() {
        showOnWhite {
            Row {
                for (kind in ButtonKind.entries) {
                    DecklyButton("Кнопка", null, kind, null, {}, Modifier.size(140.dp, 80.dp).testTag(kind.name))
                }
            }
        }

        fun measureBorderWidth(kind: ButtonKind): Int {
            val bitmap = capture(kind.name)
            val middle = bitmap.height / 2
            return (0 until 8).takeWhile { AndroidColor.red(bitmap.getPixel(it, middle)) < 128 }.size
        }
        assertEquals(3, measureBorderWidth(ButtonKind.PRIMARY))
        assertEquals(2, measureBorderWidth(ButtonKind.NORMAL))
        // Тихая и недоступная кнопки занимают то же место, но рамки не рисуют.
        assertEquals(0, measureBorderWidth(ButtonKind.QUIET))
        assertEquals(0, measureBorderWidth(ButtonKind.OFF))
    }

    @Test
    fun pressingDoesNotDrawIndication() {
        showOnWhite {
            CompositionLocalProvider(LocalIndication provides IndicationProbe) {
                Column {
                    DecklyButton("Знал", DecklyIcons.Check, ButtonKind.NORMAL, null, {}, Modifier.testTag("button"))
                    SectionTabs(buildTabs(openIndex = 0, activeIndex = 1), {}, Modifier.width(928.dp).testTag("tabs"))
                }
            }
        }

        // Индикацию создают при первом нажатии и рисуют, пока касание удержано (ripple и подсветка запрещены ТЗ).
        // Вкладку нажимаем саму: середина всей полосы приходится на промежуток между колонками.
        IndicationProbe.reset()
        fun pressAndCheck(name: String, press: androidx.compose.ui.test.SemanticsNodeInteraction, area: String) {
            press.performTouchInput { down(center) }
            composeRule.waitForIdle()
            assertTrue(name, capture(area).listPixels().none { AndroidColor.red(it) > 200 && AndroidColor.green(it) < 80 })
            press.performTouchInput { up() }
        }
        pressAndCheck("кнопка", composeRule.onNodeWithTag("button"), "button")
        pressAndCheck("вкладка", composeRule.onNodeWithContentDescription("II, 23 карточки"), "tabs")
        assertEquals(0, IndicationProbe.createdCount)
    }

    // --- шапка ---------------------------------------------------------------------------------

    @Test
    fun topBarIs64dpHigh() {
        showOnWhite { TopBar(Modifier.testTag("bar")) { Metric("Обучение", "14 / 40") } }

        composeRule.onNodeWithTag("bar").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun metricShowsLabelAndValueWithDivider() {
        showOnWhite { Metric("Успехи", "3 / 4", Modifier.testTag("metric")) }

        composeRule.onNodeWithText("Успехи").assertExists()
        composeRule.onNodeWithText("3 / 4").assertExists()
        composeRule.onNodeWithTag("metric").assertWidthIsAtLeast(132.dp)
        val bitmap = capture("metric")
        // Слева линия в 1 пиксель, дальше белое поле отступа.
        assertTrue(bitmap.isInk(0, bitmap.height / 2))
        assertFalse(bitmap.isInk(1, bitmap.height / 2))
        assertFalse(bitmap.isInk(10, bitmap.height / 2))
    }

    @Test
    fun topBarSpacesItemsBy24dp() {
        showOnWhite {
            TopBar {
                Box(Modifier.size(100.dp, 20.dp).testTag("first"))
                Box(Modifier.size(50.dp, 20.dp).testTag("second"))
            }
        }

        composeRule.onNodeWithTag("second").assertLeftPositionInRootIsEqualTo(124.dp)
    }

    @Test
    fun metricIsPaddedFromDividerAndLabelSitsAboveValue() {
        showOnWhite { Metric("Успехи", "3 / 4") }

        // Линия 1 dp и отступ 24 dp: подпись начинается с 25 dp. Между подписью и значением 2 dp.
        composeRule.onNodeWithText("Успехи").assertLeftPositionInRootIsEqualTo(25.dp)
        composeRule.onNodeWithText("3 / 4").assertLeftPositionInRootIsEqualTo(25.dp)
        val label = composeRule.onNodeWithText("Успехи").getUnclippedBoundsInRoot()
        val value = composeRule.onNodeWithText("3 / 4").getUnclippedBoundsInRoot()
        assertEquals(2f, value.top.value - label.bottom.value, 0.6f)
    }

    // --- вкладки секций ------------------------------------------------------------------------

    @Test
    fun tabsShowSixSectionsWithAgreedDescriptions() {
        showOnWhite { SectionTabs(buildTabs(openIndex = 2, activeIndex = 2), {}, Modifier.width(928.dp)) }

        composeRule.onNodeWithContentDescription("Новые, 112 карточек").assertHasClickAction()
        composeRule.onNodeWithContentDescription("I, 40 карточек").assertExists()
        composeRule.onNodeWithContentDescription("II, 23 карточки, сейчас обучение").assertExists()
        composeRule.onNodeWithContentDescription("III, 9 карточек").assertExists()
        composeRule.onNodeWithContentDescription("IV, 0 карточек").assertExists()
        composeRule.onNodeWithContentDescription("Выучено, 61 карточка").assertExists()
    }

    @Test
    fun tabsAreSixColumnsSeparatedByGaps() {
        showOnWhite { SectionTabs(buildTabs(openIndex = 2, activeIndex = 2), {}, Modifier.width(928.dp)) }

        val descriptions = listOf(
            "Новые, 112 карточек", "I, 40 карточек", "II, 23 карточки, сейчас обучение",
            "III, 9 карточек", "IV, 0 карточек", "Выучено, 61 карточка",
        )
        // (928 - 5 × 8) / 6 = 148 на колонку, шаг 156.
        descriptions.forEachIndexed { index, description ->
            composeRule.onNodeWithContentDescription(description)
                .assertLeftPositionInRootIsEqualTo((index * 156).dp)
                .assertWidthIsEqualTo(148.dp)
        }
    }

    @Test
    fun onlyOpenTabIsSelected() {
        showOnWhite { SectionTabs(buildTabs(openIndex = 2, activeIndex = 1), {}, Modifier.width(928.dp)) }

        composeRule.onNodeWithContentDescription("II, 23 карточки").assertIsSelected()
        composeRule.onNodeWithContentDescription("I, 40 карточек, сейчас обучение").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    @Test
    fun tabClickOpensItsSection() {
        val opened = mutableListOf<SectionTab>()
        showOnWhite { SectionTabs(buildTabs(openIndex = 2, activeIndex = 1), { opened += it }, Modifier.width(928.dp)) }

        composeRule.onNodeWithContentDescription("Выучено, 61 карточка").performClick()
        composeRule.onNodeWithContentDescription("II, 23 карточки").performClick()

        assertEquals(listOf(SectionTab.LEARNED, SectionTab.EPOCH_2), opened)
    }

    @Test
    fun openTabIsUnderlinedAndStripHasBottomLine() {
        showOnWhite { SectionTabs(buildTabs(openIndex = 2, activeIndex = 1), {}, Modifier.width(928.dp).testTag("tabs")) }

        composeRule.onNodeWithTag("tabs").assertHeightIsEqualTo(72.dp).assertWidthIsEqualTo(928.dp)
        val bitmap = capture("tabs")
        // Шесть колонок по 148 с промежутком 8: открытая вторая занимает x от 312 до 460.
        for (y in 67 until 71) {
            assertTrue("y=$y", bitmap.isInk(400, y))
            assertFalse("y=$y", bitmap.isInk(100, y))
            assertFalse("y=$y", bitmap.isInk(600, y))
        }
        // Общая линия под всей полосой.
        assertTrue(bitmap.isInk(100, 71))
        assertTrue(bitmap.isInk(927, 71))
    }

    @Test
    fun activeMarkDoesNotMoveTheCount() {
        var activeIndex by mutableStateOf(-1)
        showOnWhite {
            SectionTabs(buildTabs(openIndex = 0, activeIndex = activeIndex), {}, Modifier.width(928.dp).testTag("tabs"))
        }
        val withoutMark = capture("tabs")

        activeIndex = 3
        composeRule.waitForIdle()
        val withMark = capture("tabs")

        // Колонка III (индекс 3): x от 468 до 616. Всё, что было нарисовано без отметки, на месте, и добавилось новое справа.
        val column = 468 until 616
        val rows = 0 until 66
        var added = 0
        for (x in column) for (y in rows) {
            val before = withoutMark.isInk(x, y)
            val after = withMark.isInk(x, y)
            assertTrue("число сдвинулось: ($x, $y)", !before || after)
            if (!before && after) added++
        }
        assertTrue(added > 30)
    }

    @Test
    fun countIsCenteredInItsTabAndMarkSitsSixDpAfterIt() {
        var activeIndex by mutableStateOf(-1)
        showOnWhite {
            SectionTabs(buildTabs(openIndex = 0, activeIndex = activeIndex), {}, Modifier.width(928.dp).testTag("tabs"))
        }
        val withoutMark = capture("tabs")
        activeIndex = 3
        composeRule.waitForIdle()
        val withMark = capture("tabs")

        val column = 468 until 616
        val rows = 0 until 66
        // Число — нижняя из двух полос чернил в колонке (над ним подпись). Без отметки оно стоит по центру колонки.
        val countRows = withoutMark.findInkBands(column, rows).last()
        val number = withoutMark.findInkColumns(column, countRows)!!
        val cellCenter = 468 + 148 / 2
        assertEquals("число не по центру", cellCenter.toFloat(), (number.first + number.last) / 2f, 3f)
        // С отметкой число на том же месте, а новая краска начинается правее него: зазор 6 dp плюс поле внутри значка.
        var markLeft = Int.MAX_VALUE
        for (x in column) for (y in countRows) if (!withoutMark.isInk(x, y) && withMark.isInk(x, y)) markLeft = minOf(markLeft, x)
        val distance = markLeft - number.last
        assertTrue("расстояние до отметки $distance", distance in 10..17)
    }

    @Test
    fun openTabIsBolderThanClosed() {
        var openIndex by mutableStateOf(0)
        showOnWhite { SectionTabs(buildTabs(openIndex = openIndex, activeIndex = -1), {}, Modifier.width(928.dp).testTag("tabs")) }
        val closed = capture("tabs")

        openIndex = 5
        composeRule.waitForIdle()
        val open = capture("tabs")

        // Подпись «Выучено» в шестой колонке (x от 780 до 928), выше подчёркивания: жирный шрифт даёт больше чернил.
        fun countInk(bitmap: Bitmap): Int {
            var count = 0
            for (x in 780 until 928) for (y in 0 until 30) if (bitmap.isInk(x, y)) count++
            return count
        }
        assertTrue(countInk(open) > countInk(closed))
    }

    // --- лист и нижняя строка ------------------------------------------------------------------

    @Test
    fun sheetDrawsFourCornerMarks() {
        showOnWhite { Sheet(Modifier.size(300.dp, 400.dp).testTag("sheet")) }

        val bitmap = capture("sheet")
        val right = bitmap.width - 1
        val bottom = bitmap.height - 1
        // Угловые метки: две линии по 18 толщиной 2, снаружи ничего, внутри пусто.
        for ((x, y) in listOf(0 to 0, 17 to 0, 0 to 17, right to 0, right - 17 to 0, right to 17, 0 to bottom, 17 to bottom, 0 to bottom - 17, right to bottom, right - 17 to bottom, right to bottom - 17)) {
            assertTrue("($x, $y)", bitmap.isInk(x, y))
        }
        assertTrue(bitmap.isInk(5, 1))
        assertTrue(bitmap.isInk(1, 5))
        assertFalse(bitmap.isInk(5, 2))
        assertFalse(bitmap.isInk(2, 5))
        assertFalse(bitmap.isInk(18, 0))
        assertFalse(bitmap.isInk(0, 18))
        assertFalse(bitmap.isInk(10, 10))
        // По краям посередине рамки нет: метки только в углах.
        assertFalse(bitmap.isInk(bitmap.width / 2, 0))
        assertFalse(bitmap.isInk(0, bitmap.height / 2))
    }

    @Test
    fun sheetShowsItsContentAboveNothing() {
        showOnWhite { Sheet(Modifier.size(300.dp, 400.dp)) { androidx.compose.foundation.text.BasicText("Нет карточек") } }

        composeRule.onNodeWithText("Нет карточек").assertExists()
    }

    @Test
    fun footerLayoutsPlaceCellsOnTheGrid() {
        showOnWhite {
            Column {
                for (layout in FooterLayout.entries) {
                    // Просмотр: третья колонка гибкая и пустая, стрелка «вперёд» стоит в четвёртой.
                    val cells = (0 until layout.columns.size).map { column ->
                        if (layout == FooterLayout.NAVIGATION && column == 2) null else buildCell("${layout.name}-$column")
                    }
                    Footer(layout, cells, Modifier.width(928.dp).testTag(layout.name))
                }
            }
        }

        // Учебная строка: 112, 112, две гибкие колонки по (928 - 224 - 48) / 2 = 328.
        assertCell("LEARNING-0", 0.dp, 112.dp)
        assertCell("LEARNING-1", 128.dp, 112.dp)
        assertCell("LEARNING-2", 256.dp, 328.dp)
        assertCell("LEARNING-3", 600.dp, 328.dp)
        // Просмотр: 112, 112, гибкая пустая, 112.
        assertCell("NAVIGATION-0", 0.dp, 112.dp)
        assertCell("NAVIGATION-1", 128.dp, 112.dp)
        composeRule.onNodeWithTag("NAVIGATION-2").assertDoesNotExist()
        assertCell("NAVIGATION-3", 816.dp, 112.dp)
        // Две и одна колонка.
        assertCell("TWO-0", 0.dp, 456.dp)
        assertCell("TWO-1", 472.dp, 456.dp)
        assertCell("ONE-0", 0.dp, 928.dp)
        for (layout in FooterLayout.entries) composeRule.onNodeWithTag(layout.name).assertHeightIsEqualTo(112.dp)
    }

    @Test
    fun footerCellsFillTheirColumnAndLeaveMissingOnesEmpty() {
        showOnWhite {
            Footer(FooterLayout.LEARNING, listOf(buildCell("first"), null, null, buildCell("last")), Modifier.width(928.dp))
        }

        composeRule.onNodeWithTag("first").assertWidthIsEqualTo(112.dp).assertHeightIsEqualTo(112.dp)
        // Пустые колонки остаются на месте: последняя не сдвигается влево.
        composeRule.onNodeWithTag("last").assertLeftPositionInRootIsEqualTo(600.dp)
    }

    @Test
    fun moreCellsThanColumnsAreRejected() {
        assertTrue(runCatching { requireFooterCells(FooterLayout.ONE, 2) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { requireFooterCells(FooterLayout.LEARNING, 5) }.exceptionOrNull() is IllegalArgumentException)
        requireFooterCells(FooterLayout.LEARNING, 4)
        requireFooterCells(FooterLayout.TWO, 1)
    }

    @Test
    fun dialogFooterIsLowerThanScreenFooter() {
        showOnWhite { Footer(FooterLayout.TWO, listOf(null, null), Modifier.width(500.dp).testTag("footer"), height = Tokens.DialogFooterHeight) }

        composeRule.onNodeWithTag("footer").assertHeightIsEqualTo(88.dp)
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun capture(tag: String): Bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

    private fun showOnWhite(content: @Composable BoxScope.() -> Unit) {
        composeRule.setContent { Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(Color.White), content = content) }
    }

    private fun buildCell(tag: String): @Composable (Modifier) -> Unit = { modifier -> Box(modifier.testTag(tag)) }

    private fun assertCell(tag: String, left: Dp, width: Dp) {
        composeRule.onNodeWithTag(tag).assertLeftPositionInRootIsEqualTo(left).assertWidthIsEqualTo(width).assertHeightIsEqualTo(112.dp)
    }

    private fun buildTabs(openIndex: Int, activeIndex: Int): List<TabState> {
        val counts = listOf(112, 40, 23, 9, 0, 61)
        return SectionTab.entries.mapIndexed { index, tab -> TabState(tab, counts[index], isOpen = index == openIndex, isActive = index == activeIndex) }
    }

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = AndroidColor.red(getPixel(x, y)) < 128

    /** Границы чернил по горизонтали в строках [rows] внутри колонок [columns]; нет чернил — `null`. */
    private fun Bitmap.findInkColumns(columns: IntRange, rows: IntRange): IntRange? {
        val inked = columns.filter { x -> rows.any { y -> isInk(x, y) } }
        return if (inked.isEmpty()) null else inked.first()..inked.last()
    }

    /** Подряд идущие строки с чернилами в колонках [columns]: полосы текста, разделённые пустыми строками. */
    private fun Bitmap.findInkBands(columns: IntRange, rows: IntRange): List<IntRange> {
        val bands = mutableListOf<IntRange>()
        var start = -1
        for (y in rows) {
            val hasInk = columns.any { isInk(it, y) }
            if (hasInk && start < 0) start = y
            if (!hasInk && start >= 0) {
                bands += start..(y - 1)
                start = -1
            }
        }
        if (start >= 0) bands += start..rows.last
        return bands
    }

    private fun Bitmap.listPixels(): List<Int> = buildList { for (y in 0 until height) for (x in 0 until width) add(getPixel(x, y)) }
}
