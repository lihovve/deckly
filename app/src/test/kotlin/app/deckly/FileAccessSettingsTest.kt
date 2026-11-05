package app.deckly

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileAccessSettingsTest {
    @Test
    fun intentOpensFileAccessScreenOfThePackage() {
        val intent = buildFileAccessSettingsIntent("app.deckly")

        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, intent.action)
        assertEquals("package:app.deckly", intent.dataString)
    }

    @Test
    fun fallbackIntentOpensCommonFileAccessScreen() {
        val intent = buildCommonFileAccessSettingsIntent()

        assertEquals(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, intent.action)
        assertEquals(null, intent.data)
    }
}
