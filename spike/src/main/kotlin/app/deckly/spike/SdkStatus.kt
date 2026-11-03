package app.deckly.spike

/** Итог инициализации Onyx SDK; пишет [SpikeApplication], читает экран. */
object SdkStatus {
    @Volatile var isOnyxDevice: Boolean = false
    @Volatile var initError: Throwable? = null
    @Volatile var deviceClass: String? = null

    /** Режимы «Onyx» доступны, только если устройство Onyx и SDK поднялся без ошибок. */
    val isPenAvailable: Boolean
        get() = isOnyxDevice && initError == null

    fun logSummary() {
        SpikeLog.line(
            "SDK",
            "onyx" to isOnyxDevice,
            "init" to (initError?.toString() ?: "ok"),
            "device" to deviceClass,
        )
    }
}
