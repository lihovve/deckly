package app.deckly.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import kotlin.math.ceil

/**
 * Запасной холст: обычный `View` с событиями `MotionEvent`. Включается, если Onyx SDK не поднялся; линия
 * здесь заметно отстаёт от пера. Рисует штрихи тем же кодом ([drawStrokes]), что и остальное приложение.
 *
 * Рисует стилус без кнопки. Ластик пера и стилус с нажатой кнопкой стирают. Палец не рисует и не стирает, но
 * касание поглощается: ладонь не должна дойти до того, что под листом.
 *
 * Не проверено на планшете: тесты идут на JVM (Robolectric). Лист Onyx — [OnyxDrawingView].
 */
internal class CanvasDrawingView(context: Context) : View(context), DrawingSurface {
    private enum class Gesture { NONE, DRAW, ERASE, IGNORE }

    private val book = ShownStrokes()
    private val gesturePoints = ArrayList<RawPoint>()
    private var gesture = Gesture.NONE

    private var penWidthPx = 0f
    private var isPenEnabled = true
    private var onStroke: (Stroke) -> Unit = {}
    private var onErase: (List<StrokePoint>, Float) -> Unit = { _, _ -> }

    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    override fun bind(state: DrawingState) {
        penWidthPx = state.penWidthPx
        onStroke = state.onStroke
        onErase = state.onErase
        // Диалог открылся посреди жеста: штрих недорисован, отдавать его хозяину не нужно.
        if (isPenEnabled && !state.isPenEnabled) cancelGesture()
        isPenEnabled = state.isPenEnabled

        if (book.updateFromHost(state.strokes) != StrokeChange.None) invalidate()
    }

    // Фон — прямоугольник листа, а не drawColor: тот заливает всю область отсечения, а Compose не отсекает
    // AndroidView по границам, и заливка закрасила бы всё, что нарисовано до листа.
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val scale = CardGeometry.computeScale(width.toFloat())
        drawStrokes(canvas, book.shown, scale)
        if (gesture == Gesture.DRAW) {
            buildStroke(gesturePoints, penWidthPx, scale)?.let { drawStrokes(canvas, listOf(it), scale) }
        }
    }

    // Лист рисуют стилусом, нажатия у него нет: performClick не нужен, а касание пальцем поглощается.
    @Suppress("ClickableViewAccessibility")
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
        gesturePoints.clear()
        val toolType = event.getToolType(0)
        val isButtonPressed = event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        gesture = when {
            !isPenEnabled -> Gesture.IGNORE
            toolType == MotionEvent.TOOL_TYPE_ERASER -> Gesture.ERASE
            toolType == MotionEvent.TOOL_TYPE_STYLUS && isButtonPressed -> Gesture.ERASE
            toolType == MotionEvent.TOOL_TYPE_STYLUS -> Gesture.DRAW
            else -> Gesture.IGNORE
        }
        if (gesture != Gesture.IGNORE) collectPoints(event, includeHistory = false)
    }

    private fun continueGesture(event: MotionEvent) {
        if (gesture == Gesture.NONE || gesture == Gesture.IGNORE) return
        collectPoints(event, includeHistory = true)
    }

    private fun finishGesture() {
        val scale = CardGeometry.computeScale(width.toFloat())
        when (gesture) {
            Gesture.DRAW -> buildStroke(gesturePoints, penWidthPx, scale)?.let { stroke ->
                // Штрих виден сразу, не дожидаясь хоста: тот присылает список после записи.
                book.appendLocal(stroke)
                onStroke(stroke)
            }
            Gesture.ERASE -> onErase(buildErasePath(gesturePoints, scale), computeEraserRadius(scale))
            Gesture.NONE, Gesture.IGNORE -> Unit
        }
        gesture = Gesture.NONE
        gesturePoints.clear()
    }

    private fun cancelGesture() {
        val wasDrawing = gesture == Gesture.DRAW
        gesture = Gesture.NONE
        gesturePoints.clear()
        if (wasDrawing) invalidate()
    }

    // --- точки ---------------------------------------------------------------------------------

    // У каждой точки свой нажим: у исторических точек пачки он свой, у текущей — текущий.
    private fun collectPoints(event: MotionEvent, includeHistory: Boolean) {
        val previousSize = gesturePoints.size
        if (includeHistory) {
            for (index in 0 until event.historySize) {
                gesturePoints += RawPoint(
                    event.getHistoricalX(0, index),
                    event.getHistoricalY(0, index),
                    event.getHistoricalPressure(0, index),
                )
            }
        }
        gesturePoints += RawPoint(event.getX(0), event.getY(0), event.getPressure(0))
        if (gesture == Gesture.DRAW) invalidateNewSegments(previousSize)
    }

    // Обновляется только то, что добавили новые точки, вместе с отрезком от последней прежней.
    private fun invalidateNewSegments(previousSize: Int) {
        val margin = ceil(penWidthPx / 2f + 2.5f).toInt()
        val firstIndex = maxOf(previousSize - 1, 0)
        val touched = gesturePoints.subList(firstIndex, gesturePoints.size)
        val left = touched.minOf { it.x }.toInt() - margin
        val top = touched.minOf { it.y }.toInt() - margin
        val right = ceil(touched.maxOf { it.x }).toInt() + margin
        val bottom = ceil(touched.maxOf { it.y }).toInt() + margin
        invalidateRegion(left, top, right, bottom)
    }

    // View.invalidate(l, t, r, b) объявлен устаревшим с API 28 (с аппаратным рендером обновляется вся
    // область), но остаётся единственным способом подсказать грязный прямоугольник.
    @Suppress("DEPRECATION")
    private fun invalidateRegion(left: Int, top: Int, right: Int, bottom: Int) {
        invalidate(left, top, right, bottom)
    }
}
