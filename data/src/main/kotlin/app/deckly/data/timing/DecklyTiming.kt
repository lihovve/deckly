package app.deckly.data.timing

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.util.Locale

/**
 * Строки времени для проверки на планшете: тег [TAG], уровень `Log.i`. Пишутся в отлаживаемой сборке (`FLAG_DEBUGGABLE`)
 * и в любой другой, если для тега разрешён уровень `DEBUG`: `adb shell setprop log.tag.DecklyTiming DEBUG` и перезапуск
 * приложения. Иначе [isEnabled] ложно и строка даже не собирается. Лог ничего не читает с диска: замеры берутся из
 * `System.nanoTime()`, которое звали бы и без лога.
 *
 * Флаг ставит [enableIfRequested]; приложение зовёт его в начале [app.deckly.data.openDecklyData], до того как
 * кто-то что-либо пишет в лог. До этого и в тестах без `Context` лог выключен.
 */
object DecklyTiming {
    const val TAG = "DecklyTiming"

    @Volatile
    var isEnabled: Boolean = false
        private set

    /** Включает лог, если сборка отлаживаемая или для тега разрешён уровень `DEBUG`; значение берётся заново при каждом вызове. */
    fun enableIfRequested(context: Context) {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        isEnabled = isDebuggable || Log.isLoggable(TAG, Log.DEBUG)
    }

    /** Строка собирается, только когда лог включён. */
    inline fun logInfo(message: () -> String) {
        if (isEnabled) Log.i(TAG, message())
    }

    fun logWarning(message: String, error: Throwable) {
        if (isEnabled) Log.w(TAG, message, error)
    }

    /** Миллисекунды между [startNanos] и [endNanos] с одной цифрой после запятой: «12.3». */
    fun describeElapsed(startNanos: Long, endNanos: Long = System.nanoTime()): String =
        String.format(Locale.ROOT, "%.1f", (endNanos - startNanos) / NANOS_PER_MILLI)

    private const val NANOS_PER_MILLI = 1_000_000.0
}
