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

        composeRule.onNodeWithText("Колоды").assertExists()
        composeRule.onNodeWithText("Нет колод").assertExists()
        composeRule.onNodeWithContentDescription("К карточкам").assertDoesNotExist()
        composeRule.onNodeWithText("Новая колода").assertExists()
        // Значок «карточки» над заголовком, как в пустой секции: сверху лист, снизу нижняя строка.
        val title = composeRule.onNodeWithText("Нет колод", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        val iconTop = (title.top.value - 28f - 68f).toInt()
        assertIconShape("нет колод", bitmap, 464 until 528, iconTop until iconTop + 64, IconId.CARDS, 64f, Tokens.IconEmptyStroke, tolerance = 1.2f)
        // Лист на всю высоту между шапкой и нижней строкой: заголовок посередине (112…1163).
        assertEquals((112f + 1163f) / 2f, (title.top.value + title.bottom.value) / 2f - (68f + 28f) / 2f, 1.5f)
    }

    @Test
    fun backToCardsOnlyWhenThereIsCurrentDeck() {
        showFrame { DeckListScreen(buildListState(buildDecks(), canGoBack = false), { listActions += it }) }
        composeRule.onNodeWithContentDescription("К карточкам").assertDoesNotExist()
        showFrame { DeckListScreen(buildListState(buildDecks(), canGoBack = true), { listActions += it }) }

        val back = composeRule.onNodeWithContentDescription("К карточкам")
        back.assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(64.dp)
        assertEquals(32f, back.getUnclippedBoundsInRoot().left.value, 0.5f)
        assertIconInButton("к карточкам", back, IconId.LEFT)
        // Название следует за кнопкой через промежуток шапки 24.
        assertEquals(32f + 64f + 24f, composeRule.onNodeWithText("Колоды", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value, 0.5f)
        back.performClick()
        assertEquals(listOf<DeckListAction>(DeckListAction.Back), listActions)
    }

    @Test
    fun headingIsBoldTitleLikeInEditor() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        val style = readTextStyle(composeRule.onNodeWithText("Колоды", useUnmergedTree = true))
        assertEquals(28f, style.fontSize.value, 0.01f)
        assertEquals(-0.6f, style.letterSpacing.value, 0.01f)
        assertEquals(FontWeight.Bold, style.fontWeight)
    }

    @Test
    fun rowsShowNumbersNamesAndCaptionsLikeMockup() {
        val decks = listOf(
            DeckRow(1, "Немецкий A2", 245, activeEpoch = 1, isAllLearned = false, isCurrent = true),
            DeckRow(2, "Английские времена", 86, activeEpoch = 2, isAllLearned = false, isCurrent = false),
            DeckRow(3, "Формулы", 18, activeEpoch = null, isAllLearned = true, isCurrent = false),
            DeckRow(4, "История искусства", 1, activeEpoch = null, isAllLearned = false, isCurrent = false),
        )
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        for (name in decks.map { it.name }) composeRule.onNodeWithText(name).assertExists()
        composeRule.onNodeWithText("245 карточек ·").assertExists()
        composeRule.onNodeWithText("86 карточек ·").assertExists()
        composeRule.onNodeWithText("18 карточек · выучены").assertExists()
        composeRule.onNodeWithText("1 карточка").assertExists()
        // Эпоха — римская цифра справа от значка ▶.
        composeRule.onNode(hasText("I") and hasClickAction()).assertExists()
        composeRule.onNode(hasText("II") and hasClickAction()).assertExists()
        // Номера 02, 03, 04; у текущей колоды вместо номера галочка.
        for (number in listOf("02", "03", "04")) composeRule.onNodeWithText(number).assertExists()
        composeRule.onNodeWithText("01").assertDoesNotExist()
    }

    @Test
    fun captionMarksActiveEpochWithArrowIcon() {
        val decks = listOf(DeckRow(1, "Немецкий A2", 245, activeEpoch = 3, isAllLearned = false, isCurrent = false))
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        val text = composeRule.onNodeWithText("245 карточек ·", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val epoch = composeRule.onNodeWithText("III", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        // ▶ 18 dp между подписью и эпохой с зазором 8 с каждой стороны.
        assertEquals(text.right.value + 8f + 18f + 8f, epoch.left.value, 1f)
        val ink = measureInkBox(bitmap, (text.right.value + 8f).toInt() until (text.right.value + 26f).toInt(), text.top.value.toInt() until text.bottom.value.toInt())
        assertNotNull("значка ▶ нет", ink)
        assertEquals(20f, readTextStyle(composeRule.onNodeWithText("245 карточек ·", useUnmergedTree = true)).fontSize.value, 0.01f)
    }

    @Test
    fun currentDeckIsChecked() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        // Галочка 24 dp в колонке номера (44 dp слева); у остальных строк там номер текстом.
        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        val numberColumn = 32 until 32 + 44
        assertIconShape("текущая колода", bitmap, numberColumn, 129 until 129 + 147, IconId.CHECK, 24f, Tokens.IconStroke)
        val current = readTextStyle(composeRule.onNodeWithText("Немецкий A2", useUnmergedTree = true))
        val other = readTextStyle(composeRule.onNodeWithText("Английские времена", useUnmergedTree = true))
        assertEquals(FontWeight.Bold, current.fontWeight)
        assertEquals(FontWeight.Medium, other.fontWeight)
        assertEquals(30f, current.fontSize.value, 0.01f)
        assertEquals(36f, current.lineHeight.value, 0.01f)
        assertEquals(20f, readTextStyle(composeRule.onNodeWithText("02", useUnmergedTree = true)).fontSize.value, 0.01f)
    }

    @Test
    fun rowsKeepMockupGeometry() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        // Список начинается после шапки (96) и зазоров 16 + 16, линия 1 сверху; строка 148 с линией внизу.
        val first = composeRule.onNode(hasText("Немецкий A2") and hasClickAction()).getUnclippedBoundsInRoot()
        val second = composeRule.onNode(hasText("Английские времена") and hasClickAction()).getUnclippedBoundsInRoot()
        assertEquals(32f + 44f + 24f, first.left.value, 0.5f)
        assertEquals(960f, first.right.value, 0.5f)
        assertEquals(112f, first.bottom.value - first.top.value, 1f)
        assertEquals(148f, second.top.value - first.top.value, 0.5f)
        assertEquals(129f + 147f / 2f, (first.top.value + first.bottom.value) / 2f, 1f)
        // Название и подпись разделены 9 dp.
        val name = composeRule.onNodeWithText("Немецкий A2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val caption = composeRule.onNodeWithText("245 карточек ·", useUnmergedTree = true).getUnclippedBoundsInRoot()
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
            DeckRow(11, "Первая", 5, null, false, isCurrent = false),
            DeckRow(22, "Вторая", 6, null, false, isCurrent = true),
        )
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        val bitmap = composeRule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        // Галочка во второй строке (277…424), у первой номер 01; номера 02 нет.
        assertIconShape("вторая строка", bitmap, 32 until 76, 277 until 277 + 147, IconId.CHECK, 24f, Tokens.IconStroke)
        composeRule.onNodeWithText("01").assertExists()
        composeRule.onNodeWithText("02").assertDoesNotExist()
    }

    @Test
    fun longDeckNameStaysOnOneLine() {
        val name = "Очень длинное название колоды, которое никак не помещается в одну строку списка целиком"
        val decks = listOf(DeckRow(1, name, 5, null, false, isCurrent = false), DeckRow(2, "Вторая", 6, null, false, isCurrent = false))
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        val bounds = composeRule.onNodeWithText(name, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val second = composeRule.onNode(hasText("Вторая") and hasClickAction()).getUnclippedBoundsInRoot()
        val first = composeRule.onNode(hasText(name) and hasClickAction()).getUnclippedBoundsInRoot()
        // Одна строка, обрезанная многоточием: высота 36, ширина не шире колонки названия, строки остаются по 148.
        assertEquals(36f, bounds.bottom.value - bounds.top.value, 3f)
        assertTrue(bounds.right.value <= 960f + 0.5f)
        assertEquals(148f, second.top.value - first.top.value, 0.5f)
    }

    @Test
    fun rowOpensItsDeck() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        composeRule.onNodeWithText("Формулы").performClick()
        composeRule.onNodeWithText("Немецкий A2").performClick()

        assertEquals(listOf<DeckListAction>(DeckListAction.OpenDeck(103), DeckListAction.OpenDeck(101)), listActions)
    }

    @Test
    fun listScrollsSoNoDeckIsLostBehindFooter() {
        val decks = (1..20).map { DeckRow(it.toLong(), "Колода $it", it, null, false, it == 1) }
        showFrame { DeckListScreen(buildListState(decks), { listActions += it }) }

        composeRule.onNodeWithText("Колода 20").assertIsNotDisplayed()
        composeRule.onNodeWithText("Колода 20").performScrollTo().assertIsDisplayed()
        // Нижняя строка осталась на месте: список прокручивается над ней.
        assertEquals(1323f - 32f - 112f, composeRule.onNodeWithText("Новая колода").getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun newDeckButtonFillsFooterAndCreatesDeck() {
        showFrame { DeckListScreen(buildListState(buildDecks()), { listActions += it }) }

        val button = composeRule.onNodeWithText("Новая колода")
        val bounds = button.getUnclippedBoundsInRoot()
        assertEquals(32f, bounds.left.value, 0.5f)
        assertEquals(928f, bounds.right.value - bounds.left.value, 0.5f)
        assertEquals(112f, bounds.bottom.value - bounds.top.value, 0.5f)
        assertEquals(1323f - 32f, bounds.bottom.value, 0.5f)
        assertEquals(3, measureBorder(button))
        assertEquals(26f, readTextStyle(composeRule.onNodeWithText("Новая колода", useUnmergedTree = true)).fontSize.value, 0.01f)
        composeRule.assertIconBeforeLabel("Новая колода", IconId.PLUS, borderMargin = 8)
        button.performClick()
        assertEquals(listOf<DeckListAction>(DeckListAction.CreateDeck), listActions)
    }

    // --- новая колода --------------------------------------------------------------------------

    @Test
    fun createIsGreyUntilNameEntered() {
        showFrame { DeckNameScreen(buildNameState(""), { nameActions += it }) }

        composeRule.onNodeWithText("Создать").assertIsNotEnabled().performClick()
        assertTrue(nameActions.isEmpty())

        showFrame { DeckNameScreen(buildNameState("Физика"), { nameActions += it }) }

        composeRule.onNodeWithText("Создать").assertIsEnabled().performClick()
        assertEquals(listOf<DeckNameAction>(DeckNameAction.Save), nameActions)
        assertEquals(3, measureBorder(composeRule.onNodeWithText("Создать")))
    }

    @Test
    fun headerOfNewDeckHasCancelAndCreate() {
        showFrame { DeckNameScreen(buildNameState(""), { nameActions += it }) }

        composeRule.onNodeWithText("Новая колода").assertExists()
        val cancel = composeRule.onNodeWithText("Отмена")
        assertEquals(32f, cancel.getUnclippedBoundsInRoot().left.value, 0.5f)
        // «Отмена» тихая, без рамки; «Создать» без имени серое и тоже без рамки.
        assertEquals(0, measureBorder(cancel))
        assertEquals(0, measureBorder(composeRule.onNodeWithText("Создать")))
        assertEquals(960f, composeRule.onNodeWithText("Создать").getUnclippedBoundsInRoot().right.value, 0.5f)
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

        composeRule.onNode(hasSetTextAction()).performTextInput("Немецкий")
        assertEquals(listOf<DeckNameAction>(DeckNameAction.ChangeName("Немецкий")), nameActions)

        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(listOf(DeckNameAction.ChangeName("Немецкий"), DeckNameAction.Save), nameActions)
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

        composeRule.onNode(hasSetTextAction()).performTextInput("Немецкий")
        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }

        // Enter в поле имени перевод строки не вставляет.
        val typed = nameActions.filterIsInstance<DeckNameAction.ChangeName>().last().text
        assertEquals("Немецкий", typed)
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
        val label = composeRule.onNodeWithText("Название колоды", useUnmergedTree = true)
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
        val placeholder = composeRule.onNodeWithText("Немецкий A2", useUnmergedTree = true)
        assertEquals(28f, readTextStyle(placeholder).fontSize.value, 0.01f)
        assertEquals(Tokens.Unavailable, readTextStyle(placeholder).color)
        assertEquals(32f + 2f + 16f, placeholder.getUnclippedBoundsInRoot().left.value, 1f)
    }

    @Test
    fun placeholderDisappearsWhenNameIsEntered() {
        showFrame { DeckNameScreen(buildNameState("Физика"), { nameActions += it }) }

        composeRule.onNodeWithText("Немецкий A2").assertDoesNotExist()
        composeRule.onNodeWithText("Физика").assertExists()
        assertEquals(28f, readTextStyle(composeRule.onNodeWithText("Физика", useUnmergedTree = true)).fontSize.value, 0.01f)
    }

    // --- запуск --------------------------------------------------------------------------------

    @Test
    fun startupStatesHaveTheirTexts() {
        val cases = listOf(
            Triple(StartupState.NEEDS_FILE_ACCESS, "Нужен доступ к файлам", "Колоды хранятся в папке Deckly, без доступа их не открыть."),
            Triple(StartupState.DATABASE_DAMAGED, "База повреждена", "Файл Deckly/deckly.db не открылся, ничего не удалено."),
            Triple(StartupState.DATABASE_MISSING, "База не найдена", "В папке Deckly нет файла deckly.db, рисунки карточек не тронуты."),
        )
        val buttons = mapOf(
            StartupState.NEEDS_FILE_ACCESS to ("Открыть настройки" to StartupAction.OpenSettings),
            StartupState.DATABASE_DAMAGED to ("Закрыть" to StartupAction.Close),
            StartupState.DATABASE_MISSING to ("Закрыть" to StartupAction.Close),
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
        val title = composeRule.onNodeWithText("База повреждена", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((992f - 736f) / 2f + 3f + 40f, title.left.value, 1.5f)
        composeRule.onNodeWithText("Колоды").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий A2").assertDoesNotExist()
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

    // Ширина чёрной рамки у левого края узла на середине высоты.
    private fun measureBorder(node: SemanticsNodeInteraction): Int {
        val bitmap = node.captureToImage().asAndroidBitmap()
        return (0 until 8).takeWhile { bitmap.isInkAt(it, bitmap.height / 2) }.size
    }

    private fun buildDecks() = listOf(
        DeckRow(101, "Немецкий A2", 245, activeEpoch = 1, isAllLearned = false, isCurrent = true),
        DeckRow(102, "Английские времена", 86, activeEpoch = 2, isAllLearned = false, isCurrent = false),
        DeckRow(103, "Формулы", 18, activeEpoch = null, isAllLearned = true, isCurrent = false),
    )

    private fun buildListState(decks: List<DeckRow>, canGoBack: Boolean = decks.isNotEmpty()) = DeckListState(decks, canGoBack)

    private fun buildNameState(name: String) = DeckNameState(name, canSave = name.isNotBlank())
}
