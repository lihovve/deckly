package app.deckly.ink

/** Через сколько после кадра перо включается снова: столько прошивке нужно, чтобы показать интерфейс. */
internal const val PEN_RESUME_DELAY_MS = 150L

/** Действия над сырым пером; в [OnyxDrawingView] это `TouchHelper`, в тестах — заглушка. */
internal interface PenPort {
    /** Включает перо и ставит стиль, толщину и вид отрисовки заново: включение их сбрасывает. */
    fun enable()

    fun disable()
}

/** Отложенные шаги пера; в [OnyxDrawingView] идут через `Choreographer` и `postDelayed`. */
internal interface PenScheduler {
    fun afterNextFrame(action: () -> Unit)

    fun afterDelay(delayMs: Long, action: () -> Unit)
}

/**
 * Когда перо должно быть включено. Пока оно включено, прошивка замораживает весь экран, поэтому
 * видимое изменение вне холста показывается циклом: перо выключено → кадр → [PEN_RESUME_DELAY_MS] → перо
 * включено ([requestCycle]). Включено оно только при всех условиях сразу: сессия открыта, окно в фокусе,
 * хост разрешает, цикла нет. Новый цикл начинает ожидание заново.
 */
internal class PenGate(private val pen: PenPort, private val scheduler: PenScheduler) {
    var isOn: Boolean = false
        private set

    private var isSessionOpen = false
    private var isWindowActive = false
    private var isHostEnabled = true
    private var isHeld = false

    // Номер цикла: когда задержка прерванного цикла истекает, она видит чужой номер и ничего не делает.
    private var generation = 0

    /** Открытие сессии включает перо само, поэтому [PenPort.enable] здесь не нужен. */
    fun onSessionOpened() {
        isSessionOpen = true
        isOn = true
        apply()
    }

    // Цикл при закрытии сессии не отменяется: интерфейс, ради которого он начат, ещё не показан, и новая
    // сессия остаётся выключенной до конца цикла.
    fun onSessionClosed() {
        isSessionOpen = false
        isOn = false
    }

    fun setWindowActive(isActive: Boolean) {
        isWindowActive = isActive
        apply()
    }

    fun setHostEnabled(isEnabled: Boolean) {
        isHostEnabled = isEnabled
        apply()
    }

    fun requestCycle() {
        generation++
        val cycle = generation
        isHeld = true
        apply()
        scheduler.afterNextFrame {
            scheduler.afterDelay(PEN_RESUME_DELAY_MS) {
                if (cycle != generation) return@afterDelay
                isHeld = false
                apply()
            }
        }
    }

    private fun apply() {
        val isWanted = isSessionOpen && isWindowActive && isHostEnabled && !isHeld
        if (isWanted == isOn) return
        isOn = isWanted
        if (isWanted) pen.enable() else pen.disable()
    }
}
