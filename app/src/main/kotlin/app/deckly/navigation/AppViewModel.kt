package app.deckly.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.data.DecklyData
import app.deckly.startup.StartupLoader
import app.deckly.ui.contract.StartupState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Текущий экран приложения. Запуск начинается сам; события жизненного цикла activity передаёт
 * [handleLifecycleEvent].
 */
class AppViewModel(private val loader: StartupLoader) : ViewModel() {
    private val mutableScreen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = mutableScreen.asStateFlow()

    private var data: DecklyData? = null
    private var loadJob: Job? = null

    init {
        startLoading()
    }

    fun navigate(to: AppScreen) {
        mutableScreen.value = to
    }

    /**
     * `ON_RESUME` на экране запроса доступа повторяет запуск: владелец мог выдать доступ в настройках.
     * Повреждённую базу повтор не лечит, поэтому там запуск не повторяется. `ON_STOP` сообщает MediaStore об
     * изменениях, чтобы компьютер по MTP видел свежие размеры файлов.
     */
    fun handleLifecycleEvent(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME ->
                if (screen.value == AppScreen.Startup(StartupState.NEEDS_FILE_ACCESS)) startLoading()
            Lifecycle.Event.ON_STOP -> data?.mediaStore?.notifyChanged()
            else -> Unit
        }
    }

    // Пока загрузка идёт, повторная не начинается: быстрые возвраты из настроек открыли бы хранение дважды.
    private fun startLoading() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val result = loader.loadStart()
            result.data?.let { data = it }
            mutableScreen.value = result.screen
        }
    }
}
