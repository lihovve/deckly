package app.deckly.design.e

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.deckly.design.e.MockupSources.listIconElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class IconsTest {
    @get:Rule
    val composeRule = createComposeRule()

    // --- состав и данные -----------------------------------------------------------------------

    @Test
    fun everyIconOfMockupHasAnImageVectorAndNothingExtra() {
        assertEquals(listIconElements().keys, IconId.entries.map { it.key }.toSet())
        assertEquals(17, IconId.entries.size)
    }

    @Test
    fun shapesAreTakenFromMockupElementsOneToOne() {
        for (id in IconId.entries) {
            val elements = listIconElements().getValue(id.key)
            assertEquals(id.key, elements.size, id.shapes.size)
            elements.zip(id.shapes).forEach { (element, shape) ->
                if (element.startsWith("<path")) {
                    assertEquals(id.key, Regex("d=\"([^\"]*)\"").find(element)!!.groupValues[1], shape.pathData)
                }
                assertEquals(id.key, element.contains("fill=\"currentColor\""), shape.isFilled)
            }
        }
    }

    @Test
    fun circlesAndRectangleKeepTheirGeometry() {
        // more: три круга r=1 в (5,12), (12,12), (19,12); cards: прямоугольник 6,6, 14 × 16 со скруглением 2.
        val more = IconId.MORE.shapes.map { it.pathData }
        assertEquals(3, more.size)
        for ((shape, centerX) in more.zip(listOf(5, 12, 19))) {
            assertTrue(shape, shape.startsWith("M${centerX - 1} 12"))
        }
        assertTrue(IconId.CARDS.shapes.first().pathData.startsWith("M8 6"))
    }

    @Test
    fun iconsUseTwentyFourGridAndRoundLineOfMockupWidth() {
        for (id in IconId.entries) {
            val icon = DecklyIcons.get(id)
            assertEquals(id.key, 24f, icon.viewportWidth, 0f)
            assertEquals(id.key, 24f, icon.viewportHeight, 0f)
            val paths = icon.root.filterIsInstance<VectorPath>()
            assertEquals(id.key, id.shapes.size, paths.size)
            for ((path, shape) in paths.zip(id.shapes)) {
                if (shape.isFilled) {
                    assertNotNull(id.key, path.fill)
                    assertNull(id.key, path.stroke)
                } else {
                    assertNull(id.key, path.fill)
                    assertNotNull(id.key, path.stroke)
                    assertEquals(id.key, Tokens.IconStroke, path.strokeLineWidth, 0f)
                    assertEquals(id.key, StrokeCap.Round, path.strokeLineCap)
                    assertEquals(id.key, StrokeJoin.Round, path.strokeLineJoin)
                }
            }
        }
    }

    @Test
    fun largeEmptyStateIconsGetThinnerLine() {
        val thin = DecklyIcons.get(IconId.CARDS, Tokens.IconEmptyStroke)

        assertEquals(1.2f, thin.root.filterIsInstance<VectorPath>().first().strokeLineWidth, 0f)
        assertEquals(1.8f, DecklyIcons.Cards.root.filterIsInstance<VectorPath>().first().strokeLineWidth, 0f)
    }

    @Test
    fun namedIconsAreTheSameAsLookedUpOnes() {
        assertEquals(DecklyIcons.get(IconId.PLUS), DecklyIcons.Plus)
        assertEquals(DecklyIcons.get(IconId.ACTIVE), DecklyIcons.Active)
        assertEquals(DecklyIcons.get(IconId.EXPORT), DecklyIcons.Export)
    }

    // --- как значки рисуются -------------------------------------------------------------------

    @Test
    fun everyIconDrawsInkInsideItsBox() {
        composeRule.setContent {
            Column {
                for (id in IconId.entries) {
                    Image(
                        DecklyIcons.get(id),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).testTag("icon-${id.key}"),
                        colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color.Black),
                    )
                }
            }
        }

        for (id in IconId.entries) {
            val bitmap = composeRule.onNodeWithTag("icon-${id.key}").captureToImage().asAndroidBitmap()
            assertTrue(id.key, countInk(bitmap) > 10)
        }
    }

    @Test
    fun plusIsACrossAtCenter() {
        val bitmap = drawIcon(DecklyIcons.Plus)

        assertTrue(bitmap.isInk(48, 48))
        assertTrue(bitmap.isInk(48, 20))
        assertTrue(bitmap.isInk(20, 48))
        assertFalse(bitmap.isInk(20, 20))
    }

    @Test
    fun activeMarkIsFilledTriangle() {
        val bitmap = drawIcon(DecklyIcons.Active)

        // Внутри треугольника (12, 12) закрашено: у контурного значка там было бы пусто.
        assertTrue(bitmap.isInk(48, 48))
        assertTrue(bitmap.isInk(44, 40))
        assertFalse(bitmap.isInk(88, 48))
    }

    @Test
    fun moreIsThreeDotsInARow() {
        val bitmap = drawIcon(DecklyIcons.More)

        assertTrue(bitmap.isInk(20, 48))
        assertTrue(bitmap.isInk(48, 48))
        assertTrue(bitmap.isInk(76, 48))
        assertFalse(bitmap.isInk(34, 48))
        assertFalse(bitmap.isInk(62, 48))
    }

    @Test
    fun cardsAreTwoOutlinedCards() {
        val bitmap = drawIcon(DecklyIcons.Cards)

        // Верхний край передней карточки и задней; середина передней пуста: карточка не залита.
        assertTrue(bitmap.isInk(52, 24))
        assertTrue(bitmap.isInk(40, 12))
        assertFalse(bitmap.isInk(52, 60))
    }

    // --- вспомогательное -----------------------------------------------------------------------

    /** Значок 24 × 24 на сетке 96 × 96 пикселей: одна единица значка — 4 пикселя. */
    private fun drawIcon(icon: ImageVector): Bitmap {
        composeRule.setContent {
            Image(icon, contentDescription = null, modifier = Modifier.size(96.dp).testTag("icon"), colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color.Black))
        }
        return composeRule.onNodeWithTag("icon").captureToImage().asAndroidBitmap()
    }

    private fun countInk(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (bitmap.isInk(x, y)) count++
        return count
    }

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = Color.red(getPixel(x, y)) < 128
}
