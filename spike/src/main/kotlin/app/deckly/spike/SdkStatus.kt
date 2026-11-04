package app.deckly.spike

/** Итог инициализации Onyx SDK; пишет [SpikeApplication], читает экран. */
object SdkStatus {
    @Volatile var isOnyxDevice: Boolean = false
    @Volatile var initError: Throwable? = null
    @Volatile var deviceClass: String? = null

    /** Сырой максимум нажима пера: нажим точки делится на него, чтобы получить 0..1. */
    @Volatile var maxTouchPressure: Float = FALLBACK_MAX_TOUCH_PRESSURE

    /** Режимы «Onyx» доступны, только если устройство Onyx и SDK поднялся без ошибок. */
    val isPenAvailable: Boolean
        get() = isOnyxDevice && initError == null

    fun logSummary() {
        SpikeLog.write(
            "SDK",
            "onyx" to isOnyxDevice,
            "init" to (initError?.toString() ?: "ok"),
            "device" to deviceClass,
        )
    }

    /** Максимум нажима, если SDK его не отдал (в журнале фазы Б нажим доходил до 2555 при 4096). */
    const val FALLBACK_MAX_TOUCH_PRESSURE = 4096f
}
