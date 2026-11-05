package app.deckly.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import app.deckly.domain.model.Section
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

        composeRule.onNodeWithText("Новая карточка").assertExists()
        composeRule.onNodeWithText("Сохранить").assertExists()
    }

    @Test
    fun startWithCurrentDeckOpensDeck() {
        startApp(buildData(appState = FakeAppStateRepository(DECK_ID)))

        composeRule.onNodeWithText("Немецкий A2").assertExists()
    }

    @Test
    fun startWithoutCurrentDeckOpensDeckList() {
        startApp(buildData())

        composeRule.onNodeWithText("Колоды").assertExists()
        composeRule.onNodeWithText("Немецкий A2").assertExists()
    }

    @Test
    fun loadingScreenShowsNothing() {
        val gate = CompletableDeferred<Unit>()
        startApp(buildData(), openGate = gate)

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(0)
        gate.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Колоды").assertExists()
    }

    @Test
    fun accessRequestOffersSettings() {
        startApp(buildData(), hasFileAccess = false)

        composeRule.onNodeWithText("Нужен доступ к файлам").assertExists()
        composeRule.onNodeWithText("Открыть настройки").performClick()

        assertEquals(1, settingsOpened)
        assertEquals(0, closed)
    }

    @Test
    fun damagedDatabaseOffersClose() {
        startApp(buildData(), openFailure = DatabaseDamagedException(File("deckly.db"), "повреждена"))

        composeRule.onNodeWithText("База повреждена").assertExists()
        composeRule.onNodeWithText("Закрыть").performClick()

        assertEquals(1, closed)
        assertEquals(0, settingsOpened)
    }

    // --- системная «Назад» ---------------------------------------------------------------------

    @Test
    fun backInEditorOpensSaveDialogAndSecondBackDismissesIt() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))

        pressBack()

        composeRule.onNodeWithText("Сохранить и выйти?").assertExists()
        assertFalse(composeRule.activity.isFinishing)

        pressBack()

        composeRule.onNodeWithText("Сохранить и выйти?").assertDoesNotExist()
        composeRule.onNodeWithText("Новая карточка").assertExists()
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
        composeRule.onNode(hasContentDescription("Действия с карточкой") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Удалить карточку").performClick()
        composeRule.onNodeWithText("Удалить карточку?").assertExists()

        pressBack()

        composeRule.onNodeWithText("Удалить карточку?").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий A2").assertExists()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backWithOpenCardMenuClosesMenuNotApp() {
        startApp(buildData(decks = buildCatalogWithCard(), appState = FakeAppStateRepository(DECK_ID)))
        composeRule.onNode(hasContentDescription("Действия с карточкой") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Удалить карточку").assertExists()

        pressBack()

        composeRule.onNodeWithText("Удалить карточку").assertDoesNotExist()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backInDeckListWithCurrentDeckReturnsToIt() {
        startApp(buildData(appState = FakeAppStateRepository(DECK_ID)))
        composeRule.onNodeWithContentDescription("Немецкий A2. Выбрать колоду").performClick()
        composeRule.onNodeWithText("Колоды").assertExists()

        pressBack()

        composeRule.onNodeWithContentDescription("Немецкий A2. Выбрать колоду").assertExists()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backInDeckListWithoutCurrentDeckLeavesApp() {
        startApp(buildData())
        composeRule.onNodeWithText("Колоды").assertExists()

        pressBack()

        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun backInNewDeckFormCancelsToDeckList() {
        startApp(buildData())
        composeRule.onNodeWithText("Новая колода").performClick()
        composeRule.onNodeWithText("Название колоды").assertExists()

        pressBack()

        composeRule.onNodeWithText("Название колоды").assertDoesNotExist()
        composeRule.onNodeWithText("Колоды").assertExists()
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
        drafts.stored = StoredDraft(DECK_ID, Draft(null, emptyList(), emptyList(), Side.BACK, Section.NEW))
        drafts.loadGate = CompletableDeferred()

        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.ResumeDraft))
        composeRule.waitForIdle()

        // Пока черновик читается: ни экрана дизайна, ни обновления, ни обработчика «Назад»; лист белый, как у загрузки.
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

        assertEquals(FakeCardContentRepository.buildContent(7).front, sheets.deckStrokes.last())
        // Описание листа ставит дизайн, слот его не затирает.
        composeRule.onNodeWithContentDescription("Рукописная карточка").assertExists()
    }

    @Test
    fun editorSlotGetsCanvasOfEditorAndKeepsDescription() {
        val stroke = Stroke(6f, listOf(StrokePoint(1f, 1f, 1f)))
        val draft = Draft(null, listOf(stroke), emptyList(), Side.FRONT, Section.NEW)
        startApp(buildData(drafts = FakeDraftRepository(StoredDraft(DECK_ID, draft))))

        assertEquals(listOf(stroke), sheets.canvases.last().strokes)
        composeRule.onNodeWithContentDescription("Холст. Рисование только пером").assertExists()
    }

    @Test
    fun strokeAndEraseFromCanvasReachEditor() {
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts))
        val stroke = Stroke(6f, listOf(StrokePoint(100f, 100f, 1f), StrokePoint(200f, 200f, 1f)))

        composeRule.runOnUiThread { sheets.onStroke(stroke) }
        composeRule.waitForIdle()

        assertEquals(listOf(stroke), sheets.canvases.last().strokes)
        assertEquals("штрих записан в черновик", listOf(stroke), drafts.stored?.draft?.front)
        composeRule.onNodeWithContentDescription("Отменить штрих").assertIsEnabled()

        composeRule.runOnUiThread { sheets.onErase(listOf(StrokePoint(150f, 150f, 1f)), 50f) }
        composeRule.waitForIdle()

        assertEquals(emptyList<Stroke>(), sheets.canvases.last().strokes)
    }

    @Test
    fun editorStartsMatchTheirScreens() {
        val card = buildCard(id = 7, section = Section.NEW, position = 0)
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), card))
        val app = startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID)))

        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.NewCard(Section.NEW)))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Новая карточка").assertExists()

        app.navigate(AppScreen.Editor(DECK_ID, EditorStart.EditCard(7, Section.NEW)))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Редактирование").assertExists()
    }

    // --- обновление экрана ---------------------------------------------------------------------

    @Test
    fun editorRefreshesFullyWithPenPaused() {
        val penWhenRefreshing = mutableListOf<Boolean>()
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts), refreshScreen = { penWhenRefreshing += sheets.canvases.last().isPenEnabled })

        // Одно полное обновление при открытии, перо в этот момент выключено и потом включено снова.
        assertEquals(listOf(false), penWhenRefreshing)
        assertTrue(sheets.canvases.last().isPenEnabled)
    }

    @Test
    fun deckRefreshesFullyOnTransitionAndPartiallyOnFlip() {
        // Переворот записывается в колоду, поэтому нужен репозиторий, который умеет сохранять.
        val card = buildCard(id = 7, section = Section.NEW, position = 0)
        val repository = FakeDeckRepository(buildDeckState(buildDeck(DECK_ID, "Немецкий A2"), card))
        val app = startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID)))
        assertEquals(1, fullRefreshes)

        composeRule.onNodeWithContentDescription("Показать ответ").performClick()
        composeRule.waitForIdle()
        assertEquals("переворот обновляется частично", 1, fullRefreshes)

        app.navigate(AppScreen.Deck(DECK_ID))
        composeRule.waitForIdle()
        assertEquals("переход на равный экран обновляется полностью", 2, fullRefreshes)
    }

    @Test
    fun startupScreenRefreshesFullyWhenItAppears() {
        startApp(buildData(), hasFileAccess = false)

        assertEquals(1, fullRefreshes)
    }

    @Test
    fun deckListAndNewDeckFormRefreshFullyWhenTheyAppear() {
        val app = startApp(buildData())
        assertEquals("список колод", 1, fullRefreshes)

        app.navigate(AppScreen.DeckName)
        composeRule.waitForIdle()

        assertEquals("форма новой колоды", 2, fullRefreshes)
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
    fun nextCardRefreshesFully() {
        val repository = FakeDeckRepository(
            buildDeckState(
                buildDeck(DECK_ID, "Немецкий A2"),
                buildCard(id = 7, section = Section.NEW, position = 0),
                buildCard(id = 8, section = Section.NEW, position = 1),
            ),
        )
        startApp(buildData(decks = repository, appState = FakeAppStateRepository(DECK_ID)))
        assertEquals(1, fullRefreshes)

        composeRule.onNodeWithContentDescription("Следующая карточка").performClick()
        composeRule.waitForIdle()

        assertEquals("другая карточка обновляется полностью", 2, fullRefreshes)
    }

    @Test
    fun editorSideSwitchRefreshesFullyWithPenPaused() {
        val penWhenRefreshing = mutableListOf<Boolean>()
        val drafts = FakeDraftRepository(StoredDraft(DECK_ID, buildEmptyDraft()))
        startApp(buildData(drafts = drafts), refreshScreen = { penWhenRefreshing += sheets.canvases.last().isPenEnabled })
        assertEquals(1, fullRefreshes)

        composeRule.onNodeWithText("Ответ").performClick()
        composeRule.waitForIdle()

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

        assertEquals(FakeCardContentRepository.buildContent(8).front, sheets.deckStrokes.last())
        composeRule.onNode(hasContentDescription("II, 2 карточки")).assertIsSelected()
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
        val footerBottom = composeRule.onNodeWithText("Новая колода").getUnclippedBoundsInRoot().bottom.value

        showKeyboard(heightPx = 420)

        // Нижняя строка поднялась ровно на высоту клавиатуры: поле имени и холст не остаются под ней.
        val lifted = composeRule.onNodeWithText("Новая колода").getUnclippedBoundsInRoot().bottom.value
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
            AppRoot(
                app = app,
                container = container,
                onOpenSettings = { settingsOpened++ },
                onClose = { closed++ },
                design = design,
                sheets = sheetsInUse,
                refreshScreen = { fullRefreshes++; refreshScreen?.invoke() },
            )
        }
        composeRule.waitForIdle()
        return app
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
        val deckStrokes = mutableListOf<List<Stroke>>()
        val canvases = mutableListOf<CanvasState>()
        lateinit var onStroke: (Stroke) -> Unit
        lateinit var onErase: (List<StrokePoint>, Float) -> Unit

        @Composable
        override fun DeckSheet(strokes: List<Stroke>, modifier: Modifier) {
            SideEffect { deckStrokes += strokes }
            Box(modifier)
        }

        @Composable
        override fun EditorSheet(
            canvas: CanvasState,
            onStroke: (Stroke) -> Unit,
            onErase: (List<StrokePoint>, Float) -> Unit,
            modifier: Modifier,
        ) {
            SideEffect {
                canvases += canvas
                this.onStroke = onStroke
                this.onErase = onErase
            }
            Box(modifier)
        }
    }

    // Дизайн, который ничего не рисует, а запоминает, что видит дерево над экраном.
    private class ProbeDesign : Design {
        var indication: Any? = null
        var overscroll: Any? = null
        var storeOwnerStore: Any? = null
        val listMarkers = mutableListOf<Any>()
        val editorStates = mutableListOf<EditorScreenState>()

        @Composable
        override fun StartupScreen(state: StartupState, onAction: (StartupAction) -> Unit) {
            indication = LocalIndication.current
            overscroll = LocalOverscrollFactory.current
            storeOwnerStore = LocalViewModelStoreOwner.current?.viewModelStore
        }

        @Composable
        override fun DeckScreen(state: DeckScreenState, onAction: (DeckAction) -> Unit, sheet: @Composable (Modifier) -> Unit) = Unit

        @Composable
        override fun EditorScreen(state: EditorScreenState, onAction: (EditorAction) -> Unit, sheet: @Composable (Modifier) -> Unit) {
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

    private companion object {
        const val DECK_ID = 1L
    }
}
