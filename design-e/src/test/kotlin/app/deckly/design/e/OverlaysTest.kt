package app.deckly.design.e

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.down
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
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
class OverlaysTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backgroundClicks = 0
    private val answers = mutableListOf<String>()

    // --- окно ----------------------------------------------------------------------------------

    @Test
    fun dialogBackgroundIgnoresTouches() {
        showFrame {
            Box(Modifier.fillMaxSize().clickable { backgroundClicks++ })
            Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions())
        }

        // Касание фона за окном не доходит до экрана под ним: углы, середина левого края и место рядом с окном.
        for (position in listOf(Offset(20f, 20f), Offset(971f, 20f), Offset(20f, 660f), Offset(496f, 1300f), Offset(100f, 400f))) {
            composeRule.onNodeWithTag("frame").performTouchInput { click(position) }
        }
        assertEquals(0, backgroundClicks)
    }

    @Test
    fun dialogActionsWorkAndTheyAreReported() {
        showFrame {
            Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions())
        }

        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Keep").performClick()

        assertEquals(listOf("Delete", "Keep"), answers)
    }

    @Test
    fun dialogShowsTitleAndTextAndIsAnnouncedAsPane() {
        showFrame { Dialog("All done for today", null, listOf(DialogAction("Open New", isPrimary = true, onClick = {}))) }

        composeRule.onNodeWithText("All done for today").assertExists()
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "All done for today")).assertExists()
    }

    @Test
    fun dialogIsCenteredAndAsWideAsInMockup() {
        showFrame { Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions()) }

        val dialog = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Delete card?"))
        dialog.assertWidthIsEqualTo(736.dp).assertLeftPositionInRootIsEqualTo(128.dp)
        val bounds = dialog.getUnclippedBoundsInRoot()
        // По вертикали по центру кадра с точностью до пикселя.
        assertEquals(1323f / 2, (bounds.top.value + bounds.bottom.value) / 2, 1f)
    }

    @Test
    fun dialogDoesNotDimTheScreenAndKeepsWhiteRing() {
        showFrame(background = Color.Black) {
            Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions())
        }

        val dialog = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Delete card?"))
        val bounds = dialog.getUnclippedBoundsInRoot()
        val frame = capture("frame")
        val middle = ((bounds.top.value + bounds.bottom.value) / 2).toInt()
        val left = bounds.left.value.toInt()
        // Чёрный фон остаётся чёрным: затемнения нет. Вокруг окна белое кольцо в 12 пикселей, затем снова фон.
        assertTrue(frame.isInk(5, 5))
        assertFalse(frame.isInk(left - 6, middle))
        assertTrue(frame.isInk(left - 14, middle))
        // Рамка окна в 3 пикселя.
        assertTrue(frame.isInk(left, middle) || frame.isInk(left + 1, middle))
    }

    @Test
    fun dialogDoesNotDimWhiteScreen() {
        showFrame { Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions()) }

        val frame = capture("frame")
        assertEquals(AndroidColor.WHITE, frame.getPixel(5, 5))
        assertEquals(AndroidColor.WHITE, frame.getPixel(900, 1250))
    }

    @Test
    fun dialogBorderIsExactlyThreePixels() {
        showFrame(background = Color.Black) {
            Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions())
        }

        val bounds = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Delete card?")).getUnclippedBoundsInRoot()
        val frame = capture("frame")
        val middle = ((bounds.top.value + bounds.bottom.value) / 2).toInt()
        val left = bounds.left.value.toInt()
        val centerX = ((bounds.left.value + bounds.right.value) / 2).toInt()
        val top = bounds.top.value.toInt()
        // Чёрный фон, белое кольцо, чёрная рамка, белое поле окна: от края окна ровно три чёрных пикселя.
        assertEquals(3, (0 until 8).takeWhile { frame.isInk(left + it, middle) }.size)
        assertEquals(3, (0 until 8).takeWhile { frame.isInk(centerX, top + it) }.size)
    }

    @Test
    fun menuAndScrimDoNotDrawIndicationWhenPressed() {
        showFrame {
            CompositionLocalProvider(LocalIndication provides IndicationProbe) {
                Popup("Card actions", buildPopupItems(), onDismiss = {})
            }
        }

        IndicationProbe.reset()
        composeRule.onNodeWithText("Return to New").performTouchInput { down(center) }
        composeRule.waitForIdle()
        val pressed = composeRule.onNodeWithText("Return to New").captureToImage().asAndroidBitmap()
        composeRule.onNodeWithText("Return to New").performTouchInput { up() }
        composeRule.onNodeWithTag("frame").performTouchInput { down(Offset(100f, 800f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("frame").performTouchInput { up() }

        val hasRed = (0 until pressed.height).any { y -> (0 until pressed.width).any { x -> AndroidColor.red(pressed.getPixel(x, y)) > 200 && AndroidColor.green(pressed.getPixel(x, y)) < 80 } }
        assertFalse(hasRed)
        assertEquals(0, IndicationProbe.createdCount)
    }

    @Test
    fun primaryActionHasThickerBorderThanOrdinaryOne() {
        showFrame { Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions()) }

        fun measureBorderWidth(label: String): Int {
            val bitmap = composeRule.onNodeWithText(label).captureToImage().asAndroidBitmap()
            return (0 until 8).takeWhile { bitmap.isInk(it, bitmap.height / 2) }.size
        }
        assertEquals(3, measureBorderWidth("Keep"))
        assertEquals(2, measureBorderWidth("Delete"))
    }

    @Test
    fun singleActionTakesWholeWidthAndTwoShareIt() {
        showFrame { Dialog("All done for today", null, listOf(DialogAction("Open New", true) {})) }
        // Внутри окна 736 - 2 × 3 (рамка) - 2 × 40 (поля) = 650.
        composeRule.onNodeWithText("Open New").assertWidthIsEqualTo(650.dp)
    }

    @Test
    fun dialogActionMayCarryAnIcon() {
        showFrame {
            Dialog("All done for today", null, listOf(DialogAction("Open New", isPrimary = true, onClick = {}, icon = DecklyIcons.Right)))
        }

        // Кнопка на всю ширину; стрелка 26 dp стоит левее подписи, внутри рамки.
        val label = composeRule.onNodeWithText("Open New", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val buttonNode = composeRule.onNodeWithText("Open New")
        val button = buttonNode.getUnclippedBoundsInRoot()
        val bitmap = buttonNode.captureToImage().asAndroidBitmap()
        val textLeft = (label.left.value - button.left.value).toInt()
        assertIconShape("кнопка окна", bitmap, 8 until textLeft - 4, 8 until bitmap.height - 8, IconId.RIGHT, 26f, Tokens.IconStroke)
    }

    @Test
    fun twoActionsShareTheWidthWithGap() {
        showFrame { Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions()) }

        // (650 - 16) / 2 = 317; высота нижней строки окна 88.
        composeRule.onNodeWithText("Keep").assertWidthIsEqualTo(317.dp).assertHeightIsEqualTo(88.dp)
        composeRule.onNodeWithText("Delete").assertWidthIsEqualTo(317.dp).assertLeftPositionInRootIsEqualTo(128.dp + 3.dp + 40.dp + 317.dp + 16.dp)
    }

    @Test
    fun dialogPartsAreSpacedLikeInMockup() {
        showFrame { Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildTwoActions()) }

        val dialog = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Delete card?")).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("Delete card?").getUnclippedBoundsInRoot()
        val text = composeRule.onNodeWithText("Both sides will be deleted. You can't undo this.").getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText("Keep").getUnclippedBoundsInRoot()
        // Рамка 3 и поля 40 сверху и снизу, между заголовком и текстом 24, до кнопок ещё 12 сверх промежутка.
        assertEquals(3f + 40f, title.top.value - dialog.top.value, 1f)
        assertEquals(24f, text.top.value - title.bottom.value, 1f)
        assertEquals(24f + 12f, button.top.value - text.bottom.value, 1f)
        assertEquals(40f + 3f, dialog.bottom.value - button.bottom.value, 1f)
        assertEquals(3f + 40f, button.left.value - dialog.left.value, 0.5f)
    }

    // --- меню ----------------------------------------------------------------------------------

    @Test
    fun popupSitsAtTopRightAndIsAsWideAsInMockup() {
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = {}) }

        composeRule.onNodeWithContentDescription("Card actions")
            .assertWidthIsEqualTo(432.dp)
            .assertLeftPositionInRootIsEqualTo((992 - 32 - 432).dp)
            .assertTopPositionInRootIsEqualTo(108.dp)
    }

    @Test
    fun popupCanBeLowerForDeckMenus() {
        showFrame { Popup("English A2", buildPopupItems(), onDismiss = {}, top = 206.dp) }

        composeRule.onNodeWithContentDescription("English A2").assertTopPositionInRootIsEqualTo(206.dp)
    }

    @Test
    fun popupItemsAreTallEnoughAndReportClicks() {
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = {}) }

        composeRule.onNodeWithText("Return to New").assertHeightIsAtLeast(80.dp)
        composeRule.onNodeWithText("Delete card").assertHeightIsAtLeast(80.dp)
        composeRule.onNodeWithText("Return to New").performClick()
        composeRule.onNodeWithText("Delete card").performClick()

        assertEquals(listOf("Return to New", "Delete card"), answers)
    }

    @Test
    fun touchOutsidePopupDismissesIt() {
        var dismissed = 0
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = { dismissed++ }) }

        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(100f, 800f)) }
        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(20f, 20f)) }

        assertEquals(2, dismissed)
    }

    @Test
    fun touchInsidePopupOutsideItemsDoesNotDismissIt() {
        var dismissed = 0
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = { dismissed++ }) }

        composeRule.onNodeWithText("Card actions").performClick()

        assertEquals(0, dismissed)
        assertTrue(answers.isEmpty())
    }

    @Test
    fun popupDoesNotDimTheScreen() {
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = {}) }

        val frame = capture("frame")
        assertEquals(AndroidColor.WHITE, frame.getPixel(10, 10))
        assertEquals(AndroidColor.WHITE, frame.getPixel(400, 800))
    }

    @Test
    fun popupTitleIsUnderlinedAndItemsFollow() {
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = {}) }

        val popup = composeRule.onNodeWithContentDescription("Card actions").getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("Card actions").getUnclippedBoundsInRoot()
        val firstItem = composeRule.onNodeWithText("Return to New").getUnclippedBoundsInRoot()
        val image = composeRule.onNodeWithContentDescription("Card actions").captureToImage().asAndroidBitmap()
        // Узел текста не включает отступ под ним (16): линия лежит на нижнем краю отступа, пункты идут сразу за ней.
        val titleBottom = title.bottom.value + 16f
        val lineY = (titleBottom - popup.top.value).toInt() - 1
        // Линия под заголовком в 1 пиксель; слева от текста рамка 2 и поле 24.
        assertTrue(image.isInk(100, lineY))
        assertFalse(image.isInk(100, lineY - 2))
        assertFalse(image.isInk(100, lineY + 2))
        assertEquals(titleBottom, firstItem.top.value, 1f)
        assertEquals(2f + 24f, title.left.value - popup.left.value, 0.5f)
    }

    @Test
    fun dangerousItemIsSeparatedByLine() {
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = {}) }

        val danger = composeRule.onNodeWithText("Delete card").captureToImage().asAndroidBitmap()
        val ordinary = composeRule.onNodeWithText("Return to New").captureToImage().asAndroidBitmap()
        // У опасного пункта сверху линия в 1 пиксель на всю ширину, у обычного её нет.
        assertTrue(danger.isInk(200, 0))
        assertFalse(ordinary.isInk(200, 0))
        assertFalse(danger.isInk(200, 3))
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun showFrame(background: Color = Color.White, content: @Composable BoxScope.() -> Unit) {
        composeRule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(background).testTag("frame"), content = content)
        }
    }

    private fun capture(tag: String): Bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

    private fun buildTwoActions() = listOf(
        DialogAction("Keep", isPrimary = true) { answers += "Keep" },
        DialogAction("Delete", isPrimary = false) { answers += "Delete" },
    )

    private fun buildPopupItems() = listOf(
        PopupItem("Return to New", DecklyIcons.Undo, isDanger = false) { answers += "Return to New" },
        PopupItem("Delete card", DecklyIcons.Trash, isDanger = true) { answers += "Delete card" },
    )

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = AndroidColor.red(getPixel(x, y)) < 128
}
