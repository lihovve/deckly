package app.deckly

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.deckly.navigation.AppViewModel
import app.deckly.ui.AppRoot

/**
 * Единственная activity, только портрет. Экраны, переходы и «Назад» ведёт [AppRoot]; здесь то, что умеет только
 * activity: скрыть системные панели, открыть настройки, закрыть задачу и передать модели события жизненного цикла.
 */
open class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as DecklyApplication).container

    private val app: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel(container.startupLoader) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Возврат из настроек с выданным доступом повторяет запуск, остановка сообщает MediaStore об изменениях.
        lifecycle.addObserver(LifecycleEventObserver { _, event -> app.handleLifecycleEvent(event) })
        setContent {
            AppRoot(app = app, container = container, onOpenSettings = ::openFileAccessSettings, onClose = ::finishAndRemoveTask)
        }
        hideSystemBars()
    }

    // Трасса касаний для проверки на планшете (только отлаживаемая сборка): дошло ли касание до приложения.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        TouchTrace.logTouch(event)
        return super.dispatchTouchEvent(event)
    }

    // Панели могут вернуться после шторки или чужого окна: прячем их снова, когда окно получает фокус.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Перекрывается в тестах: на JVM панелей нет, и вызов проверяется по записи.
    protected open fun hideSystemBars() {
        applyHiddenSystemBars(window.insetsController)
    }

    // Если экрана доступа именно для приложения на прошивке нет, открывается общий список приложений.
    private fun openFileAccessSettings() {
        try {
            startActivity(buildFileAccessSettingsIntent(packageName))
        } catch (failure: ActivityNotFoundException) {
            startActivity(buildCommonFileAccessSettingsIntent())
        }
    }
}

/**
 * Экран кадра макета 992 × 1323 занимает весь экран: панели скрыты и выезжают только по свайпу от края.
 * Окно без контроллера (его ещё нет) пропускается.
 */
fun applyHiddenSystemBars(controller: WindowInsetsController?) {
    controller ?: return
    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsets.Type.systemBars())
}
