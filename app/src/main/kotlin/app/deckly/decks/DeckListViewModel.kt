package app.deckly.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.SerialWriter
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckSummary
import app.deckly.navigation.AppScreen
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckRow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Строка списка из сводки колоды. Эпоха с ▶ — текущая эпоха колоды; у пустой колоды и у полностью выученной её нет:
 * проходить нечего. Пустая колода не считается выученной.
 */
fun buildDeckRow(summary: DeckSummary, isCurrent: Boolean): DeckRow {
    val isAllLearned = summary.cardCount > 0 && summary.learnedCount == summary.cardCount
    return DeckRow(
        id = summary.id,
        name = summary.name,
        cardCount = summary.cardCount,
        activeEpoch = if (summary.cardCount == 0 || isAllLearned) null else summary.currentEpoch,
        isAllLearned = isAllLearned,
        isCurrent = isCurrent,
    )
}

/**
 * Список колод: только показывает колоды, открывает их и ведёт к форме новой колоды. Переименование, удаление,
 * экспорт, импорт и страницы — план 5.
 */
class DeckListViewModel(
    private val decks: DeckRepository,
    private val appState: AppStateRepository,
    private val writer: SerialWriter,
    private val navigate: (AppScreen) -> Unit,
) : ViewModel() {
    private val actions = Channel<DeckListAction>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<DeckListState?>(null)

    /** `null`, пока колоды загружаются. */
    val state: StateFlow<DeckListState?> = mutableState.asStateFlow()

    // Текущая колода из хранилища, если она есть в списке: к ней ведёт «Назад».
    private var currentDeckId: Long? = null

    init {
        viewModelScope.launch {
            val summaries = decks.listDecks()
            currentDeckId = appState.loadCurrentDeckId()?.takeIf { id -> summaries.any { it.id == id } }
            val rows = summaries.map { buildDeckRow(it, isCurrent = it.id == currentDeckId) }
            mutableState.value = DeckListState(rows, canGoBack = currentDeckId != null)
            for (action in actions) handle(action)
        }
    }

    fun onAction(action: DeckListAction) {
        actions.trySend(action)
    }

    private suspend fun handle(action: DeckListAction) {
        when (action) {
            is DeckListAction.OpenDeck -> openDeck(action.id)
            DeckListAction.CreateDeck -> navigate(AppScreen.DeckName)
            DeckListAction.Back -> currentDeckId?.let { navigate(AppScreen.Deck(it)) }
        }
    }

    // Колоды, которой нет в списке, не открываем: строка могла устареть.
    private suspend fun openDeck(id: Long) {
        if (mutableState.value?.decks?.none { it.id == id } != false) return
        writer.write { appState.saveCurrentDeckId(id) }
        navigate(AppScreen.Deck(id))
    }
}
