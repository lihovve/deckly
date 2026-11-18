package app.deckly.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.SerialWriter
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckSummary
import app.deckly.navigation.AppScreen
import app.deckly.navigation.DeckToRename
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckRow
import app.deckly.ui.contract.DeleteDeckDialog
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
 * Список колод: показывает колоды, открывает их, ведёт к форме новой колоды и к форме переименования, спрашивает про
 * удаление и удаляет. Экспорт, импорт и страницы — отдельная работа. Меню строки открыто или нет, знает только дизайн;
 * окно удаления приходит состоянием.
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
    private var rows: List<DeckRow> = emptyList()
    private var deleteDialog: DeleteDeckDialog? = null

    init {
        viewModelScope.launch {
            reload()
            for (action in actions) handle(action)
        }
    }

    fun onAction(action: DeckListAction) {
        actions.trySend(action)
    }

    private suspend fun handle(action: DeckListAction) {
        when (action) {
            is DeckListAction.OpenDeck -> openDeck(action.id)
            DeckListAction.CreateDeck -> navigate(AppScreen.DeckName())
            DeckListAction.Back -> currentDeckId?.let { navigate(AppScreen.Deck(it)) }
            // Устаревшую строку (колоды уже нет) молча теряем, как и при открытии.
            is DeckListAction.RenameDeck -> findRow(action.id)?.let { navigate(AppScreen.DeckName(DeckToRename(it.id, it.name))) }
            is DeckListAction.AskDeleteDeck -> findRow(action.id)?.let { row ->
                deleteDialog = DeleteDeckDialog(row.id, row.name, row.cardCount)
                publish()
            }
            DeckListAction.ConfirmDeleteDeck -> deleteAskedDeck()
            DeckListAction.DismissDeleteDialog -> {
                deleteDialog = null
                publish()
            }
        }
    }

    // Список читается из хранилища целиком: после удаления «текущей» может не быть, и «Назад» пропадает.
    private suspend fun reload() {
        val summaries = decks.listDecks()
        currentDeckId = appState.loadCurrentDeckId()?.takeIf { id -> summaries.any { it.id == id } }
        rows = summaries.map { buildDeckRow(it, isCurrent = it.id == currentDeckId) }
        publish()
    }

    private fun publish() {
        mutableState.value = DeckListState(rows, canGoBack = currentDeckId != null, deleteDialog = deleteDialog)
    }

    private fun findRow(id: Long): DeckRow? = rows.firstOrNull { it.id == id }

    // Окно закрывается сразу, а удаление идёт через писателя: повторное подтверждение, пока оно идёт, окна уже не находит.
    private suspend fun deleteAskedDeck() {
        val asked = deleteDialog ?: return
        deleteDialog = null
        publish()
        writer.write { decks.deleteDeck(asked.id) }
        reload()
    }

    // Колоды, которой нет в списке, не открываем: строка могла устареть.
    private suspend fun openDeck(id: Long) {
        if (findRow(id) == null) return
        writer.write { appState.saveCurrentDeckId(id) }
        navigate(AppScreen.Deck(id))
    }
}
