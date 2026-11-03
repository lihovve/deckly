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
