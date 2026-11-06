package app.deckly

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.db.DatabaseDamagedException
import app.deckly.data.timing.DecklyTiming
import app.deckly.fakes.FakeCardContentRepository
import app.deckly.fakes.FakeDeckCatalog
import app.deckly.fakes.buildFakeData
import app.deckly.fixtures.buildDeck
import app.deckly.fixtures.buildDeckState
import java.io.File
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** Activity целиком: приложение из [TestDecklyApplication], настоящие ViewModel и дизайн, хранение в памяти. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestDecklyApplication::class, qualifiers = "w1000dp-h1400dp-mdpi")
class MainActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        TestWorld.reset()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    // --- манифест ------------------------------------------------------------------------------

    // adjustResize в LayoutParams помечен устаревшим, но манифест задаёт именно его, и прошивка его читает.
    @Suppress("DEPRECATION")
    @Test
    fun activityIsLauncherInPortraitAndResizesForKeyboard() {
        val packageManager = application.packageManager
        val info = packageManager.getActivityInfo(ComponentName(application, MainActivity::class.java), PackageManager.GET_META_DATA)

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, info.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
        assertTrue(info.exported)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(application.packageName)
        val launchers = packageManager.queryIntentActivities(launcher, 0)
        assertEquals(listOf(MainActivity::class.java.name), launchers.map { it.activityInfo.name })
    }

    // --- экран запуска -------------------------------------------------------------------------

    @Test
    fun closeOnDamagedDatabaseFinishesTask() {
        TestWorld.openFailure = DatabaseDamagedException(File("deckly.db"), "повреждена")
        launch()
        waitForText("Database damaged")

        composeRule.onNodeWithText("Close").performClick()

        scenario!!.onActivity { assertTrue(it.isFinishing) }
    }

    @Test
    fun openSettingsStartsFileAccessScreenOfThisApp() {
        TestWorld.hasFileAccess = false
        launch()
        waitForText("We need file access")

        composeRule.onNodeWithText("Open settings").performClick()

        scenario!!.onActivity { activity ->
            val started = shadowOf(activity).nextStartedActivity
            assertNotNull(started)
            assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, started.action)
            assertEquals("package:${activity.packageName}", started.dataString)
        }
    }

    @Test
    fun openSettingsFallsBackToCommonScreenWhenAppScreenIsMissing() {
        shadowOf(application).checkActivities(true)
        // Общий экран настроек есть, экрана именно для приложения нет.
        val settings = ComponentName("com.android.settings", "com.android.settings.Settings")
        val packageManager = shadowOf(application.packageManager)
        packageManager.addActivityIfNotPresent(settings)
        packageManager.addIntentFilterForActivity(
            settings,
            IntentFilter(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )
        TestWorld.hasFileAccess = false
        launch()
        waitForText("We need file access")

        composeRule.onNodeWithText("Open settings").performClick()

        scenario!!.onActivity { activity ->
            assertEquals(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, shadowOf(activity).nextStartedActivity.action)
        }
    }

    @Test
    fun returnFromSettingsWithGrantedAccessContinuesStart() {
        TestWorld.hasFileAccess = false
        TestWorld.data = buildData()
        launch()
        waitForText("We need file access")

        // Владелец выдал доступ и вернулся в приложение: ON_RESUME повторяет запуск сам.
        TestWorld.hasFileAccess = true
        scenario!!.moveToState(Lifecycle.State.STARTED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)

        waitForText("Decks")
    }

    // --- системные вызовы activity -------------------------------------------------------------

    @Test
    fun systemBarsAreHiddenAtStartAndWhenWindowFocusReturns() {
        TestWorld.hasFileAccess = false
        val controller = Robolectric.buildActivity(RecordingMainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(1, activity.hideCalls)

        activity.onWindowFocusChanged(false)
        assertEquals("без фокуса панели не трогаем", 1, activity.hideCalls)
        activity.onWindowFocusChanged(true)

        assertEquals(2, activity.hideCalls)
        controller.pause().stop().destroy()
    }

    @Test
    fun touchesAreTracedInDebuggableBuildAndStillReachTheWindow() {
        TestWorld.hasFileAccess = false
        val controller = Robolectric.buildActivity(RecordingMainActivity::class.java).setup()
        val activity = controller.get()
        val window = addTouchCountingView(activity)
        val originalFlags = application.applicationInfo.flags
        try {
            application.applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_DEBUGGABLE
            DecklyTiming.enableIfDebuggable(application)
            ShadowLog.clear()

            tap(activity, MotionEvent.TOOL_TYPE_STYLUS, 12f, 21f)

            val lines = ShadowLog.getLogsForTag(DecklyTiming.TAG).map { it.msg }.filter { it.startsWith("touch ") }
            assertEquals(2, lines.size)
            assertTrue(lines[0], Regex("touch tool=STYLUS action=DOWN x=12 y=21 delay=\\d+ms").matches(lines[0]))
            assertTrue(lines[1], Regex("touch tool=STYLUS action=UP x=12 y=21 delay=\\d+ms").matches(lines[1]))
            assertEquals("касание и подъём дошли до окна: прежний разбор не нарушен", 2, window.touchEvents)
        } finally {
            application.applicationInfo.flags = originalFlags
            DecklyTiming.enableIfDebuggable(application)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun touchesAreNotTracedInReleaseBuild() {
        TestWorld.hasFileAccess = false
        val controller = Robolectric.buildActivity(RecordingMainActivity::class.java).setup()
        val activity = controller.get()
        val window = addTouchCountingView(activity)
        val originalFlags = application.applicationInfo.flags
        try {
            application.applicationInfo.flags = originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            DecklyTiming.enableIfDebuggable(application)
            ShadowLog.clear()

            tap(activity, MotionEvent.TOOL_TYPE_FINGER, 12f, 21f)

            assertTrue(ShadowLog.getLogsForTag(DecklyTiming.TAG).none { it.msg.startsWith("touch ") })
            assertEquals("касание и подъём дошли до окна и без трассы", 2, window.touchEvents)
        } finally {
            application.applicationInfo.flags = originalFlags
            DecklyTiming.enableIfDebuggable(application)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun closeRemovesTheTaskNotJustTheActivity() {
        TestWorld.openFailure = DatabaseDamagedException(File("deckly.db"), "повреждена")
        val controller = Robolectric.buildActivity(RecordingMainActivity::class.java).setup()
        val activity = controller.get()
        waitForText("Database damaged")

        composeRule.onNodeWithText("Close").performClick()

        assertEquals(1, activity.removeTaskCalls)
        assertEquals(0, activity.plainFinishCalls)
        controller.pause().stop().destroy()
    }

    @Test
    fun systemBarsControllerHidesBarsThatComeBackBySwipe() {
        val calls = mutableListOf<String>()
        val controller = Proxy.newProxyInstance(
            WindowInsetsController::class.java.classLoader,
            arrayOf(WindowInsetsController::class.java),
        ) { _, method, args ->
            calls += method.name + args.orEmpty().joinToString(prefix = "(", postfix = ")")
            null
        } as WindowInsetsController

        applyHiddenSystemBars(controller)

        assertEquals(
            listOf(
                "setSystemBarsBehavior(${WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE})",
                "hide(${WindowInsets.Type.systemBars()})",
            ),
            calls,
        )
        // Окна без контроллера (его ещё нет) не должны ронять приложение.
        applyHiddenSystemBars(null)
    }

    // Activity, которая записывает платформенные вызовы: настоящие на JVM ничего не показывают.
    class RecordingMainActivity : MainActivity() {
        var hideCalls = 0
        var removeTaskCalls = 0
        var plainFinishCalls = 0

        override fun hideSystemBars() {
            hideCalls++
        }

        override fun finishAndRemoveTask() {
            removeTaskCalls++
        }

        override fun finish() {
            plainFinishCalls++
            super.finish()
        }
    }

    // --- вспомогательное -----------------------------------------------------------------------

    // Касание и подъём одного указателя настоящими MotionEvent, доставленные окну активности.
    private fun tap(activity: Activity, tool: Int, x: Float, y: Float) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f })
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try {
                activity.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    // Вид поверх всего окна: считает, сколько событий до него дошло.
    private fun addTouchCountingView(activity: Activity): TouchCountingView {
        val counter = TouchCountingView(activity)
        activity.addContentView(counter, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Без прохода по очереди вид не измерен, и касания его не находят.
        shadowOf(Looper.getMainLooper()).idle()
        return counter
    }

    private class TouchCountingView(context: Context) : View(context) {
        var touchEvents = 0

        override fun onTouchEvent(event: MotionEvent): Boolean {
            touchEvents++
            return true
        }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun buildData() = buildFakeData(
        context = application,
        rootDir = folder.newFolder(),
        decks = FakeDeckCatalog(listOf(buildDeckState(buildDeck(1, "Немецкий A2")))),
        contents = FakeCardContentRepository(),
    )
}
