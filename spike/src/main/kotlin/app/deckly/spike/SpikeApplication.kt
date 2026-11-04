package app.deckly.spike

import android.app.Application
import android.os.Build
import android.view.InputDevice
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.device.Device
import com.onyx.android.sdk.rx.RxManager
import com.onyx.android.sdk.utils.ResManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

class SpikeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SdkStatus.isOnyxDevice = isOnyxManufacturer() && isOnyxSdkPresent()
        if (SdkStatus.isOnyxDevice) {
            initOnyxSdk()
        }
        SdkStatus.logSummary()
        readMaxTouchPressure()
        logInputDevices()
    }

    private fun isOnyxManufacturer(): Boolean =
        Build.MANUFACTURER.equals("ONYX", ignoreCase = true) ||
            Build.BRAND.equals("ONYX", ignoreCase = true)

    private fun isOnyxSdkPresent(): Boolean =
        try {
            Class.forName("com.onyx.android.sdk.pen.TouchHelper")
            true
        } catch (_: Throwable) {
            false
        }

    // Без этих трёх вызовов SDK пера падает (расхождение 3 с ТЗ): скрытые API, RxManager, ResManager.
    private fun initOnyxSdk() {
        try {
            HiddenApiBypass.setHiddenApiExemptions("")
            RxManager.Builder.initAppContext(this)
            ResManager.init(this)
            SdkStatus.deviceClass = Device.currentDevice().javaClass.simpleName
        } catch (error: Throwable) {
            SdkStatus.initError = error
        }
    }

    // Максимум нажима нужен, чтобы нормировать нажим точек в 0..1. Без SDK, при ошибке и при значении
    // не больше нуля берём запасное.
    private fun readMaxTouchPressure() {
        var value = 0f
        if (SdkStatus.isPenAvailable) {
            try {
                value = EpdController.getMaxTouchPressure()
            } catch (error: Throwable) {
                SpikeLog.write("PEN_MAX_PRESSURE", "error" to error)
            }
        }
        val isFromSdk = value > 0f
        SdkStatus.maxTouchPressure = if (isFromSdk) value else SdkStatus.FALLBACK_MAX_TOUCH_PRESSURE
        SpikeLog.write(
            "PEN_MAX_PRESSURE",
            "value" to SdkStatus.maxTouchPressure,
            "source" to if (isFromSdk) "sdk" else "fallback",
        )
    }

    // По строке на устройство ввода: по имени пера видно, какой рендер выберет TouchHelper.create.
    private fun logInputDevices() {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            SpikeLog.write(
                "INPUT_DEVICE",
                "id" to id,
                "name" to device.name,
                "sources" to "0x" + Integer.toHexString(device.sources),
                "isStylus" to device.supportsSource(InputDevice.SOURCE_STYLUS),
            )
        }
    }
}
