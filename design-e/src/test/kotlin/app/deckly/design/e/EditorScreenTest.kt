package app.deckly.design.e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.Thickness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class EditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<EditorAction>()

    // --- шапка ---------------------------------------------------------------------------------

    @Test
    fun editorShowsToolsAndSideTabs() {
        showScreen(buildState())

        val described = listOf(
            "Толщина пера", "Тонкая линия", "Средняя линия", "Толстая линия", "Отменить штрих",
            "Сторона карточки", "Инструменты рисования", "Холст. Рисование только пером",
        )
        for (description in described) composeRule.onNodeWithContentDescription(description).assertExists()
        composeRule.onNodeWithText("Вопрос").assertExists()
        composeRule.onNodeWithText("Ответ").assertExists()
        composeRule.onNodeWithText("Сохранить").assertExists()
    }

    @Test
    fun editorHasNoPenAndEraserButtons() {
        showScreen(buildState())

        // Переключателя инструмента нет: пишет кончик пера, стирает боковая кнопка стилуса.
        composeRule.onNodeWithContentDescription("Перо").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Ластик").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Толщина пера").assertExists()
        composeRule.onNodeWithContentDescription("Отменить штрих").assertExists()
    }

    @Test
    fun newAndEditHaveDifferentLeftAction() {
        showScreen(buildState(isNewCard = true))

        composeRule.onNodeWithText("Новая карточка").assertExists()
        composeRule.onNodeWithText("Редактирование").assertDoesNotExist()
        composeRule.onNodeWithText("Удалить").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Удалить карточку").assertDoesNotExist()
        // «Отменить» без значка: левее подписи чернил нет.
        val cancel = composeRule.onNodeWithText("Отменить")
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        val textLeft = composeRule.onNodeWithText("Отменить", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value - cancelBounds.left.value
        val bitmap = cancel.captureToImage().asAndroidBitmap()
        assertNull(measureInkBox(bitmap, 0 until textLeft.toInt() - 1, 0 until bitmap.height))
        composeRule.onNodeWithContentDescription("Отменить создание").performClick()
        assertEquals(listOf<EditorAction>(EditorAction.Cancel), actions)

        actions.clear()
        showScreen(buildState(isNewCard = false))

        composeRule.onNodeWithText("Редактирование").assertExists()
        composeRule.onNodeWithText("Новая карточка").assertDoesNotExist()
        composeRule.onNodeWithText("Отменить").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Отменить создание").assertDoesNotExist()
        composeRule.assertIconBeforeLabel("Удалить", IconId.TRASH, borderMargin = 8)
        composeRule.onNodeWithContentDescription("Удалить карточку").performClick()
        assertEquals(listOf<EditorAction>(EditorAction.Delete), actions)
    }

    @Test
    fun saveIsPrimaryWithCheckAtRightEdge() {
        showScreen(buildState())

        val save = composeRule.onNodeWithText("Сохранить")
        assertEquals(960f, save.getUnclippedBoundsInRoot().right.value, 0.5f)
        composeRule.assertIconBeforeLabel("Сохранить", IconId.CHECK, borderMargin = 8)
        // Главное действие: рамка 3 px.
        assertEquals(3, measureBorder(save))
        save.performClick()
        assertEquals(listOf<EditorAction>(EditorAction.Save), actions)
    }

    @Test
    fun titleIsBoldHeadingBetweenTheButtons() {
        showScreen(buildState(isNewCard = true))

        val title = composeRule.onNodeWithText("Новая карточка", useUnmergedTree = true)
        val style = readTextStyle(title)
        assertEquals(28f, style.fontSize.value, 0.01f)
        assertEquals(33.6f, style.lineHeight.value, 0.01f)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(-0.6f, style.letterSpacing.value, 0.01f)
        val bounds = title.getUnclippedBoundsInRoot()
        val cancel = composeRule.onNodeWithText("Отменить").getUnclippedBoundsInRoot()
        val save = composeRule.onNodeWithText("Сохранить").getUnclippedBoundsInRoot()
        // Промежуток между элементами шапки 24 dp; шапка начинается в 32 dp и занимает 64 dp.
        assertEquals(32f, cancel.left.value, 0.5f)
        assertEquals(cancel.right.value + 24f, bounds.left.value, 0.5f)
        assertEquals(32f + 64f / 2f, (bounds.top.value + bounds.bottom.value) / 2f, 1.5f)
        assertTrue(bounds.right.value <= save.left.value - 24f + 0.5f)
    }

    // --- вкладки сторон ------------------------------------------------------------------------

    @Test
    fun sideTabsFollowStateAndSwitchSide() {
        showScreen(buildState(side = CardSideShown.FRONT))

        composeRule.onNodeWithText("Вопрос").assertIsSelected()
        composeRule.onNodeWithText("Ответ").assertIsNotSelected()
        composeRule.onNodeWithText("Ответ").performClick()
        assertEquals(listOf<EditorAction>(EditorAction.SelectSide(CardSideShown.BACK)), actions)

        showScreen(buildState(side = CardSideShown.BACK))

        composeRule.onNodeWithText("Ответ").assertIsSelected()
        composeRule.onNodeWithText("Вопрос").assertIsNotSelected()
        composeRule.onNodeWithText("Вопрос").performClick()
        assertEquals(EditorAction.SelectSide(CardSideShown.FRONT), actions.last())
    }

    @Test
    fun sideTabsShareWidthWithGapAndUnderlineOnlyTheSelected() {
        showScreen(buildState(side = CardSideShown.FRONT))

        // Две колонки по (928 − 32) / 2 = 448 с промежутком 32; высота полосы 72, линия 1 внизу.
        val front = composeRule.onNodeWithText("Вопрос").getUnclippedBoundsInRoot()
        val back = composeRule.onNodeWithText("Ответ").getUnclippedBoundsInRoot()
        assertEquals(32f, front.left.value, 0.5f)
        assertEquals(448f, front.right.value - front.left.value, 0.5f)
        assertEquals(32f + 448f + 32f, back.left.value, 0.5f)
        assertEquals(448f, back.right.value - back.left.value, 0.5f)
        assertEquals(112f, front.top.value, 0.5f)
        assertEquals(112f + 71f, front.bottom.value, 0.5f)
        val frontImage = composeRule.onNodeWithText("Вопрос").captureToImage().asAndroidBitmap()
        val backImage = composeRule.onNodeWithText("Ответ").captureToImage().asAndroidBitmap()
        // Подчёркивание 4 px у выбранной вкладки; у второй полоса прозрачна.
        assertEquals(4, (0 until 8).count { offset -> frontImage.isInkAt(200, frontImage.height - 1 - offset) })
        assertEquals(0, (0 until 8).count { offset -> backImage.isInkAt(200, backImage.height - 1 - offset) })
        // Линия 1 px под полосой тянется по всей ширине, в том числе между вкладками.
        val frame = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        assertTrue(frame.isInkAt(496, 112 + 71))
        assertTrue(!frame.isInkAt(496, 112 + 68))
    }

    @Test
    fun sideTabLabelsAre24spAndSelectedIsBold() {
        showScreen(buildState(side = CardSideShown.BACK))

        val selected = readTextStyle(composeRule.onNodeWithText("Ответ", useUnmergedTree = true))
        val other = readTextStyle(composeRule.onNodeWithText("Вопрос", useUnmergedTree = true))
        assertEquals(24f, selected.fontSize.value, 0.01f)
        assertEquals(24f, other.fontSize.value, 0.01f)
        assertEquals(FontWeight.Bold, selected.fontWeight)
        assertEquals(FontWeight.Normal, other.fontWeight)
    }

    // --- лист и инструменты --------------------------------------------------------------------

    @Test
    fun sheetSlotAndToolbarKeepMockupGeometry() {
        showScreen(buildState())

        // Лист тот же, что у колоды: 928 × 963 в точке (32, 200); под ним зазор 16 и инструменты высотой 112.
        composeRule.onNodeWithTag("slot").getUnclippedBoundsInRoot().let { slot ->
            assertEquals(32f, slot.left.value, 0.5f)
            assertEquals(200f, slot.top.value, 0.5f)
            assertEquals(928f, slot.right.value - slot.left.value, 0.5f)
            assertEquals(963f, slot.bottom.value - slot.top.value, 0.5f)
        }
        val group = composeRule.onNodeWithContentDescription("Толщина пера").getUnclippedBoundsInRoot()
        val undo = composeRule.onNodeWithContentDescription("Отменить штрих").getUnclippedBoundsInRoot()
        // Группа толщин прижата влево, без черт и боковых полей: три кнопки по 80 с зазором 4 = 248; высота 112 − 2·12.
        assertEquals(32f, group.left.value, 0.5f)
        assertEquals(248f, group.right.value - group.left.value, 0.5f)
        assertEquals(88f, group.bottom.value - group.top.value, 0.5f)
        assertEquals(1323f - 32f - 112f + 12f, group.top.value, 0.5f)
        // Отмена прижата вправо, 112 × 112, на нижней строке рядом с группой.
        assertEquals(960f, undo.right.value, 0.5f)
        assertEquals(112f, undo.right.value - undo.left.value, 0.5f)
        assertEquals(112f, undo.bottom.value - undo.top.value, 0.5f)
        assertEquals(1323f - 32f - 112f, undo.top.value, 0.5f)
    }

    @Test
    fun undoButtonCarriesItsIcon() {
        showScreen(buildState())

        assertIconInButton("отмена штриха", composeRule.onNodeWithContentDescription("Отменить штрих"), IconId.UNDO)
    }

    @Test
    fun thicknessSamplesHaveMockupHeightsAndSelectionBorder() {
        showScreen(buildState(thickness = Thickness.MEDIUM))

        val heights = listOf("Тонкая линия" to 2f, "Средняя линия" to 7f, "Толстая линия" to 15f)
        for ((description, height) in heights) {
            val button = composeRule.onNodeWithContentDescription(description)
            button.assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(72.dp)
            val bitmap = button.captureToImage().asAndroidBitmap()
            // Образец: чёрная полоса 34 dp заданной высоты по центру кнопки.
            val sample = measureInkBox(bitmap, 6 until bitmap.width - 6, 6 until bitmap.height - 6)
            assertNotNull(description, sample)
            assertEquals(description, 34f, sample!!.width, 1.5f)
            assertEquals(description, height, sample.height, 1.5f)
        }
        // Концы образца скруглены: у самой толстой линии угловой пиксель не закрашен.
        val thick = composeRule.onNodeWithContentDescription("Толстая линия").captureToImage().asAndroidBitmap()
        val columns = (0 until thick.width).filter { x -> (0 until thick.height).any { y -> y in 6 until thick.height - 6 && thick.isInkAt(x, y) } }
        val rows = (6 until thick.height - 6).filter { y -> (6 until thick.width - 6).any { x -> thick.isInkAt(x, y) } }
        assertTrue(!thick.isInkAt(columns.first(), rows.first()))
        assertTrue(thick.isInkAt(columns.first() + 4, rows.first() + 4))
        // Выбранная толщина в рамке 3 px, остальные без видимой рамки.
        assertEquals(3, measureBorder(composeRule.onNodeWithContentDescription("Средняя линия")))
        assertEquals(0, measureBorder(composeRule.onNodeWithContentDescription("Тонкая линия")))
        assertEquals(0, measureBorder(composeRule.onNodeWithContentDescription("Толстая линия")))
        composeRule.onNodeWithContentDescription("Средняя линия").assertIsSelected()
        composeRule.onNodeWithContentDescription("Тонкая линия").assertIsNotSelected()
    }

    @Test
    fun thicknessButtonsSelectTheirThickness() {
        showScreen(buildState(thickness = Thickness.THIN))

        composeRule.onNodeWithContentDescription("Тонкая линия").assertIsSelected()
        composeRule.onNodeWithContentDescription("Средняя линия").performClick()
        composeRule.onNodeWithContentDescription("Толстая линия").performClick()
        composeRule.onNodeWithContentDescription("Тонкая линия").performClick()

        assertEquals(
            listOf<EditorAction>(
                EditorAction.SelectThickness(Thickness.MEDIUM),
                EditorAction.SelectThickness(Thickness.THICK),
                EditorAction.SelectThickness(Thickness.THIN),
            ),
            actions,
        )
    }

    @Test
    fun thicknessGroupHasNoSideLines() {
        showScreen(buildState())

        val bitmap = composeRule.onNodeWithContentDescription("Толщина пера").captureToImage().asAndroidBitmap()
        val middle = bitmap.height / 2
        // Разделять больше нечего: ни черт по краям группы, ни поля между чертой и кнопкой. Крайние кнопки без рамки.
        assertTrue(!bitmap.isInkAt(0, middle))
        assertTrue(!bitmap.isInkAt(bitmap.width - 1, middle))
        // Группа — ровно три кнопки по 80 с зазором 4, без полей по краям.
        assertEquals(248, bitmap.width)
    }

    @Test
    fun undoIsGreyWhenUnavailable() {
        showScreen(buildState(canUndo = false))

        composeRule.onNodeWithContentDescription("Отменить штрих").assertIsNotEnabled().performClick()
        assertTrue(actions.isEmpty())

        showScreen(buildState(canUndo = true))

        composeRule.onNodeWithContentDescription("Отменить штрих").assertIsEnabled().performClick()
        assertEquals(listOf<EditorAction>(EditorAction.Undo), actions)
    }

    // --- окна ----------------------------------------------------------------------------------

    @Test
    fun discardDraftDialogUsesMockupTexts() {
        showScreen(buildState(isNewCard = true, dialog = EditorDialog.DISCARD_DRAFT))

        composeRule.onNodeWithText("Удалить черновик?").assertExists()
        composeRule.onNodeWithText("Рисунки на обеих сторонах не сохранятся.").assertExists()
        assertEquals(3, measureBorder(composeRule.onNodeWithText("Продолжить")))
        composeRule.onNodeWithText("Продолжить").performClick()
        composeRule.onNodeWithText("Удалить черновик").performClick()

        assertEquals(listOf<EditorAction>(EditorAction.DismissDialog, EditorAction.ConfirmDialog), actions)
    }

    @Test
    fun deleteCardDialogUsesMockupTexts() {
        showScreen(buildState(isNewCard = false, dialog = EditorDialog.DELETE_CARD))

        composeRule.onNodeWithText("Удалить карточку?").assertExists()
        composeRule.onNodeWithText("Вопрос и ответ будут удалены безвозвратно.").assertExists()
        composeRule.onNodeWithText("Продолжить").performClick()
        // В правке «Удалить» есть и в шапке под окном, у неё есть описание; кнопка окна без описания.
        composeRule.onNode(hasText("Удалить") and !hasContentDescription("Удалить карточку")).performClick()

        assertEquals(listOf<EditorAction>(EditorAction.DismissDialog, EditorAction.ConfirmDialog), actions)
    }

    @Test
    fun saveOnBackDialogHasTitleOnlyAndTwoButtons() {
        showScreen(buildState(dialog = EditorDialog.SAVE_ON_BACK))

        composeRule.onNodeWithText("Сохранить и выйти?").assertExists()
        // Текста у окна нет: между заголовком и кнопками ничего, кроме подписей двух кнопок.
        composeRule.onNodeWithText("Рисунки на обеих сторонах не сохранятся.").assertDoesNotExist()
        composeRule.onNodeWithText("Продолжить").performClick()
        composeRule.onNodeWithText("Сохранить и выйти").performClick()

        assertEquals(listOf<EditorAction>(EditorAction.DismissDialog, EditorAction.ConfirmDialog), actions)
    }

    @Test
    fun dialogBlocksScreenUnderIt() {
        showScreen(buildState(dialog = EditorDialog.SAVE_ON_BACK))

        // Кнопки под окном касаний не получают: «Сохранить» в шапке, перо и отмена штриха в панели инструментов.
        touchAt(900f, 64f)
        touchAt(100f, 1323f - 32f - 56f)
        touchAt(900f, 1323f - 32f - 56f)

        assertTrue(actions.isEmpty())
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private var screenState by mutableStateOf<EditorScreenState?>(null)
    private var isHosted = false

    private fun showScreen(state: EditorScreenState) {
        screenState = state
        if (isHosted) {
            composeRule.waitForIdle()
            return
        }
        isHosted = true
        composeRule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(Color.White).testTag("frame")) {
                screenState?.let { shown -> EditorScreen(shown, { actions += it }) { modifier -> Box(modifier.testTag("slot")) } }
            }
        }
    }

    private fun touchAt(x: Float, y: Float) {
        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(x, y)) }
    }

    // Ширина чёрной рамки у левого края кнопки на середине высоты: 3 у выбранной и главной, 2 у обычной, 0 у прозрачной.
    private fun measureBorder(node: SemanticsNodeInteraction): Int {
        val bitmap = node.captureToImage().asAndroidBitmap()
        return (0 until 8).takeWhile { bitmap.isInkAt(it, bitmap.height / 2) }.size
    }

    private fun buildState(
        isNewCard: Boolean = true,
        side: CardSideShown = CardSideShown.FRONT,
        thickness: Thickness = Thickness.MEDIUM,
        canUndo: Boolean = true,
        dialog: EditorDialog? = null,
    ) = EditorScreenState(isNewCard, side, thickness, canUndo, dialog)
}
