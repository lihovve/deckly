package app.deckly.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
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
 * Показанный экран вместе с хранилищем его ViewModel. Каждый переход даёт новую запись, даже на экран, равный
 * прежнему: ключ — сам факт перехода, а не значение [screen], поэтому запись сравнивается по ссылке. ViewModel
 * экрана читают данные один раз в `init`, и экран, на который вернулись (из редактора в ту же колоду), обязан
 * прочитать их заново. [token] растёт с каждым переходом.
 */
class ScreenEntry internal constructor(val screen: AppScreen, val token: Long) {
    val viewModelStore = ViewModelStore()
}

/**
 * Текущий экран приложения. Запуск начинается сам; события жизненного цикла activity передаёт
 * [handleLifecycleEvent]. Хранилище ViewModel текущего экрана держит эта модель, поэтому оно переживает смену
 * конфигурации; при переходе старое очищается, при очистке модели очищается и текущее.
 */
class AppViewModel(private val loader: StartupLoader) : ViewModel() {
    private var nextToken = 0L
    private val mutableEntry = MutableStateFlow(ScreenEntry(AppScreen.Loading, nextToken++))
    private val mutableScreen = MutableStateFlow<AppScreen>(AppScreen.Loading)

    /** Экран вместе с хранилищем его ViewModel; обновляется при каждом переходе. */
    val entry: StateFlow<ScreenEntry> = mutableEntry.asStateFlow()
    val screen: StateFlow<AppScreen> = mutableScreen.asStateFlow()

    private var data: DecklyData? = null
    private var loadJob: Job? = null

    init {
        startLoading()
    }

    fun navigate(to: AppScreen) {
        show(to)
    }

    // Новая запись публикуется раньше, чем очищается старая: экран, ещё стоящий на прежней, не остаётся без хранилища.
    private fun show(to: AppScreen) {
        val previous = mutableEntry.value
        mutableEntry.value = ScreenEntry(to, nextToken++)
        mutableScreen.value = to
        previous.viewModelStore.clear()
    }

    override fun onCleared() {
        mutableEntry.value.viewModelStore.clear()
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
            show(result.screen)
        }
    }
}
