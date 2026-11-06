package app.deckly.editor

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Запомненная толщина контура: настройка приложения в `SharedPreferences`, переживает перезапуск. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutlineWidthStoreTest {
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences("outline-width-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @Test
    fun widthIsThreeUntilSomethingIsWritten() {
        assertEquals(3, PreferencesOutlineWidthStore(preferences).readWidth())
    }

    @Test
    fun writtenWidthSurvivesANewStoreOnTheSamePreferences() {
        PreferencesOutlineWidthStore(preferences).writeWidth(9)

        assertEquals(9, PreferencesOutlineWidthStore(preferences).readWidth())
    }

    @Test
    fun lastWriteWins() {
        val store = PreferencesOutlineWidthStore(preferences)

        store.writeWidth(4)
        store.writeWidth(11)

        assertEquals(11, store.readWidth())
    }

    @Test
    fun everyValueOfTheSliderIsKept() {
        val store = PreferencesOutlineWidthStore(preferences)
        for (width in 1..12) {
            store.writeWidth(width)

            assertEquals(width, store.readWidth())
        }
    }

    @Test
    fun widthOutsideTheSliderIsReadAsThree() {
        for (stored in listOf(0, -5, 13, 100, Int.MAX_VALUE)) {
            preferences.edit().putInt("outline_width", stored).commit()

            assertEquals("записано $stored", 3, PreferencesOutlineWidthStore(preferences).readWidth())
        }
    }

    @Test
    fun valueOfAnotherTypeUnderTheKeyIsReadAsThree() {
        preferences.edit().putString("outline_width", "много").commit()

        assertEquals(3, PreferencesOutlineWidthStore(preferences).readWidth())
    }
}
