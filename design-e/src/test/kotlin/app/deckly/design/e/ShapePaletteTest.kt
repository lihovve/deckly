package app.deckly.design.e

import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Палитра фигур (`.shape-palette` в макете): окно, сетка видов, толщина контура и закрытие. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapePaletteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val host by lazy { EditorHost(composeRule) }
    private val actions get() = host.actions

    // --- окно ----------------------------------------------------------------------------------

    @Test
    fun paletteIsAbsentUntilItIsOpened() {
        host.show(buildEditorState())

        composeRule.onNodeWithText("Shapes").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Close shape menu").assertDoesNotExist()
        // В обычной панели значок «Shapes» есть, а в сетке нет ни одного вида.
        for (kind in ShapeKind.entries) composeRule.onNodeWithContentDescription(EditorTexts.describeShape(kind)).assertDoesNotExist()
    }

    @Test
    fun windowKeepsMockupGeometryAboveTheToolbar() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val title = composeRule.onNodeWithText("Shapes").getUnclippedBoundsInRoot()
        val firstChoice = composeRule.onNodeWithContentDescription("Line").getUnclippedBoundsInRoot()
        val cross = findCross().getUnclippedBoundsInRoot()
        val slider = findSizeSlider().getUnclippedBoundsInRoot()

        // Окно: слева 32, ширина 672, снизу 160 от низа кадра; рамка 2 и поля 32 по бокам, 24 сверху.
        assertEquals(32f + 2f + 32f, title.left.value, 0.5f)
        assertEquals(32f + 2f + 32f, firstChoice.left.value, 0.5f)
        assertEquals(32f + 672f - 2f - 32f, cross.right.value, 0.5f)
        assertEquals(64f, cross.right.value - cross.left.value, 0.5f)
        assertEquals(64f, cross.bottom.value - cross.top.value, 0.5f)
        // Последний ползунок (размер фигуры) заканчивается на 28 выше внутренней кромки рамки; окно на 160 выше низа кадра.
        assertEquals(1323f - 160f - 2f - 28f, slider.bottom.value, 0.5f)
        // Заголовок и крест стоят в одной строке, заголовок слева от креста.
        assertTrue(title.right.value <= cross.left.value)
        assertEquals(cross.top.value + 32f, (title.top.value + title.bottom.value) / 2f, 1.5f)
    }

    @Test
    fun windowIsWhiteWithRoundedBlackBorder() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        // Правая кромка окна (x = 32 + 672): левая и нижняя совпадают с рамкой листа под окном, по ней форму не проверить.
        val right = 32 + 672
        val top = findCross().getUnclippedBoundsInRoot().top.value.toInt() - 2 - 24
        val middle = composeRule.onNodeWithContentDescription("Line").getUnclippedBoundsInRoot().let { ((it.top.value + it.bottom.value) / 2f).toInt() }
        // Рамка 2 по правой кромке, белое поле сразу за ней и пусто снаружи.
        assertTrue(bitmap.isInkAt(right - 1, middle))
        assertTrue(bitmap.isInkAt(right - 2, middle))
        assertTrue(!bitmap.isInkAt(right - 3, middle))
        assertTrue(!bitmap.isInkAt(right, middle))
        // Радиус 12: угловой пиксель не закрашен, а верхняя кромка прямая уже в 20 от угла.
        assertTrue(!bitmap.isInkAt(right - 1, top))
        assertTrue(bitmap.isInkAt(right - 20, top))
        assertTrue(bitmap.isInkAt(right - 20, top + 1))
    }

    @Test
    fun titleIsBoldTwentyEightWithTightSpacing() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val style = readTextStyle(composeRule.onNodeWithText("Shapes", useUnmergedTree = true))

        assertEquals(28f, style.fontSize.value, 0.01f)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(-0.6f, style.letterSpacing.value, 0.01f)
    }

    // --- сетка видов ---------------------------------------------------------------------------

    @Test
    fun gridHasFifteenShapesInMockupOrderInFiveColumns() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val bounds = ShapeKind.entries.map { composeRule.onNodeWithContentDescription(EditorTexts.describeShape(it)).getUnclippedBoundsInRoot() }

        assertEquals(15, bounds.size)
        // Колонки равной ширины с зазором 8 внутри полезной ширины 672 − 2·2 − 2·32 = 604: (604 − 4·8) / 5 = 114,4.
        val columnWidth = (604f - 4f * 8f) / 5f
        for ((index, box) in bounds.withIndex()) {
            val column = index % 5
            val row = index / 5
            assertEquals("ширина вида $index", columnWidth, box.right.value - box.left.value, 1.0f)
            assertEquals("высота вида $index", 104f, box.bottom.value - box.top.value, 0.5f)
            // Положение округляется до пикселя: ширина колонки 114,4 даёт 114 или 115.
            assertEquals("колонка вида $index ${bounds.map { it.left.value to it.right.value }.take(5)}", bounds[0].left.value + column * (columnWidth + 8f), box.left.value, 1.5f)
            assertEquals("строка вида $index", bounds[0].top.value + row * (104f + 8f), box.top.value, 0.6f)
        }
    }

    @Test
    fun shapeNamesAreTheMockupLabelsInTheirOrder() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val labels = MockupSources.listShapeLabels()

        assertEquals(labels, ShapeKind.entries.map { EditorTexts.describeShape(it) })
        assertEquals(15, labels.size)
    }

    @Test
    fun noShapeIsSelectedInThePalette() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        for (kind in ShapeKind.entries) {
            val choice = composeRule.onNodeWithContentDescription(EditorTexts.describeShape(kind))
            choice.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            // Рамка у вида прозрачная: ни одного чёрного пикселя у левой кромки на середине высоты.
            val bitmap = choice.captureToImage().asAndroidBitmap()
            assertTrue("у вида $kind есть рамка", (0 until 4).none { bitmap.isInkAt(it, bitmap.height / 2) })
        }
    }

    @Test
    fun glyphsComeFromTheSlotWithTheOutlineWidthAt56() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 7))

        for (kind in ShapeKind.entries) {
            val glyph = composeRule.onNodeWithTag("glyph-${kind.name}-7", useUnmergedTree = true)
            glyph.assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
            val choice = composeRule.onNodeWithContentDescription(EditorTexts.describeShape(kind)).getUnclippedBoundsInRoot()
            val box = glyph.getUnclippedBoundsInRoot()
            // Значок по центру вида: и по горизонтали, и по вертикали.
            assertEquals((choice.left.value + choice.right.value) / 2f, (box.left.value + box.right.value) / 2f, 0.6f)
            assertEquals((choice.top.value + choice.bottom.value) / 2f, (box.top.value + box.bottom.value) / 2f, 0.6f)
        }
    }

    @Test
    fun glyphsFollowTheOutlineWidthOfTheState() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 3))
        composeRule.onNodeWithTag("glyph-STAR-3", useUnmergedTree = true).assertExists()

        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 9))

        composeRule.onNodeWithTag("glyph-STAR-3", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("glyph-STAR-9", useUnmergedTree = true).assertExists()
    }

    @Test
    fun everyShapeChoiceSendsItsKind() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        for (kind in ShapeKind.entries) composeRule.onNodeWithContentDescription(EditorTexts.describeShape(kind)).performClick()

        assertEquals(ShapeKind.entries.map { EditorAction.ChooseShape(it) }, actions)
    }

    // --- толщина контура -----------------------------------------------------------------------

    @Test
    fun widthRowShowsLabelNumberAndSampleWithTheCurrentWidth() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 5))

        composeRule.onNodeWithText("Outline width").assertExists()
        composeRule.onNodeWithText("5").assertExists()
        val label = readTextStyle(composeRule.onNodeWithText("Outline width", useUnmergedTree = true))
        assertEquals(22f, label.fontSize.value, 0.01f)
        val sample = measureSample()
        // Образец `M8 12H72`: 64 в длину и ровно выбранной толщины в высоту, концы не скруглены.
        assertEquals(64f, sample.width, 1.5f)
        assertEquals(5f, sample.height, 1.5f)
    }

    @Test
    fun sampleThicknessFollowsTheWidthFromOneToTwelve() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 1))
        assertEquals(1f, measureSample().height, 1.2f)

        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 12))

        assertEquals(12f, measureSample().height, 1.2f)
    }

    @Test
    fun sliderHasMockupGeometryAndTheRangeOfOutlineWidths() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 4))

        val slider = findSlider()
        slider.assertHeightIsEqualTo(64.dp)
        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(4f, range.current, 0f)
        assertEquals(1f..12f, range.range)
        // Шагов между крайними значениями десять: 2…11.
        assertEquals(10, range.steps)
        // Ширина — вся полезная ширина окна 604.
        assertEquals(604f, slider.getUnclippedBoundsInRoot().let { it.right.value - it.left.value }, 0.5f)
    }

    @Test
    fun sliderThumbIsWhiteCircleWithBlackBorderAndMovesWithTheValue() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 1))
        val atMinimum = findThumbCenter()

        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 12))
        val atMaximum = findThumbCenter()

        // Центр бегунка ходит от r до ширина − r: 16 у единицы и 604 − 16 у двенадцати.
        assertEquals(16f, atMinimum.first, 1.5f)
        assertEquals(604f - 16f, atMaximum.first, 1.5f)
        val slider = findSlider().captureToImage().asAndroidBitmap()
        // Белая середина бегунка вокруг центра и рамка 3 по кромке круга на уровне центра (центр у правого края: 604 − 16).
        val center = 604 - 16
        val y = slider.height / 2
        assertTrue(!slider.isInkAt(center, y))
        assertEquals(3, (0 until 6).takeWhile { slider.isInkAt(center + 16 - 1 - it, y) }.size)
    }

    @Test
    fun sliderTrackIsTwoThickBlackLine() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteOutlineWidth = 12))

        val bitmap = findSlider().captureToImage().asAndroidBitmap()
        // Дорожка слева от бегунка, у самого края, где его нет: две чёрные строки по центру высоты 64.
        val x = 100
        val rows = (0 until bitmap.height).filter { bitmap.isInkAt(x, it) }
        assertEquals(2, rows.size)
        assertEquals(31, rows.first())
    }

    @Test
    fun fingerTouchOnSliderChoosesWidthUnderIt() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val slider = findSlider()
        slider.performTouchInput { click(Offset(16f, 32f)) }
        slider.performTouchInput { click(Offset(width - 16f, 32f)) }
        slider.performTouchInput { click(Offset(width / 2f, 32f)) }

        // Края дают 1 и 12, середина — ближайшее к половине деления: 1 + 11 · 0,5 = 6,5 → 7.
        assertEquals(
            listOf<EditorAction>(EditorAction.SetOutlineWidth(1), EditorAction.SetOutlineWidth(12), EditorAction.SetOutlineWidth(7)),
            actions,
        )
    }

    @Test
    fun fingerDragMovesTheThumbAndReportsEveryStep() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        findSlider().performTouchInput {
            down(Offset(16f, 32f))
            moveTo(Offset(width / 2f, 32f))
            moveTo(Offset(width - 16f, 32f))
            up()
        }

        val widths = actions.filterIsInstance<EditorAction.SetOutlineWidth>().map { it.width }
        assertEquals(1, widths.first())
        assertEquals(12, widths.last())
        assertEquals("значения растут", widths.sorted(), widths)
    }

    @Test
    fun penChoosesWidthTooAndDragPastTheEndsIsClamped() {
        host.show(buildEditorState(isShapePaletteOpen = true))
        val bounds = findSlider().getUnclippedBoundsInRoot()
        val y = bounds.top.value + 32f
        val left = bounds.left.value

        // Касание на середине дорожки (302 из 604) даёт 7, движение за правый край — 12.
        dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, listOf((left + 302f) to y, (left + 640f) to y))
        assertEquals(listOf<EditorAction>(EditorAction.SetOutlineWidth(7), EditorAction.SetOutlineWidth(12)), actions.toList())
        actions.clear()

        // Ведение за левый край уводит значение в 1 и дальше не уходит; касание у самого правого края — 12.
        dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, listOf((left + 302f) to y, (left - 40f) to y))
        assertEquals(listOf<EditorAction>(EditorAction.SetOutlineWidth(7), EditorAction.SetOutlineWidth(1)), actions.toList())
        actions.clear()
        dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, listOf((left + 603f) to y))
        assertEquals(listOf<EditorAction>(EditorAction.SetOutlineWidth(12)), actions.toList())
    }

    @Test
    fun accessibilityActionSetsTheProgressWithinBounds() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val setProgress = findSlider().fetchSemanticsNode().config[SemanticsActions.SetProgress].action
        assertNotNull(setProgress)
        composeRule.runOnUiThread {
            assertTrue(setProgress!!.invoke(8.4f))
            assertTrue(setProgress.invoke(40f))
            assertTrue(setProgress.invoke(-3f))
        }

        assertEquals(
            listOf<EditorAction>(EditorAction.SetOutlineWidth(8), EditorAction.SetOutlineWidth(12), EditorAction.SetOutlineWidth(1)),
            actions,
        )
    }

    // --- размер новой фигуры (`.shape-width.shape-size`) ---------------------------------------

    @Test
    fun sizeRowShowsLabelAndNumberAtTheRightWithoutASample() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteShapeSize = 300))

        val label = composeRule.onNodeWithText("Default shape size", useUnmergedTree = true)
        val number = composeRule.onNodeWithText("300", useUnmergedTree = true)
        assertEquals(22f, readTextStyle(label).fontSize.value, 0.01f)
        assertEquals(22f, readTextStyle(number).fontSize.value, 0.01f)
        // Две колонки `1fr auto`: подпись у левого края содержимого окна, число у правого, образца линии справа от него нет.
        assertEquals(32f + 2f + 32f, label.getUnclippedBoundsInRoot().left.value, 0.5f)
        assertEquals(32f + 672f - 2f - 32f, number.getUnclippedBoundsInRoot().right.value, 1f)
    }

    @Test
    fun sizeBlockSitsUnderTheOutlineBlockBehindALineAndTwentyFourOfPadding() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val outline = findSlider().getUnclippedBoundsInRoot()
        val label = composeRule.onNodeWithText("Default shape size", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val slider = findSizeSlider().getUnclippedBoundsInRoot()
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        // Между блоками промежуток 20 окна, затем линия 1 на всю ширину содержимого и поле 24 до строки с подписью.
        val line = (outline.bottom.value + 20f).toInt()
        assertTrue("линия блока", (64 + 100..64 + 500).all { bitmap.isInkAt(it, line) })
        assertTrue("над линией пусто", (64 + 100..64 + 500).none { bitmap.isInkAt(it, line - 1) })
        assertEquals(line + 1f + 24f, label.top.value, 1.5f)
        // Ползунок на 8 ниже строки, высота 64 и вся ширина содержимого 604.
        assertEquals(label.bottom.value + 8f, slider.top.value, 1.5f)
        assertEquals(64f, slider.bottom.value - slider.top.value, 0.5f)
        assertEquals(604f, slider.right.value - slider.left.value, 0.5f)
    }

    @Test
    fun sizeSliderRangeIsOneHundredToFourHundredFortyInTens() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteShapeSize = 240))

        val range = findSizeSlider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(240f, range.current, 0f)
        assertEquals(100f..440f, range.range)
        // Значений 35, между крайними 33.
        assertEquals(33, range.steps)
    }

    @Test
    fun sizeThumbMovesFromOneEndOfTheTrackToTheOther() {
        host.show(buildEditorState(isShapePaletteOpen = true, paletteShapeSize = 100))
        val atMinimum = findThumbCenter(findSizeSlider())

        host.show(buildEditorState(isShapePaletteOpen = true, paletteShapeSize = 440))
        val atMaximum = findThumbCenter(findSizeSlider())

        assertEquals(16f, atMinimum.first, 1.5f)
        assertEquals(604f - 16f, atMaximum.first, 1.5f)
    }

    @Test
    fun fingerTouchOnSizeSliderChoosesTheTenUnderIt() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val slider = findSizeSlider()
        slider.performTouchInput { click(Offset(16f, 32f)) }
        slider.performTouchInput { click(Offset(width - 16f, 32f)) }
        slider.performTouchInput { click(Offset(width / 2f, 32f)) }

        // Края дают 100 и 440, середина 100 + 340 · 0,5 = 270.
        assertEquals(
            listOf<EditorAction>(EditorAction.SetShapeSize(100), EditorAction.SetShapeSize(440), EditorAction.SetShapeSize(270)),
            actions,
        )
    }

    @Test
    fun fingerDragOnSizeSliderReportsOnlyWholeTensInGrowingOrder() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        findSizeSlider().performTouchInput {
            down(Offset(16f, 32f))
            moveTo(Offset(width / 3f, 32f))
            moveTo(Offset(width - 16f, 32f))
            up()
        }

        val sizes = actions.filterIsInstance<EditorAction.SetShapeSize>().map { it.size }
        assertEquals(100, sizes.first())
        assertEquals(440, sizes.last())
        assertTrue("кратно 10: $sizes", sizes.all { it % 10 == 0 })
        assertEquals("значения растут", sizes.sorted(), sizes)
    }

    @Test
    fun penChoosesSizeAndDragPastTheEndsIsClamped() {
        host.show(buildEditorState(isShapePaletteOpen = true))
        val bounds = findSizeSlider().getUnclippedBoundsInRoot()
        val y = bounds.top.value + 32f
        val left = bounds.left.value

        dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, listOf((left + 302f) to y, (left + 640f) to y))
        assertEquals(listOf<EditorAction>(EditorAction.SetShapeSize(270), EditorAction.SetShapeSize(440)), actions.toList())
        actions.clear()
        dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, listOf((left + 302f) to y, (left - 40f) to y))
        assertEquals(listOf<EditorAction>(EditorAction.SetShapeSize(270), EditorAction.SetShapeSize(100)), actions.toList())
    }

    @Test
    fun accessibilityActionSnapsTheSizeToTensWithinBounds() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val setProgress = findSizeSlider().fetchSemanticsNode().config[SemanticsActions.SetProgress].action
        assertNotNull(setProgress)
        composeRule.runOnUiThread {
            assertTrue(setProgress!!.invoke(254f))
            assertTrue(setProgress.invoke(256f))
            assertTrue(setProgress.invoke(1000f))
            assertTrue(setProgress.invoke(-3f))
        }

        assertEquals(
            listOf<EditorAction>(EditorAction.SetShapeSize(250), EditorAction.SetShapeSize(260), EditorAction.SetShapeSize(440), EditorAction.SetShapeSize(100)),
            actions,
        )
    }

    @Test
    fun outlineSliderStillMovesInOnesWhileTheSizeSliderExists() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        findSlider().performTouchInput { click(Offset(width / 2f, 32f)) }

        assertEquals(listOf<EditorAction>(EditorAction.SetOutlineWidth(7)), actions)
    }

    // --- закрытие ------------------------------------------------------------------------------

    @Test
    fun crossClosesThePalette() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        findCross().performClick()

        assertEquals(listOf<EditorAction>(EditorAction.CloseShapePalette), actions)
    }

    @Test
    fun touchBesideTheWindowClosesItAndDoesNotReachTheScreenBelow() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        // Справа от окна (оно занимает x 32…704): кнопка «Undo» под ним (отмена штриха, 112 × 112 у правой кромки).
        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(900f, 1323f - 32f - 56f)) }
        // Над окном, в шапке: кнопка «Save».
        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(900f, 64f)) }

        assertEquals(listOf<EditorAction>(EditorAction.CloseShapePalette, EditorAction.CloseShapePalette), actions)
    }

    @Test
    fun touchInsideTheWindowOutsideControlsDoesNothing() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        // Левое поле окна рядом с сеткой (между рамкой и видами) и промежуток между рядами видов: ни закрытия, ни выбора.
        val line = composeRule.onNodeWithContentDescription("Line").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(50f, line.top.value + 50f)) }
        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(line.left.value + 20f, line.bottom.value + 4f)) }

        assertTrue(actions.isEmpty())
    }

    @Test
    fun systemBackClosesThePalette() {
        host.show(buildEditorState(isShapePaletteOpen = true))
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf<EditorAction>(EditorAction.CloseShapePalette), actions)
        assertTrue("приложение не уходит", !composeRule.activity.isFinishing)
    }

    @Test
    fun backIsLeftToTheSystemOnceThePaletteIsClosed() {
        host.show(buildEditorState(isShapePaletteOpen = true))
        host.show(buildEditorState(isShapePaletteOpen = false))
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertTrue(actions.isEmpty())
        assertTrue(composeRule.activity.isFinishing)
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun findSlider(): SemanticsNodeInteraction = composeRule.onNodeWithContentDescription("Outline width")

    private fun findSizeSlider(): SemanticsNodeInteraction = composeRule.onNodeWithContentDescription("Default shape size")

    // Описание «Close shape menu» у двух узлов: у прозрачного фона (первым в дереве) и у креста в заголовке.
    private fun findCross(): SemanticsNodeInteraction = composeRule.onAllNodesWithContentDescription("Close shape menu")[1]

    // Рамка чернил образца толщины: правая часть строки над ползунком, ширина образца 80, строка не выше 40.
    private fun measureSample(): InkSize {
        val slider = findSlider().getUnclippedBoundsInRoot()
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        val right = (32f + 672f - 2f - 32f).toInt()
        val xs = (right - 80) until right
        val ys = (slider.top.value - 8f - 40f).toInt() until (slider.top.value - 8f).toInt()
        val box = measureInkBox(bitmap, xs, ys)
        assertNotNull("образца толщины нет", box)
        return box!!
    }

    // Центр бегунка по горизонтали: середина между крайними столбцами круга, видного выше и ниже дорожки.
    private fun findThumbCenter(slider: SemanticsNodeInteraction = findSlider()): Pair<Float, Float> {
        val bitmap = slider.captureToImage().asAndroidBitmap()
        val columns = (0 until bitmap.width).filter { x -> bitmap.isInkAt(x, 20) || bitmap.isInkAt(x, bitmap.height - 21) }
        return (columns.first() + columns.last()) / 2f to bitmap.height / 2f
    }
}
