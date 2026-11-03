package app.deckly.spike

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = PenDefaults.STROKE_WIDTH
    }

    override fun pause() = Unit

    override fun resume() = Unit

    override fun redrawAll() {
        invalidate()
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
        SpikeLog.line("MOTION", "tool" to toolName(tool), "buttons" to "0x" + Integer.toHexString(buttons))

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
            SpikeLog.line("PEN_FINGER_IGNORED")
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
        StrokeStore.add(SpikeStroke(strokeValues.toFloatArray(), PenDefaults.STROKE_WIDTH))
        SpikeLog.line("PEN_STROKE", "points" to strokeValues.size / SpikeStroke.VALUES_PER_POINT)
        strokeValues.clear()
        refreshPartially(gestureBounds)
    }

    private fun finishErase() {
        SpikeLog.line("PEN_ERASE", "removed" to removedInGesture, "rect" to gestureBounds?.toShortString())
        erasePath.clear()
        refreshPartially(gestureBounds)
    }

    // --- точки ---------------------------------------------------------------------------------

    private fun collectPoints(event: MotionEvent, includeHistory: Boolean) {
        val newPoints = ArrayList<PointF>()
        if (includeHistory) {
            for (index in 0 until event.historySize) {
                newPoints += PointF(event.getHistoricalX(0, index), event.getHistoricalY(0, index))
            }
        }
        newPoints += PointF(event.getX(0), event.getY(0))

        when (gesture) {
            Gesture.DRAW -> addToStroke(newPoints, event.getPressure(0))
            Gesture.ERASE -> eraseWith(newPoints)
            Gesture.NONE, Gesture.IGNORE -> Unit
        }
    }

    private fun addToStroke(points: List<PointF>, pressure: Float) {
        for (point in points) {
            strokeValues += point.x
            strokeValues += point.y
            strokeValues += pressure
        }
        val dirty = boundsOf(points, PenDefaults.STROKE_WIDTH.toInt() + 2)
        extendBounds(dirty)
        invalidateRegion(dirty)
    }

    private fun eraseWith(points: List<PointF>) {
        erasePath += points
        val before = StrokeStore.count()
        val dirty = StrokeStore.eraseAlong(points, PenDefaults.ERASE_RADIUS)
        removedInGesture += before - StrokeStore.count()
        if (dirty != null) {
            extendBounds(dirty)
            invalidateRegion(dirty)
        }
    }

    private fun boundsOf(points: List<PointF>, margin: Int): Rect {
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

    private fun drawLiveStroke(canvas: Canvas) {
        val count = strokeValues.size / SpikeStroke.VALUES_PER_POINT
        for (index in 1 until count) {
            val from = (index - 1) * SpikeStroke.VALUES_PER_POINT
            val to = index * SpikeStroke.VALUES_PER_POINT
            canvas.drawLine(strokeValues[from], strokeValues[from + 1], strokeValues[to], strokeValues[to + 1], livePaint)
        }
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
            SpikeLog.line("REFRESH_REGION", "error" to error)
            invalidateRegion(rect)
        }
    }

    // View.invalidate(Rect) объявлен устаревшим с API 28 (с аппаратным рендером обновляется вся область),
    // но остаётся единственным способом подсказать грязный прямоугольник.
    @Suppress("DEPRECATION")
    private fun invalidateRegion(rect: Rect) {
        invalidate(rect)
    }

    private fun toolName(tool: Int): String = when (tool) {
        MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
        MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
        MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
        MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
        else -> "UNKNOWN($tool)"
    }
}
