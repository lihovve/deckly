package app.deckly.spike

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.NeoFountainPenWrapper
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Отрисовка сохранённого штриха ([SpikeStroke]) тремя способами по [PenSettings.strokeRender]:
 * ровная линия, своя с толщиной по нажиму и перо SDK Onyx. Им же рисуется штрих, который ещё идёт
 * в режиме «Canvas». Заодно пишет в журнал данные для подгонки своей кривой под кривую SDK.
 */
object StrokeRenderer {
    /** Доля базовой толщины при нулевом нажиме в своей отрисовке; подгоняется по `PEN_FOUNTAIN_CURVE`. */
    const val MIN_WIDTH_FACTOR = 0.3f

    private const val CURVE_MAX_PAIRS = 12
    private const val SDK_SCALE = 1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isFirstSdkCall = true
    private var lastSdkError: String? = null

    /** Толщина отрезка при нажиме 0..1: даже при нулевом нажиме линия тонкая, но видимая. */
    fun computePressureWidth(baseWidth: Float, pressure: Float): Float =
        baseWidth * (MIN_WIDTH_FACTOR + (1f - MIN_WIDTH_FACTOR) * pressure.coerceIn(0f, 1f))

    /** Запас прямоугольника вокруг штриха: ручка SDK может выйти за базовую толщину. */
    fun computeMargin(stroke: SpikeStroke): Int = computeMargin(stroke.width)

    fun computeMargin(width: Float): Int = ceil(width).toInt() * 2 + 2

    // Все способы рисуют одной кистью, поэтому под одной блокировкой: штрих листа Onyx рисуется
    // из потока колбэков, а текущий штрих Canvas — из потока интерфейса.
    @Synchronized
    fun drawStroke(canvas: Canvas, stroke: SpikeStroke) {
        if (stroke.pointCount == 0) return
        when (PenSettings.strokeRender) {
            StrokeRender.CONSTANT -> drawConstant(canvas, stroke)
            StrokeRender.OWN -> drawOwn(canvas, stroke)
            StrokeRender.SDK -> if (SdkStatus.isPenAvailable) drawSdk(canvas, stroke) else drawOwn(canvas, stroke)
        }
    }

    /** Данные штриха для подгонки: `PEN_PRESSURE` всегда, `PEN_FOUNTAIN_CURVE` для ручки при поднятом SDK. */
    fun logStroke(stroke: SpikeStroke) {
        logPressure(stroke)
        if (PenSettings.penStyle == PenStyle.FOUNTAIN && SdkStatus.isPenAvailable) {
            logFountainCurve(stroke)
        }
    }

    // --- способы рисования ---------------------------------------------------------------------

    private fun drawConstant(canvas: Canvas, stroke: SpikeStroke) {
        preparePaint(Paint.Style.STROKE, stroke.width)
        if (stroke.pointCount == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(stroke.x(0), stroke.y(0), stroke.width / 2, paint)
            return
        }
        val path = Path().apply {
            moveTo(stroke.x(0), stroke.y(0))
            for (index in 1 until stroke.pointCount) {
                lineTo(stroke.x(index), stroke.y(index))
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawOwn(canvas: Canvas, stroke: SpikeStroke) {
        if (stroke.pointCount == 1) {
            preparePaint(Paint.Style.FILL, stroke.width)
            val diameter = computePressureWidth(stroke.width, stroke.pressure(0))
            canvas.drawCircle(stroke.x(0), stroke.y(0), diameter / 2, paint)
            return
        }
        preparePaint(Paint.Style.STROKE, stroke.width)
        for (index in 1 until stroke.pointCount) {
            val pressure = (stroke.pressure(index - 1) + stroke.pressure(index)) / 2
            paint.strokeWidth = computePressureWidth(stroke.width, pressure)
            canvas.drawLine(stroke.x(index - 1), stroke.y(index - 1), stroke.x(index), stroke.y(index), paint)
        }
    }

    // Если процесс упадёт в нативном коде SDK, последней строкой в logcat останется PEN_RENDER_SDK call=first.
    private fun drawSdk(canvas: Canvas, stroke: SpikeStroke) {
        if (isFirstSdkCall) {
            isFirstSdkCall = false
            SpikeLog.write("PEN_RENDER_SDK", "call" to "first")
        }
        try {
            preparePaint(Paint.Style.STROKE, stroke.width)
            NeoFountainPenWrapper.drawStroke(
                canvas,
                paint,
                toTouchPoints(stroke),
                SDK_SCALE,
                stroke.width,
                SdkStatus.maxTouchPressure,
                false,
            )
        } catch (error: Throwable) {
            reportSdkError(error)
            drawOwn(canvas, stroke)
        }
    }

    // Лист перерисовывает все штрихи при каждом обновлении, поэтому одну и ту же ошибку пишем один раз.
    private fun reportSdkError(error: Throwable) {
        val text = error.toString()
        if (text == lastSdkError) return
        lastSdkError = text
        SpikeLog.write("PEN_RENDER_SDK", "error" to text)
    }

    private fun preparePaint(style: Paint.Style, width: Float) {
        paint.color = Color.BLACK
        paint.style = style
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = width
    }

    // Нажим в SDK сырой (до maxTouchPressure), толщина точки — базовая; затем SDK пересчитывает её.
    private fun toTouchPoints(stroke: SpikeStroke): List<TouchPoint> {
        val maxPressure = SdkStatus.maxTouchPressure
        val points = ArrayList<TouchPoint>(stroke.pointCount)
        for (index in 0 until stroke.pointCount) {
            points += TouchPoint(
                stroke.x(index),
                stroke.y(index),
                stroke.pressure(index) * maxPressure,
                stroke.width,
                0L,
            )
        }
        return points
    }

    // --- журнал --------------------------------------------------------------------------------

    private fun logPressure(stroke: SpikeStroke) {
        if (stroke.pointCount == 0) return
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var sum = 0f
        for (index in 0 until stroke.pointCount) {
            val pressure = stroke.pressure(index)
            min = minOf(min, pressure)
            max = maxOf(max, pressure)
            sum += pressure
        }
        SpikeLog.write(
            "PEN_PRESSURE",
            "points" to stroke.pointCount,
            "min" to format(min),
            "max" to format(max),
            "avg" to format(sum / stroke.pointCount),
        )
    }

    // Что SDK делает с нажимом: до CURVE_MAX_PAIRS равномерных точек результата как «нажим:толщина».
    private fun logFountainCurve(stroke: SpikeStroke) {
        try {
            val maxPressure = SdkStatus.maxTouchPressure
            val result = NeoFountainPenWrapper.computeStrokePoints(
                toTouchPoints(stroke),
                SDK_SCALE,
                stroke.width,
                maxPressure,
            )
            if (result.isNullOrEmpty()) {
                SpikeLog.write("PEN_FOUNTAIN_CURVE", "width" to stroke.width, "pairs" to "none", "in" to stroke.pointCount, "out" to 0)
                return
            }
            val count = minOf(CURVE_MAX_PAIRS, result.size)
            val pairs = (0 until count).joinToString(",") { slot ->
                val index = if (count == 1) 0 else (slot * (result.size - 1).toDouble() / (count - 1)).roundToInt()
                val point = result[index]
                "${format(point.pressure / maxPressure)}:${format(point.size)}"
            }
            SpikeLog.write(
                "PEN_FOUNTAIN_CURVE",
                "width" to stroke.width,
                "pairs" to pairs,
                "in" to stroke.pointCount,
                "out" to result.size,
            )
        } catch (error: Throwable) {
            SpikeLog.write("PEN_FOUNTAIN_CURVE", "error" to error)
        }
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.2f", value)
}
