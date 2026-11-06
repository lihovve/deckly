package app.deckly.ink

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Без SDK Onyx (любое устройство, кроме планшета, и JVM) обновление экрана не должно падать.
 *
 * Сама защита `OnyxSdk.isPenAvailable` на JVM не наблюдается: вызов `EpdController` здесь не падает, и
 * убрать защиту можно без красного теста (проверено мутацией). Что вызов на устройстве Onyx
 * действительно обновляет экран, проверит задача 12 плана 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenRefreshTest {
    @Test
    fun fullRefreshWithoutSdkDoesNotFail() {
        ScreenRefresh.refreshFullScreen()

        assertTrue(ShadowLog.getLogsForTag("ScreenRefresh").isEmpty())
    }

    @Test
    fun applyingViewUpdatesWithoutSdkDoesNotFail() {
        val view = View(ApplicationProvider.getApplicationContext<Context>())

        ScreenRefresh.applyFastViewUpdates(view)

        assertTrue(ShadowLog.getLogsForTag("ScreenRefresh").isEmpty())
    }

    @Test
    fun resettingViewUpdatesWithoutSdkDoesNotFail() {
        val view = View(ApplicationProvider.getApplicationContext<Context>())

        ScreenRefresh.resetViewUpdates(view)

        assertTrue(ShadowLog.getLogsForTag("ScreenRefresh").isEmpty())
    }
}
