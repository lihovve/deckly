package app.deckly

import android.os.SystemClock
import android.view.MotionEvent
import app.deckly.data.timing.DecklyTiming
import kotlin.math.roundToInt

/**
 * Трасса касаний для проверки на планшете: дошло ли касание до приложения и с каким опозданием. Если кнопка «не
 * сработала с первого раза», а в логе касания нет, его не передала прошивка (перо засыпает, система разбирает жест);
 * если есть, причина в приложении. Идёт через [DecklyTiming], значит, только в отлаживаемой сборке.
 *
 * В строке только тип инструмента, действие, координаты в пикселях окна и опоздание доставки: ни карточек, ни путей.
 * Следим за касанием, подъёмом и отменой первого указателя; движений слишком много, и для вопроса «дошло ли» они не нужны.
 */
object TouchTrace {
    /** Пишет строку о касании в лог, если сборка отлаживаемая и действие трассируется. */
    fun logTouch(event: MotionEvent) {
        if (!DecklyTiming.isEnabled) return
        val line = describeTouch(event.getToolType(0), event.actionMasked, event.x, event.y, event.eventTime, SystemClock.uptimeMillis())
        if (line != null) DecklyTiming.logInfo { line }
    }

    /**
     * Строка о касании или `null`, если действие не трассируется. [toolType] и [action] — значения `MotionEvent`,
     * [eventTimeMs] и [nowMs] — время события и доставки в миллисекундах от запуска системы.
     */
    fun describeTouch(toolType: Int, action: Int, x: Float, y: Float, eventTimeMs: Long, nowMs: Long): String? {
        val actionName = when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> return null
        }
        return "touch tool=${describeTool(toolType)} action=$actionName x=${x.roundToInt()} y=${y.roundToInt()} delay=${nowMs - eventTimeMs}ms"
    }

    private fun describeTool(toolType: Int): String = when (toolType) {
        MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
        MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
        MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
        MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
        else -> "UNKNOWN"
    }
}
