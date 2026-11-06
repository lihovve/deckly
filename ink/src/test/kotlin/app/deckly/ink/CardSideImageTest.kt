package app.deckly.ink

import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
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
@Config(sdk = [35], qualifiers = "w1000dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardSideImageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun imageHasDescriptionForAccessibility() {
        composeRule.setContent {
            CardSideImage(CardSide(), Modifier.size(SHEET_DP.dp), "Drawing unavailable")
        }

        composeRule.onNodeWithContentDescription("Drawing unavailable")
            .assertWidthIsEqualTo(SHEET_DP.dp)
            .assertHeightIsEqualTo(SHEET_DP.dp)
    }

    @Test
    fun imageWithoutDescriptionKeepsTheOneOfItsCaller() {
        // Дизайн ставит описание листа сам через модификатор слота и передаёт null, чтобы описание не затёрлось.
        composeRule.setContent {
            CardSideImage(CardSide(), Modifier.size(SHEET_DP.dp).semantics { contentDescription = "Question" }, description = null)
        }

        composeRule.onNodeWithContentDescription("Question").assertExists()
        val descriptions = composeRule.onNodeWithContentDescription("Question").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Question"), descriptions)
    }

    @Test
    fun imageWithoutDescriptionAddsNoneOfItsOwn() {
        composeRule.setContent {
            CardSideImage(CardSide(), Modifier.size(SHEET_DP.dp).testTag("sheet"), description = null)
        }

        val config = composeRule.onNodeWithTag("sheet").fetchSemanticsNode().config
        assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun imageScalesStrokesByWidthOfItsSheet() {
        // Лист 500 × 800 px при плотности 1: масштаб по ширине 0,5 (по высоте был бы 0,8). Точка в
        // (500, 500) единиц карточки — (250, 250) px, толщина пера 12 единиц = 6 px, нажим 1: диаметр 9 px.
        val stroke = Stroke(12f, listOf(StrokePoint(500f, 500f, 1f)))
        composeRule.setContent {
            CardSideImage(CardSide(listOf(stroke)), Modifier.size(SHEET_DP.dp, SHEET_HEIGHT_DP.dp), "Рисунок")
        }

        val image = composeRule.onRoot().captureToImage().asAndroidBitmap()

        assertTrue(isInk(image.getPixel(250, 250)))
        assertTrue(isInk(image.getPixel(253, 250)))
        assertFalse(isInk(image.getPixel(256, 250)))
    }

    @Test
    fun imageDrawsShapesNextToStrokesWithTheScaleOfItsSheet() {
        // Лист 500 px: масштаб 0,5. Левый край контура прямоугольника x = 128 единиц, то есть 64 px, толщина 6 единиц = 3 px.
        val rectangle = Shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, 0f, 6f)
        val stroke = Stroke(12f, listOf(StrokePoint(800f, 800f, 1f)))
        composeRule.setContent {
            CardSideImage(CardSide(listOf(stroke), listOf(rectangle)), Modifier.size(SHEET_DP.dp, SHEET_HEIGHT_DP.dp), "Рисунок")
        }

        val image = composeRule.onRoot().captureToImage().asAndroidBitmap()

        assertTrue(isInk(image.getPixel(64, 175)))
        assertFalse(isInk(image.getPixel(68, 175)))
        assertTrue(isInk(image.getPixel(400, 400)))
    }

    private fun isInk(argb: Int): Boolean = Color.red(argb) < 128

    private companion object {
        const val SHEET_DP = 500
        const val SHEET_HEIGHT_DP = 800
    }
}
