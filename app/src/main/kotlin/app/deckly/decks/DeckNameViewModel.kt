package app.deckly.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.SerialWriter
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.navigation.AppScreen
import app.deckly.ui.contract.DeckNameAction
import app.deckly.ui.contract.DeckNameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Форма новой колоды. Набранное название попадает в состояние сразу, без очереди: от него зависят `canSave`,
 * «Создать» и «Готово» на клавиатуре. Поле ввода держит свой буфер и состоянием не перезаписывается, иначе
 * отставание теряло бы символы. Запись идёт в корутине, но уже с названием на момент нажатия «Создать».
 */
class DeckNameViewModel(
    private val decks: DeckRepository,
    private val appState: AppStateRepository,
    private val writer: SerialWriter,
    private val navigate: (AppScreen) -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeckNameState(name = "", canSave = false))
    val state: StateFlow<DeckNameState> = mutableState.asStateFlow()

    // После «Создать» или «Отмена» форма больше ничего не принимает: вторая колода с тем же названием не нужна.
    private var isLeaving = false

    fun onAction(action: DeckNameAction) {
        if (isLeaving) return
        when (action) {
            is DeckNameAction.ChangeName -> mutableState.value = DeckNameState(action.text, canSave = action.text.isNotBlank())
            DeckNameAction.Save -> if (mutableState.value.canSave) saveAndLeave(mutableState.value.name.trim())
            DeckNameAction.Cancel -> {
                isLeaving = true
                navigate(AppScreen.DeckList)
            }
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
