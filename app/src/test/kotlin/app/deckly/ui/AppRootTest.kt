package app.deckly.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import app.deckly.AppContainer
import app.deckly.data.DecklyData
import app.deckly.data.db.DatabaseDamagedException
import app.deckly.domain.editor.Draft
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Section
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.CardContentRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DraftRepository
import app.deckly.domain.port.StoredDraft
import app.deckly.design.e.DesignE
import app.deckly.editor.CanvasState
import app.deckly.fakes.FakeAppStateRepository
import app.deckly.fakes.FakeCardContentRepository
import app.deckly.fakes.FakeDeckCatalog
import app.deckly.fakes.FakeDeckRepository
import app.deckly.fakes.FakeDraftRepository
import app.deckly.fakes.buildEmptyDraft
import app.deckly.fakes.buildFakeData
import app.deckly.fixtures.buildCard
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import app.deckly.fixtures.buildEpochRange
import app.deckly.fixtures.buildPassDeck
import app.deckly.navigation.AppScreen
import app.deckly.navigation.AppViewModel
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckNameAction
import app.deckly.ui.contract.DeckNameState
import app.deckly.ui.contract.DeckScreenState
import app.deckly.ui.contract.Design
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.ShapeKind
import app.deckly.ui.contract.StartupAction
import app.deckly.ui.contract.StartupState
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Сборка экранов: настоящий дизайн варианта E, настоящие ViewModel и порты в памяти. Листы (карточка, холст)
 * подменяются записывающими, потому что перо и холст на JVM проверить нечем.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
class AppRootTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private lateinit var dispatcher: TestDispatcher
    private var settingsOpened = 0
    private var closed = 0
    private var fullRefreshes = 0
    private val sheets = RecordingSheets()
    private var sheetsInUse: SheetSlots by mutableStateOf(sheets)

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- запуск --------------------------------------------------------------------------------

    @Test
    fun startWithDraftOpensEditor() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))

        composeRule.onNodeWithText("New card").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
    }

    @Test
    fun startWithCurrentDeckOpensDeck() {
        startApp(buildData(appState = FakeAppStateRepository(DECK_ID)))

        composeRule.onNodeWithText("Немецкий A2").assertExists()
    }

    @Test
    fun startWithoutCurrentDeckOpensDeckList() {
        startApp(buildData())

        composeRule.onNodeWithText("Decks").assertExists()
        composeRule.onNodeWithText("Немецкий A2").assertExists()
    }

    @Test
    fun loadingScreenShowsOnlyBrandUntilStorageOpens() {
        val gate = CompletableDeferred<Unit>()
        startApp(buildData(), openGate = gate)

        // Пока хранилище открывается, на экране одно слово «Deckly»: ни кнопок, ни других надписей.
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(1)
        composeRule.onNodeWithText("Deckly").assertExists()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        gate.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Decks").assertExists()
        composeRule.onNodeWithText("Deckly").assertDoesNotExist()
    }

    @Test
    fun accessRequestOffersSettings() {
        startApp(buildData(), hasFileAccess = false)

        composeRule.onNodeWithText("We need file access").assertExists()
        composeRule.onNodeWithText("Open settings").performClick()

        assertEquals(1, settingsOpened)
        assertEquals(0, closed)
    }

    @Test
    fun damagedDatabaseOffersClose() {
        startApp(buildData(), openFailure = DatabaseDamagedException(File("deckly.db"), "повреждена"))

        composeRule.onNodeWithText("Database damaged").assertExists()
        composeRule.onNodeWithText("Close").performClick()

        assertEquals(1, closed)
        assertEquals(0, settingsOpened)
    }

    // --- системная «Назад» ---------------------------------------------------------------------

    @Test
    fun backInEditorOpensSaveDialogAndSecondBackDismissesIt() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))

        pressBack()

        composeRule.onNodeWithText("Save and exit?").assertExists()
        assertFalse(composeRule.activity.isFinishing)

        pressBack()

        composeRule.onNodeWithText("Save and exit?").assertDoesNotExist()
        composeRule.onNodeWithText("New card").assertExists()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backInDeckWithoutDialogLeavesApp() {
        startApp(buildData(appState = FakeAppStateRepository(DECK_ID)))

        pressBack()

        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun backInDeckClosesOpenDialogOnly() {
        startApp(buildData(decks = buildCatalogWithCard(), appState = FakeAppStateRepository(DECK_ID)))
        composeRule.onNode(hasContentDescription("Card actions") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Delete card").performClick()
        composeRule.onNodeWithText("Delete card?").assertExists()

        pressBack()

        composeRule.onNodeWithText("Delete card?").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий A2").assertExists()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backWithOpenCardMenuClosesMenuNotApp() {
        startApp(buildData(decks = buildCatalogWithCard(), appState = FakeAppStateRepository(DECK_ID)))
        composeRule.onNode(hasContentDescription("Card actions") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Delete card").assertExists()

        pressBack()

        composeRule.onNodeWithText("Delete card").assertDoesNotExist()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backInDeckListWithCurrentDeckReturnsToIt() {
        startApp(buildData(appState = FakeAppStateRepository(DECK_ID)))
        composeRule.onNodeWithContentDescription("Немецкий A2. Choose deck").performClick()
        composeRule.onNodeWithText("Decks").assertExists()

        pressBack()

        composeRule.onNodeWithContentDescription("Немецкий A2. Choose deck").assertExists()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backInDeckListWithoutCurrentDeckLeavesApp() {
        startApp(buildData())
        composeRule.onNodeWithText("Decks").assertExists()

        pressBack()

        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun backInNewDeckFormCancelsToDeckList() {
        startApp(buildData())
        composeRule.onNodeWithText("New deck").performClick()
        composeRule.onNodeWithText("Deck name").assertExists()

        pressBack()

        composeRule.onNodeWithText("Deck name").assertDoesNotExist()
        composeRule.onNodeWithText("Decks").assertExists()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backOnStartupScreenLeavesApp() {
        startApp(buildData(), hasFileAccess = false)

        pressBack()

        assertTrue(composeRule.activity.isFinishing)
    }

    // --- время жизни ViewModel -----------------------------------------------------------------

    @Test
    fun returnToEqualDeckScreenReadsTheDeckAgain() {
        val catalog = CountingCatalog(FakeDeckCatalog(listOf(buildDeckState(buildDeck(DECK_ID, "Немецкий A2")))))
        val app = startApp(buildData(decks = catalog, appState = FakeAppStateRepository(DECK_ID)))
        assertEquals(1, catalog.deckLoads)

        // Тот же экран по значению, но переход состоялся: ViewModel новая и читает колоду заново.
        app.navigate(AppScreen.Deck(DECK_ID))
        composeRule.waitForIdle()

        assertEquals(2, catalog.deckLoads)
        composeRule.onNodeWithText("Немецкий A2").assertExists()
    }

    @Test
    fun screenViewModelsAreTakenFromTheEntryStore() {
        val probe = ProbeDesign()
        val app = startApp(buildData(), hasFileAccess = false, design = probe)

        assertSame(app.entry.value.viewModelStore, probe.storeOwnerStore)
    }

    @Test
    fun editorShowsNothingWhileResumedDraftLoads() {
        val drafts = FakeDraftRepository()
        val probe = ProbeDesign()
        val app = startApp(buildData(drafts = drafts, appState = FakeAppStateRepository(DECK_ID)), design = probe)
        assertEquals(1, fullRefreshes)
        drafts.stored = StoredDraft(DECK_ID, Draft(null, CardSide(), CardSide(), Side.BACK, Section.NEW))
        drafts.loadGate = CompletableDeferred()
        // Вход в редактор должен стать 30-й сменой после первого обновления: тогда обновление при входе видно в счётчике.
        navigateAround(app, REFRESH_PERIOD - 1)
        assertEquals(1, fullRefreshes)

        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.ResumeDraft))
        composeRule.waitForIdle()

        // Пока черновик читается: ни экрана дизайна, ни обновления (и смена ещё не посчитана), ни обработчика «Назад»;
        // лист белый, как у загрузки.
        assertTrue(probe.editorStates.isEmpty())
        assertEquals(1, fullRefreshes)
        drafts.loadGate!!.complete(Unit)
        composeRule.waitForIdle()

        // Первое же показанное состояние верное, обновление при входе ровно одно.
        assertTrue(probe.editorStates.isNotEmpty())
        assertTrue(probe.editorStates.all { it.isNewCard && it.side == CardSideShown.BACK })
        assertEquals(2, fullRefreshes)
    }

    @Test
    fun editorShowsNothingWhileEditedCardLoads() {
        val card = buildCard(id = 7, section = Section.NEW, position = 0)
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), card))
        val contents = FakeCardContentRepository()
        val probe = ProbeDesign()
        val app = startApp(buildData(decks = repository, contents = contents, appState = FakeAppStateRepository(DECK_ID)), design = probe)
        contents.loadGate = CompletableDeferred()

        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.EditCard(7, Section.NEW)))
        composeRule.waitForIdle()
        assertTrue(probe.editorStates.isEmpty())
        contents.loadGate!!.complete(Unit)
        composeRule.waitForIdle()

        assertTrue(probe.editorStates.isNotEmpty())
        assertTrue(probe.editorStates.all { !it.isNewCard && it.side == CardSideShown.FRONT })
    }

    @Test
    fun backWhileEditorLoadsIsLeftToTheSystem() {
        val drafts = FakeDraftRepository()
        val app = startApp(buildData(drafts = drafts, appState = FakeAppStateRepository(DECK_ID)))
        drafts.stored = StoredDraft(DECK_ID, buildEmptyDraft())
        drafts.loadGate = CompletableDeferred()
        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.ResumeDraft))
        composeRule.waitForIdle()

        pressBack()

        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun screenViewModelIsTakenFromTheStoreOncePerEntry() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        val app = startApp(buildData(drafts = drafts))
        val loadsAfterStart = drafts.loadCount

        // Хранилище записи очищено (так делает переход), а хост перекомпонован другим листом.
        app.entry.value.viewModelStore.clear()
        sheetsInUse = RecordingSheets()
        composeRule.waitForIdle()

        assertEquals("модель не создаётся заново в очищенном хранилище", loadsAfterStart, drafts.loadCount)
    }

    // --- слоты листов --------------------------------------------------------------------------

    @Test
    fun deckSlotGetsStrokesOfShownCard() {
        val card = buildCard(id = 7, section = Section.NEW, position = 0)
        val catalog = FakeDeckCatalog(listOf(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), card)))
        startApp(buildData(decks = catalog, appState = FakeAppStateRepository(DECK_ID)))

        assertEquals(FakeCardContentRepository.buildContent(7).front.strokes, sheets.deckSides.last().strokes)
        // Описание листа ставит дизайн, слот его не затирает.
        composeRule.onNodeWithContentDescription("Handwritten card").assertExists()
    }

    @Test
    fun editorSlotGetsCanvasOfEditorAndKeepsDescription() {
        val stroke = Stroke(6f, listOf(StrokePoint(1f, 1f, 1f)))
        val draft = Draft(null, CardSide(listOf(stroke)), CardSide(), Side.FRONT, Section.NEW)
        startApp(buildData(drafts = FakeDraftRepository(StoredDraft(DECK_ID, draft))))

        assertEquals(listOf(stroke), sheets.canvases.last().side.strokes)
        composeRule.onNodeWithContentDescription("Canvas. Draw with the pen only").assertExists()
    }

    @Test
    fun browseShowsShapesOfShownCardAndFlipShowsTheOtherSide() {
        val card = buildCard(id = 7, section = Section.NEW, position = 0)
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), card))
        startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID), contents = buildContentsWithShapes(7)))

        assertEquals(CardSide(listOf(FRONT_STROKE), listOf(FRONT_SHAPE)), sheets.deckSides.last())

        composeRule.onNodeWithContentDescription("Show answer").performClick()
        composeRule.waitForIdle()

        assertEquals(CardSide(emptyList(), listOf(BACK_SHAPE)), sheets.deckSides.last())
    }

    @Test
    fun learningShowsShapesOfShownCardAndFlipShowsTheOtherSide() {
        val repository = FakeDeckRepository(buildDeckState(buildPassDeck(epoch = 1), buildEpochRange(1, 7, 1)))
        startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID), contents = buildContentsWithShapes(7)))

        assertEquals(CardSide(listOf(FRONT_STROKE), listOf(FRONT_SHAPE)), sheets.deckSides.last())

        composeRule.onNodeWithContentDescription("Show answer").performClick()
        composeRule.waitForIdle()

        assertEquals(CardSide(emptyList(), listOf(BACK_SHAPE)), sheets.deckSides.last())
    }

    @Test
    fun shapesButtonOpensPaletteChoosingKindShowsShapePanelAndApplyBringsBackTheToolbar() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))

        composeRule.onNodeWithContentDescription("Shapes").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Outline width").assertExists()
        assertFalse("под палитрой перо выключено", sheets.canvases.last().isPenEnabled)

        composeRule.onNodeWithContentDescription("Rectangle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Outline width").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Apply shape").assertExists()
        composeRule.onNodeWithContentDescription("Shape and outline width").assertExists()
        assertEquals(listOf(Shape.Kind.RECTANGLE), sheets.canvases.last().side.shapes.map { it.kind })
        assertFalse("при выделении перо выключено", sheets.canvases.last().isPenEnabled)
        assertEquals("фигура в руках на диск не идёт", null, drafts.stored?.draft?.front?.shapes?.firstOrNull())

        composeRule.onNodeWithContentDescription("Apply shape").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Undo stroke").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Apply shape").assertDoesNotExist()
        assertTrue(sheets.canvases.last().isPenEnabled)
        assertEquals(listOf(Shape.Kind.RECTANGLE), drafts.stored?.draft?.front?.shapes?.map { it.kind })
    }

    @Test
    fun canvasShapeTapSelectsTheShapeAndShowsTheShapePanel() {
        val draft = Draft(null, CardSide(shapes = listOf(FRONT_SHAPE)), CardSide(), Side.FRONT, Section.NEW)
        startApp(buildData(drafts = FakeDraftRepository(StoredDraft(DECK_ID, draft))))
        composeRule.onNodeWithContentDescription("Apply shape").assertDoesNotExist()

        composeRule.runOnUiThread { sheets.onShapeTap(0) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Apply shape").assertExists()
        assertFalse(sheets.canvases.last().isPenEnabled)
    }

    @Test
    fun systemBackClosesTheShapePaletteAndDoesNotAskToLeave() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))
        composeRule.onNodeWithContentDescription("Shapes").performClick()
        composeRule.waitForIdle()

        pressBack()

        composeRule.onNodeWithText("Outline width").assertDoesNotExist()
        composeRule.onNodeWithText("Save and exit?").assertDoesNotExist()
        assertTrue(sheets.canvases.last().isPenEnabled)
    }

    @Test
    fun rememberedOutlineWidthReachesTheNextEditorFromThePreferences() {
        val container = AppContainer(application, hasFileAccess = { true })
        container.outlineWidthStore.writeWidth(10)
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))
        composeRule.onNodeWithContentDescription("Shapes").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rectangle").performClick()
        composeRule.waitForIdle()

        // Контейнер приложения читает ту же настройку: новая фигура получает толщину 10 единиц макета.
        assertEquals(10f * app.deckly.domain.model.CardUnits.PER_DESIGN_UNIT, sheets.canvases.last().side.shapes.single().width, 1e-3f)
    }

    @Test
    fun strokeAndEraseFromCanvasReachEditor() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))
        val stroke = Stroke(6f, listOf(StrokePoint(100f, 100f, 1f), StrokePoint(200f, 200f, 1f)))

        composeRule.runOnUiThread { sheets.onStroke(stroke) }
        composeRule.waitForIdle()

        assertEquals(listOf(stroke), sheets.canvases.last().side.strokes)
        assertEquals("штрих записан в черновик", listOf(stroke), drafts.stored?.draft?.front?.strokes)
        composeRule.onNodeWithContentDescription("Undo stroke").assertIsEnabled()

        composeRule.runOnUiThread { sheets.onErase(listOf(StrokePoint(150f, 150f, 1f)), 50f) }
        composeRule.waitForIdle()

        assertEquals(emptyList<Stroke>(), sheets.canvases.last().side.strokes)
    }

    @Test
    fun editorStartsMatchTheirScreens() {
        val card = buildCard(id = 7, section = Section.NEW, position = 0)
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), card))
        val app = startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID)))

        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.NewCard(Section.NEW)))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New card").assertExists()

        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.EditCard(7, Section.NEW)))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit card").assertExists()
    }

    // --- обновление экрана ---------------------------------------------------------------------

    @Test
    fun editorRefreshesFullyWithPenPaused() {
        val penWhenRefreshing = mutableListOf<Boolean>()
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts), refreshScreen = { penWhenRefreshing += sheets.canvases.last().isPenEnabled })

        // Редактор — первый экран после запуска, поэтому полное обновление при открытии есть; перо в этот момент
        // выключено и потом включено снова.
        assertEquals(listOf(false), penWhenRefreshing)
        assertTrue(sheets.canvases.last().isPenEnabled)
    }

    @Test
    fun flipIsNotCountedAsChange() {
        // Переворот записывается в колоду, поэтому нужен репозиторий, который умеет сохранять.
        val card = buildCard(id = 7, section = Section.NEW, position = 0)
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), card))
        val app = startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID)))
        assertEquals("первый экран после запуска", 1, fullRefreshes)

        composeRule.onNodeWithContentDescription("Show answer").performClick()
        composeRule.waitForIdle()
        // Переворот не смена: 29 смен экрана после него всё ещё не дают обновления, а 30-я даёт.
        navigateAround(app, REFRESH_PERIOD - 1, AppScreen.DeckName)
        assertEquals("переворот не считается", 1, fullRefreshes)

        app.navigate(AppScreen.DeckName)
        composeRule.waitForIdle()
        assertEquals("30-я смена экрана", 2, fullRefreshes)
    }

    @Test
    fun startupScreenRefreshesFullyWhenItAppears() {
        startApp(buildData(), hasFileAccess = false)

        assertEquals(1, fullRefreshes)
    }

    @Test
    fun deckListIsRefreshedFirstAndNewDeckFormOnlyCounted() {
        val app = startApp(buildData())
        assertEquals("список колод — первый экран после запуска", 1, fullRefreshes)

        app.navigate(AppScreen.DeckName)
        composeRule.waitForIdle()

        assertEquals("форма новой колоды — обычная смена, её обновляет прошивка", 1, fullRefreshes)
    }

    @Test
    fun loadingScreenIsDrawnByDesign() {
        val probe = ProbeDesign()
        startApp(buildData(), design = probe, openGate = CompletableDeferred())

        assertTrue("экран загрузки рисует дизайн", probe.loadingShown)
    }

    @Test
    fun loadingScreenShowsBrandOfDesign() {
        startApp(buildData(), openGate = CompletableDeferred())

        composeRule.onNodeWithText("Deckly").assertExists()
    }

    @Test
    fun loadingScreenIsNotRefreshed() {
        startApp(buildData(), openGate = CompletableDeferred())

        assertEquals(0, fullRefreshes)
    }

    @Test
    fun subtreeIsRecreatedOnEveryTransition() {
        val probe = ProbeDesign()
        val app = startApp(buildData(), design = probe)
        assertEquals(1, probe.listMarkers.size)

        // Переход на тот же экран даёт новое поддерево: ничего из прежнего не просвечивает.
        app.navigate(AppScreen.DeckList)
        composeRule.waitForIdle()

        assertEquals(2, probe.listMarkers.size)
    }

    @Test
    fun nextCardIsCountedAsChange() {
        val cards = (0 until REFRESH_PERIOD + 1).map { buildCard(id = 100L + it, section = Section.NEW, position = it) }
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), cards))
        startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID)))
        assertEquals("первый экран после запуска", 1, fullRefreshes)

        repeat(REFRESH_PERIOD - 1) { clickNextCard() }
        assertEquals("29 смен карточки", 1, fullRefreshes)

        clickNextCard()
        assertEquals("30-я смена карточки", 2, fullRefreshes)
    }

    @Test
    fun screenCardAndEditorSideChangesAreCountedTogether() {
        val cards = (0 until 12).map { buildCard(id = 100L + it, section = Section.NEW, position = it) }
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), cards))
        val drafts = FakeDraftRepository()
        val app = startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID), drafts = drafts))
        assertEquals(1, fullRefreshes)

        repeat(10) { clickNextCard() }
        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.NewCard(Section.NEW)))
        composeRule.waitForIdle()
        repeat(9) { clickSide(if (it % 2 == 0) "Answer" else "Question") }
        // 10 карточек, вход в редактор и 9 смен стороны — 20 смен; ещё 9 смен экрана — 29.
        navigateAround(app, 9, AppScreen.DeckName)
        assertEquals("29 смен вперемешку", 1, fullRefreshes)

        app.navigate(AppScreen.DeckName)
        composeRule.waitForIdle()
        assertEquals("30-я смена", 2, fullRefreshes)
    }

    @Test
    fun thirtyFirstScreenGivesSecondRefresh() {
        val app = startApp(buildData(appState = FakeAppStateRepository(DECK_ID)))
        assertEquals("первый экран", 1, fullRefreshes)

        navigateAround(app, REFRESH_PERIOD - 1)
        assertEquals("30-й экран подряд", 1, fullRefreshes)

        app.navigate(AppScreen.DeckList)
        composeRule.waitForIdle()
        assertEquals("31-й экран", 2, fullRefreshes)
    }

    @Test
    fun recompositionOfRootWithSameActivityKeepsRefreshCounter() {
        val parentTick = mutableIntStateOf(0)
        val app = startApp(buildData(appState = FakeAppStateRepository(DECK_ID)), parentTick = parentTick)
        assertEquals(1, fullRefreshes)

        // Родитель корня перекомпонован и заново передал те же, равные по значению ссылки на методы activity.
        repeat(3) {
            parentTick.intValue++
            composeRule.waitForIdle()
        }
        app.navigate(AppScreen.DeckList)
        composeRule.waitForIdle()

        // Счётчик не сброшен: иначе первый ключ после сброса снова был бы должным.
        assertEquals(1, fullRefreshes)
    }

    @Test
    fun editorSideSwitchKeepsPenCycleWithoutFullRefresh() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))
        assertEquals("редактор — первый экран", 1, fullRefreshes)
        val canvasesBefore = sheets.canvases.size
        val revisionBefore = sheets.canvases.last().uiRevision

        clickSide("Answer")

        // Смена стороны не 30-я: полного обновления нет, но цикл пера остался (лист получил новую ревизию), и перо
        // нигде не выключено: паузу на время обновления делает только полное обновление.
        assertEquals(1, fullRefreshes)
        assertEquals(revisionBefore + 1, sheets.canvases.last().uiRevision)
        assertTrue(sheets.canvases.drop(canvasesBefore).all { it.isPenEnabled })
    }

    @Test
    fun editorSideSwitchRefreshesFullyOnThirtiethChangeWithPenPaused() {
        val penWhenRefreshing = mutableListOf<Boolean>()
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts), refreshScreen = { penWhenRefreshing += sheets.canvases.last().isPenEnabled })
        assertEquals(1, fullRefreshes)

        repeat(REFRESH_PERIOD - 1) { clickSide(if (it % 2 == 0) "Answer" else "Question") }
        assertEquals("29 смен стороны", 1, fullRefreshes)
        // 29 кликов закончились на «Ответ»: 30-й возвращает «Вопрос» (клик по выбранной вкладке сторону не меняет).
        clickSide("Question")

        // 30-я смена: полное обновление, и перо на это время выключено; потом включено снова.
        assertEquals(2, fullRefreshes)
        assertEquals(listOf(false, false), penWhenRefreshing)
        assertTrue(sheets.canvases.last().isPenEnabled)
    }

    @Test
    fun deckOpensRequestedSectionAndFocusedCard() {
        // Эпоха II не активная (идёт I), поэтому открывается просмотр карточек, а не экран перехода.
        val first = buildCard(id = 7, section = Section.EPOCH_2, position = 0)
        val second = buildCard(id = 8, section = Section.EPOCH_2, position = 1)
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), first, second))
        val app = startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID)))

        // Возврат из редактора: раздел и карточка из AppScreen доходят до ViewModel колоды.
        app.navigate(AppScreen.Deck(DECK_ID, openSection = Section.EPOCH_2, focusCardId = 8))
        composeRule.waitForIdle()

        assertEquals(FakeCardContentRepository.buildContent(8).front.strokes, sheets.deckSides.last().strokes)
        composeRule.onNode(hasContentDescription("II, 2 cards")).assertIsSelected()
    }

    // --- вид и клавиатура ----------------------------------------------------------------------

    @Test
    fun treeIsQuietWithoutIndicationAndOverscroll() {
        val probe = ProbeDesign()
        startApp(buildData(), hasFileAccess = false, design = probe)

        assertSame(NoIndication, probe.indication)
        assertEquals(null, probe.overscroll)
    }

    @Test
    fun keyboardLiftsTheWholeScreen() {
        startApp(buildData())
        val footerBottom = composeRule.onNodeWithText("New deck").getUnclippedBoundsInRoot().bottom.value

        showKeyboard(heightPx = 420)

        // Нижняя строка поднялась ровно на высоту клавиатуры: поле имени и холст не остаются под ней.
        val lifted = composeRule.onNodeWithText("New deck").getUnclippedBoundsInRoot().bottom.value
        assertEquals(footerBottom - 420f, lifted, 1f)
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun startApp(
        data: DecklyData,
        hasFileAccess: Boolean = true,
        openFailure: Exception? = null,
        openGate: CompletableDeferred<Unit>? = null,
        design: Design = DesignE,
        refreshScreen: (() -> Unit)? = null,
        parentTick: IntState? = null,
    ): AppViewModel {
        val container = AppContainer(
            application,
            hasFileAccess = { hasFileAccess },
            openStorage = { _, _ ->
                openGate?.await()
                openFailure?.let { throw it }
                data
            },
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
        val app = AppViewModel(container.startupLoader)
        composeRule.setContent {
            // Чтение состояния перекомпонует родителя корня, и ссылки на методы activity вычисляются заново, как в MainActivity.
            parentTick?.intValue
            AppRoot(
                app = app,
                container = container,
                onOpenSettings = ::openSettings,
                onClose = ::closeApp,
                design = design,
                sheets = sheetsInUse,
                refreshScreen = { fullRefreshes++; refreshScreen?.invoke() },
            )
        }
        composeRule.waitForIdle()
        return app
    }

    // Как `::openFileAccessSettings` и `::finishAndRemoveTask` в MainActivity: ссылки на методы одного владельца.
    private fun openSettings() {
        settingsOpened++
    }

    private fun closeApp() {
        closed++
    }

    // Каждый переход — смена экрана: после обновления до следующего должного не хватает REFRESH_PERIOD - 1 смен.
    // Экран по умолчанию — список колод; репозиторию без `listDecks` (одна колода) нужна форма новой колоды.
    private fun navigateAround(app: AppViewModel, changes: Int, screen: AppScreen = AppScreen.DeckList) {
        repeat(changes) {
            app.navigate(screen)
            composeRule.waitForIdle()
        }
    }

    private fun clickNextCard() {
        composeRule.onNodeWithContentDescription("Next card").performClick()
        composeRule.waitForIdle()
    }

    private fun clickSide(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    private fun buildCatalogWithCard(): FakeDeckCatalog =
        FakeDeckCatalog(listOf(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), buildCard(id = 7, section = Section.NEW, position = 0))))

    private fun showKeyboard(heightPx: Int) {
        composeRule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, heightPx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView, insets)
        }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.waitForIdle()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun buildData(
        decks: DeckRepository = FakeDeckCatalog(listOf(buildDeckState(buildDeck(DECK_ID, "Немецкий A2")))),
        appState: AppStateRepository = FakeAppStateRepository(),
        drafts: DraftRepository = FakeDraftRepository(),
        contents: CardContentRepository = FakeCardContentRepository(),
    ): DecklyData = buildFakeData(
        context = application,
        rootDir = folder.newFolder(),
        appState = appState,
        drafts = drafts,
        decks = decks,
        contents = contents,
    )

    // Читает колоду и считает чтения: возврат на равный экран должен читать заново.
    private class CountingCatalog(private val catalog: FakeDeckCatalog) : DeckRepository by catalog {
        var deckLoads = 0
            private set

        override suspend fun loadDeckState(deckId: Long) = catalog.loadDeckState(deckId).also { deckLoads++ }
    }

    // Листы вместо настоящих: записывают, что им передали. Настоящие (CardSideImage, DrawingSheet) проверяет ink.
    private class RecordingSheets : SheetSlots {
        val deckSides = mutableListOf<CardSide>()
        val canvases = mutableListOf<CanvasState>()
        lateinit var onStroke: (Stroke) -> Unit
        lateinit var onErase: (List<StrokePoint>, Float) -> Unit
        lateinit var onShapeTap: (Int) -> Unit

        @Composable
        override fun DeckSheet(side: CardSide, modifier: Modifier) {
            SideEffect { deckSides += side }
            Box(modifier)
        }

        @Composable
        override fun EditorSheet(
            canvas: CanvasState,
            onStroke: (Stroke) -> Unit,
            onErase: (List<StrokePoint>, Float) -> Unit,
            onShapeTap: (Int) -> Unit,
            modifier: Modifier,
        ) {
            SideEffect {
                canvases += canvas
                this.onStroke = onStroke
                this.onErase = onErase
                this.onShapeTap = onShapeTap
            }
            Box(modifier)
        }

        @Composable
        override fun ShapeGlyph(kind: ShapeKind, outlineDesignUnits: Int, modifier: Modifier) {
            Box(modifier)
        }
    }

    // Дизайн, который ничего не рисует, а запоминает, что видит дерево над экраном.
    private class ProbeDesign : Design {
        var loadingShown = false
        var indication: Any? = null
        var overscroll: Any? = null
        var storeOwnerStore: Any? = null
        val listMarkers = mutableListOf<Any>()
        val editorStates = mutableListOf<EditorScreenState>()

        @Composable
        override fun LoadingScreen() {
            SideEffect { loadingShown = true }
        }

        @Composable
        override fun StartupScreen(state: StartupState, onAction: (StartupAction) -> Unit) {
            indication = LocalIndication.current
            overscroll = LocalOverscrollFactory.current
            storeOwnerStore = LocalViewModelStoreOwner.current?.viewModelStore
        }

        @Composable
        override fun DeckScreen(state: DeckScreenState, onAction: (DeckAction) -> Unit, sheet: @Composable (Modifier) -> Unit) = Unit

        @Composable
        override fun EditorScreen(
            state: EditorScreenState,
            onAction: (EditorAction) -> Unit,
            sheet: @Composable (Modifier) -> Unit,
            shapeGlyph: @Composable (ShapeKind, Int, Modifier) -> Unit,
        ) {
            SideEffect { editorStates += state }
        }

        @Composable
        override fun DeckListScreen(state: DeckListState, onAction: (DeckListAction) -> Unit) {
            val marker = remember { Any() }
            SideEffect { if (marker !in listMarkers) listMarkers += marker }
        }

        @Composable
        override fun DeckNameScreen(state: DeckNameState, onAction: (DeckNameAction) -> Unit) = Unit
    }

    private fun buildContentsWithShapes(cardId: Long) = FakeCardContentRepository(
        mapOf(cardId to CardContent(cardId, CardSide(listOf(FRONT_STROKE), listOf(FRONT_SHAPE)), CardSide(shapes = listOf(BACK_SHAPE)))),
    )

    private companion object {
        const val DECK_ID = 1L
        val FRONT_STROKE = Stroke(6f, listOf(StrokePoint(1f, 1f, 1f)))
        val FRONT_SHAPE = Shape(Shape.Kind.STAR, 100f, 100f, 300f, 300f, 0f, 3f)
        val BACK_SHAPE = Shape(Shape.Kind.HEART, 400f, 400f, 600f, 600f, 0f, 3f)

        // Период полного обновления из ТЗ: первый экран после запуска и затем каждая 30-я смена.
        const val REFRESH_PERIOD = 30
    }
}
