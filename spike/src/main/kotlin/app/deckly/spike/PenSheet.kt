package app.deckly.spike

import com.onyx.android.sdk.pen.TouchHelper

/** Лист для рисования: один из холстов прототипа. Экран управляет им только через этот интерфейс. */
interface PenSheet {
    /** Отпускает экран: после этого он обновляется как обычно (для Onyx — выключает перо). */
    fun pause()

    /** Возвращает перо после [pause]. */
    fun resume()

    /** Перерисовывает все штрихи из [StrokeStore] и обновляет экран. */
    fun redrawAll()

    /**
     * Применяет [PenSettings.penStyle], [PenSettings.strokeWidth] и [PenSettings.strokeRender]:
     * следующий штрих идёт новым стилем и толщиной, лист перерисован новой отрисовкой.
     */
    fun applyPenSettings()
}

object PenDefaults {
    const val STROKE_WIDTH = 3f
    const val ERASE_RADIUS = 16f
}

/** Стиль, которым прошивка рисует штрих под пером (значения `TouchHelper.STROKE_STYLE_*`). */
enum class PenStyle(val label: String, val sdkStyle: Int) {
    PENCIL("карандаш", TouchHelper.STROKE_STYLE_PENCIL),
    FOUNTAIN("ручка", TouchHelper.STROKE_STYLE_FOUNTAIN),
}

/** Чем лист перерисовывает сохранённый штрих после отрыва пера. */
enum class StrokeRender(val label: String) {
    /** Линия одной толщины, как перерисовывал прототип до задачи 6. */
    CONSTANT("ровно"),

    /** Своя отрисовка на `Canvas`: толщина отрезка зависит от нажима, SDK не нужен. */
    OWN("свой"),

    /** `NeoFountainPenWrapper.drawStroke` из SDK Onyx. */
    SDK("SDK"),
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

    /** Толщины, по которым кнопка «Толщина» ходит по кругу. */
    val WIDTH_OPTIONS = floatArrayOf(3f, 6f, 10f)

    /** Стиль пера прошивки (кнопка «Перо»). */
    @Volatile var penStyle: PenStyle = PenStyle.PENCIL

    /** Базовая толщина штриха (кнопка «Толщина»): под пером и в сохранённом штрихе. */
    @Volatile var strokeWidth: Float = PenDefaults.STROKE_WIDTH

    /** Отрисовка сохранённого штриха (кнопка «Отрисовка»). */
    @Volatile var strokeRender: StrokeRender = StrokeRender.CONSTANT
}
