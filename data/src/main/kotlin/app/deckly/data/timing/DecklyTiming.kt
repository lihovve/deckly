package app.deckly.data.timing

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.util.Locale

/**
 * Строки времени для проверки на планшете: тег [TAG], уровень `Log.i`. Пишутся только в отлаживаемой сборке
 * (`FLAG_DEBUGGABLE`), в остальных [isEnabled] ложно и строка даже не собирается. Лог ничего не читает с диска:
 * замеры берутся из `System.nanoTime()`, которое звали бы и без лога.
 *
 * Флаг ставит [enableIfDebuggable]; приложение зовёт его в начале [app.deckly.data.openDecklyData], до того как
 * кто-то что-либо пишет в лог. До этого и в тестах без `Context` лог выключен.
 */
object DecklyTiming {
    const val TAG = "DecklyTiming"

    @Volatile
    var isEnabled: Boolean = false
        private set

    fun enableIfDebuggable(context: Context) {
        isEnabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
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
