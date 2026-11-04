package app.deckly.ink

import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardSideImageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun imageHasDescriptionForAccessibility() {
        composeRule.setContent {
            CardSideImage(emptyList(), Modifier.size(SHEET_DP.dp), "Рисунок не открылся")
        }

        composeRule.onNodeWithContentDescription("Рисунок не открылся")
            .assertWidthIsEqualTo(SHEET_DP.dp)
            .assertHeightIsEqualTo(SHEET_DP.dp)
    }

    @Test
    fun imageScalesStrokesByWidthOfItsSheet() {
        // Лист 500 × 800 px при плотности 1: масштаб по ширине 0,5 (по высоте был бы 0,8). Точка в
        // (500, 500) единиц карточки — (250, 250) px, толщина пера 12 единиц = 6 px, нажим 1: диаметр 9 px.
        val stroke = Stroke(12f, listOf(StrokePoint(500f, 500f, 1f)))
        composeRule.setContent {
            CardSideImage(listOf(stroke), Modifier.size(SHEET_DP.dp, SHEET_HEIGHT_DP.dp), "Рисунок")
        }

        val image = composeRule.onRoot().captureToImage().asAndroidBitmap()

        assertTrue(isInk(image.getPixel(250, 250)))
        assertTrue(isInk(image.getPixel(253, 250)))
        assertFalse(isInk(image.getPixel(256, 250)))
    }

    private fun isInk(argb: Int): Boolean = Color.red(argb) < 128

    private companion object {
        const val SHEET_DP = 500
        const val SHEET_HEIGHT_DP = 800
    }
}
