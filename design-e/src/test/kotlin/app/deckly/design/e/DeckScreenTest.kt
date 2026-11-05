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

        composeRule.onNodeWithText("Не знал").assertDoesNotExist()
        composeRule.onNodeWithText("Знал").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Показать ответ").assertExists()
        composeRule.onNodeWithContentDescription("В конец прохода").assertExists()
    }

    @Test
    fun answerHasEqualRatings() {
        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true))

        val notKnew = composeRule.onNodeWithText("Не знал").getUnclippedBoundsInRoot()
        val knew = composeRule.onNodeWithText("Знал").getUnclippedBoundsInRoot()
        // Равнозначные кнопки: одной ширины и высоты, в двух последних колонках (328 + 328 с промежутком 16).
        assertEquals(knew.right.value - knew.left.value, notKnew.right.value - notKnew.left.value, 0.01f)
        assertEquals(knew.bottom.value - knew.top.value, notKnew.bottom.value - notKnew.top.value, 0.01f)
        composeRule.onNodeWithText("Не знал").assertLeftPositionInRootIsEqualTo(32.dp + 256.dp).assertWidthIsEqualTo(328.dp).assertHeightIsEqualTo(112.dp)
        composeRule.onNodeWithText("Знал").assertLeftPositionInRootIsEqualTo(32.dp + 600.dp).assertWidthIsEqualTo(328.dp)
        // Обе с обычной рамкой в 2 пикселя, ни одна не выделена.
        for (label in listOf("Не знал", "Знал")) {
            val bitmap = composeRule.onNodeWithText(label).captureToImage().asAndroidBitmap()
            assertEquals(label, 2, (0 until 8).takeWhile { bitmap.isInk(it, bitmap.height / 2) }.size)
        }
        composeRule.onNodeWithText("Не знал").performClick()
        composeRule.onNodeWithText("Знал").performClick()
        assertEquals(listOf(DeckAction.DidNotKnow, DeckAction.Knew), actions)
    }

    @Test
    fun blockedPostponeExplainsReasonInDescription() {
        val reasons = mapOf(
            ActionBlock.ANSWER_REVEALED to "В конец прохода: ответ уже открыт",
            ActionBlock.ALREADY_POSTPONED to "В конец прохода: уже отложена",
            ActionBlock.LAST_IN_PASS to "В конец прохода: последняя",
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

        composeRule.onNodeWithContentDescription("В конец прохода").assertIsEnabled().performClick()

        assertEquals(listOf<DeckAction>(DeckAction.Postpone), actions)
        // Тихая кнопка без рамки, а недоступная была бы серой.
        val bitmap = composeRule.onNodeWithContentDescription("В конец прохода").captureToImage().asAndroidBitmap()
        assertFalse(bitmap.isInk(0, bitmap.height / 2))
    }

    @Test
    fun flipIsSecondCellInLearningAndBrowsing() {
        showScreen(buildLearningState(side = CardSideShown.FRONT))
        composeRule.onNodeWithContentDescription("Показать ответ")
            .assertLeftPositionInRootIsEqualTo(32.dp + 128.dp).assertWidthIsEqualTo(112.dp).assertHeightIsEqualTo(112.dp)
            .assertTopPositionInRootIsEqualTo(1323.dp - 32.dp - 112.dp)
        composeRule.onNodeWithContentDescription("В конец прохода").assertLeftPositionInRootIsEqualTo(32.dp).assertWidthIsEqualTo(112.dp)

        showScreen(buildBrowsingState(side = CardSideShown.BACK))
        composeRule.onNodeWithContentDescription("Показать вопрос")
            .assertLeftPositionInRootIsEqualTo(32.dp + 128.dp).assertWidthIsEqualTo(112.dp).assertHeightIsEqualTo(112.dp)
        composeRule.onNodeWithContentDescription("Предыдущая карточка").assertLeftPositionInRootIsEqualTo(32.dp).assertWidthIsEqualTo(112.dp)
        composeRule.onNodeWithContentDescription("Следующая карточка").assertLeftPositionInRootIsEqualTo(32.dp + 816.dp).assertWidthIsEqualTo(112.dp)
    }

    @Test
    fun flipIsPrimaryOnQuestionOfLearningAndOrdinaryOnAnswerAndWhenBrowsing() {
        showScreen(buildLearningState(side = CardSideShown.FRONT))
        assertEquals(3, measureBorder("Показать ответ"))

        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true))
        assertEquals(2, measureBorder("Показать вопрос"))

        showScreen(buildBrowsingState(side = CardSideShown.FRONT))
        assertEquals(2, measureBorder("Показать ответ"))
    }

    @Test
    fun flipSendsFlipInBothModes() {
        showScreen(buildLearningState())
        composeRule.onNodeWithContentDescription("Показать ответ").performClick()
        showScreen(buildBrowsingState())
        composeRule.onNodeWithContentDescription("Показать ответ").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.Flip, DeckAction.Flip), actions)
    }

    // --- просмотр ------------------------------------------------------------------------------

    @Test
    fun browsingArrowsSendActionsAndAreGreyForSingleCard() {
        showScreen(buildBrowsingState(canBrowse = true))
        composeRule.onNodeWithContentDescription("Предыдущая карточка").performClick()
        composeRule.onNodeWithContentDescription("Следующая карточка").performClick()
        assertEquals(listOf(DeckAction.ShowPrevious, DeckAction.ShowNext), actions)

        actions.clear()
        showScreen(buildBrowsingState(canBrowse = false))
        composeRule.onNodeWithContentDescription("Предыдущая карточка").assertIsNotEnabled().performClick()
        composeRule.onNodeWithContentDescription("Следующая карточка").assertIsNotEnabled().performClick()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun browsingHeaderSaysViewing() {
        showScreen(buildBrowsingState(position = PositionMetric(PositionKind.BROWSING, 2, 112), successes = null))

        composeRule.onNodeWithText("Просмотр").assertExists()
        composeRule.onNodeWithText("2 / 112").assertExists()
        composeRule.onNodeWithContentDescription("Просмотр, карточка 2 из 112").assertExists()
        composeRule.onNodeWithText("Успехи").assertDoesNotExist()
    }

    @Test
    fun ratingsAndStartCarryIconsAndLargeLabels() {
        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true))
        for ((label, icon) in listOf("Не знал" to IconId.CROSS, "Знал" to IconId.CHECK)) {
            assertEquals(label, 26f, readTextStyle(composeRule.onNodeWithText(label, useUnmergedTree = true)).fontSize.value, 0.01f)
            assertIconBeforeLabel(label, icon, borderMargin = 8)
        }

        for ((kind, label) in listOf(TransitionKind.REGULAR to "Начать проход", TransitionKind.RESUMED to "Продолжить")) {
            showScreen(buildTransitionState(kind, 4, 60, 100))
            assertEquals(label, 26f, readTextStyle(composeRule.onNodeWithText(label, useUnmergedTree = true)).fontSize.value, 0.01f)
            assertIconBeforeLabel(label, IconId.RIGHT, borderMargin = 8)
        }
    }

    @Test
    fun unavailableFlipIsGreyAndInert() {
        showScreen(buildLearningState(flip = ActionState(false)))

        composeRule.onNodeWithContentDescription("Показать ответ").assertIsNotEnabled().performClick()

        assertTrue(actions.isEmpty())
    }

    // --- шапка ---------------------------------------------------------------------------------

    @Test
    fun headerShowsDeckNameMetricsAndButtonsInMockupOrder() {
        showScreen(buildLearningState(canCreateCard = true))

        val name = composeRule.onNodeWithContentDescription("Немецкий A2. Выбрать колоду").getUnclippedBoundsInRoot()
        val position = composeRule.onNodeWithContentDescription("Обучение, карточка 14 из 40").getUnclippedBoundsInRoot()
        val successes = composeRule.onNodeWithText("Успехи").getUnclippedBoundsInRoot()
        val create = composeRule.onNodeWithContentDescription("Создать карточку").getUnclippedBoundsInRoot()
        val menu = composeRule.onNode(hasContentDescription("Действия с карточкой") and hasClickAction()).getUnclippedBoundsInRoot()
        // Название слева, затем счётчик позиции, успехи, плюс и «⋯».
        assertTrue(name.left.value < position.left.value)
        assertTrue(position.right.value <= successes.left.value)
        assertTrue(successes.right.value <= create.left.value)
        assertTrue(create.right.value <= menu.left.value)
        composeRule.onNodeWithText("Обучение").assertExists()
        composeRule.onNodeWithText("14 / 40").assertExists()
        composeRule.onNodeWithText("3 / 4").assertExists()
        composeRule.onNodeWithText("Немецкий A2").assertExists()
        // Кнопки шапки квадратные 64 × 64, у «⋯» правый край совпадает с полем кадра.
        composeRule.onNodeWithContentDescription("Создать карточку").assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(64.dp)
        assertEquals(992f - 32f, menu.right.value, 0.5f)
    }

    @Test
    fun headerButtonsAppearOnlyWhenOffered() {
        showScreen(buildLearningState(canCreateCard = false, menu = emptyList()))

        composeRule.onNodeWithContentDescription("Создать карточку").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Действия с карточкой").assertCountEquals(0)
    }

    @Test
    fun deckNameOpensDeckList() {
        showScreen(buildLearningState())

        composeRule.onNodeWithContentDescription("Немецкий A2. Выбрать колоду").performClick()
        composeRule.onNodeWithContentDescription("Создать карточку").assertDoesNotExist()

        assertEquals(listOf<DeckAction>(DeckAction.OpenDeckList), actions)
    }

    @Test
    fun plusCreatesCard() {
        showScreen(buildLearningState(canCreateCard = true))

        composeRule.onNodeWithContentDescription("Создать карточку").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.CreateCard), actions)
    }

    @Test
    fun longDeckNameDoesNotPushButtonsOffScreen() {
        showScreen(buildLearningState(deckName = "Очень длинное название колоды, которое не помещается в строку шапки целиком", canCreateCard = true))

        val menu = composeRule.onNode(hasContentDescription("Действия с карточкой") and hasClickAction()).getUnclippedBoundsInRoot()
        assertEquals(992f - 32f, menu.right.value, 0.5f)
    }

    @Test
    fun headerIconButtonsKeepFullIconWithoutInnerPadding() {
        showScreen(buildLearningState(canCreateCard = true))

        // Значок 32 dp без полей: крест плюса и крайние точки «⋯» занимают 16 клеток сетки 24 и линию 1,8, около 23,7 px.
        // С внутренними полями обычной кнопки значок сжался бы до высоты 28 dp, то есть до 20,7 px.
        assertEquals(23.7f, measureInkWidth(composeRule.onNodeWithContentDescription("Создать карточку")), 1f)
        assertEquals(23.7f, measureInkWidth(composeRule.onNode(hasContentDescription("Действия с карточкой") and hasClickAction())), 1f)
    }

    @Test
    fun deckNameIsFollowedByTwentyDpArrowTenDpApart() {
        showScreen(buildLearningState(deckName = "Немецкий A2"))

        val heading = composeRule.onNodeWithContentDescription("Немецкий A2. Выбрать колоду")
        val textRight = composeRule.onNodeWithText("Немецкий A2", useUnmergedTree = true).getUnclippedBoundsInRoot().right.value
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
        showScreen(buildLearningState(deckName = "Немецкий A2"))

        val style = readTextStyle(composeRule.onNodeWithText("Немецкий A2", useUnmergedTree = true))
        assertEquals(28f, style.fontSize.value, 0.01f)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(-0.5f, style.letterSpacing.value, 0.01f)
        // Цель касания названия не ниже строки шапки: 64 dp.
        composeRule.onNodeWithContentDescription("Немецкий A2. Выбрать колоду").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun positionMetricIsOneNodeForScreenReader() {
        showScreen(buildLearningState())

        // Подпись и значение читаются одной фразой «Обучение, карточка 14 из 40», а не отдельно от неё.
        composeRule.onNode(hasContentDescription("Обучение, карточка 14 из 40") and hasText("14 / 40") and hasText("Обучение")).assertExists()
    }

    // --- вкладки -------------------------------------------------------------------------------

    @Test
    fun activeMarkFollowsState() {
        showScreen(buildLearningState(tabs = buildTabs(openIndex = 2, activeIndex = 2)))
        composeRule.onNodeWithContentDescription("II, 23 карточки, сейчас обучение").assertExists()
        composeRule.onNodeWithContentDescription("I, 40 карточек").assertExists()

        // Открыта другая секция, активная прежняя: отметка ▶ остаётся у II.
        showScreen(buildBrowsingState(tabs = buildTabs(openIndex = 3, activeIndex = 2)))
        composeRule.onNodeWithContentDescription("II, 23 карточки, сейчас обучение").assertExists()
        composeRule.onNodeWithContentDescription("III, 9 карточек").assertExists()
        composeRule.onNodeWithContentDescription("Секции колоды").assertExists()
    }

    @Test
    fun tabClickOpensSection() {
        showScreen(buildLearningState())

        composeRule.onNodeWithContentDescription("Выучено, 61 карточка").performClick()

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
            buildLearningState() to "Вопрос",
            buildBrowsingState() to "Рукописная карточка",
            buildEmptyState(isPool = false) to "Пустая секция",
            buildTransitionState(TransitionKind.REGULAR, 2, 23, 23) to "Следующий проход: эпоха II",
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
        composeRule.onNodeWithContentDescription("Вопрос").assertExists()

        showScreen(buildLearningState(side = CardSideShown.BACK, areRatingsShown = true))
        composeRule.onNodeWithContentDescription("Ответ").assertExists()

        showScreen(buildBrowsingState(side = CardSideShown.BACK))
        composeRule.onNodeWithContentDescription("Рукописная карточка").assertExists()

        showScreen(buildLearningState(isContentDamaged = true))
        composeRule.onNodeWithContentDescription("Рисунок не открылся").assertExists()
    }

    // --- пустая секция и переходы --------------------------------------------------------------

    @Test
    fun emptySectionShowsIconAndTitle() {
        showScreen(buildEmptyState(isPool = false))
        composeRule.onNodeWithText("Нет карточек").assertExists()
        composeRule.onNodeWithContentDescription("Пустая секция").assertExists()
        composeRule.onNodeWithTag("slot").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Показать ответ").assertDoesNotExist()

        showScreen(buildEmptyState(isPool = true))
        composeRule.onNodeWithText("Нет новых карточек").assertExists()
    }

    @Test
    fun emptySectionStacksIconAndBoldTitleAtCenterOfSheet() {
        showScreen(buildEmptyState(isPool = false))

        // Столбец .card: значок 64 с отступом 4, зазор 28, заголовок; всё по центру листа 928 × 963. Высоту строки текста
        // задаёт шрифт (Robolectric округляет её вверх), поэтому берём измеренную, а заданную проверяем по стилю.
        val title = composeRule.onNodeWithText("Нет карточек", useUnmergedTree = true)
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

        val titleTop = composeRule.onNodeWithText("Нет карточек", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        val sheetTop = composeRule.onNodeWithContentDescription("Пустая секция").getUnclippedBoundsInRoot().top.value
        val bitmap = composeRule.onNodeWithContentDescription("Пустая секция").captureToImage().asAndroidBitmap()
        val iconTop = (titleTop - 28f - 68f - sheetTop).toInt()
        // Значок 64 dp с линией 1,2: тоньше обычных значков, поэтому габариты точные.
        assertIconShape("пустая секция", bitmap, 432 until 496, iconTop until iconTop + 64, IconId.CARDS, 64f, Tokens.IconEmptyStroke, tolerance = 1.2f)
    }

    @Test
    fun transitionStacksIconNumeralAndHintAtCenterOfSheet() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))

        // Значок 64 с отступом 4, зазор 28, цифра 80 sp, зазор 28, подсказка 24 sp; высоты строк измеряем (см. выше).
        val numeral = composeRule.onNode(hasText("II") and hasAnyAncestor(hasContentDescription("Следующий проход: эпоха II")), useUnmergedTree = true)
        val hint = composeRule.onNodeWithText("23 карточки", useUnmergedTree = true)
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
    fun transitionIconFollowsKind() {
        val cases = listOf(
            Triple(TransitionKind.REGULAR, "Следующий проход: эпоха II", IconId.CHECK),
            Triple(TransitionKind.INSERTED, "Вставной проход", IconId.RIGHT),
            Triple(TransitionKind.RESUMED, "Продолжение прохода", IconId.UNDO),
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
        val expected = mapOf(1 to "1 карточка", 23 to "23 карточки", 40 to "40 карточек", 112 to "112 карточек")
        for ((count, text) in expected) {
            showScreen(buildTransitionState(TransitionKind.REGULAR, 2, count, count))
            composeRule.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun transitionsShowEpochAndRemainingLikeMockup() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))
        // Вкладка эпохи II тоже подписана «II»: цифра листа — только та, что лежит внутри самого листа.
        composeRule.onNode(hasText("II") and hasAnyAncestor(hasContentDescription("Следующий проход: эпоха II"))).assertExists()
        composeRule.onNodeWithText("23 карточки").assertExists()
        composeRule.onNodeWithContentDescription("Следующий проход: эпоха II").assertExists()
        composeRule.onNodeWithText("Начать проход").assertExists()

        showScreen(buildTransitionState(TransitionKind.INSERTED, 1, 40, 40))
        composeRule.onNodeWithText("40 карточек").assertExists()
        composeRule.onNodeWithContentDescription("Вставной проход").assertExists()
        composeRule.onNodeWithText("Начать проход").assertExists()

        showScreen(buildTransitionState(TransitionKind.RESUMED, 4, 60, 100))
        composeRule.onNode(hasText("IV") and hasAnyAncestor(hasContentDescription("Продолжение прохода"))).assertExists()
        composeRule.onNodeWithText("Осталось 60 из 100").assertExists()
        composeRule.onNodeWithContentDescription("Продолжение прохода").assertExists()
        composeRule.onNodeWithText("Продолжить").assertExists()
        composeRule.onNodeWithText("Начать проход").assertDoesNotExist()
    }

    @Test
    fun startButtonFillsWholeFooterAndSendsStartPass() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))

        composeRule.onNodeWithText("Начать проход").assertLeftPositionInRootIsEqualTo(32.dp).assertWidthIsEqualTo(928.dp).assertHeightIsEqualTo(112.dp)
        assertEquals(3, measureBorder(composeRule.onNodeWithText("Начать проход")))
        composeRule.onNodeWithText("Начать проход").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.StartPass), actions)
    }

    @Test
    fun transitionShowsNoMetricsMenuOrPlus() {
        showScreen(buildTransitionState(TransitionKind.REGULAR, 2, 23, 23))

        composeRule.onNodeWithText("Обучение").assertDoesNotExist()
        composeRule.onNodeWithText("Успехи").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Создать карточку").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Действия с карточкой").assertCountEquals(0)
    }

    // --- меню карточки -------------------------------------------------------------------------

    @Test
    fun menuOffersStateItemsOnly() {
        val variants = mapOf(
            listOf(CardMenuItem.EDIT, CardMenuItem.DELETE) to listOf("Редактировать", "Удалить карточку"),
            listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE) to listOf("Вернуть в новые", "Удалить карточку"),
            listOf(CardMenuItem.DELETE) to listOf("Удалить карточку"),
        )
        val everything = listOf("Редактировать", "Вернуть в новые", "Удалить карточку")
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

        val icons = listOf("Редактировать" to IconId.PEN, "Вернуть в новые" to IconId.UNDO, "Удалить карточку" to IconId.TRASH)
        for ((label, icon) in icons) assertIconBeforeLabel(label, icon, borderMargin = 0, topMargin = 3)
        // Линия в 1 px сверху только у опасного пункта.
        val delete = composeRule.onNodeWithText("Удалить карточку").captureToImage().asAndroidBitmap()
        assertTrue(delete.isInk(200, 0))
        for (label in listOf("Редактировать", "Вернуть в новые")) {
            assertTrue(label, !composeRule.onNodeWithText(label).captureToImage().asAndroidBitmap().isInk(200, 0))
        }
    }

    @Test
    fun menuItemSendsActionAndClosesMenu() {
        showScreen(buildLearningState(menu = listOf(CardMenuItem.RETURN_TO_NEW, CardMenuItem.DELETE)))
        openMenu()
        composeRule.onNodeWithText("Вернуть в новые").performClick()

        assertEquals(listOf<DeckAction>(DeckAction.ChooseMenuItem(CardMenuItem.RETURN_TO_NEW)), actions)
        composeRule.onNodeWithText("Удалить карточку").assertDoesNotExist()
    }

    @Test
    fun touchOutsideMenuClosesItWithoutAction() {
        showScreen(buildLearningState())
        openMenu()
        composeRule.onNodeWithText("Удалить карточку").assertExists()

        composeRule.onNodeWithContentDescription("Закрыть меню").performClick()

        composeRule.onNodeWithText("Удалить карточку").assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun menuSitsAtMockupPositionAndItemsFollowTheirOrder() {
        showScreen(buildBrowsingState(menu = listOf(CardMenuItem.EDIT, CardMenuItem.DELETE)))
        openMenu()

        val edit = composeRule.onNodeWithText("Редактировать").getUnclippedBoundsInRoot()
        val delete = composeRule.onNodeWithText("Удалить карточку").getUnclippedBoundsInRoot()
        assertTrue(edit.top.value < delete.top.value)
        composeRule.onNode(hasContentDescription("Действия с карточкой") and hasNoClickAction())
            .assertLeftPositionInRootIsEqualTo((992 - 32 - 432).dp).assertTopPositionInRootIsEqualTo(108.dp)
    }

    // --- окна ----------------------------------------------------------------------------------

    @Test
    fun deleteDialogUsesMockupTexts() {
        showScreen(buildLearningState(dialog = DeckDialog.CONFIRM_DELETE_CARD))

        composeRule.onNodeWithText("Удалить карточку?").assertExists()
        composeRule.onNodeWithText("Вопрос и ответ будут удалены безвозвратно.").assertExists()
        // Первым стоит «Оставить» (основная), вторым «Удалить».
        val keep = composeRule.onNodeWithText("Оставить").getUnclippedBoundsInRoot()
        val delete = composeRule.onNodeWithText("Удалить").getUnclippedBoundsInRoot()
        assertTrue(keep.left.value < delete.left.value)
        composeRule.onNodeWithText("Оставить").performClick()
        composeRule.onNodeWithText("Удалить").performClick()
        assertEquals(listOf(DeckAction.DismissDialog, DeckAction.ConfirmDialog), actions)
    }

    @Test
    fun returnDialogUsesMockupTexts() {
        showScreen(buildLearningState(dialog = DeckDialog.CONFIRM_RETURN_TO_NEW))

        composeRule.onNodeWithText("Вернуть в новые?").assertExists()
        composeRule.onNodeWithText("Успехи обнулятся.").assertExists()
        composeRule.onNodeWithText("Оставить").performClick()
        composeRule.onNodeWithText("Вернуть").performClick()
        assertEquals(listOf(DeckAction.DismissDialog, DeckAction.ConfirmDialog), actions)
    }

    @Test
    fun needNewCardDialogOffersOpeningPool() {
        showScreen(buildEmptyState(isPool = true, dialog = DeckDialog.NEED_NEW_CARD))

        composeRule.onNodeWithText("Пока нечего повторять").assertExists()
        composeRule.onNodeWithText("Нет карточек для обучения.").assertExists()
        composeRule.onNodeWithText("Открыть новые").performClick()
        assertEquals(listOf<DeckAction>(DeckAction.ConfirmDialog), actions)
    }

    @Test
    fun doneForTodayDialogHasOnlyOpenNewWithArrow() {
        showScreen(buildEmptyState(isPool = true, dialog = DeckDialog.DONE_FOR_TODAY))

        composeRule.onNodeWithText("На сегодня всё").assertExists()
        composeRule.onNodeWithText("Открыть новые").assertExists()
        composeRule.onNodeWithText("Оставить").assertDoesNotExist()
        // Пояснения у окна нет (в макете `modal` текст пуст), у кнопки — стрелка.
        composeRule.onNodeWithText("Нет карточек для обучения.").assertDoesNotExist()
        assertIconBeforeLabel("Открыть новые", IconId.RIGHT, borderMargin = 8)
        composeRule.onNodeWithText("Открыть новые").performClick()
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
        composeRule.onNodeWithText("Удалить карточку").assertExists()

        showScreen(buildLearningState(dialog = DeckDialog.CONFIRM_DELETE_CARD))

        composeRule.onNodeWithText("Удалить карточку?").assertExists()
        composeRule.onNodeWithText("Редактировать").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Закрыть меню").assertDoesNotExist()
    }

    // --- вспомогательное -----------------------------------------------------------------------

    // Экран один на весь тест; повторный вызов подменяет состояние, как это делает приложение.
    private var screenState by mutableStateOf<DeckScreenState?>(null)
    private var isHosted = false

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
        composeRule.onNode(hasContentDescription("Действия с карточкой") and hasClickAction()).performClick()
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
        deckName: String = "Немецкий A2",
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
        deckName: String = "Немецкий A2",
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
