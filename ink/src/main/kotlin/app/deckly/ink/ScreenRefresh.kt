package app.deckly.ink

import android.util.Log
import android.view.View
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode

/**
 * Обновление экрана e-ink через `EpdController`. Без Onyx SDK ничего не делает; сбой вызова SDK не
 * роняет приложение: экран обновится системой, а вызов повторится при следующей смене экрана.
 *
 * В редакторе приложение само ничего не обновляет частично: на касании каждое обновление панели давало помеху в
 * координатах пера (ТЗ, «Обновление экрана»). Область перерисованного вида обновляет система, а режим этого обновления
 * задаёт [applyFastViewUpdates].
 *
 * Не проверено на планшете: на JVM `EpdController` не работает.
 */
object ScreenRefresh {
    /** Полное обновление с очисткой остаточного изображения. Правило, когда его звать, — в `app`. */
    fun refreshFullScreen() {
        if (!OnyxSdk.isPenAvailable) return
        try {
            EpdController.repaintEveryThing(UpdateMode.GC)
        } catch (error: Throwable) {
            Log.w(TAG, "Полное обновление экрана не удалось", error)
        }
    }

    /**
     * Режим `DU` для обновлений области [view]: чёрно-белая волна, меняет состояние только тех пикселей, что изменились, без
     * очищающего импульса. Прошивка берёт режим вида при каждом обновлении его области: `SurfaceView.lockCanvas` регистрирует
     * область с этим режимом, а `View.invalidate` передаёт его окну. По умолчанию у вида `UI_DEFAULT_MODE` (волну выбирает
     * драйвер). Значение лежит в самом виде, глобального состояния нет; вернёт его [resetViewUpdates].
     */
    fun applyFastViewUpdates(view: View) {
        callGuarded(view) { EpdController.setViewDefaultUpdateMode(view, UpdateMode.DU) }
    }

    /** Возвращает [view] режим обновления по умолчанию (`UI_DEFAULT_MODE`), каким он был до [applyFastViewUpdates]. */
    fun resetViewUpdates(view: View) {
        callGuarded(view) { EpdController.resetViewUpdateMode(view) }
    }

    // Общая защита двух вызовов: не Onyx — молча ничего; сбой SDK журналируется и не роняет.
    private fun callGuarded(view: View, call: () -> Unit) {
        if (!OnyxSdk.isPenAvailable) return
        try {
            call()
        } catch (error: Throwable) {
            Log.w(TAG, "Режим обновления вида ${view.javaClass.simpleName} не задан", error)
        }
    }

    private const val TAG = "ScreenRefresh"
}
