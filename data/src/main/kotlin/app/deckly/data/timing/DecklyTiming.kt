package app.deckly.data.timing

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.SystemClock
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
        describeMillis((endNanos - startNanos) / NANOS_PER_MILLI)

    /** Миллисекунды с одной цифрой после запятой и точкой при любой локали: «12.3». */
    fun describeMillis(millis: Double): String = String.format(Locale.ROOT, "%.1f", millis)

    /**
     * Сколько миллисекунд прошло от старта процесса до момента [atNanos] (значение `System.nanoTime()`). Читает часы
     * системы, поэтому звать только внутри [logInfo].
     */
    fun measureProcessAgeMs(atNanos: Long): Double {
        val ageNowMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        return ageNowMs - (System.nanoTime() - atNanos) / NANOS_PER_MILLI
    }

    /** Строка об открытии хранения: от старта процесса до начала открытия, проверка базы, сборка Room, всё открытие. */
    fun describeOpen(processAgeMs: Double, quickCheckNanos: Long, roomNanos: Long, totalNanos: Long): String =
        "open sinceProcessStart=${describeMillis(processAgeMs)}ms quickCheck=${describeNanos(quickCheckNanos)}ms " +
            "room=${describeNanos(roomNanos)}ms total=${describeNanos(totalNanos)}ms"

    /** Строка об уборке: чтение ссылок из базы (Room открывает файл здесь) и уборка файлов штрихов. */
    fun describeCleanup(referencesNanos: Long, filesNanos: Long): String =
        "cleanup references=${describeNanos(referencesNanos)}ms files=${describeNanos(filesNanos)}ms"

    /** Строка о записи черновика: файл, транзакция базы и число штрихов на лице и обороте. Без имён и путей. */
    fun describeDraftStore(fileNanos: Long, transactionNanos: Long, frontStrokes: Int, backStrokes: Int): String =
        "draft file=${describeNanos(fileNanos)}ms db=${describeNanos(transactionNanos)}ms strokes=$frontStrokes/$backStrokes"

    private fun describeNanos(nanos: Long): String = describeMillis(nanos / NANOS_PER_MILLI)

    private const val NANOS_PER_MILLI = 1_000_000.0
}
