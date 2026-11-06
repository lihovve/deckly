package app.deckly.ink

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Пока лист в редакторе, корневой вид Compose обновляется режимом листа, а когда лист уходит (выход из редактора), режим по
 * умолчанию возвращается тому же виду: остальные экраны режима не видят. Сам вызов `EpdController` на JVM не виден, поэтому
 * проверяется, кому и когда эффект отдаёт вызов.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FastViewUpdatesEffectTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val applied = mutableListOf<View>()
    private val reset = mutableListOf<View>()
    private var isShown by mutableStateOf(true)

    @Test
    fun rootViewIsAppliedOnceWhileSheetIsShown() {
        showEffect()
        composeRule.waitForIdle()

        assertEquals(1, applied.size)
        assertTrue(reset.isEmpty())
    }

    @Test
    fun sameViewIsResetWhenSheetLeavesComposition() {
        showEffect()
        composeRule.waitForIdle()

        isShown = false
        composeRule.waitForIdle()

        assertEquals(1, applied.size)
        assertEquals(1, reset.size)
        assertSame(applied.single(), reset.single())
    }

    @Test
    fun showingSheetAgainAppliesAgain() {
        showEffect()
        composeRule.waitForIdle()
        isShown = false
        composeRule.waitForIdle()

        isShown = true
        composeRule.waitForIdle()

        assertEquals(2, applied.size)
        assertEquals(1, reset.size)
    }

    private fun showEffect() {
        composeRule.setContent {
            if (isShown) FastViewUpdatesEffect(applyUpdates = { applied += it }, resetUpdates = { reset += it })
        }
    }
}
