package app.deckly.editor

import android.content.SharedPreferences
import app.deckly.ui.contract.Thickness

/** Толщина контура по умолчанию, в единицах макета. */
const val DEFAULT_OUTLINE_WIDTH = 3

/** Границы толщины контура, в единицах макета: ползунок палитры идёт от одной до двенадцати. */
const val MIN_OUTLINE_WIDTH = 1
const val MAX_OUTLINE_WIDTH = 12

/** Размер новой фигуры, в единицах макета: сторона рамки (у линии и стрелок длина). Ползунок палитры идёт от 100 до 440 по 10. */
const val DEFAULT_SHAPE_SIZE = 220
const val MIN_SHAPE_SIZE = 100
const val MAX_SHAPE_SIZE = 440
const val SHAPE_SIZE_STEP = 10

/** Размер, приведённый к границам и шагу ползунка палитры: ближайшее значение 100, 110, …, 440. */
fun snapShapeSize(size: Int): Int =
    MIN_SHAPE_SIZE + (size.coerceIn(MIN_SHAPE_SIZE, MAX_SHAPE_SIZE) - MIN_SHAPE_SIZE + SHAPE_SIZE_STEP / 2) / SHAPE_SIZE_STEP * SHAPE_SIZE_STEP

/** Толщина пера по умолчанию. */
val DEFAULT_PEN_THICKNESS = Thickness.MEDIUM

/**
 * Запомненные настройки редактора: толщина пера, толщина контура и размер новых фигур. Это настройки приложения, а не данные
 * карточек: в папку `Deckly` они не входят. Значение вне [MIN_OUTLINE_WIDTH]…[MAX_OUTLINE_WIDTH] читается как [DEFAULT_OUTLINE_WIDTH],
 * незнакомая или чужого типа толщина пера — как [DEFAULT_PEN_THICKNESS], размер вне ползунка или не по его шагу — как
 * [DEFAULT_SHAPE_SIZE].
 */
interface EditorSettingsStore {
    fun readOutlineWidth(): Int

    fun writeOutlineWidth(width: Int)

    fun readPenThickness(): Thickness

    fun writePenThickness(thickness: Thickness)

    fun readShapeSize(): Int

    fun writeShapeSize(size: Int)
}

/** Хранит настройки в `SharedPreferences` приложения: они переживают перезапуск. */
class PreferencesEditorSettingsStore(private val preferences: SharedPreferences) : EditorSettingsStore {
    // Значение под ключом может быть чужого типа (другая версия, ручная правка): это тоже «не записано».
    override fun readOutlineWidth(): Int =
        (preferences.all[OUTLINE_WIDTH_KEY] as? Int)?.takeIf { it in MIN_OUTLINE_WIDTH..MAX_OUTLINE_WIDTH } ?: DEFAULT_OUTLINE_WIDTH

    override fun writeOutlineWidth(width: Int) {
        preferences.edit().putInt(OUTLINE_WIDTH_KEY, width).apply()
    }

    // Под ключом лежит имя значения; незнакомое имя или значение другого типа тоже «не записано».
    override fun readPenThickness(): Thickness =
        Thickness.entries.firstOrNull { it.name == preferences.all[PEN_THICKNESS_KEY] } ?: DEFAULT_PEN_THICKNESS

    override fun writePenThickness(thickness: Thickness) {
        preferences.edit().putString(PEN_THICKNESS_KEY, thickness.name).apply()
    }

    // Размер вне ползунка или не по его шагу (ручная правка, другая версия) тоже «не записан».
    override fun readShapeSize(): Int =
        (preferences.all[SHAPE_SIZE_KEY] as? Int)?.takeIf { it == snapShapeSize(it) } ?: DEFAULT_SHAPE_SIZE

    override fun writeShapeSize(size: Int) {
        preferences.edit().putInt(SHAPE_SIZE_KEY, size).apply()
    }

    private companion object {
        const val OUTLINE_WIDTH_KEY = "outline_width"
        const val PEN_THICKNESS_KEY = "pen_thickness"
        const val SHAPE_SIZE_KEY = "shape_size"
    }
}
