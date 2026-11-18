package app.deckly.editor

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.deckly.ui.contract.Thickness
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Запомненная толщина контура: настройка приложения в `SharedPreferences`, переживает перезапуск. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorSettingsStoreTest {
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences("outline-width-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @Test
    fun widthIsThreeUntilSomethingIsWritten() {
        assertEquals(3, PreferencesEditorSettingsStore(preferences).readOutlineWidth())
    }

    @Test
    fun writtenWidthSurvivesANewStoreOnTheSamePreferences() {
        PreferencesEditorSettingsStore(preferences).writeOutlineWidth(9)

        assertEquals(9, PreferencesEditorSettingsStore(preferences).readOutlineWidth())
    }

    @Test
    fun lastWriteWins() {
        val store = PreferencesEditorSettingsStore(preferences)

        store.writeOutlineWidth(4)
        store.writeOutlineWidth(11)

        assertEquals(11, store.readOutlineWidth())
    }

    @Test
    fun everyValueOfTheSliderIsKept() {
        val store = PreferencesEditorSettingsStore(preferences)
        for (width in 1..12) {
            store.writeOutlineWidth(width)

            assertEquals(width, store.readOutlineWidth())
        }
    }

    @Test
    fun widthOutsideTheSliderIsReadAsThree() {
        for (stored in listOf(0, -5, 13, 100, Int.MAX_VALUE)) {
            preferences.edit().putInt("outline_width", stored).commit()

            assertEquals("записано $stored", 3, PreferencesEditorSettingsStore(preferences).readOutlineWidth())
        }
    }

    @Test
    fun valueOfAnotherTypeUnderTheKeyIsReadAsThree() {
        preferences.edit().putString("outline_width", "много").commit()

        assertEquals(3, PreferencesEditorSettingsStore(preferences).readOutlineWidth())
    }

    @Test
    fun penThicknessIsMediumUntilSomethingIsWritten() {
        assertEquals(Thickness.MEDIUM, PreferencesEditorSettingsStore(preferences).readPenThickness())
    }

    @Test
    fun writtenPenThicknessSurvivesANewStoreOnTheSamePreferences() {
        PreferencesEditorSettingsStore(preferences).writePenThickness(Thickness.THICK)

        assertEquals(Thickness.THICK, PreferencesEditorSettingsStore(preferences).readPenThickness())
    }

    @Test
    fun everyPenThicknessIsKeptAndTheLastWriteWins() {
        val store = PreferencesEditorSettingsStore(preferences)
        for (thickness in Thickness.entries) {
            store.writePenThickness(thickness)

            assertEquals(thickness, store.readPenThickness())
        }
        store.writePenThickness(Thickness.THIN)
        store.writePenThickness(Thickness.THICK)

        assertEquals(Thickness.THICK, store.readPenThickness())
    }

    @Test
    fun unknownStringUnderThePenKeyIsReadAsMedium() {
        for (stored in listOf("HUGE", "", "thin", "1")) {
            preferences.edit().putString("pen_thickness", stored).commit()

            assertEquals("записано «$stored»", Thickness.MEDIUM, PreferencesEditorSettingsStore(preferences).readPenThickness())
        }
    }

    @Test
    fun valueOfAnotherTypeUnderThePenKeyIsReadAsMedium() {
        preferences.edit().putInt("pen_thickness", 2).commit()

        assertEquals(Thickness.MEDIUM, PreferencesEditorSettingsStore(preferences).readPenThickness())
    }

    @Test
    fun penThicknessAndOutlineWidthDoNotTouchEachOther() {
        val store = PreferencesEditorSettingsStore(preferences)

        store.writePenThickness(Thickness.THIN)
        store.writeOutlineWidth(11)

        assertEquals(Thickness.THIN, store.readPenThickness())
        assertEquals(11, store.readOutlineWidth())
    }

    @Test
    fun shapeSizeIsTwoHundredTwentyUntilSomethingIsWritten() {
        assertEquals(220, PreferencesEditorSettingsStore(preferences).readShapeSize())
    }

    @Test
    fun writtenShapeSizeSurvivesANewStoreOnTheSamePreferences() {
        PreferencesEditorSettingsStore(preferences).writeShapeSize(340)

        assertEquals(340, PreferencesEditorSettingsStore(preferences).readShapeSize())
    }

    @Test
    fun everySizeOfTheSliderIsKept() {
        val store = PreferencesEditorSettingsStore(preferences)
        for (size in 100..440 step 10) {
            store.writeShapeSize(size)

            assertEquals(size, store.readShapeSize())
        }
    }

    @Test
    fun sizeOutsideTheSliderOrOffItsStepIsReadAsTwoHundredTwenty() {
        for (stored in listOf(0, -10, 90, 95, 105, 225, 441, 450, 10_000, Int.MAX_VALUE)) {
            preferences.edit().putInt("shape_size", stored).commit()

            assertEquals("записано $stored", 220, PreferencesEditorSettingsStore(preferences).readShapeSize())
        }
    }

    @Test
    fun valueOfAnotherTypeUnderTheSizeKeyIsReadAsTwoHundredTwenty() {
        preferences.edit().putString("shape_size", "300").commit()

        assertEquals(220, PreferencesEditorSettingsStore(preferences).readShapeSize())
    }

    @Test
    fun shapeSizeDoesNotTouchTheOtherSettings() {
        val store = PreferencesEditorSettingsStore(preferences)

        store.writeShapeSize(400)

        assertEquals(3, store.readOutlineWidth())
        assertEquals(Thickness.MEDIUM, store.readPenThickness())
        store.writePenThickness(Thickness.THICK)
        store.writeOutlineWidth(8)
        assertEquals(400, store.readShapeSize())
    }

    @Test
    fun valuesAreKeptUnderTheKeysOfTheSettingsFile() {
        // Имена ключей — формат файла настроек: переименование потеряло бы запомненное у уже работающего приложения.
        val store = PreferencesEditorSettingsStore(preferences)
        store.writeOutlineWidth(7)
        store.writePenThickness(Thickness.THIN)
        store.writeShapeSize(340)

        assertEquals(7, preferences.getInt("outline_width", -1))
        assertEquals("THIN", preferences.getString("pen_thickness", null))
        assertEquals(340, preferences.getInt("shape_size", -1))
    }
}
