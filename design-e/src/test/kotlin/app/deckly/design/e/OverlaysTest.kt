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
            Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions())
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
            Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions())
        }

        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.onNodeWithText("Оставить").performClick()

        assertEquals(listOf("Удалить", "Оставить"), answers)
    }

    @Test
    fun dialogShowsTitleAndTextAndIsAnnouncedAsPane() {
        showFrame { Dialog("На сегодня всё", null, listOf(DialogAction("Открыть новые", isPrimary = true, onClick = {}))) }

        composeRule.onNodeWithText("На сегодня всё").assertExists()
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "На сегодня всё")).assertExists()
    }

    @Test
    fun dialogIsCenteredAndAsWideAsInMockup() {
        showFrame { Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions()) }

        val dialog = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Удалить карточку?"))
        dialog.assertWidthIsEqualTo(736.dp).assertLeftPositionInRootIsEqualTo(128.dp)
        val bounds = dialog.getUnclippedBoundsInRoot()
        // По вертикали по центру кадра с точностью до пикселя.
        assertEquals(1323f / 2, (bounds.top.value + bounds.bottom.value) / 2, 1f)
    }

    @Test
    fun dialogDoesNotDimTheScreenAndKeepsWhiteRing() {
        showFrame(background = Color.Black) {
            Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions())
        }

        val dialog = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Удалить карточку?"))
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
        showFrame { Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions()) }

        val frame = capture("frame")
        assertEquals(AndroidColor.WHITE, frame.getPixel(5, 5))
        assertEquals(AndroidColor.WHITE, frame.getPixel(900, 1250))
    }

    @Test
    fun dialogBorderIsExactlyThreePixels() {
        showFrame(background = Color.Black) {
            Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions())
        }

        val bounds = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Удалить карточку?")).getUnclippedBoundsInRoot()
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
                Popup("Действия с карточкой", buildPopupItems(), onDismiss = {})
            }
        }

        IndicationProbe.reset()
        composeRule.onNodeWithText("Вернуть в новые").performTouchInput { down(center) }
        composeRule.waitForIdle()
        val pressed = composeRule.onNodeWithText("Вернуть в новые").captureToImage().asAndroidBitmap()
        composeRule.onNodeWithText("Вернуть в новые").performTouchInput { up() }
        composeRule.onNodeWithTag("frame").performTouchInput { down(Offset(100f, 800f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("frame").performTouchInput { up() }

        val hasRed = (0 until pressed.height).any { y -> (0 until pressed.width).any { x -> AndroidColor.red(pressed.getPixel(x, y)) > 200 && AndroidColor.green(pressed.getPixel(x, y)) < 80 } }
        assertFalse(hasRed)
        assertEquals(0, IndicationProbe.createdCount)
    }

    @Test
    fun primaryActionHasThickerBorderThanOrdinaryOne() {
        showFrame { Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions()) }

        fun measureBorderWidth(label: String): Int {
            val bitmap = composeRule.onNodeWithText(label).captureToImage().asAndroidBitmap()
            return (0 until 8).takeWhile { bitmap.isInk(it, bitmap.height / 2) }.size
        }
        assertEquals(3, measureBorderWidth("Оставить"))
        assertEquals(2, measureBorderWidth("Удалить"))
    }

    @Test
    fun singleActionTakesWholeWidthAndTwoShareIt() {
        showFrame { Dialog("На сегодня всё", null, listOf(DialogAction("Открыть новые", true) {})) }
        // Внутри окна 736 - 2 × 3 (рамка) - 2 × 40 (поля) = 650.
        composeRule.onNodeWithText("Открыть новые").assertWidthIsEqualTo(650.dp)
    }

    @Test
    fun twoActionsShareTheWidthWithGap() {
        showFrame { Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions()) }

        // (650 - 16) / 2 = 317; высота нижней строки окна 88.
        composeRule.onNodeWithText("Оставить").assertWidthIsEqualTo(317.dp).assertHeightIsEqualTo(88.dp)
        composeRule.onNodeWithText("Удалить").assertWidthIsEqualTo(317.dp).assertLeftPositionInRootIsEqualTo(128.dp + 3.dp + 40.dp + 317.dp + 16.dp)
    }

    @Test
    fun dialogPartsAreSpacedLikeInMockup() {
        showFrame { Dialog("Удалить карточку?", "Вопрос и ответ будут удалены безвозвратно.", buildTwoActions()) }

        val dialog = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Удалить карточку?")).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("Удалить карточку?").getUnclippedBoundsInRoot()
        val text = composeRule.onNodeWithText("Вопрос и ответ будут удалены безвозвратно.").getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText("Оставить").getUnclippedBoundsInRoot()
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
        showFrame { Popup("Действия с карточкой", buildPopupItems(), onDismiss = {}) }

        composeRule.onNodeWithContentDescription("Действия с карточкой")
            .assertWidthIsEqualTo(432.dp)
            .assertLeftPositionInRootIsEqualTo((992 - 32 - 432).dp)
            .assertTopPositionInRootIsEqualTo(108.dp)
    }

    @Test
    fun popupCanBeLowerForDeckMenus() {
        showFrame { Popup("Немецкий A2", buildPopupItems(), onDismiss = {}, top = 206.dp) }

        composeRule.onNodeWithContentDescription("Немецкий A2").assertTopPositionInRootIsEqualTo(206.dp)
    }

    @Test
    fun popupItemsAreTallEnoughAndReportClicks() {
        showFrame { Popup("Действия с карточкой", buildPopupItems(), onDismiss = {}) }

        composeRule.onNodeWithText("Вернуть в новые").assertHeightIsAtLeast(80.dp)
        composeRule.onNodeWithText("Удалить карточку").assertHeightIsAtLeast(80.dp)
        composeRule.onNodeWithText("Вернуть в новые").performClick()
        composeRule.onNodeWithText("Удалить карточку").performClick()

        assertEquals(listOf("Вернуть в новые", "Удалить карточку"), answers)
    }

    @Test
    fun touchOutsidePopupDismissesIt() {
        var dismissed = 0
        showFrame { Popup("Действия с карточкой", buildPopupItems(), onDismiss = { dismissed++ }) }

        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(100f, 800f)) }
        composeRule.onNodeWithTag("frame").performTouchInput { click(Offset(20f, 20f)) }

        assertEquals(2, dismissed)
    }

    @Test
    fun touchInsidePopupOutsideItemsDoesNotDismissIt() {
        var dismissed = 0
        showFrame { Popup("Действия с карточкой", buildPopupItems(), onDismiss = { dismissed++ }) }

        composeRule.onNodeWithText("Действия с карточкой").performClick()

        assertEquals(0, dismissed)
        assertTrue(answers.isEmpty())
    }

    @Test
    fun popupDoesNotDimTheScreen() {
        showFrame { Popup("Действия с карточкой", buildPopupItems(), onDismiss = {}) }

        val frame = capture("frame")
        assertEquals(AndroidColor.WHITE, frame.getPixel(10, 10))
        assertEquals(AndroidColor.WHITE, frame.getPixel(400, 800))
    }

    @Test
    fun popupTitleIsUnderlinedAndItemsFollow() {
        showFrame { Popup("Действия с карточкой", buildPopupItems(), onDismiss = {}) }

        val popup = composeRule.onNodeWithContentDescription("Действия с карточкой").getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("Действия с карточкой").getUnclippedBoundsInRoot()
        val firstItem = composeRule.onNodeWithText("Вернуть в новые").getUnclippedBoundsInRoot()
        val image = composeRule.onNodeWithContentDescription("Действия с карточкой").captureToImage().asAndroidBitmap()
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
        showFrame { Popup("Действия с карточкой", buildPopupItems(), onDismiss = {}) }

        val danger = composeRule.onNodeWithText("Удалить карточку").captureToImage().asAndroidBitmap()
        val ordinary = composeRule.onNodeWithText("Вернуть в новые").captureToImage().asAndroidBitmap()
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
        DialogAction("Оставить", isPrimary = true) { answers += "Оставить" },
        DialogAction("Удалить", isPrimary = false) { answers += "Удалить" },
    )

    private fun buildPopupItems() = listOf(
        PopupItem("Вернуть в новые", DecklyIcons.Undo, isDanger = false) { answers += "Вернуть в новые" },
        PopupItem("Удалить карточку", DecklyIcons.Trash, isDanger = true) { answers += "Удалить карточку" },
    )

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = AndroidColor.red(getPixel(x, y)) < 128
}
