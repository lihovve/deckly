package app.deckly.ink

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnyxSdkTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun deviceOtherThanOnyxLeavesSdkOff() {
        val isInitialized = OnyxSdk.initialize(application)

        assertFalse(isInitialized)
        assertFalse(OnyxSdk.isPenAvailable)
        // Устройство не Onyx: до вызовов SDK дело не дошло, ошибки в журнале нет.
        assertTrue(ShadowLog.getLogsForTag("OnyxSdk").isEmpty())
    }

    @Test
    fun failureOfInitializationLeavesSdkOffInsteadOfCrashing() {
        // На JVM нет ни скрытых API Android, ни прошивки Onyx: первый же вызов SDK падает.
        val isInitialized = withManufacturer("ONYX") { OnyxSdk.initialize(application) }

        assertFalse(isInitialized)
        assertFalse(OnyxSdk.isPenAvailable)
        // Ответ «нет» получен потому, что вызов SDK упал и ошибка поймана, а не из-за проверки устройства.
        assertEquals(1, ShadowLog.getLogsForTag("OnyxSdk").size)
    }

    @Test
    fun maximumPressureFallsBackWhenSdkDoesNotAnswer() {
        OnyxSdk.initialize(application)

        assertEquals(4096f, OnyxSdk.maxTouchPressure, 0f)
    }

    private fun <T> withManufacturer(manufacturer: String, block: () -> T): T {
        val original = Build.MANUFACTURER
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", manufacturer)
        try {
            return block()
        } finally {
            ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", original)
        }
    }
}
