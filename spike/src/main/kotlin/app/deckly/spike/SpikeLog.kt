package app.deckly.spike

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Журнал проверок прототипа: строка `EVENT k=v k=v` уходит в logcat с тегом [TAG] и в [lines]
 * (последние [MAX_LINES]) для блока статуса на экране.
 */
object SpikeLog {
    const val TAG = "DecklySpike"
    private const val MAX_LINES = 30

    private val mutableLines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    fun write(event: String, vararg fields: Pair<String, Any?>) {
        val text = buildString {
            append(event)
            for ((key, value) in fields) {
                append(' ').append(key).append('=').append(format(value))
            }
        }
        Log.i(TAG, text)
        mutableLines.update { (it + text).takeLast(MAX_LINES) }
    }

    // Значение с пробелами берём в кавычки, чтобы строка по-прежнему делилась на k=v.
    private fun format(value: Any?): String {
        val text = value.toString()
        return if (text.any { it.isWhitespace() }) "\"$text\"" else text
    }
}
