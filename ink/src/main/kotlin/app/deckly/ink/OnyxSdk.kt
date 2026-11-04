package app.deckly.ink

import android.app.Application
import android.os.Build
import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.rx.RxManager
import com.onyx.android.sdk.utils.ResManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Состояние Onyx SDK. Без инициализации перо SDK падает на первом штрихе (ТЗ, «Onyx SDK»), поэтому
 * [DrawingSheet] берёт сырое перо только при [isPenAvailable], иначе запасной `Canvas`.
 */
object OnyxSdk {
    /** Максимум нажима, если SDK его не отдал (в журнале прототипа нажим доходил до 2555 при 4096). */
    const val FALLBACK_MAX_TOUCH_PRESSURE = 4096f

    /** Устройство Onyx, и SDK поднялся без ошибок. */
    @Volatile
    var isPenAvailable: Boolean = false
        private set

    /** Сырой максимум нажима: нажим точки пера делится на него, чтобы получить 0..1. */
    @Volatile
    internal var maxTouchPressure: Float = FALLBACK_MAX_TOUCH_PRESSURE
        private set

    /**
     * Три вызова из ТЗ: скрытые API, `RxManager`, `ResManager`. Зовётся из `Application.onCreate`.
     * `false` на устройстве не Onyx и при любой ошибке SDK: приложение тогда работает на запасном холсте.
     */
    fun initialize(application: Application): Boolean {
        isPenAvailable = false
        maxTouchPressure = FALLBACK_MAX_TOUCH_PRESSURE
        if (!isOnyxManufacturer()) return false
        return try {
            HiddenApiBypass.setHiddenApiExemptions("")
            RxManager.Builder.initAppContext(application)
            ResManager.init(application)
            maxTouchPressure = readMaxTouchPressure()
            isPenAvailable = true
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Onyx SDK не поднялся, перо работает на запасном холсте", error)
            false
        }
    }

    private fun isOnyxManufacturer(): Boolean =
        Build.MANUFACTURER.equals("ONYX", ignoreCase = true) || Build.BRAND.equals("ONYX", ignoreCase = true)

    // Без значения от SDK, при ошибке и при значении не больше нуля берём запасное.
    private fun readMaxTouchPressure(): Float =
        try {
            EpdController.getMaxTouchPressure().takeIf { it > 0f } ?: FALLBACK_MAX_TOUCH_PRESSURE
        } catch (error: Throwable) {
            Log.w(TAG, "Максимум нажима от SDK не получен", error)
            FALLBACK_MAX_TOUCH_PRESSURE
        }

    private const val TAG = "OnyxSdk"
}
