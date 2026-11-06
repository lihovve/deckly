package app.deckly.editor

import android.content.SharedPreferences

/** Толщина контура по умолчанию, в единицах макета. */
const val DEFAULT_OUTLINE_WIDTH = 3

/** Границы толщины контура, в единицах макета: ползунок палитры идёт от одной до двенадцати. */
const val MIN_OUTLINE_WIDTH = 1
const val MAX_OUTLINE_WIDTH = 12

/**
 * Запомненная толщина контура новых фигур. Это настройка приложения, а не данные карточек: в папку `Deckly` она не
 * входит. Значение вне [MIN_OUTLINE_WIDTH]…[MAX_OUTLINE_WIDTH] читается как [DEFAULT_OUTLINE_WIDTH].
 */
interface OutlineWidthStore {
    fun readWidth(): Int

    fun writeWidth(width: Int)
}

/** Хранит толщину в `SharedPreferences` приложения: переживает перезапуск. */
class PreferencesOutlineWidthStore(private val preferences: SharedPreferences) : OutlineWidthStore {
    // Значение под ключом может быть чужого типа (другая версия, ручная правка): это тоже «не записано».
    override fun readWidth(): Int =
        (preferences.all[KEY] as? Int)?.takeIf { it in MIN_OUTLINE_WIDTH..MAX_OUTLINE_WIDTH } ?: DEFAULT_OUTLINE_WIDTH

    override fun writeWidth(width: Int) {
        preferences.edit().putInt(KEY, width).apply()
    }

    private companion object {
        const val KEY = "outline_width"
    }
}
