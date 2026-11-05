package app.deckly.design.e

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Экран загрузки из макета `startup-loading`: «Deckly» по центру белого листа, без кнопок и индикаторов. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoadingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun brandIsCenteredOnTheFrame() {
        showLoading()

        val brand = composeRule.onNodeWithText("Deckly").getUnclippedBoundsInRoot()
        assertEquals(Tokens.FrameWidth.value / 2f, (brand.left.value + brand.right.value) / 2f, 1f)
        assertEquals(Tokens.FrameHeight.value / 2f, (brand.top.value + brand.bottom.value) / 2f, 1f)
    }

    @Test
    fun brandIsSetLikeLoadingBrandHeading() {
        showLoading()

        val style = readTextStyle(composeRule.onNodeWithText("Deckly", useUnmergedTree = true))
        assertEquals(64f, style.fontSize.value, 0.01f)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(-2f, style.letterSpacing.value, 0.01f)
        assertEquals(70.4f, style.lineHeight.value, 0.01f)
    }

    @Test
    fun loadingHasNoButtonsAndNoTextBesideBrand() {
        showLoading()

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun backgroundIsWhite() {
        showLoading()

        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        assertFalse(bitmap.isInkAt(2, 2))
        assertFalse(bitmap.isInkAt(bitmap.width - 3, bitmap.height - 3))
    }

    private fun showLoading() {
        composeRule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).testTag("frame")) { LoadingScreen() }
        }
    }
}
