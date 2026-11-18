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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckNameAction
import app.deckly.ui.contract.DeckNameState
import app.deckly.ui.contract.DeckRow
import app.deckly.ui.contract.DeleteDeckDialog
import app.deckly.ui.contract.StartupAction
import app.deckly.ui.contract.StartupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Список колод, форма новой колоды и экран запуска: по `decks`, `decks-empty`, `deck-new` и решению 7 плана 4. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OtherScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val listActions = mutableListOf<DeckListAction>()
    private val nameActions = mutableListOf<DeckNameAction>()
    private val startupActions = mutableListOf<StartupAction>()

    // --- список колод --------------------------------------------------------------------------

    @Test
    fun emptyDeckListShowsNoDecks() {
        showFrame { DeckListScreen(buildListState(emptyList()), { listActions += it }) }

        composeRule.onNodeWithText("Decks").assertExists()
        composeRule.onNodeWithText("No decks").assertExists()
        composeRule.onNodeWithContentDescription("Back to cards").assertDoesNotExist()
        composeRule.onNodeWithText("New deck").assertExists()
        // Значок «карточки» над заголовком, как в пустой секции: сверху лист, снизу нижняя строка.
        val title = composeRule.onNodeWithText("No decks", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        val iconTop = (title.top.value - 28f - 68f).toInt()
        assertIconShape("нет колод", bitmap, 464 until 528, iconTop until iconTop + 64, IconId.CARDS, 64f, Tokens.IconEmptyStroke, tolerance = 1.2f)
        // Лист на всю высоту между шапкой и нижней строкой: заголовок посередине (112…1163).
        assertEquals((112f + 1163f) / 2f, (title.top.value + title.bottom.value) / 2f - (68f + 28f) / 2f, 1.5f)
    }

    @Test
    fun backToCardsOnlyWhenThereIsCurrentDeck() {
        showFrame { DeckListScreen(buildListState(buildDecks(), canGoBack = false), { listActions += it }) }
        composeRule.onNodeWithContentDescription("Back to cards").assertDoesNotExist()
        showFrame { DeckListScreen(buildListState(buildDecks(), canGoBack = true), { listActions += it }) }

        val back = composeRule.onNodeWithContentDescription("Back to cards")
        back.assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(64.dp)
        assertEquals(32f, back.getUnclippedBoundsInRoot().left.value, 0.5f)
        assertIconInButton("к карточкам", back, IconId.LEFT)
        // Название следует за кнопкой через промежуток шапки 24.
        assertEquals(32f + 64f + 24f, composeRule.onNodeWithText("Decks", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value, 0.5f)
        back.performClick()
        assertEquals(listOf<DeckListAction>(DeckListAction.Back), listActions)
    }

    @Test
    fun headingIsBoldTitleLikeInEditor() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        val style = readTextStyle(composeRule.onNodeWithText("Decks", useUnmergedTree = true))
        assertEquals(28f, style.fontSize.value, 0.01f)
        assertEquals(-0.6f, style.letterSpacing.value, 0.01f)
        assertEquals(FontWeight.Bold, style.fontWeight)
    }

    @Test
    fun rowsShowNumbersNamesAndCaptionsLikeMockup() {
        val decks = listOf(
            DeckRow(1, "English A2", 245, activeEpoch = 1, isAllLearned = false, isCurrent = true),
            DeckRow(2, "English tenses", 86, activeEpoch = 2, isAllLearned = false, isCurrent = false),
            DeckRow(3, "Formulas", 18, activeEpoch = null, isAllLearned = true, isCurrent = false),
            DeckRow(4, "Art history", 1, activeEpoch = null, isAllLearned = false, isCurrent = false),
        )
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        for (name in decks.map { it.name }) composeRule.onNodeWithText(name).assertExists()
        composeRule.onNodeWithText("245 cards ·").assertExists()
        composeRule.onNodeWithText("86 cards ·").assertExists()
        composeRule.onNodeWithText("18 cards · learned").assertExists()
        composeRule.onNodeWithText("1 card").assertExists()
        // Эпоха — римская цифра справа от значка ▶.
        composeRule.onNode(hasText("I") and hasClickAction()).assertExists()
        composeRule.onNode(hasText("II") and hasClickAction()).assertExists()
        // Номеров строк нет совсем: колонка слева пуста, у текущей колоды в ней галочка.
        for (number in listOf("01", "02", "03", "04")) composeRule.onNodeWithText(number).assertDoesNotExist()
    }

    @Test
    fun numberColumnOfOtherDecksIsEmpty() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        // Колонка номера 44 dp слева; вторая и третья строки (277…423 и 425…571, линии 424 и 572 не считаем) без единого штриха.
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        for (rows in listOf(277 until 424, 425 until 572)) {
            assertTrue("в колонке номера чужой строки $rows чернила", (32 until 76).none { x -> rows.any { y -> bitmap.isInkAt(x, y) } })
        }
    }

    @Test
    fun captionMarksActiveEpochWithArrowIcon() {
        val decks = listOf(DeckRow(1, "English A2", 245, activeEpoch = 3, isAllLearned = false, isCurrent = false))
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        val text = composeRule.onNodeWithText("245 cards ·", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val epoch = composeRule.onNodeWithText("III", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        // ▶ 18 dp между подписью и эпохой с зазором 8 с каждой стороны.
        assertEquals(text.right.value + 8f + 18f + 8f, epoch.left.value, 1f)
        val ink = measureInkBox(bitmap, (text.right.value + 8f).toInt() until (text.right.value + 26f).toInt(), text.top.value.toInt() until text.bottom.value.toInt())
        assertNotNull("значка ▶ нет", ink)
        assertEquals(20f, readTextStyle(composeRule.onNodeWithText("245 cards ·", useUnmergedTree = true)).fontSize.value, 0.01f)
    }

    @Test
    fun currentDeckIsChecked() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        // Галочка 24 dp в колонке номера (44 dp слева); у остальных строк там номер текстом.
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        val numberColumn = 32 until 32 + 44
        assertIconShape("текущая колода", bitmap, numberColumn, 129 until 129 + 147, IconId.CHECK, 24f, Tokens.IconStroke)
        val current = readTextStyle(composeRule.onNodeWithText("English A2", useUnmergedTree = true))
        val other = readTextStyle(composeRule.onNodeWithText("English tenses", useUnmergedTree = true))
        assertEquals(FontWeight.Bold, current.fontWeight)
        assertEquals(FontWeight.Medium, other.fontWeight)
        assertEquals(30f, current.fontSize.value, 0.01f)
        assertEquals(36f, current.lineHeight.value, 0.01f)
    }

    @Test
    fun rowsKeepMockupGeometry() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        // Список начинается после шапки (96) и зазоров 16 + 16, линия 1 сверху; строка 148 с линией внизу.
        val first = composeRule.onNode(hasText("English A2") and hasClickAction()).getUnclippedBoundsInRoot()
        val second = composeRule.onNode(hasText("English tenses") and hasClickAction()).getUnclippedBoundsInRoot()
        assertEquals(32f + 44f + 24f, first.left.value, 0.5f)
        // Справа кнопка меню 64 через промежуток 24: колонка названия кончается на 960 − 64 − 24.
        assertEquals(960f - 64f - 24f, first.right.value, 0.5f)
        assertEquals(112f, first.bottom.value - first.top.value, 1f)
        assertEquals(148f, second.top.value - first.top.value, 0.5f)
        assertEquals(129f + 147f / 2f, (first.top.value + first.bottom.value) / 2f, 1f)
        // Название и подпись разделены 9 dp.
        val name = composeRule.onNodeWithText("English A2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val caption = composeRule.onNodeWithText("245 cards ·", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(9f, caption.top.value - name.bottom.value, 0.5f)
        val frame = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        // Линия над списком и под каждой строкой, во всю ширину.
        assertTrue(frame.isInkAt(500, 128))
        assertTrue(frame.isInkAt(500, 129 + 147))
        assertTrue(frame.isInkAt(500, 129 + 148 + 147))
        assertTrue(!frame.isInkAt(500, 129 + 147 - 3))
    }

    @Test
    fun checkFollowsTheCurrentDeckWhateverItsPlace() {
        val decks = listOf(
            DeckRow(11, "First", 5, null, false, isCurrent = false),
            DeckRow(22, "Second", 6, null, false, isCurrent = true),
        )
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        // Галочка во второй строке (277…424); у первой строки колонка номера пуста.
        assertIconShape("вторая строка", bitmap, 32 until 76, 277 until 277 + 147, IconId.CHECK, 24f, Tokens.IconStroke)
        assertTrue("у первой строки колонка номера пуста", (32 until 76).none { x -> (129 until 276).any { y -> bitmap.isInkAt(x, y) } })
    }

    @Test
    fun longDeckNameStaysOnOneLine() {
        val name = "A very long deck name that does not fit into one row of the list at all"
        val decks = listOf(DeckRow(1, name, 5, null, false, isCurrent = false), DeckRow(2, "Second", 6, null, false, isCurrent = false))
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        val bounds = composeRule.onNodeWithText(name, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val second = composeRule.onNode(hasText("Second") and hasClickAction()).getUnclippedBoundsInRoot()
        val first = composeRule.onNode(hasText(name) and hasClickAction()).getUnclippedBoundsInRoot()
        // Одна строка, обрезанная многоточием: высота 36, ширина не шире колонки названия, строки остаются по 148.
        assertEquals(36f, bounds.bottom.value - bounds.top.value, 3f)
        assertTrue(bounds.right.value <= 960f - 64f - 24f + 0.5f)
        assertEquals(148f, second.top.value - first.top.value, 0.5f)
    }

    @Test
    fun rowOpensItsDeck() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        composeRule.onNodeWithText("Formulas").performClick()
        composeRule.onNodeWithText("English A2").performClick()

        assertEquals(listOf<DeckListAction>(DeckListAction.OpenDeck(103), DeckListAction.OpenDeck(101)), listActions)
    }

    @Test
    fun listScrollsSoNoDeckIsLostBehindFooter() {
        val decks = (1..20).map { DeckRow(it.toLong(), "Deck $it", it, null, false, it == 1) }
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        composeRule.onNodeWithText("Deck 20").assertIsNotDisplayed()
        composeRule.onNodeWithText("Deck 20").performScrollTo().assertIsDisplayed()
        // Нижняя строка осталась на месте: список прокручивается над ней.
        assertEquals(1323f - 32f - 112f, composeRule.onNodeWithText("New deck").getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun newDeckButtonTakesTheLeftHalfOfTheFooterAndCreatesDeck() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        val button = composeRule.onNodeWithText("New deck")
        val bounds = button.getUnclippedBoundsInRoot()
        // `.foot.two`: две колонки по `1fr` с промежутком 16: (928 − 16) / 2 = 456.
        assertEquals(32f, bounds.left.value, 0.5f)
        assertEquals(456f, bounds.right.value - bounds.left.value, 0.5f)
        assertEquals(112f, bounds.bottom.value - bounds.top.value, 0.5f)
        assertEquals(1323f - 32f, bounds.bottom.value, 0.5f)
        assertEquals(3, measureBorder(button))
        assertEquals(26f, readTextStyle(composeRule.onNodeWithText("New deck", useUnmergedTree = true)).fontSize.value, 0.01f)
        composeRule.assertIconBeforeLabel("New deck", IconId.PLUS, borderMargin = 8)
        button.performClick()
        assertEquals(listOf<DeckListAction>(DeckListAction.CreateDeck), listActions)
    }

    @Test
    fun importButtonTakesTheRightHalfOfTheFooterIsQuietAndImports() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        val button = composeRule.onNodeWithText("Import from file")
        val bounds = button.getUnclippedBoundsInRoot()
        assertEquals(32f + 456f + 16f, bounds.left.value, 0.5f)
        assertEquals(960f, bounds.right.value, 0.5f)
        assertEquals(112f, bounds.bottom.value - bounds.top.value, 0.5f)
        assertEquals(1323f - 32f, bounds.bottom.value, 0.5f)
        // Тихая кнопка: без рамки, кегль 24 (`.foot .btn.quiet`), значок «файл» перед подписью.
        assertEquals(0, measureBorder(button))
        assertEquals(24f, readTextStyle(composeRule.onNodeWithText("Import from file", useUnmergedTree = true)).fontSize.value, 0.01f)
        composeRule.assertIconBeforeLabel("Import from file", IconId.FILE, borderMargin = 8)
        button.performClick()
        assertEquals(listOf<DeckListAction>(DeckListAction.ImportDeck), listActions)
    }

    @Test
    fun emptyListHasTheSameTwoFooterButtons() {
        showFrame { DeckListScreen(buildListState(emptyList()), { listActions += it }) }

        composeRule.onNodeWithText("New deck").performClick()
        composeRule.onNodeWithText("Import from file").performClick()

        assertEquals(listOf<DeckListAction>(DeckListAction.CreateDeck, DeckListAction.ImportDeck), listActions)
        assertEquals(456f, composeRule.onNodeWithText("New deck").getUnclippedBoundsInRoot().let { it.right.value - it.left.value }, 0.5f)
    }

    // --- окно ошибки импорта ----------------------------------------------------------------------

    @Test
    fun importErrorDialogHasTheMockupTextAndOneMainButton() {
        showFrame { DeckListScreen(buildListState(buildDecks(), isImportErrorShown = true), { listActions += it }) }

        composeRule.onNodeWithText("Couldn't open file").assertExists()
        composeRule.onNodeWithText("This file is damaged or uses a format we can't open.").assertExists()
        assertEquals(3, measureBorder(composeRule.onNodeWithText("Close")))
        composeRule.onNodeWithText("Close").performClick()
        assertEquals(listOf<DeckListAction>(DeckListAction.DismissImportError), listActions)
    }

    @Test
    fun noImportErrorUntilTheStateHasOne() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        composeRule.onNodeWithText("Couldn't open file").assertDoesNotExist()
    }

    @Test
    fun rowsBehindTheImportErrorDoNotGetTouches() {
        showFrame { DeckListScreen(buildListState(buildDecks(), isImportErrorShown = true), { listActions += it }) }

        composeRule.onNodeWithText("Formulas").performClick()
        composeRule.onNodeWithText("Import from file").performClick()

        assertTrue(listActions.isEmpty())
    }

    // --- меню и окно удаления у строки -----------------------------------------------------------

    @Test
    fun everyRowHasAMenuButtonAtTheRightEdgeCenteredInTheRow() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        // Подпись для диктора с названием колоды, 64 × 64, у правого края (960), по центру строки 148 (129…276 и ниже).
        for ((index, deck) in buildDecks().withIndex()) {
            val button = composeRule.onNodeWithContentDescription("Deck menu: ${deck.name}")
            button.assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(64.dp)
            val bounds = button.getUnclippedBoundsInRoot()
            assertEquals(960f, bounds.right.value, 0.5f)
            assertEquals(129f + 148f * index + 147f / 2f, (bounds.top.value + bounds.bottom.value) / 2f, 1f)
            assertIconInButton("меню ${deck.name}", button, IconId.MORE)
        }
    }

    @Test
    fun menuIsClosedUntilTheButtonIsPressed() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        composeRule.onNodeWithContentDescription("English tenses").assertDoesNotExist()
        composeRule.onNodeWithText("Rename").assertDoesNotExist()
        composeRule.onNodeWithText("Delete deck").assertDoesNotExist()
    }

    @Test
    fun buttonOpensTheMenuWithTheNameOfTheDeckAndRenameExportDeleteInThatOrder() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        composeRule.onNodeWithContentDescription("Deck menu: English tenses").performClick()

        composeRule.onNodeWithContentDescription("English tenses").assertExists()
        val tops = listOf("Rename", "Export to file", "Delete deck").map { composeRule.onNodeWithText(it, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value }
        assertEquals(tops.sorted(), tops)
        // Пункты по 80 друг под другом, как в макете.
        assertEquals(80f, tops[1] - tops[0], 2f)
        assertEquals(80f, tops[2] - tops[1], 2f)
        assertTrue("кнопка только открывает меню", listActions.isEmpty())
    }

    @Test
    fun exportItemSendsTheDeckAndClosesTheMenu() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }
        composeRule.onNodeWithContentDescription("Deck menu: English tenses").performClick()

        composeRule.onNodeWithText("Export to file").performClick()

        assertEquals(listOf<DeckListAction>(DeckListAction.ExportDeck(102)), listActions)
        composeRule.onNodeWithContentDescription("English tenses").assertDoesNotExist()
    }

    @Test
    fun menuTopIsTheTopOfItsRowPlusSeventySevenAndItStandsAtTheRightMargin() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        // Макет: `top` = 206 + (номер строки − 1) · 148; строка 1 начинается на 129. Справа поле 32, ширина 432.
        for ((index, deck) in buildDecks().withIndex()) {
            composeRule.onNodeWithContentDescription("Deck menu: ${deck.name}").performClick()

            val menu = composeRule.onNodeWithContentDescription(deck.name).getUnclippedBoundsInRoot()
            assertEquals("строка ${index + 1}", 206f + 148f * index, menu.top.value, 1f)
            assertEquals(992f - 32f, menu.right.value, 0.5f)
            assertEquals(432f, menu.right.value - menu.left.value, 0.5f)
            composeRule.onNodeWithContentDescription("Close menu").performClick()
        }
    }

    @Test
    fun menuFollowsTheScrolledRow() {
        val decks = (1..20).map { DeckRow(it.toLong(), "Deck $it", it, null, false, it == 1) }
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }
        composeRule.onNodeWithText("Deck 5").performScrollTo()
        val button = composeRule.onNodeWithContentDescription("Deck menu: Deck 5").getUnclippedBoundsInRoot()

        composeRule.onNodeWithContentDescription("Deck menu: Deck 5").performClick()

        // Верх строки на экране = середина кнопки − 74 (строка 148, кнопка по центру); меню на 77 ниже.
        val rowTop = (button.top.value + button.bottom.value) / 2f - 74f
        assertEquals(rowTop + 77f, composeRule.onNodeWithContentDescription("Deck 5").getUnclippedBoundsInRoot().top.value, 1f)
    }

    @Test
    fun menuThatDoesNotFitMovesUpExactlyToTheBottomMargin() {
        val decks = (1..20).map { DeckRow(it.toLong(), "Deck $it", it, null, false, it == 1) }
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }
        composeRule.onNodeWithText("Deck 20").performScrollTo()
        val button = composeRule.onNodeWithContentDescription("Deck menu: Deck 20").getUnclippedBoundsInRoot()
        val naturalTop = (button.top.value + button.bottom.value) / 2f - 74f + 77f

        composeRule.onNodeWithContentDescription("Deck menu: Deck 20").performClick()

        // Последняя строка у низа списка: до поля кадра 1323 − 32 меню не доходит, и оно встаёт нижней кромкой на поле.
        val menu = composeRule.onNodeWithContentDescription("Deck 20").getUnclippedBoundsInRoot()
        assertTrue("меню выше естественного места: ${menu.top.value} против $naturalTop", menu.top.value < naturalTop - 20f)
        assertEquals(1323f - 32f, menu.bottom.value, 1f)
    }

    @Test
    fun menuThatFitsIsNotMoved() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        composeRule.onNodeWithContentDescription("Deck menu: English A2").performClick()

        val menu = composeRule.onNodeWithContentDescription("English A2").getUnclippedBoundsInRoot()
        assertEquals(206f, menu.top.value, 1f)
        assertTrue(menu.bottom.value < 1323f - 32f - 200f)
    }

    @Test
    fun renameItemSendsTheDeckAndClosesTheMenu() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }
        composeRule.onNodeWithContentDescription("Deck menu: English tenses").performClick()

        composeRule.onNodeWithText("Rename").performClick()

        assertEquals(listOf<DeckListAction>(DeckListAction.RenameDeck(102)), listActions)
        composeRule.onNodeWithContentDescription("English tenses").assertDoesNotExist()
    }

    @Test
    fun deleteItemAsksAboutTheDeckAndClosesTheMenu() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }
        composeRule.onNodeWithContentDescription("Deck menu: Formulas").performClick()

        composeRule.onNodeWithText("Delete deck").performClick()

        assertEquals(listOf<DeckListAction>(DeckListAction.AskDeleteDeck(103)), listActions)
        composeRule.onNodeWithContentDescription("Formulas").assertDoesNotExist()
    }

    @Test
    fun deleteItemIsSeparatedByALineAndHasAnIconLikeTheCardMenu() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }
        composeRule.onNodeWithContentDescription("Deck menu: English A2").performClick()

        val rename = composeRule.onNodeWithText("Rename", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val delete = composeRule.onNodeWithText("Delete deck", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val frame = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        // Пункты по 80, у удаления линия 1 сверху: между «Export to file» и «Delete deck» на черте строки есть чернила.
        val export = composeRule.onNodeWithText("Export to file", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val middle = ((export.bottom.value + delete.top.value) / 2f).toInt()
        assertTrue((600..800).any { x -> (middle - 40..middle + 40).any { y -> frame.isInkAt(x, y) } })
        assertEquals(160f, delete.top.value - rename.top.value, 2f)
        // Черта во всю ширину пункта ровно одна и только над «Delete deck»: у «Export to file» её нет (по правому краю текста нет).
        val lines = (rename.top.value.toInt() - 5..delete.bottom.value.toInt() + 30).filter { y -> (870..920).all { x -> frame.isInkAt(x, y) } }
        assertEquals("черты между пунктами: $lines", 1, lines.size)
        assertTrue("черта между «Export to file» и «Delete deck»: $lines", lines.single() > export.bottom.value && lines.single() < delete.top.value)
    }

    @Test
    fun touchOutsideTheMenuClosesItWithoutActions() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }
        composeRule.onNodeWithContentDescription("Deck menu: English tenses").performClick()

        composeRule.onNodeWithContentDescription("Close menu").performClick()

        composeRule.onNodeWithContentDescription("English tenses").assertDoesNotExist()
        assertTrue(listActions.isEmpty())
    }

    @Test
    fun menuClosesWhenItsDeckIsGone() {
        var decks = buildDecks()
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }
        composeRule.onNodeWithContentDescription("Deck menu: English tenses").performClick()
        composeRule.onNodeWithContentDescription("English tenses").assertExists()

        decks = decks.filter { it.id != 102L }
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        composeRule.onNodeWithContentDescription("English tenses").assertDoesNotExist()
        composeRule.onNodeWithText("Rename").assertDoesNotExist()
    }

    @Test
    fun deleteDialogShowsTheNameAndTheNumberOfCardsWithKeepAndDelete() {
        val dialog = DeleteDeckDialog(102, "English tenses", 86)
        showFrame { DeckListScreen(buildListState(buildDecks(), deleteDialog = dialog), { listActions += it }) }

        composeRule.onNodeWithText("Delete “English tenses”?").assertExists()
        composeRule.onNodeWithText("All 86 cards and their progress will be deleted. You can't undo this.").assertExists()
        // «Keep» главная (рамка 3), «Delete» обычная (рамка 2).
        assertEquals(3, measureBorder(composeRule.onNodeWithText("Keep")))
        assertEquals(2, measureBorder(composeRule.onNodeWithText("Delete")))
        composeRule.onNodeWithText("Keep").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf<DeckListAction>(DeckListAction.DismissDeleteDialog, DeckListAction.ConfirmDeleteDeck), listActions)
    }

    @Test
    fun deleteDialogCountsOneCardInTheSingular() {
        showFrame { DeckListScreen(buildListState(buildDecks(), deleteDialog = DeleteDeckDialog(103, "Formulas", 1)), { listActions += it }) }

        composeRule.onNodeWithText("All 1 card and their progress will be deleted. You can't undo this.").assertExists()
    }

    @Test
    fun noDialogUntilTheStateHasOne() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        composeRule.onNodeWithText("Keep").assertDoesNotExist()
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun rowsBehindTheDeleteDialogDoNotGetTouches() {
        showFrame { DeckListScreen(buildListState(buildDecks(), deleteDialog = DeleteDeckDialog(102, "English tenses", 86)), { listActions += it }) }

        composeRule.onNodeWithText("Formulas").performClick()

        assertTrue(listActions.isEmpty())
    }

    // --- новая колода --------------------------------------------------------------------------

    @Test
    fun createIsGreyUntilNameEntered() {
        showFrame { DeckNameScreen(buildNameState(""), { nameActions += it }) }

        composeRule.onNodeWithText("Create").assertIsNotEnabled().performClick()
        assertTrue(nameActions.isEmpty())

        showFrame { DeckNameScreen(buildNameState("Physics"), { nameActions += it }) }

        composeRule.onNodeWithText("Create").assertIsEnabled().performClick()
        assertEquals(listOf<DeckNameAction>(DeckNameAction.Save), nameActions)
        assertEquals(3, measureBorder(composeRule.onNodeWithText("Create")))
    }

    @Test
    fun headerOfNewDeckHasCancelAndCreate() {
        showFrame { DeckNameScreen(buildNameState(""), { nameActions += it }) }

        composeRule.onNodeWithText("New deck").assertExists()
        val cancel = composeRule.onNodeWithText("Cancel")
        assertEquals(32f, cancel.getUnclippedBoundsInRoot().left.value, 0.5f)
        // «Отмена» тихая, без рамки; «Создать» без имени серое и тоже без рамки.
        assertEquals(0, measureBorder(cancel))
        assertEquals(0, measureBorder(composeRule.onNodeWithText("Create")))
        assertEquals(960f, composeRule.onNodeWithText("Create").getUnclippedBoundsInRoot().right.value, 0.5f)
        cancel.performClick()
        assertEquals(listOf<DeckNameAction>(DeckNameAction.Cancel), nameActions)
    }

    @Test
    fun typingSendsTheTextAndDoneSavesOnlyWhenPossible() {
        // Как в приложении: введённый текст возвращается в состояние формы.
        formName = ""
        showFrame {
            DeckNameScreen(
                buildNameState(formName),
                { action ->
                    nameActions += action
                    if (action is DeckNameAction.ChangeName) formName = action.text
                },
            )
        }

        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertTrue("«Готово» при пустом имени ничего не делает", nameActions.isEmpty())

        composeRule.onNode(hasSetTextAction()).performTextInput("English")
        assertEquals(listOf<DeckNameAction>(DeckNameAction.ChangeName("English")), nameActions)

        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(listOf(DeckNameAction.ChangeName("English"), DeckNameAction.Save), nameActions)
    }

    @Test
    fun typedTextStaysWhenStateLags() {
        // Хост получает ChangeName, но состояние не возвращает (имя в нём навсегда пустое).
        showFrame { DeckNameScreen(buildNameState(""), { nameActions += it }) }

        composeRule.onNode(hasSetTextAction()).performTextInput("English")

        // Поле держит набранное само: оно не откатывается к устаревшему состоянию, а VM получил копию в том же вызове.
        assertEquals("English", readFieldText())
        assertEquals(listOf<DeckNameAction>(DeckNameAction.ChangeName("English")), nameActions)
        // Подсказка по набранному тексту, а не по состоянию.
        composeRule.onNodeWithText("English A2").assertDoesNotExist()
    }

    @Test
    fun fieldIsSingleLine() {
        formName = ""
        showFrame {
            DeckNameScreen(
                buildNameState(formName),
                { action ->
                    nameActions += action
                    if (action is DeckNameAction.ChangeName) formName = action.text
                },
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("English")
        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }

        // Enter в поле имени перевод строки не вставляет.
        val typed = nameActions.filterIsInstance<DeckNameAction.ChangeName>().last().text
        assertEquals("English", typed)
    }

    @Test
    fun fieldIsFocusedWhenOpened() {
        showFrame { DeckNameScreen(buildNameState(""), { nameActions += it }) }

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun keyboardIsShownWhenOpened() {
        showFrame { DeckNameScreen(buildNameState(""), { nameActions += it }) }

        val inputMethods = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        assertTrue(org.robolectric.Shadows.shadowOf(inputMethods).isSoftInputVisible)
    }

    @Test
    fun fieldHasLabelPlaceholderAndMockupGeometry() {
        showFrame { DeckNameScreen(buildNameState(""), { nameActions += it }) }

        // Подпись через 40 dp после шапки (96 + зазор 16), поле через 16 под ней: высота 88, рамка 2 px.
        val label = composeRule.onNodeWithText("Deck name", useUnmergedTree = true)
        val labelBounds = label.getUnclippedBoundsInRoot()
        assertEquals(96f + 16f + 40f, labelBounds.top.value, 1f)
        assertEquals(20f, readTextStyle(label).fontSize.value, 0.01f)
        // Рамка поля 2 px на всю ширину кадра минус поля, высота 88: считаем по картинке, узел текста лежит внутри рамки.
        val frame = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        val top = (labelBounds.bottom.value + 16f).toInt()
        val middle = top + 44
        assertEquals(2, (32 until 40).takeWhile { frame.isInkAt(it, middle) }.size)
        assertEquals(2, (959 downTo 950).takeWhile { frame.isInkAt(it, middle) }.size)
        assertEquals(2, (top until top + 8).takeWhile { frame.isInkAt(500, it) }.size)
        assertEquals(2, (top + 87 downTo top + 80).takeWhile { frame.isInkAt(500, it) }.size)
        assertTrue(!frame.isInkAt(500, top - 1))
        assertTrue(!frame.isInkAt(500, top + 88))
        val inner = composeRule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        assertEquals(32f + 2f + 16f, inner.left.value, 0.5f)
        val placeholder = composeRule.onNodeWithText("English A2", useUnmergedTree = true)
        assertEquals(28f, readTextStyle(placeholder).fontSize.value, 0.01f)
        assertEquals(Tokens.Unavailable, readTextStyle(placeholder).color)
        assertEquals(32f + 2f + 16f, placeholder.getUnclippedBoundsInRoot().left.value, 1f)
    }

    @Test
    fun placeholderDisappearsWhenNameIsEntered() {
        showFrame { DeckNameScreen(buildNameState("Physics"), { nameActions += it }) }

        composeRule.onNodeWithText("English A2").assertDoesNotExist()
        composeRule.onNodeWithText("Physics").assertExists()
        assertEquals(28f, readTextStyle(composeRule.onNodeWithText("Physics", useUnmergedTree = true)).fontSize.value, 0.01f)
    }

    // --- переименование ------------------------------------------------------------------------

    @Test
    fun renameFormHasRenameTitleCancelAndSaveWithTheCurrentName() {
        showFrame { DeckNameScreen(buildNameState("English tenses", isRename = true), { nameActions += it }) }

        composeRule.onNodeWithText("Rename").assertExists()
        composeRule.onNodeWithText("New deck").assertDoesNotExist()
        composeRule.onNodeWithText("Create").assertDoesNotExist()
        composeRule.onNodeWithText("English tenses").assertExists()
        composeRule.onNodeWithText("Save").assertIsEnabled()
        assertEquals(3, measureBorder(composeRule.onNodeWithText("Save")))
        assertEquals(960f, composeRule.onNodeWithText("Save").getUnclippedBoundsInRoot().right.value, 0.5f)
        assertEquals(0, measureBorder(composeRule.onNodeWithText("Cancel")))
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf<DeckNameAction>(DeckNameAction.Save, DeckNameAction.Cancel), nameActions)
    }

    @Test
    fun renameCursorStandsAtTheEndOfTheName() {
        showFrame { DeckNameScreen(buildNameState("English tenses", isRename = true), { nameActions += it }) }

        val selection = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(androidx.compose.ui.text.TextRange("English tenses".length), selection)
    }

    @Test
    fun renameSaveIsGreyWhileTheNameIsBlank() {
        showFrame { DeckNameScreen(buildNameState("", isRename = true), { nameActions += it }) }

        composeRule.onNodeWithText("Save").assertIsNotEnabled().performClick()
        assertTrue(nameActions.isEmpty())
        assertEquals(0, measureBorder(composeRule.onNodeWithText("Save")))
    }

    @Test
    fun renameDoneOnTheKeyboardSavesOnlyWhenPossible() {
        showFrame { DeckNameScreen(buildNameState("Formulas", isRename = true), { nameActions += it }) }

        composeRule.onNode(hasSetTextAction()).performImeAction()

        assertEquals(listOf<DeckNameAction>(DeckNameAction.Save), nameActions)
    }

    // --- запуск --------------------------------------------------------------------------------

    @Test
    fun startupStatesHaveTheirTexts() {
        val cases = listOf(
            Triple(StartupState.NEEDS_FILE_ACCESS, "We need file access", "Your decks are in the Deckly folder. Allow file access to open them."),
            Triple(StartupState.DATABASE_DAMAGED, "Database damaged", "Couldn't open Deckly/deckly.db. We haven't deleted anything."),
            Triple(StartupState.DATABASE_MISSING, "Database not found", "We can't find deckly.db in the Deckly folder. Your drawings are still there."),
        )
        val buttons = mapOf(
            StartupState.NEEDS_FILE_ACCESS to ("Open settings" to StartupAction.OpenSettings),
            StartupState.DATABASE_DAMAGED to ("Close" to StartupAction.Close),
            StartupState.DATABASE_MISSING to ("Close" to StartupAction.Close),
        )
        for ((state, title, text) in cases) {
            startupState = state
            showFrame { startupState.let { shown -> StartupScreen(shown, { startupActions += it }) } }

            composeRule.onNodeWithText(title).assertExists()
            composeRule.onNodeWithText(text).assertExists()
            val (label, action) = buttons.getValue(state)
            // Одна главная кнопка на всю ширину окна; другой кнопки нет.
            val button = composeRule.onNodeWithText(label)
            assertEquals(3, measureBorder(button))
            button.performClick()
            assertEquals(listOf<StartupAction>(action), startupActions)
            startupActions.clear()
        }
    }

    @Test
    fun startupIsDialogOnPlainWhiteScreen() {
        showFrame { StartupScreen(StartupState.DATABASE_DAMAGED, { startupActions += it }) }

        // Окно по центру кадра (ширина 736); шапки колоды, вкладок и нижней строки экрана нет.
        val title = composeRule.onNodeWithText("Database damaged", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((992f - 736f) / 2f + 3f + 40f, title.left.value, 1.5f)
        composeRule.onNodeWithText("Decks").assertDoesNotExist()
        composeRule.onNodeWithText("English A2").assertDoesNotExist()
        val frame = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        assertTrue(!frame.isInkAt(40, 40))
        assertTrue(!frame.isInkAt(950, 1280))
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private var startupState by mutableStateOf(StartupState.NEEDS_FILE_ACCESS)
    private var formName by mutableStateOf("")
    private var content by mutableStateOf<@androidx.compose.runtime.Composable () -> Unit>({})
    private var isHosted = false

    // Экран один на весь тест: повторный вызов подменяет содержимое, как это делает приложение при смене состояния.
    private fun showFrame(screen: @androidx.compose.runtime.Composable () -> Unit) {
        content = screen
        if (isHosted) {
            composeRule.waitForIdle()
            return
        }
        isHosted = true
        composeRule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(Color.White).testTag("frame")) { content() }
        }
    }

    // Текст в поле ввода без подсказки.
    private fun readFieldText(): String =
        composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    // Ширина чёрной рамки у левого края узла на середине высоты.
    private fun measureBorder(node: SemanticsNodeInteraction): Int {
        val bitmap = node.captureToImage().asAndroidBitmap()
        return (0 until 8).takeWhile { bitmap.isInkAt(it, bitmap.height / 2) }.size
    }

    private fun buildDecks() = listOf(
        DeckRow(101, "English A2", 245, activeEpoch = 1, isAllLearned = false, isCurrent = true),
        DeckRow(102, "English tenses", 86, activeEpoch = 2, isAllLearned = false, isCurrent = false),
        DeckRow(103, "Formulas", 18, activeEpoch = null, isAllLearned = true, isCurrent = false),
    )

    private fun buildListState(
        decks: List<DeckRow>,
        canGoBack: Boolean = decks.isNotEmpty(),
        deleteDialog: DeleteDeckDialog? = null,
        isImportErrorShown: Boolean = false,
    ) = DeckListState(decks, canGoBack, deleteDialog, isImportErrorShown)

    private fun buildNameState(name: String, isRename: Boolean = false) = DeckNameState(name, canSave = name.isNotBlank(), isRename = isRename)
}
