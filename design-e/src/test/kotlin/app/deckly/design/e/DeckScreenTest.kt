package app.deckly.design.e

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.deckly.ui.contract.ActionBlock
import app.deckly.ui.contract.ActionState
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.DeckBody
import app.deckly.ui.contract.DeckDialog
import app.deckly.ui.contract.DeckFooter
import app.deckly.ui.contract.DeckHeader
import app.deckly.ui.contract.DeckScreenState
import app.deckly.ui.contract.PositionKind
import app.deckly.ui.contract.PositionMetric
import app.deckly.ui.contract.SectionTab
import app.deckly.ui.contract.SuccessMetric
import app.deckly.ui.contract.TabState
import app.deckly.ui.contract.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<DeckAction>()

    // --- обучение: нижняя строка ---------------------------------------------------------------

    @Test
    fun questionHasNoRatings() {
        showScreen(buildLearningState(side = CardSideShown.FRONT, areRatingsShown = false))

        composeRule.onNodeWithText("Missed it").assertDoesNotExist()
        composeRule.onNodeWithText("Nailed it").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Show answer").assertExists()
        composeRule.onNodeWithContentDescription("Move to end of pass").assertExists()
    }

    @Test
    fun answerHasEqualRatings() {
        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true))

        val notKnew = composeRule.onNodeWithText("Missed it").getUnclippedBoundsInRoot()
        val knew = composeRule.onNodeWithText("Nailed it").getUnclippedBoundsInRoot()
        // Равнозначные кнопки: одной ширины и высоты, в двух последних колонках (328 + 328 с промежутком 16).
        assertEquals(knew.right.value - knew.left.value, notKnew.right.value - notKnew.left.value, 0.01f)
        assertEquals(knew.bottom.value - knew.top.value, notKnew.bottom.value - notKnew.top.value, 0.01f)
        composeRule.onNodeWithText("Missed it").assertLeftPositionInRootIsEqualTo(32.dp + 256.dp).assertWidthIsEqualTo(328.dp).assertHeightIsEqualTo(112.dp)
        composeRule.onNodeWithText("Nailed it").assertLeftPositionInRootIsEqualTo(32.dp + 600.dp).assertWidthIsEqualTo(328.dp)
        // Обе с обычной рамкой в 2 пикселя, ни одна не выделена.
        for (label in listOf("Missed it", "Nailed it")) {
            val bitmap = composeRule.onNodeWithText(label).captureToImage().asAndroidBitmap()
            assertEquals(label, 2, (0 until 8).takeWhile { bitmap.isInk(it, bitmap.height / 2) }.size)
        }
        composeRule.onNodeWithText("Missed it").performClick()
        composeRule.onNodeWithText("Nailed it").performClick()
        assertEquals(listOf(DeckAction.DidNotKnow, DeckAction.Knew), actions)
    }

    @Test
    fun blockedPostponeExplainsReasonInDescription() {
        val reasons = mapOf(
            ActionBlock.ANSWER_REVEALED to "Move to end of pass: answer already revealed",
            ActionBlock.ALREADY_POSTPONED to "Move to end of pass: already postponed",
            ActionBlock.LAST_IN_PASS to "Move to end of pass: last card",
        )
        for ((block, description) in reasons) {
            actions.clear()
            showScreen(buildLearningState(postpone = ActionState(false, block)))
            composeRule.onNodeWithContentDescription(description).assertIsNotEnabled().performClick()
            assertTrue(block.name, actions.isEmpty())
        }
    }

    @Test
    fun availablePostponeIsQuietAndSendsAction() {
        showScreen(buildLearningState(postpone = ActionState(true)))

        composeRule.onNodeWithContentDescription("Move to end of pass").assertIsEnabled().performClick()

        assertEquals(listOf<DeckAction>(DeckAction.Postpone), actions)
        // Тихая кнопка без рамки, а недоступная была бы серой.
        val bitmap = composeRule.onNodeWithContentDescription("Move to end of pass").captureToImage().asAndroidBitmap()
        assertFalse(bitmap.isInk(0, bitmap.height / 2))
    }

    @Test
    fun flipIsSecondCellInLearningAndBrowsing() {
        showScreen(buildLearningState(side = CardSideShown.FRONT))
        composeRule.onNodeWithContentDescription("Show answer")
            .assertLeftPositionInRootIsEqualTo(32.dp + 128.dp).assertWidthIsEqualTo(112.dp).assertHeightIsEqualTo(112.dp)
            .assertTopPositionInRootIsEqualTo(1323.dp - 32.dp - 112.dp)
        composeRule.onNodeWithContentDescription("Move to end of pass").assertLeftPositionInRootIsEqualTo(32.dp).assertWidthIsEqualTo(112.dp)

        showScreen(buildBrowsingState(side = CardSideShown.BACK))
        composeRule.onNodeWithContentDescription("Show question")
            .assertLeftPositionInRootIsEqualTo(32.dp + 128.dp).assertWidthIsEqualTo(112.dp).assertHeightIsEqualTo(112.dp)
        composeRule.onNodeWithContentDescription("Previous card").assertLeftPositionInRootIsEqualTo(32.dp).assertWidthIsEqualTo(112.dp)
        composeRule.onNodeWithContentDescription("Next card").assertLeftPositionInRootIsEqualTo(32.dp + 816.dp).assertWidthIsEqualTo(112.dp)
    }

    @Test
    fun flipIsPrimaryOnQuestionOfLearningAndOrdinaryOnAnswerAndWhenBrowsing() {
        showScreen(buildLearningState(side = CardSideShown.FRONT))
        assertEquals(3, measureBorder("Show answer"))

        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true))
        assertEquals(2, measureBorder("Show question"))

        showScreen(buildBrowsingState(side = CardSideShown.FRONT))
        assertEquals(2, measureBorder("Show answer"))
    }

    @Test
    fun flipSendsFlipInBothModes() {
        showScreen(buildLearningState())
        composeRule.onNodeWithContentDescription("Show answer").performClick()
        showScreen(buildBrowsingState())
        composeRule.onNodeWithContentDescription("Show answer").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.Flip, DeckAction.Flip), actions)
    }

    // --- просмотр ------------------------------------------------------------------------------

    @Test
    fun browsingArrowsSendActionsAndAreGreyForSingleCard() {
        showScreen(buildBrowsingState(canBrowse = true))
        composeRule.onNodeWithContentDescription("Previous card").performClick()
        composeRule.onNodeWithContentDescription("Next card").performClick()
        assertEquals(listOf(DeckAction.ShowPrevious, DeckAction.ShowNext), actions)

        actions.clear()
        showScreen(buildBrowsingState(canBrowse = false))
        composeRule.onNodeWithContentDescription("Previous card").assertIsNotEnabled().performClick()
        composeRule.onNodeWithContentDescription("Next card").assertIsNotEnabled().performClick()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun browsingHeaderSaysViewing() {
        showScreen(buildBrowsingState(position = PositionMetric(PositionKind.BROWSING, 2, 112), successes = null))

        composeRule.onNodeWithText("Browse").assertExists()
        assertMetricShown("Browse", 2, 112)
        composeRule.onNodeWithContentDescription("Browse, card 2 of 112").assertExists()
        composeRule.onNodeWithText("Successes").assertDoesNotExist()
    }

    @Test
    fun ratingsAndStartCarryIconsAndLargeLabels() {
        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true))
        for ((label, icon) in listOf("Missed it" to IconId.CROSS, "Nailed it" to IconId.CHECK)) {
            assertEquals(label, 26f, readTextStyle(composeRule.onNodeWithText(label, useUnmergedTree = true)).fontSize.value, 0.01f)
            assertIconBeforeLabel(label, icon, borderMargin = 8)
        }

        for ((kind, label) in listOf(TransitionKind.REGULAR to "Start pass", TransitionKind.RESUMED to "Continue")) {
            showScreen(buildTransitionState(kind, 4, 60, 100))
            assertEquals(label, 26f, readTextStyle(composeRule.onNodeWithText(label, useUnmergedTree = true)).fontSize.value, 0.01f)
            assertIconBeforeLabel(label, IconId.RIGHT, borderMargin = 8)
        }
    }

    @Test
    fun unavailableFlipIsGreyAndInert() {
        showScreen(buildLearningState(flip = ActionState(false)))

        composeRule.onNodeWithContentDescription("Show answer").assertIsNotEnabled().performClick()

        assertTrue(actions.isEmpty())
    }

    // --- шапка ---------------------------------------------------------------------------------

    @Test
    fun headerShowsDeckNameMetricsAndButtonsInMockupOrder() {
        showScreen(buildLearningState(canCreateCard = true))

        val name = composeRule.onNodeWithContentDescription("English A2. Choose deck").getUnclippedBoundsInRoot()
        val position = composeRule.onNodeWithContentDescription("Learning, card 14 of 40").getUnclippedBoundsInRoot()
        val successes = composeRule.onNodeWithText("Successes").getUnclippedBoundsInRoot()
        val create = composeRule.onNodeWithContentDescription("Create card").getUnclippedBoundsInRoot()
        val menu = composeRule.onNode(hasContentDescription("Card actions") and hasClickAction()).getUnclippedBoundsInRoot()
        // Название слева, затем счётчик позиции, успехи, плюс и «⋯».
        assertTrue(name.left.value < position.left.value)
        assertTrue(position.right.value <= successes.left.value)
        assertTrue(successes.right.value <= create.left.value)
        assertTrue(create.right.value <= menu.left.value)
        composeRule.onNodeWithText("Learning").assertExists()
        assertMetricShown("Learning", 14, 40)
        assertMetricShown("Successes", 3, 4)
        composeRule.onNodeWithText("English A2").assertExists()
        // Кнопки шапки квадратные 64 × 64, у «⋯» правый край совпадает с полем кадра.
        composeRule.onNodeWithContentDescription("Create card").assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(64.dp)
        assertEquals(992f - 32f, menu.right.value, 0.5f)
    }

    @Test
    fun headerButtonsAppearOnlyWhenOffered() {
        showScreen(buildLearningState(canCreateCard = false, menu = emptyList()))

        composeRule.onNodeWithContentDescription("Create card").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Card actions").assertCountEquals(0)
    }

    @Test
    fun deckNameOpensDeckList() {
        showScreen(buildLearningState())

        composeRule.onNodeWithContentDescription("English A2. Choose deck").performClick()
        composeRule.onNodeWithContentDescription("Create card").assertDoesNotExist()

        assertEquals(listOf<DeckAction>(DeckAction.OpenDeckList), actions)
    }

    @Test
    fun plusCreatesCard() {
        showScreen(buildLearningState(canCreateCard = true))

        composeRule.onNodeWithContentDescription("Create card").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.CreateCard), actions)
    }

    @Test
    fun longDeckNameDoesNotPushButtonsOffScreen() {
        showScreen(buildLearningState(deckName = "A very long deck name that does not fit the header line completely", canCreateCard = true))

        val menu = composeRule.onNode(hasContentDescription("Card actions") and hasClickAction()).getUnclippedBoundsInRoot()
        assertEquals(992f - 32f, menu.right.value, 0.5f)
    }

    @Test
    fun headerIconButtonsKeepFullIconWithoutInnerPadding() {
        showScreen(buildLearningState(canCreateCard = true))

        // Значок 32 dp без полей: крест плюса и крайние точки «⋯» занимают 16 клеток сетки 24 и линию 1,8, около 23,7 px.
        // С внутренними полями обычной кнопки значок сжался бы до высоты 28 dp, то есть до 20,7 px.
        assertEquals(23.7f, measureInkWidth(composeRule.onNodeWithContentDescription("Create card")), 1f)
        assertEquals(23.7f, measureInkWidth(composeRule.onNode(hasContentDescription("Card actions") and hasClickAction())), 1f)
    }

    @Test
    fun deckNameIsFollowedByTwentyDpArrowTenDpApart() {
        showScreen(buildLearningState(deckName = "English A2"))

        val heading = composeRule.onNodeWithContentDescription("English A2. Choose deck")
        val textRight = composeRule.onNodeWithText("English A2", useUnmergedTree = true).getUnclippedBoundsInRoot().right.value
        val headingLeft = heading.getUnclippedBoundsInRoot().left.value
        val bitmap = heading.captureToImage().asAndroidBitmap()
        val arrowStart = (textRight - headingLeft).toInt() + 3
        val arrowColumns = (arrowStart until bitmap.width).filter { x -> (0 until bitmap.height).any { y -> bitmap.isInk(x, y) } }

        // Шеврон «m6 9 6 6 6-6» в клетках 6…18 плюс линия 1,8: при размере 20 dp он начинается в 14,25 px от текста
        // (10 dp зазора и 5,1 клетки) и шириной 11,5 px; значок 26 dp был бы шире на 3,5 px.
        assertTrue("стрелки нет", arrowColumns.isNotEmpty())
        assertEquals(14.25f, arrowColumns.first() - (textRight - headingLeft), 1.5f)
        assertEquals(11.5f, (arrowColumns.last() - arrowColumns.first() + 1).toFloat(), 1.5f)
    }

    @Test
    fun deckNameIsBoldTitleAndAsTallAsTheBar() {
        showScreen(buildLearningState(deckName = "English A2"))

        val style = readTextStyle(composeRule.onNodeWithText("English A2", useUnmergedTree = true))
        assertEquals(28f, style.fontSize.value, 0.01f)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(-0.5f, style.letterSpacing.value, 0.01f)
        // Цель касания названия не ниже строки шапки: 64 dp.
        composeRule.onNodeWithContentDescription("English A2. Choose deck").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun positionMetricIsOneNodeForScreenReader() {
        showScreen(buildLearningState())

        // Подпись и значение читаются одной фразой «Обучение, карточка 14 из 40», а не отдельно от неё.
        composeRule.onNode(hasContentDescription("Learning, card 14 of 40") and hasText("14") and hasText("/") and hasText("40") and hasText("Learning")).assertExists()
    }

    // --- вкладки -------------------------------------------------------------------------------

    @Test
    fun activeMarkFollowsState() {
        showScreen(buildLearningState(tabs = buildTabs(openIndex = 2, activeIndex = 2)))
        composeRule.onNodeWithContentDescription("II, 23 cards, currently learning").assertExists()
        composeRule.onNodeWithContentDescription("I, 40 cards").assertExists()

        // Открыта другая секция, активная прежняя: отметка ▶ остаётся у II.
        showScreen(buildBrowsingState(tabs = buildTabs(openIndex = 3, activeIndex = 2)))
        composeRule.onNodeWithContentDescription("II, 23 cards, currently learning").assertExists()
        composeRule.onNodeWithContentDescription("III, 9 cards").assertExists()
        composeRule.onNodeWithContentDescription("Deck sections").assertExists()
    }

    @Test
    fun tabClickOpensSection() {
        showScreen(buildLearningState())

        composeRule.onNodeWithContentDescription("Learned, 61 cards").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.OpenSection(SectionTab.LEARNED)), actions)
    }

    // --- лист ----------------------------------------------------------------------------------

    @Test
    fun sheetSlotFillsTheSheetOfMockup() {
        showScreen(buildLearningState())

        // Лист 928 × 963 в точке (32, 200), как в README макетов.
        composeRule.onNodeWithTag("slot")
            .assertLeftPositionInRootIsEqualTo(32.dp).assertTopPositionInRootIsEqualTo(200.dp)
            .assertWidthIsEqualTo(928.dp).assertHeightIsEqualTo(963.dp)
    }

    @Test
    fun sheetGeometryIsTheSameForEveryBodyAndFooter() {
        // Нижняя строка занимает место и когда пуста: лист всегда 928 × 963 в точке (32, 200).
        val cases = listOf(
            buildLearningState() to "Question",
            buildBrowsingState() to "Handwritten card",
            buildEmptyState(isPool = false) to "Empty section",
            buildTransitionState(TransitionKind.REGULAR, 2, 23, 23) to "Next pass: epoch II",
        )
        for ((state, label) in cases) {
            showScreen(state)
            val sheet = composeRule.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            assertEquals(label, 32f, sheet.left.value, 0.5f)
            assertEquals(label, 200f, sheet.top.value, 0.5f)
            assertEquals(label, 928f, sheet.right.value - sheet.left.value, 0.5f)
            assertEquals(label, 963f, sheet.bottom.value - sheet.top.value, 0.5f)
        }
    }

    @Test
    fun sheetIsLabelledByBodyAndFooterMode() {
        showScreen(buildLearningState(side = CardSideShown.FRONT))
        composeRule.onNodeWithContentDescription("Question").assertExists()

        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true))
        composeRule.onNodeWithContentDescription("Answer").assertExists()

        showScreen(buildBrowsingState(side = CardSideShown.BACK))
        composeRule.onNodeWithContentDescription("Handwritten card").assertExists()

        showScreen(buildLearningState(isContentDamaged = true))
        composeRule.onNodeWithContentDescription("Drawing unavailable").assertExists()
    }

    // --- пустая секция и переходы --------------------------------------------------------------

    @Test
    fun emptySectionShowsIconAndTitle() {
        showScreen(buildEmptyState(isPool = false))
        composeRule.onNodeWithText("No cards").assertExists()
        composeRule.onNodeWithContentDescription("Empty section").assertExists()
        composeRule.onNodeWithTag("slot").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Show answer").assertDoesNotExist()

        showScreen(buildEmptyState(isPool = true))
        composeRule.onNodeWithText("No new cards").assertExists()
    }

    @Test
    fun emptySectionStacksIconAndBoldTitleAtCenterOfSheet() {
        showScreen(buildEmptyState(isPool = false))

        // Столбец .card: значок 64 с отступом 4, зазор 28, заголовок; всё по центру листа 928 × 963. Высоту строки текста
        // задаёт шрифт (Robolectric округляет её вверх), поэтому берём измеренную, а заданную проверяем по стилю.
        val title = composeRule.onNodeWithText("No cards", useUnmergedTree = true)
        val bounds = title.getUnclippedBoundsInRoot()
        val stackTop = 200f + (963f - (68f + 28f + (bounds.bottom.value - bounds.top.value))) / 2f
        assertEquals(stackTop + 68f + 28f, bounds.top.value, 1f)
        assertEquals(32f + 928f / 2f, (bounds.left.value + bounds.right.value) / 2f, 1f)
        val style = readTextStyle(title)
        assertEquals(48f, style.fontSize.value, 0.01f)
        assertEquals(55.2f, style.lineHeight.value, 0.01f)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(-1.3f, style.letterSpacing.value, 0.01f)
        assertEquals(TextAlign.Center, style.textAlign)
    }

    @Test
    fun emptySectionIconIsThinCardsAbove() {
        showScreen(buildEmptyState(isPool = false))

        val titleTop = composeRule.onNodeWithText("No cards", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        val sheetTop = composeRule.onNodeWithContentDescription("Empty section").getUnclippedBoundsInRoot().top.value
        val bitmap = composeRule.onNodeWithContentDescription("Empty section").captureToImage().asAndroidBitmap()
        val iconTop = (titleTop - 28f - 68f - sheetTop).toInt()
        // Значок 64 dp с линией 1,2: тоньше обычных значков, поэтому габариты точные.
        assertIconShape("пустая секция", bitmap, 432 until 496, iconTop until iconTop + 64, IconId.CARDS, 64f, Tokens.IconEmptyStroke, tolerance = 1.2f)
    }

    @Test
    fun transitionStacksIconNumeralAndHintAtCenterOfSheet() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))

        // Значок 64 с отступом 4, зазор 28, цифра 80 sp, зазор 28, подсказка 24 sp; высоты строк измеряем (см. выше).
        val numeral = composeRule.onNode(hasText("II") and hasAnyAncestor(hasContentDescription("Next pass: epoch II")), useUnmergedTree = true)
        val hint = composeRule.onNodeWithText("23 cards", useUnmergedTree = true)
        val numeralBounds = numeral.getUnclippedBoundsInRoot()
        val hintBounds = hint.getUnclippedBoundsInRoot()
        val textHeights = (numeralBounds.bottom.value - numeralBounds.top.value) + (hintBounds.bottom.value - hintBounds.top.value)
        val stackTop = 200f + (963f - (68f + 28f + 28f + textHeights)) / 2f
        assertEquals(stackTop + 68f + 28f, numeralBounds.top.value, 1f)
        assertEquals(numeralBounds.bottom.value + 28f, hintBounds.top.value, 1f)
        val numeralStyle = readTextStyle(numeral)
        assertEquals(80f, numeralStyle.fontSize.value, 0.01f)
        assertEquals(92f, numeralStyle.lineHeight.value, 0.01f)
        assertEquals(FontWeight.Bold, numeralStyle.fontWeight)
        assertEquals(-3f, numeralStyle.letterSpacing.value, 0.01f)
        val hintStyle = readTextStyle(hint)
        assertEquals(24f, hintStyle.fontSize.value, 0.01f)
        assertEquals(36f, hintStyle.lineHeight.value, 0.01f)
    }

    @Test
    fun regularTransitionNumeralIsShiftedLeft() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))

        val numeral = findNumeralBounds("Next pass: epoch II", "II")
        val hint = composeRule.onNodeWithText("23 cards", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // `.card .next-epoch`: translateX(-6px), на подпись под цифрой сдвиг не действует.
        assertEquals(-6f, centerX(numeral) - centerX(hint), 1f)
    }

    @Test
    fun insertedAndResumedNumeralsAreNotShifted() {
        showScreen(buildTransitionState(TransitionKind.INSERTED, 1, 40, 40))
        val inserted = findNumeralBounds("Extra pass", "I")
        val insertedHint = composeRule.onNodeWithText("40 cards", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(0f, centerX(inserted) - centerX(insertedHint), 1f)

        showScreen(buildTransitionState(TransitionKind.RESUMED, 4, 60, 100))
        val resumed = findNumeralBounds("Resume pass", "IV")
        val resumedHint = composeRule.onNodeWithText("60 of 100 remaining", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(0f, centerX(resumed) - centerX(resumedHint), 1f)
    }

    @Test
    fun numeralShiftDoesNotChangeVerticalLayout() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))
        val regular = findNumeralBounds("Next pass: epoch II", "II")
        val regularHint = composeRule.onNodeWithText("23 cards", useUnmergedTree = true).getUnclippedBoundsInRoot()

        showScreen(buildTransitionState(TransitionKind.INSERTED, 2, 23, 23))
        val inserted = findNumeralBounds("Extra pass", "II")
        val insertedHint = composeRule.onNodeWithText("23 cards", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertEquals(inserted.top.value, regular.top.value, 0.5f)
        assertEquals(insertedHint.top.value, regularHint.top.value, 0.5f)
        assertEquals(inserted.right.value - inserted.left.value, regular.right.value - regular.left.value, 0.5f)
    }

    @Test
    fun transitionIconFollowsKind() {
        val cases = listOf(
            Triple(TransitionKind.REGULAR, "Next pass: epoch II", IconId.CHECK),
            Triple(TransitionKind.INSERTED, "Extra pass", IconId.RIGHT),
            Triple(TransitionKind.RESUMED, "Resume pass", IconId.UNDO),
        )
        for ((kind, label, icon) in cases) {
            showScreen(buildTransitionState(kind, 2, 23, 23))

            val numeral = composeRule.onNode(hasText("II") and hasAnyAncestor(hasContentDescription(label)), useUnmergedTree = true)
            val sheet = composeRule.onNodeWithContentDescription(label)
            val iconTop = (numeral.getUnclippedBoundsInRoot().top.value - 28f - 68f - sheet.getUnclippedBoundsInRoot().top.value).toInt()
            val bitmap = sheet.captureToImage().asAndroidBitmap()
            assertIconShape(label, bitmap, 432 until 496, iconTop until iconTop + 64, icon, 64f, Tokens.IconEmptyStroke, tolerance = 1.2f)
        }
    }

    @Test
    fun cardCountWordAgrees() {
        val expected = mapOf(1 to "1 card", 23 to "23 cards", 40 to "40 cards", 112 to "112 cards")
        for ((count, text) in expected) {
            showScreen(buildTransitionState(TransitionKind.REGULAR, 2, count, count))
            composeRule.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun transitionsShowEpochAndRemainingLikeMockup() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))
        // Вкладка эпохи II тоже подписана «II»: цифра листа — только та, что лежит внутри самого листа.
        composeRule.onNode(hasText("II") and hasAnyAncestor(hasContentDescription("Next pass: epoch II"))).assertExists()
        composeRule.onNodeWithText("23 cards").assertExists()
        composeRule.onNodeWithContentDescription("Next pass: epoch II").assertExists()
        composeRule.onNodeWithText("Start pass").assertExists()

        showScreen(buildTransitionState(TransitionKind.INSERTED, 1, 40, 40))
        composeRule.onNodeWithText("40 cards").assertExists()
        composeRule.onNodeWithContentDescription("Extra pass").assertExists()
        composeRule.onNodeWithText("Start pass").assertExists()

        showScreen(buildTransitionState(TransitionKind.RESUMED, 4, 60, 100))
        composeRule.onNode(hasText("IV") and hasAnyAncestor(hasContentDescription("Resume pass"))).assertExists()
        composeRule.onNodeWithText("60 of 100 remaining").assertExists()
        composeRule.onNodeWithContentDescription("Resume pass").assertExists()
        composeRule.onNodeWithText("Continue").assertExists()
        composeRule.onNodeWithText("Start pass").assertDoesNotExist()
    }

    @Test
    fun startButtonFillsWholeFooterAndSendsStartPass() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))

        composeRule.onNodeWithText("Start pass").assertLeftPositionInRootIsEqualTo(32.dp).assertWidthIsEqualTo(928.dp).assertHeightIsEqualTo(112.dp)
        assertEquals(3, measureBorder(composeRule.onNodeWithText("Start pass")))
        composeRule.onNodeWithText("Start pass").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.StartPass), actions)
    }

    @Test
    fun transitionShowsNoMetricsMenuOrPlus() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))

        composeRule.onNodeWithText("Learning").assertDoesNotExist()
        composeRule.onNodeWithText("Successes").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Create card").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Card actions").assertCountEquals(0)
    }

    // --- меню карточки -------------------------------------------------------------------------

    @Test
    fun menuOffersStateItemsOnly() {
        val variants = mapOf(
            listOf(CardMenuItem.EDIT, CardMenuItem.DELETE) to listOf("Edit", "Delete card"),
            listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE) to listOf("Return to New", "Delete card"),
            listOf(CardMenuItem.DELETE) to listOf("Delete card"),
        )
        val everything = listOf("Edit", "Return to New", "Delete card")
        for ((menu, shown) in variants) {
            showScreen(buildBrowsingState(menu = menu))
            openMenu()
            for (label in everything) {
                if (label in shown) composeRule.onNodeWithText(label).assertExists() else composeRule.onNodeWithText(label).assertDoesNotExist()
            }
        }
    }

    @Test
    fun menuItemsCarryTheirIconsAndOnlyDeleteHasLineAbove() {
        showScreen(buildBrowsingState(menu = listOf(CardMenuItem.EDIT, CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE)))
        openMenu()

        val icons = listOf("Edit" to IconId.PEN, "Return to New" to IconId.UNDO, "Delete card" to IconId.TRASH)
        for ((label, icon) in icons) assertIconBeforeLabel(label, icon, borderMargin = 0, topMargin = 3)
        // Линия в 1 px сверху только у опасного пункта.
        val delete = composeRule.onNodeWithText("Delete card").captureToImage().asAndroidBitmap()
        assertTrue(delete.isInk(200, 0))
        for (label in listOf("Edit", "Return to New")) {
            assertTrue(label, !composeRule.onNodeWithText(label).captureToImage().asAndroidBitmap().isInk(200, 0))
        }
    }

    @Test
    fun menuItemSendsActionAndClosesMenu() {
        showScreen(buildLearningState(menu = listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE)))
        openMenu()
        composeRule.onNodeWithText("Return to New").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.ChooseMenuItem(CardMenuItem.RETURN_TO_NEW)), actions)
        composeRule.onNodeWithText("Delete card").assertDoesNotExist()
    }

    @Test
    fun touchOutsideMenuClosesItWithoutAction() {
        showScreen(buildLearningState())
        openMenu()
        composeRule.onNodeWithText("Delete card").assertExists()

        composeRule.onNodeWithContentDescription("Close menu").performClick()

        composeRule.onNodeWithText("Delete card").assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun menuSitsAtMockupPositionAndItemsFollowTheirOrder() {
        showScreen(buildBrowsingState(menu = listOf(CardMenuItem.EDIT, CardMenuItem.DELETE)))
        openMenu()

        val edit = composeRule.onNodeWithText("Edit").getUnclippedBoundsInRoot()
        val delete = composeRule.onNodeWithText("Delete card").getUnclippedBoundsInRoot()
        assertTrue(edit.top.value < delete.top.value)
        composeRule.onNode(hasContentDescription("Card actions") and hasNoClickAction())
            .assertLeftPositionInRootIsEqualTo((992 - 32 - 432).dp).assertTopPositionInRootIsEqualTo(108.dp)
    }

    // --- окна ----------------------------------------------------------------------------------

    @Test
    fun deleteDialogUsesMockupTexts() {
        showScreen(buildLearningState(dialog = DeckDialog.CONFIRM_DELETE_CARD))

        composeRule.onNodeWithText("Delete card?").assertExists()
        composeRule.onNodeWithText("Both sides will be deleted. You can't undo this.").assertExists()
        // Первым стоит «Оставить» (основная), вторым «Удалить».
        val keep = composeRule.onNodeWithText("Keep").getUnclippedBoundsInRoot()
        val delete = composeRule.onNodeWithText("Delete").getUnclippedBoundsInRoot()
        assertTrue(keep.left.value < delete.left.value)
        composeRule.onNodeWithText("Keep").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf(DeckAction.DismissDialog, DeckAction.ConfirmDialog), actions)
    }

    @Test
    fun returnDialogUsesMockupTexts() {
        showScreen(buildLearningState(dialog = DeckDialog.CONFIRM_RETURN_TO_NEW))

        composeRule.onNodeWithText("Return to New?").assertExists()
        composeRule.onNodeWithText("Your success count will reset.").assertExists()
        composeRule.onNodeWithText("Keep").performClick()
        composeRule.onNodeWithText("Return").performClick()
        assertEquals(listOf(DeckAction.DismissDialog, DeckAction.ConfirmDialog), actions)
    }

    @Test
    fun needNewCardDialogOffersOpeningPool() {
        showScreen(buildEmptyState(isPool = true, dialog = DeckDialog.NEED_NEW_CARD))

        composeRule.onNodeWithText("Nothing to review yet").assertExists()
        composeRule.onNodeWithText("No cards to study.").assertExists()
        composeRule.onNodeWithText("Open New").performClick()
        assertEquals(listOf<DeckAction>(DeckAction.ConfirmDialog), actions)
    }

    @Test
    fun doneForTodayDialogHasOnlyOpenNewWithArrow() {
        showScreen(buildEmptyState(isPool = true, dialog = DeckDialog.DONE_FOR_TODAY))

        composeRule.onNodeWithText("All done for today").assertExists()
        composeRule.onNodeWithText("Open New").assertExists()
        composeRule.onNodeWithText("Keep").assertDoesNotExist()
        // Пояснения у окна нет (в макете `modal` текст пуст), у кнопки — стрелка.
        composeRule.onNodeWithText("No cards to study.").assertDoesNotExist()
        assertIconBeforeLabel("Open New", IconId.RIGHT, borderMargin = 8)
        composeRule.onNodeWithText("Open New").performClick()
        assertEquals(listOf<DeckAction>(DeckAction.ConfirmDialog), actions)
    }

    @Test
    fun dialogBlocksScreenUnderIt() {
        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true, dialog = DeckDialog.CONFIRM_DELETE_CARD))

        // Кнопки экрана под окном касаний не получают.
        touchAt(32f + 256f + 100f, 1323f - 32f - 56f)
        touchAt(32f + 56f, 1323f - 32f - 56f)
        touchAt(900f, 80f)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun dialogOpenedWhileMenuIsShownHidesMenu() {
        showScreen(buildLearningState())
        openMenu()
        composeRule.onNodeWithText("Delete card").assertExists()

        showScreen(buildLearningState(dialog = DeckDialog.CONFIRM_DELETE_CARD))

        composeRule.onNodeWithText("Delete card?").assertExists()
        composeRule.onNodeWithText("Edit").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Close menu").assertDoesNotExist()
    }

    // --- вспомогательное -----------------------------------------------------------------------

    // Экран один на весь тест; повторный вызов подменяет состояние, как это делает приложение.
    private var screenState by mutableStateOf<DeckScreenState?>(null)
    private var isHosted = false

    private fun assertMetricShown(label: String, current: Int, total: Int) {
        composeRule.onNode(hasText(label) and hasText("$current") and hasText("/") and hasText("$total")).assertExists()
    }

    private fun findNumeralBounds(sheetDescription: String, numeral: String) =
        composeRule.onNode(hasText(numeral) and hasAnyAncestor(hasContentDescription(sheetDescription)), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

    private fun centerX(bounds: DpRect) = (bounds.left.value + bounds.right.value) / 2f

    private fun showScreen(state: DeckScreenState) {
        screenState = state
        if (isHosted) {
            composeRule.waitForIdle()
            return
        }
        isHosted = true
        composeRule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(Color.White).testTag("frame")) {
                screenState?.let { shown -> DeckScreen(shown, { actions += it }) { modifier -> Box(modifier.testTag("slot")) } }
            }
        }
    }

    private fun touchAt(x: Float, y: Float) {
        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(x, y)) }
    }

    private fun openMenu() {
        composeRule.onNode(hasContentDescription("Card actions") and hasClickAction()).performClick()
    }

    private fun measureBorder(description: String): Int = measureBorder(composeRule.onNodeWithContentDescription(description))

    private fun measureInkWidth(node: SemanticsNodeInteraction): Float {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val inkColumns = (0 until bitmap.width).filter { x -> (0 until bitmap.height).any { y -> bitmap.isInk(x, y) } }
        return (inkColumns.last() - inkColumns.first() + 1).toFloat()
    }

    private fun assertIconBeforeLabel(label: String, id: IconId, borderMargin: Int, topMargin: Int = borderMargin) =
        composeRule.assertIconBeforeLabel(label, id, borderMargin, topMargin)

    private fun measureBorder(node: SemanticsNodeInteraction): Int {
        val bitmap = node.captureToImage().asAndroidBitmap()
        return (0 until 8).takeWhile { bitmap.isInk(it, bitmap.height / 2) }.size
    }

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = AndroidColor.red(getPixel(x, y)) < 128

    private fun buildTabs(openIndex: Int = 2, activeIndex: Int = 2): List<TabState> {
        val counts = listOf(112, 40, 23, 9, 0, 61)
        return SectionTab.entries.mapIndexed { index, tab -> TabState(tab, counts[index], index == openIndex, index == activeIndex) }
    }

    private fun buildHeader(
        deckName: String = "English A2",
        position: PositionMetric? = PositionMetric(PositionKind.LEARNING, 14, 40),
        successes: SuccessMetric? = SuccessMetric(3, 4),
        canCreateCard: Boolean = false,
        menu: List<CardMenuItem> = listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE),
    ) = DeckHeader(deckName, position, successes, canCreateCard, menu)

    private fun buildLearningState(
        side: CardSideShown = CardSideShown.FRONT,
        areRatingsShown: Boolean = false,
        postpone: ActionState = ActionState(true),
        flip: ActionState = ActionState(true),
        isContentDamaged: Boolean = false,
        deckName: String = "English A2",
        canCreateCard: Boolean = false,
        menu: List<CardMenuItem> = listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE),
        tabs: List<TabState> = buildTabs(),
        dialog: DeckDialog? = null,
    ) = DeckScreenState(
        header = buildHeader(deckName = deckName, canCreateCard = canCreateCard, menu = menu),
        tabs = tabs,
        body = DeckBody.Card(side, isContentDamaged),
        footer = DeckFooter.Learning(postpone, flip, areRatingsShown),
        dialog = dialog,
    )

    private fun buildBrowsingState(
        side: CardSideShown = CardSideShown.FRONT,
        canBrowse: Boolean = true,
        position: PositionMetric? = PositionMetric(PositionKind.BROWSING, 2, 9),
        successes: SuccessMetric? = SuccessMetric(1, 4),
        menu: List<CardMenuItem> = listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE),
        tabs: List<TabState> = buildTabs(openIndex = 3, activeIndex = 1),
    ) = DeckScreenState(
        header = buildHeader(position = position, successes = successes, menu = menu),
        tabs = tabs,
        body = DeckBody.Card(side, isContentDamaged = false),
        footer = DeckFooter.Browsing(canBrowse),
        dialog = null,
    )

    private fun buildEmptyState(isPool: Boolean, dialog: DeckDialog? = null) = DeckScreenState(
        header = buildHeader(position = null, successes = null, canCreateCard = isPool, menu = emptyList()),
        tabs = buildTabs(openIndex = if (isPool) 0 else 4, activeIndex = 1),
        body = DeckBody.EmptySection(isPool),
        footer = DeckFooter.None,
        dialog = dialog,
    )

    private fun buildTransitionState(kind: TransitionKind, epoch: Int, remaining: Int, total: Int) = DeckScreenState(
        header = buildHeader(position = null, successes = null, menu = emptyList()),
        tabs = buildTabs(openIndex = epoch, activeIndex = epoch),
        body = DeckBody.Transition(epoch, kind, remaining, total),
        footer = DeckFooter.StartPass(kind),
        dialog = null,
    )
}
