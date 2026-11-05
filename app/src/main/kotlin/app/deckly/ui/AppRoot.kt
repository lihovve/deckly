package app.deckly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.deckly.AppContainer
import app.deckly.data.timing.DecklyTiming
import app.deckly.deck.DeckViewModel
import app.deckly.deck.ShownCard
import app.deckly.decks.DeckListViewModel
import app.deckly.decks.DeckNameViewModel
import app.deckly.design.e.DesignE
import app.deckly.editor.EditorViewModel
import app.deckly.ink.ScreenRefresh
import app.deckly.navigation.AppScreen
import app.deckly.navigation.AppViewModel
import app.deckly.navigation.ScreenEntry
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckNameAction
import app.deckly.ui.contract.Design
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.StartupAction

/**
 * Корень приложения: по текущему экрану вызывает [design], подбирает ViewModel и вставляет листы в слоты.
 *
 * ViewModel экрана живёт ровно столько, сколько экран: хранилище берётся из [ScreenEntry] (его держит
 * [AppViewModel], поэтому оно переживает смену конфигурации) и отдаётся дереву через `LocalViewModelStoreOwner`.
 * Системная «Назад» — действие текущего экрана; где действия нет (колода без окна, список без текущей колоды,
 * экран запуска), обработчик не ставится, и срабатывает системное поведение.
 *
 * [sheets] и [refreshScreen] подменяются в тестах.
 */
@Composable
fun AppRoot(
    app: AppViewModel,
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    design: Design = DesignE,
    sheets: SheetSlots = InkSheetSlots,
    refreshScreen: () -> Unit = ScreenRefresh::refreshFullScreen,
) {
    val entry by app.entry.collectAsState()
    val environment = remember(app, container, design, sheets, refreshScreen, onOpenSettings, onClose) {
        ScreenEnvironment(app, container, design, sheets, ScreenRefresher(), refreshScreen, onOpenSettings, onClose)
    }
    val storeOwner = remember(entry) { EntryStoreOwner(entry.viewModelStore) }
    AppTheme {
        // Клавиатура поднимает весь экран: поле имени и холст не прячутся под ней (вместе с adjustResize в манифесте).
        Box(Modifier.fillMaxSize().background(Color.White).imePadding()) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                // Поддерево пересоздаётся на каждый переход: состояние прежней ViewModel не просвечивает в новом экране.
                key(entry.token) { ScreenHost(entry, environment) }
            }
        }
    }
}

/** Без ripple, подсветки и свечения на краю списка: e-ink их не показывает, а анимации запрещены ТЗ. */
@Composable
internal fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides NoIndication, LocalOverscrollFactory provides null, content = content)
}

internal object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

private class EntryStoreOwner(override val viewModelStore: ViewModelStore) : ViewModelStoreOwner

/**
 * ViewModel экрана из хранилища его записи, один раз на запись. `viewModel()` запрашивает модель при каждой
 * перекомпозиции, и после очистки хранилища при переходе создала бы новую, которую уже никто не очистит. Здесь
 * запрос идёт внутри `remember`; после смены конфигурации `remember` пересчитывается и возвращает ту же модель из
 * хранилища, пережившего поворот.
 */
@Composable
private inline fun <reified T : ViewModel> rememberScreenViewModel(noinline create: () -> T): T {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "Хранилище ViewModel экрана не задано" }
    return remember(owner) { ViewModelProvider.create(owner.viewModelStore, viewModelFactory { initializer { create() } })[T::class] }
}

private class ScreenEnvironment(
    val app: AppViewModel,
    val container: AppContainer,
    val design: Design,
    val sheets: SheetSlots,
    val refresher: ScreenRefresher,
    val refreshScreen: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
private fun ScreenHost(entry: ScreenEntry, environment: ScreenEnvironment) {
    when (val screen = entry.screen) {
        AppScreen.Loading -> Box(Modifier.fillMaxSize().background(Color.White))
        is AppScreen.Startup -> {
            RefreshEffect(environment, RefreshKey(entry.token, cardId = null, editorSide = null))
            environment.design.StartupScreen(screen.state) { action ->
                when (action) {
                    StartupAction.OpenSettings -> environment.onOpenSettings()
                    StartupAction.Close -> environment.onClose()
                }
            }
        }
        AppScreen.DeckList -> DeckListHost(entry, environment)
        AppScreen.DeckName -> DeckNameHost(entry, environment)
        is AppScreen.Deck -> DeckHost(entry, screen, environment)
        is AppScreen.Editor -> EditorHost(entry, screen, environment)
    }
}

@Composable
private fun DeckListHost(entry: ScreenEntry, environment: ScreenEnvironment) {
    val data = environment.container.requireData()
    val viewModel = rememberScreenViewModel {
        DeckListViewModel(data.decks, data.appState, environment.container.writer, environment.app::navigate)
    }
    val state by viewModel.state.collectAsState()
    state?.let { shown ->
        // «Назад» ведёт к текущей колоде; если её нет, остаётся системное поведение.
        BackHandler(enabled = shown.canGoBack) { viewModel.onAction(DeckListAction.Back) }
        RefreshEffect(environment, RefreshKey(entry.token, cardId = null, editorSide = null))
        environment.design.DeckListScreen(shown, viewModel::onAction)
    }
}

@Composable
private fun DeckNameHost(entry: ScreenEntry, environment: ScreenEnvironment) {
    val data = environment.container.requireData()
    val viewModel = rememberScreenViewModel {
        DeckNameViewModel(data.decks, data.appState, environment.container.writer, environment.app::navigate)
    }
    val state by viewModel.state.collectAsState()
    BackHandler { viewModel.onAction(DeckNameAction.Cancel) }
    RefreshEffect(environment, RefreshKey(entry.token, cardId = null, editorSide = null))
    environment.design.DeckNameScreen(state, viewModel::onAction)
}

@Composable
private fun DeckHost(entry: ScreenEntry, screen: AppScreen.Deck, environment: ScreenEnvironment) {
    val data = environment.container.requireData()
    val container = environment.container
    val viewModel = rememberScreenViewModel {
        DeckViewModel(
            deckId = screen.deckId,
            decks = data.decks,
            contents = data.contents,
            appState = data.appState,
            writer = container.writer,
            clock = container.clock,
            random = container.random,
            navigate = environment.app::navigate,
            initialSection = screen.openSection,
            initialFocusCardId = screen.focusCardId,
        )
    }
    val state by viewModel.state.collectAsState()
    val shownCard by viewModel.shownCard.collectAsState()
    state?.let { shown ->
        // Меню карточки закрывает сам дизайн; здесь только окно, а без него «Назад» — системная.
        BackHandler(enabled = shown.dialog != null) { viewModel.onAction(DeckAction.DismissDialog) }
        // Переворот карточки в ключ не входит: он обновляется частично.
        RefreshEffect(environment, RefreshKey(entry.token, cardId = shownCard?.cardId, editorSide = null))
        CardFrameTimingEffect(shownCard)
        environment.design.DeckScreen(shown, viewModel::onAction) { modifier ->
            val card = shownCard
            if (card != null) environment.sheets.DeckSheet(card.strokes, modifier) else Box(modifier)
        }
    }
}

@Composable
private fun EditorHost(entry: ScreenEntry, screen: AppScreen.Editor, environment: ScreenEnvironment) {
    val data = environment.container.requireData()
    val container = environment.container
    val viewModel = rememberScreenViewModel {
        EditorViewModel(
            deckId = screen.deckId,
            start = screen.start,
            decks = data.decks,
            contents = data.contents,
            drafts = data.drafts,
            writer = container.writer,
            clock = container.clock,
            random = container.random,
            nowMillis = System::currentTimeMillis,
            navigate = environment.app::navigate,
        )
    }
    val state by viewModel.state.collectAsState()
    val canvas by viewModel.canvas.collectAsState()
    // Пока идёт полное обновление, перо выключено: включённое, оно не даёт прошивке показать перерисовку.
    var isRefreshing by remember { mutableStateOf(false) }
    // Пока сессия не загружена (state == null), экран не рисуется: белый лист, как у загрузки, без «Назад» и обновления.
    state?.let { shown ->
        // С окном «Назад» закрывает окно, без него предлагает сохранить и выйти.
        BackHandler { viewModel.onAction(if (shown.dialog != null) EditorAction.DismissDialog else EditorAction.Back) }
        RefreshEffect(environment, RefreshKey(entry.token, cardId = null, editorSide = shown.side)) { isRefreshing = it }
        environment.design.EditorScreen(shown, viewModel::onAction) { modifier ->
            val shownCanvas = if (isRefreshing) canvas.copy(isPenEnabled = false) else canvas
            environment.sheets.EditorSheet(shownCanvas, viewModel::onStroke, viewModel::onErase, modifier)
        }
    }
}

// Лог для проверки на планшете: сколько от публикации карточки до собранного экрана и до следующего кадра.
// «Кадр» — следующий кадр после сборки, так что цифра длиннее настоящей не больше чем на один кадр.
@Composable
private fun CardFrameTimingEffect(card: ShownCard?) {
    LaunchedEffect(card) {
        if (card == null || !DecklyTiming.isEnabled) return@LaunchedEffect
        val composedNanos = System.nanoTime()
        withFrameNanos { }
        DecklyTiming.logInfo {
            "card id=${card.cardId} side=${card.side} publish→composed=${DecklyTiming.describeElapsed(card.publishedNanos, composedNanos)}ms " +
                "publish→frame=${DecklyTiming.describeElapsed(card.publishedNanos)}ms"
        }
    }
}

// Каждая смена ключа считается; когда обновление должное (первый экран и каждая 30-я смена), оно идёт после кадра с новым
// экраном. Ключ меняется — прежнее обновление отменяется и остаётся должным.
@Composable
private fun RefreshEffect(environment: ScreenEnvironment, key: RefreshKey, onPenPause: ((Boolean) -> Unit)? = null) {
    LaunchedEffect(key) {
        refreshScreenIfDue(environment.refresher, key, onPenPause, awaitFrame = { withFrameNanos { } }, refresh = environment.refreshScreen)
    }
}
