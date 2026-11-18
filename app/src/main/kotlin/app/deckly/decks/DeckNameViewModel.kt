package app.deckly.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.SerialWriter
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.navigation.AppScreen
import app.deckly.navigation.DeckToRename
import app.deckly.ui.contract.DeckNameAction
import app.deckly.ui.contract.DeckNameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Форма названия колоды: новой или, если задана [rename], существующей. Набранное название попадает в состояние сразу, без
 * очереди: от него зависят `canSave`, «Создать» и «Готово» на клавиатуре. Поле ввода держит свой буфер и состоянием не
 * перезаписывается, иначе отставание теряло бы символы. Запись идёт в корутине, но уже с названием на момент нажатия.
 * Переименование возвращает в список колод и текущей колоду не делает.
 */
class DeckNameViewModel(
    private val decks: DeckRepository,
    private val appState: AppStateRepository,
    private val writer: SerialWriter,
    private val navigate: (AppScreen) -> Unit,
    private val rename: DeckToRename? = null,
) : ViewModel() {
    private val isRename = rename != null
    private val mutableState = MutableStateFlow(
        DeckNameState(name = rename?.name.orEmpty(), canSave = rename?.name?.isNotBlank() == true, isRename = isRename),
    )
    val state: StateFlow<DeckNameState> = mutableState.asStateFlow()

    // После «Создать» или «Отмена» форма больше ничего не принимает: вторая колода с тем же названием не нужна.
    private var isLeaving = false

    fun onAction(action: DeckNameAction) {
        if (isLeaving) return
        when (action) {
            is DeckNameAction.ChangeName -> mutableState.value = DeckNameState(action.text, canSave = action.text.isNotBlank(), isRename = isRename)
            DeckNameAction.Save -> if (mutableState.value.canSave) {
                val name = mutableState.value.name.trim()
                if (rename == null) saveAndLeave(name) else renameAndLeave(rename, name)
            }
            DeckNameAction.Cancel -> {
                isLeaving = true
                navigate(AppScreen.DeckList)
            }
        }
    }

    // То же название, что было, не пишем: возврат в список и всё.
    private fun renameAndLeave(target: DeckToRename, name: String) {
        isLeaving = true
        viewModelScope.launch {
            if (name != target.name) writer.write { decks.renameDeck(target.id, name) }
            navigate(AppScreen.DeckList)
        }
    }

    // Колода и пометка «текущая» пишутся одним блоком, чтобы между ними не вклинилась чужая запись.
    private fun saveAndLeave(name: String) {
        isLeaving = true
        viewModelScope.launch {
            val deck = writer.write { decks.createDeck(name).also { appState.saveCurrentDeckId(it.id) } }
            navigate(AppScreen.Deck(deck.id))
        }
    }
}
