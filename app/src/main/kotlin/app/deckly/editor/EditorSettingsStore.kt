package app.deckly.editor

import android.content.SharedPreferences

/** Толщина контура по умолчанию, в единицах макета. */
const val DEFAULT_OUTLINE_WIDTH = 3

/** Границы толщины контура, в единицах макета: ползунок палитры идёт от одной до двенадцати. */
const val MIN_OUTLINE_WIDTH = 1
const val MAX_OUTLINE_WIDTH = 12

/**
 * Запомненные настройки редактора (пока толщина контура новых фигур). Это настройки приложения, а не данные карточек: в
 * папку `Deckly` они не входят. Значение вне [MIN_OUTLINE_WIDTH]…[MAX_OUTLINE_WIDTH] читается как [DEFAULT_OUTLINE_WIDTH].
 */
interface EditorSettingsStore {
    fun readOutlineWidth(): Int

    fun writeOutlineWidth(width: Int)
}

/** Хранит толщину в `SharedPreferences` приложения: переживает перезапуск. */
class PreferencesEditorSettingsStore(private val preferences: SharedPreferences) : EditorSettingsStore {
    // Значение под ключом может быть чужого типа (другая версия, ручная правка): это тоже «не записано».
    override fun readOutlineWidth(): Int =
        (preferences.all[KEY] as? Int)?.takeIf { it in MIN_OUTLINE_WIDTH..MAX_OUTLINE_WIDTH } ?: DEFAULT_OUTLINE_WIDTH

    override fun writeOutlineWidth(width: Int) {
        preferences.edit().putInt(KEY, width).apply()
    }

    private companion object {
        const val KEY = "outline_width"
    }
}
