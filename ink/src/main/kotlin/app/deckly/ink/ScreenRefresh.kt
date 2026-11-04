package app.deckly.ink

import android.graphics.Rect
import android.util.Log
import android.view.View
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode

/**
 * Обновление экрана e-ink через `EpdController`. Без Onyx SDK ничего не делает; сбой вызова SDK не
 * роняет приложение: экран обновится системой, а вызов повторится при следующей смене экрана.
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
     * Частичное обновление прямоугольника [rect] листа [view] быстрым чёрно-белым режимом. Аргументы
     * `refreshScreenRegion`: левый край, верх, ширина, высота.
     */
    internal fun refreshRegion(view: View, rect: Rect) {
        if (!OnyxSdk.isPenAvailable) return
        try {
            EpdController.refreshScreenRegion(
                view,
                rect.left,
                rect.top,
                rect.width(),
                rect.height(),
                UpdateMode.ANIMATION_MONO,
            )
        } catch (error: Throwable) {
            Log.w(TAG, "Частичное обновление экрана не удалось", error)
        }
    }

    private const val TAG = "ScreenRefresh"
}
