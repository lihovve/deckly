package app.deckly.data.timing

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DecklyTimingTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val originalFlags = application.applicationInfo.flags

    @Before
    fun startFromReleaseWithoutTagLevel() {
        application.applicationInfo.flags = originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        ShadowLog.setLoggable(DecklyTiming.TAG, Log.INFO)
        DecklyTiming.enableIfRequested(application)
        ShadowLog.clear()
    }

    @After
    fun restore() {
        application.applicationInfo.flags = originalFlags
        ShadowLog.setLoggable(DecklyTiming.TAG, Log.INFO)
        DecklyTiming.enableIfRequested(application)
    }

    @Test
    fun isOffInReleaseBuildByDefault() {
        DecklyTiming.enableIfRequested(application)

        assertFalse(DecklyTiming.isEnabled)
    }

    @Test
    fun releaseBuildWritesNothingAndDoesNotBuildTheLine() {
        var isBuilt = false

        DecklyTiming.logInfo {
            isBuilt = true
            "строка"
        }

        assertFalse("строка собрана при выключенном логе", isBuilt)
        assertTrue(ShadowLog.getLogsForTag(DecklyTiming.TAG).isEmpty())
    }

    @Test
    fun isOnWhenTheTagIsAllowedAtDebugLevel() {
        // На планшете: adb shell setprop log.tag.DecklyTiming DEBUG и перезапуск приложения.
        ShadowLog.setLoggable(DecklyTiming.TAG, Log.DEBUG)

        DecklyTiming.enableIfRequested(application)

        assertTrue(DecklyTiming.isEnabled)
        DecklyTiming.logInfo { "open total=1.0ms" }
        assertTrue(ShadowLog.getLogsForTag(DecklyTiming.TAG).any { it.msg == "open total=1.0ms" })
    }

    @Test
    fun isOffWhenTheTagIsAllowedOnlyAtInfoLevel() {
        // Уровень INFO — обычный порог системы: он не включает лог времени.
        ShadowLog.setLoggable(DecklyTiming.TAG, Log.INFO)

        DecklyTiming.enableIfRequested(application)

        assertFalse(DecklyTiming.isEnabled)
    }

    @Test
    fun isOnInDebuggableBuildWithoutTheProperty() {
        application.applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_DEBUGGABLE

        DecklyTiming.enableIfRequested(application)

        assertTrue(DecklyTiming.isEnabled)
    }

    @Test
    fun flagFollowsTheLatestCallNotTheFirstOne() {
        ShadowLog.setLoggable(DecklyTiming.TAG, Log.DEBUG)
        DecklyTiming.enableIfRequested(application)
        assertTrue(DecklyTiming.isEnabled)

        ShadowLog.setLoggable(DecklyTiming.TAG, Log.INFO)
        DecklyTiming.enableIfRequested(application)

        assertFalse(DecklyTiming.isEnabled)
    }
}
