package app.deckly.design.e

import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.SelectedShape
import app.deckly.ui.contract.Thickness

/**
 * Экран редактора в кадре макета для тестов фигур: действия копятся в [actions], значки видов подставляет слот-заглушка
 * с тегом `glyph-ВИД-ТОЛЩИНА`, лист — заглушка с тегом `slot`. Состояние можно менять, не пересоздавая экран.
 */
internal class EditorHost(private val rule: ComposeContentTestRule) {
    val actions = mutableListOf<EditorAction>()

    private var screenState by mutableStateOf<EditorScreenState?>(null)
    private var isHosted = false

    fun show(state: EditorScreenState) {
        screenState = state
        if (isHosted) {
            rule.waitForIdle()
            return
        }
        isHosted = true
        rule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(Color.White).testTag("frame")) {
                screenState?.let { shown ->
                    EditorScreen(
                        shown,
                        { actions += it },
                        { modifier -> Box(modifier.testTag("slot")) },
                        { kind, outline, modifier -> Box(modifier.testTag("glyph-${kind.name}-$outline")) },
                    )
                }
            }
        }
    }
}

internal fun buildEditorState(
    isNewCard: Boolean = true,
    side: CardSideShown = CardSideShown.FRONT,
    thickness: Thickness = Thickness.MEDIUM,
    canUndo: Boolean = true,
    dialog: EditorDialog? = null,
    selectedShape: SelectedShape? = null,
    isShapePaletteOpen: Boolean = false,
    paletteOutlineWidth: Int = 3,
    isCapturingStroke: Boolean = false,
) = EditorScreenState(isNewCard, side, thickness, canUndo, dialog, selectedShape, isShapePaletteOpen, paletteOutlineWidth, isCapturingStroke)

/** Отсчёт пера внутри одного события: точка и нажим. */
internal data class PenSample(val x: Float, val y: Float, val pressure: Float)

/**
 * Одно касание настоящим `MotionEvent` окну активности: ни перо, ни кнопку стилуса Compose-тесты сами подать не умеют.
 * Пока кончик касается экрана, система сообщает `BUTTON_PRIMARY`; боковая кнопка стилуса добавляет `BUTTON_STYLUS_PRIMARY`.
 * [history] — отсчёты, накопленные в событии до текущего (система отдаёт их пачкой за кадр: `historical`).
 */
internal fun dispatchPointer(
    activity: ComponentActivity,
    tool: Int,
    action: Int,
    x: Float,
    y: Float,
    downTime: Long,
    buttons: Int = MotionEvent.BUTTON_PRIMARY,
    pressure: Float = 1f,
    history: List<PenSample> = emptyList(),
) {
    val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
    val samples = history + PenSample(x, y, pressure)
    fun coordsOf(sample: PenSample) = arrayOf(MotionEvent.PointerCoords().apply { this.x = sample.x; this.y = sample.y; this.pressure = sample.pressure })
    val source = if (tool == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS
    val event = MotionEvent.obtain(downTime, downTime + 10, action, 1, properties, coordsOf(samples.first()), 0, buttons, 1f, 1f, 0, 0, source, 0)
    for ((index, sample) in samples.drop(1).withIndex()) event.addBatch(downTime + 11L + index, coordsOf(sample), 0)
    try {
        activity.window.decorView.dispatchTouchEvent(event)
    } finally {
        event.recycle()
    }
}

/**
 * Жест из касания в первой точке, движения по остальным и подъёма (или отмены системой, [isCancelled]) в последней.
 * [liftAt] — подъём в другой точке, чем последнее движение: подъём приходит без движения и может оказаться не там, где
 * последнее движение. [pressures] — нажим в каждой точке жеста по порядку; при подъёме нажим 0, как у настоящего пера.
 */
internal fun dispatchGesture(
    activity: ComponentActivity,
    rule: ComposeContentTestRule,
    tool: Int,
    points: List<Pair<Float, Float>>,
    buttons: Int = MotionEvent.BUTTON_PRIMARY,
    isCancelled: Boolean = false,
    liftAt: Pair<Float, Float>? = null,
    pressures: List<Float>? = null,
) {
    val downTime = 1_000L
    val lift = liftAt ?: points.last()
    fun pressureAt(index: Int) = pressures?.get(index) ?: 1f
    rule.runOnUiThread {
        dispatchPointer(activity, tool, MotionEvent.ACTION_DOWN, points.first().first, points.first().second, downTime, buttons, pressureAt(0))
        for ((index, point) in points.withIndex().drop(1)) {
            dispatchPointer(activity, tool, MotionEvent.ACTION_MOVE, point.first, point.second, downTime, buttons, pressureAt(index))
        }
        val last = if (isCancelled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP
        dispatchPointer(activity, tool, last, lift.first, lift.second, downTime, buttons, if (pressures == null) 1f else 0f)
    }
    rule.waitForIdle()
}
