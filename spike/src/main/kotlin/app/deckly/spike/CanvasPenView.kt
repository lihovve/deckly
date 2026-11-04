package app.deckly.spike

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode

/**
 * Запасной лист: обычный `View` с событиями `MotionEvent` и частичным обновлением экрана. Нужен,
 * если SDK Onyx не поднялся или сырое перо не подошло.
 *
 * Стилус без кнопки рисует; ластик пера или стилус с нажатой кнопкой стирает; палец поглощается.
 */
class CanvasPenView(context: Context) : View(context), PenSheet {

    private enum class Gesture { NONE, DRAW, ERASE, IGNORE }

    private var gesture = Gesture.NONE
    private val strokeValues = ArrayList<Float>()
    private val erasePath = ArrayList<PointF>()
    private var gestureBounds: Rect? = null
    private var removedInGesture = 0

    override fun pause() = Unit

    override fun resume() = Unit

    override fun redrawAll() {
        invalidate()
    }

    // Стиль пера прошивки здесь не участвует; толщину и отрисовку читает StrokeRenderer при каждом рисовании.
    override fun applyPenSettings() {
        redrawAll()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        StrokeStore.drawTo(canvas)
        drawLiveStroke(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginGesture(event)
            MotionEvent.ACTION_MOVE -> continueGesture(event)
            MotionEvent.ACTION_UP -> finishGesture()
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    // --- жест ----------------------------------------------------------------------------------

    private fun beginGesture(event: MotionEvent) {
        val tool = event.getToolType(0)
        val buttons = event.buttonState
        SpikeLog.write("MOTION", "tool" to describeTool(tool), "buttons" to "0x" + Integer.toHexString(buttons))

        strokeValues.clear()
        erasePath.clear()
        gestureBounds = null
        removedInGesture = 0

        val isButtonPressed = buttons and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        gesture = when {
            tool == MotionEvent.TOOL_TYPE_ERASER -> Gesture.ERASE
            tool == MotionEvent.TOOL_TYPE_STYLUS && isButtonPressed -> Gesture.ERASE
            tool == MotionEvent.TOOL_TYPE_STYLUS -> Gesture.DRAW
            else -> Gesture.IGNORE
        }
        if (tool == MotionEvent.TOOL_TYPE_FINGER) {
            SpikeLog.write("PEN_FINGER_IGNORED")
        }
        if (gesture != Gesture.IGNORE) {
            collectPoints(event, includeHistory = false)
        }
    }

    private fun continueGesture(event: MotionEvent) {
        if (gesture == Gesture.NONE || gesture == Gesture.IGNORE) return
        collectPoints(event, includeHistory = true)
    }

    private fun finishGesture() {
        when (gesture) {
            Gesture.DRAW -> finishStroke()
            Gesture.ERASE -> finishErase()
            Gesture.NONE, Gesture.IGNORE -> Unit
        }
        gesture = Gesture.NONE
    }

    private fun cancelGesture() {
        gesture = Gesture.NONE
        strokeValues.clear()
        erasePath.clear()
        gestureBounds?.let { invalidateRegion(it) }
        gestureBounds = null
    }

    private fun finishStroke() {
        if (strokeValues.isEmpty()) return
        val stroke = SpikeStroke(strokeValues.toFloatArray(), PenSettings.strokeWidth)
        StrokeStore.add(stroke)
        SpikeLog.write("PEN_STROKE", "points" to stroke.pointCount)
        StrokeRenderer.logStroke(stroke)
        strokeValues.clear()
        refreshPartially(gestureBounds)
    }

    private fun finishErase() {
        SpikeLog.write("PEN_ERASE", "removed" to removedInGesture, "rect" to gestureBounds?.toShortString())
        erasePath.clear()
        refreshPartially(gestureBounds)
    }

    // --- точки ---------------------------------------------------------------------------------

    // У каждой точки свой нажим: у исторических точек пачки он свой, у текущей — текущий.
    private fun collectPoints(event: MotionEvent, includeHistory: Boolean) {
        val newPoints = ArrayList<PointF>()
        val newPressures = ArrayList<Float>()
        if (includeHistory) {
            for (index in 0 until event.historySize) {
                newPoints += PointF(event.getHistoricalX(0, index), event.getHistoricalY(0, index))
                newPressures += event.getHistoricalPressure(0, index).coerceIn(0f, 1f)
            }
        }
        newPoints += PointF(event.getX(0), event.getY(0))
        newPressures += event.getPressure(0).coerceIn(0f, 1f)

        when (gesture) {
            Gesture.DRAW -> addToStroke(newPoints, newPressures)
            Gesture.ERASE -> eraseWith(newPoints)
            Gesture.NONE, Gesture.IGNORE -> Unit
        }
    }

    private fun addToStroke(points: List<PointF>, pressures: List<Float>) {
        for ((index, point) in points.withIndex()) {
            strokeValues += point.x
            strokeValues += point.y
            strokeValues += pressures[index]
        }
        val dirty = computeBounds(points, StrokeRenderer.computeMargin(PenSettings.strokeWidth))
        extendBounds(dirty)
        invalidateRegion(dirty)
    }

    private fun eraseWith(points: List<PointF>) {
        erasePath += points
        val before = StrokeStore.countStrokes()
        val dirty = StrokeStore.eraseAlong(points, PenDefaults.ERASE_RADIUS)
        removedInGesture += before - StrokeStore.countStrokes()
        if (dirty != null) {
            extendBounds(dirty)
            invalidateRegion(dirty)
        }
    }

    private fun computeBounds(points: List<PointF>, margin: Int): Rect {
        val first = points.first()
        val bounds = Rect(first.x.toInt(), first.y.toInt(), first.x.toInt(), first.y.toInt())
        for (point in points) {
            bounds.union(point.x.toInt(), point.y.toInt())
        }
        bounds.inset(-margin, -margin)
        return bounds
    }

    private fun extendBounds(rect: Rect) {
        gestureBounds = StrokeStore.union(gestureBounds, rect)
    }

    // Текущий штрих рисуется тем же StrokeRenderer, что и сохранённые: в Canvas нажим виден под пером.
    private fun drawLiveStroke(canvas: Canvas) {
        if (strokeValues.isEmpty()) return
        StrokeRenderer.drawStroke(canvas, SpikeStroke(strokeValues.toFloatArray(), PenSettings.strokeWidth))
    }

    // --- обновление экрана ---------------------------------------------------------------------

    // Частичное обновление прямоугольника жеста. Без поднятого SDK или при его ошибке — обычный
    // invalidate().
    private fun refreshPartially(rect: Rect?) {
        if (rect == null) return
        if (!SdkStatus.isPenAvailable) {
            invalidateRegion(rect)
            return
        }
        try {
            EpdController.invalidate(this, rect.left, rect.top, rect.right, rect.bottom, UpdateMode.DU)
        } catch (error: Throwable) {
            SpikeLog.write("REFRESH_REGION", "error" to error)
            invalidateRegion(rect)
        }
    }

    // View.invalidate(Rect) объявлен устаревшим с API 28 (с аппаратным рендером обновляется вся область),
    // но остаётся единственным способом подсказать грязный прямоугольник.
    @Suppress("DEPRECATION")
    private fun invalidateRegion(rect: Rect) {
        invalidate(rect)
    }

    private fun describeTool(tool: Int): String = when (tool) {
        MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
        MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
        MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
        MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
        else -> "UNKNOWN($tool)"
    }
}
