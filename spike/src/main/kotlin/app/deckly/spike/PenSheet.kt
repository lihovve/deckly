package app.deckly.spike

/** Лист для рисования: один из холстов прототипа. Экран управляет им только через этот интерфейс. */
interface PenSheet {
    /** Отпускает экран: после этого он обновляется как обычно (для Onyx — выключает перо). */
    fun pause()

    /** Возвращает перо после [pause]. */
    fun resume()

    /** Перерисовывает все штрихи из [StrokeStore] и обновляет экран. */
    fun redrawAll()
}

object PenDefaults {
    const val STROKE_WIDTH = 3f
    const val ERASE_RADIUS = 16f
}

/** Чем делать GC после стирания (диагностика серого следа ластика). */
enum class EraseGcApi(val label: String) {
    /** `EpdController.invalidate(view, l, t, r, b, GC)`: инвалидация View, как было в первой сборке. */
    INVALIDATE("invalidate"),

    /** `EpdController.refreshScreenRegion(view, l, t, w, h, GC)`: обновление региона экрана. */
    REFRESH_REGION("refreshRegion"),

    /** `EpdController.repaintEveryThing(GC)`: контрольный GC всего экрана. */
    REPAINT_ALL("весь экран"),
}

/** Переключатели прототипа, которые меняет экран и читает лист. */
object PenSettings {
    /** Через сколько миллисекунд после GC включать перо (значения кнопки «Перо через»). */
    val RESUME_OPTIONS_MS = longArrayOf(150L, 450L, 800L)

    /** GC по месту стирания через 200 мс после ластика (кнопка «GC после ластика»). */
    @Volatile var isGcAfterErase: Boolean = true

    /** Вызов, которым делается GC после стирания (кнопка «GC ластика»). */
    @Volatile var eraseGcApi: EraseGcApi = EraseGcApi.REFRESH_REGION

    /** Пауза между GC и повторным включением пера, мс (кнопка «Перо через»). */
    @Volatile var eraseResumeMs: Long = 800L

    /** Рисует ли прошивка трек ластика (кнопка «Трек ластика»). */
    @Volatile var isEraserTrackEnabled: Boolean = true
}
