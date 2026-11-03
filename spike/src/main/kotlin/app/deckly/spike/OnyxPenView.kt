package app.deckly.spike

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.device.Device
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList

/**
 * Лист на сыром пере Onyx (`TouchHelper`). [useSfRender] выбирает быстрый рендер прошивки через
 * `FEATURE_SF_TOUCH_RENDER`; без него `TouchHelper.create` сам решает по имени устройства ввода.
 *
 * Пока перо включено, прошивка замораживает экран: штрихи видны как их рисует прошивка, а поверхность
 * обновляется только после [pause].
 */
class OnyxPenView(context: Context, private val useSfRender: Boolean) :
    SurfaceView(context), SurfaceHolder.Callback, PenSheet {

    private var touchHelper: TouchHelper? = null
    private val surfaceLock = Any()

    // Пауза, выставленная владельцем листа (активити, окно без фокуса, цикл «Статус»). Пока она стоит,
    // отложенная фиксация стирания не должна включать перо.
    @Volatile private var isPausedByOwner = false

    // Фиксация стирания (см. commitErase): состояние под eraseLock, потому что колбэки пера могут
    // прийти не в том потоке, где срабатывают отложенные шаги.
    private val eraseLock = Any()
    private var pendingEraseRect: Rect? = null
    private var eraseCommitStartedAt = 0L
    private var isEraseGcDone = false

    private val gcStep = Runnable { runEraseGcStep() }
    private val resumeStep = Runnable { runEraseResumeStep() }

    private val callback = object : RawInputCallback() {
        override fun onBeginRawDrawing(shortcutDrawing: Boolean, point: TouchPoint) {
            logPoint("PEN_BEGIN", point)
        }

        override fun onEndRawDrawing(outLimitRegion: Boolean, point: TouchPoint) {
            logPoint("PEN_END", point)
        }

        override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint) = Unit

        override fun onRawDrawingTouchPointListReceived(pointList: TouchPointList) {
            handleStroke(pointList)
        }

        override fun onBeginRawErasing(shortcutErasing: Boolean, point: TouchPoint) {
            logPoint("ERASE_BEGIN", point)
        }

        override fun onEndRawErasing(outLimitRegion: Boolean, point: TouchPoint) {
            logPoint("ERASE_END", point)
        }

        override fun onRawErasingTouchPointMoveReceived(point: TouchPoint) = Unit

        override fun onRawErasingTouchPointListReceived(pointList: TouchPointList) {
            handleErase(pointList)
        }
    }

    init {
        holder.addCallback(this)
    }

    // --- SurfaceHolder.Callback ----------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        createTouchHelper()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawSurface(null)
        startSession()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        cancelEraseCommit()
        val helper = touchHelper ?: return
        try {
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()
        } catch (error: Throwable) {
            SpikeLog.write("PEN_CLOSE", "error" to error)
        }
        touchHelper = null
    }

    // --- PenSheet ------------------------------------------------------------------------------

    override fun pause() {
        isPausedByOwner = true
        cancelEraseCommit()
        disableRawDrawing()
    }

    override fun resume() {
        isPausedByOwner = false
        enableRawDrawing()
    }

    private fun disableRawDrawing() {
        try {
            touchHelper?.setRawDrawingEnabled(false)
        } catch (error: Throwable) {
            SpikeLog.write("PEN_PAUSE", "error" to error)
        }
    }

    // setRawDrawingEnabled(true) выключает трек ластика и сбрасывает стиль, поэтому их ставим заново.
    private fun enableRawDrawing() {
        val helper = touchHelper ?: return
        try {
            helper.setRawDrawingEnabled(true)
            enableEraserTrack(helper)
            applyPenStyle(helper)
        } catch (error: Throwable) {
            SpikeLog.write("PEN_RESUME", "error" to error)
        }
    }

    override fun redrawAll() {
        drawSurface(null)
        refreshRegion(Rect(0, 0, width, height))
    }

    // --- создание и сессия ---------------------------------------------------------------------

    private fun createTouchHelper() {
        try {
            val helper = if (useSfRender) {
                TouchHelper.create(this, TouchHelper.FEATURE_SF_TOUCH_RENDER, callback)
            } else {
                TouchHelper.create(this, callback)
            }
            touchHelper = helper
            SpikeLog.write("PEN_RENDER", "class" to describeRenders(helper), "requestedSf" to useSfRender)
        } catch (error: Throwable) {
            SpikeLog.write("PEN_CREATE", "error" to error)
        }
    }

    // TouchHelper не отдаёт выбранный рендер наружу, но держит его в приватном списке.
    private fun describeRenders(helper: TouchHelper): String =
        try {
            val field = TouchHelper::class.java.getDeclaredField("touchRenderList")
            field.isAccessible = true
            val renders = field.get(helper) as? List<*>
            renders?.joinToString("+") { it?.javaClass?.simpleName ?: "null" }
                ?.takeIf { it.isNotEmpty() } ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }

    // Порядок шагов из Notable `setupSurface`; open и close сбрасывают стиль в PENCIL, поэтому стиль
    // ставится после openRawDrawing().
    private fun startSession() {
        val helper = touchHelper ?: return
        val started = SystemClock.elapsedRealtime()
        var previous = started
        val steps = mutableListOf<String>()
        fun mark(step: String) {
            val now = SystemClock.elapsedRealtime()
            steps += "$step:${now - previous}"
            previous = now
        }
        try {
            helper.setRawDrawingEnabled(false)
            mark("disable")
            helper.closeRawDrawing()
            mark("close")
            val limit = Rect()
            getLocalVisibleRect(limit)
            helper.setLimitRect(listOf(limit))
            mark("limit")
            helper.openRawDrawing()
            mark("open")
            helper.setRawDrawingEnabled(true)
            mark("enable")
            enableEraserTrack(helper)
            mark("eraser")
            applyPenStyle(helper)
            mark("style")
            // Поверхность могла пересоздаться, пока владелец держит паузу (окно без фокуса): перо
            // включит resume().
            if (isPausedByOwner) {
                helper.setRawDrawingEnabled(false)
                mark("paused")
            }
            SpikeLog.write(
                "PEN_SESSION",
                "ms" to (previous - started),
                "steps" to steps.joinToString(","),
                "limit" to limit.toShortString(),
            )
        } catch (error: Throwable) {
            SpikeLog.write("PEN_SESSION", "error" to error, "steps" to steps.joinToString(","))
        }
    }

    private fun enableEraserTrack(helper: TouchHelper) {
        try {
            Device.currentDevice().setStrokeParameters(
                ERASER_STROKE_ID,
                floatArrayOf(ERASER_TRACK_WIDTH, 0.5f, 1f),
            )
            helper.setEraserRawDrawingEnabled(true, ERASER_STROKE_ID)
        } catch (error: Throwable) {
            SpikeLog.write("PEN_ERASER_TRACK", "error" to error)
        }
    }

    private fun applyPenStyle(helper: TouchHelper) {
        helper.setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
        helper.setStrokeWidth(PenDefaults.STROKE_WIDTH)
        helper.setStrokeColor(Color.BLACK)
    }

    // --- обратные вызовы пера -------------------------------------------------------------------
    // SFTouchRender доставляет их через hostView.post(), то есть в потоке интерфейса (по байткоду
    // SDK 1.5.8); для остальных рендеров поток не проверялся, поэтому код на него не полагается.

    private fun logPoint(event: String, point: TouchPoint) {
        SpikeLog.write(
            event,
            "x" to point.x.toInt(),
            "y" to point.y.toInt(),
            "pressure" to point.pressure,
            "ts" to point.timestamp,
        )
    }

    private fun handleStroke(pointList: TouchPointList) {
        val points = pointList.points
        if (points.isEmpty()) return
        val values = FloatArray(points.size * SpikeStroke.VALUES_PER_POINT)
        points.forEachIndexed { index, point ->
            values[index * SpikeStroke.VALUES_PER_POINT] = point.x
            values[index * SpikeStroke.VALUES_PER_POINT + 1] = point.y
            values[index * SpikeStroke.VALUES_PER_POINT + 2] = point.pressure
        }
        val stroke = SpikeStroke(values, PenDefaults.STROKE_WIDTH)
        StrokeStore.add(stroke)
        SpikeLog.write(
            "PEN_STROKE",
            "points" to points.size,
            "firstTs" to points.first().timestamp,
            "lastTs" to points.last().timestamp,
            "nowMs" to System.currentTimeMillis(),
            "uptimeMs" to SystemClock.uptimeMillis(),
        )
        // Поверхность держим в соответствии со штрихами, чтобы после pause() ничего не пропало.
        drawSurface(computeStrokeBounds(points))
    }

    private fun handleErase(pointList: TouchPointList) {
        val path = pointList.points.map { PointF(it.x, it.y) }
        val before = StrokeStore.countStrokes()
        val removedRect = StrokeStore.eraseAlong(path, PenDefaults.ERASE_RADIUS)
        val removed = before - StrokeStore.countStrokes()
        // Серый след ластика лежит вдоль пути, а не только на удалённых штрихах.
        val dirty = StrokeStore.union(removedRect, StrokeStore.computePathBounds(path, ERASER_TRACK_WIDTH))
        SpikeLog.write("PEN_ERASE", "removed" to removed, "rect" to dirty?.toShortString())
        if (dirty == null) return
        commitErase(dirty)
    }

    private fun computeStrokeBounds(points: List<TouchPoint>): Rect {
        val first = points.first()
        val bounds = Rect(first.x.toInt(), first.y.toInt(), first.x.toInt(), first.y.toInt())
        for (point in points) {
            bounds.union(point.x.toInt(), point.y.toInt())
        }
        val margin = PenDefaults.STROKE_WIDTH.toInt() + 2
        bounds.inset(-margin, -margin)
        return bounds
    }

    // --- рисование и обновление экрана ---------------------------------------------------------

    // Рисует штрихи на поверхность ([dirty] == null — вся поверхность). Экран при этом не трогает.
    private fun drawSurface(dirty: Rect?) {
        synchronized(surfaceLock) {
            if (!holder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = if (dirty == null) holder.lockCanvas() else holder.lockCanvas(Rect(dirty))
                canvas?.let {
                    it.drawColor(Color.WHITE)
                    StrokeStore.drawTo(it)
                }
            } catch (error: Throwable) {
                SpikeLog.write("SURFACE_DRAW", "error" to error)
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }
    }

    // Аргументы refreshScreenRegion: левый край, верх, ширина, высота (как у Notable). Порядок
    // проверяет задача 5 по тому, обновляется ли именно этот прямоугольник.
    private fun refreshRegion(rect: Rect) {
        try {
            EpdController.refreshScreenRegion(
                this,
                rect.left,
                rect.top,
                rect.width(),
                rect.height(),
                UpdateMode.ANIMATION_MONO,
            )
        } catch (error: Throwable) {
            SpikeLog.write("REFRESH_REGION", "error" to error)
        }
    }

    // --- фиксация стирания (как коммит стирания у Notable) -------------------------------------
    //
    // Пока сырое перо включено, прошивка замораживает экран: перерисовка поверхности и обновление
    // региона не показались бы, и удалённый штрих остался бы на экране. Поэтому после стирания перо
    // выключается, поверхность перерисовывается и обновляется регион; через GC_DELAY_MS идёт GC по
    // накопленному прямоугольнику (если включён переключатель, он снимает серый след ластика на
    // Go 10.3, Notable PR #346); ещё через ERASE_RESUME_MS перо включается снова. Пока перо выключено,
    // штрихи не принимаются.

    private fun commitErase(dirty: Rect) {
        synchronized(eraseLock) {
            if (eraseCommitStartedAt == 0L) {
                eraseCommitStartedAt = SystemClock.elapsedRealtime()
                isEraseGcDone = false
                disableRawDrawing()
            }
            pendingEraseRect = StrokeStore.union(pendingEraseRect, dirty)
            removeCallbacks(gcStep)
            removeCallbacks(resumeStep)
        }
        drawSurface(dirty)
        refreshRegion(dirty)
        postDelayed(gcStep, GC_DELAY_MS)
    }

    private fun runEraseGcStep() {
        val rect = synchronized(eraseLock) { pendingEraseRect }
        val isGcEnabled = PenSettings.isGcAfterErase
        if (isGcEnabled && rect != null) {
            try {
                EpdController.invalidate(this, rect.left, rect.top, rect.right, rect.bottom, UpdateMode.GC)
                SpikeLog.write("PEN_GC", "rect" to rect.toShortString())
            } catch (error: Throwable) {
                SpikeLog.write("PEN_GC", "error" to error)
            }
        }
        synchronized(eraseLock) { isEraseGcDone = isGcEnabled }
        postDelayed(resumeStep, ERASE_RESUME_MS)
    }

    private fun runEraseResumeStep() {
        val rect: Rect?
        val pausedMs: Long
        val isGc: Boolean
        synchronized(eraseLock) {
            rect = pendingEraseRect
            pausedMs = SystemClock.elapsedRealtime() - eraseCommitStartedAt
            isGc = isEraseGcDone
            pendingEraseRect = null
            eraseCommitStartedAt = 0L
        }
        // Если владелец успел поставить паузу (активити ушла, окно потеряло фокус), перо не включаем.
        val isResumed = !isPausedByOwner
        if (isResumed) {
            enableRawDrawing()
        }
        SpikeLog.write(
            "ERASE_COMMIT",
            "rect" to rect?.toShortString(),
            "gc" to isGc,
            "pausedMs" to pausedMs,
            "resumed" to isResumed,
        )
    }

    // Отменяет отложенную фиксацию (пауза владельца, уничтожение поверхности); перо включит resume().
    private fun cancelEraseCommit() {
        synchronized(eraseLock) {
            removeCallbacks(gcStep)
            removeCallbacks(resumeStep)
            if (eraseCommitStartedAt != 0L) {
                SpikeLog.write("ERASE_COMMIT_CANCELLED", "rect" to pendingEraseRect?.toShortString())
            }
            pendingEraseRect = null
            eraseCommitStartedAt = 0L
        }
    }

    private companion object {
        const val ERASER_STROKE_ID = 8
        const val ERASER_TRACK_WIDTH = 20f
        const val GC_DELAY_MS = 200L
        const val ERASE_RESUME_MS = 150L
    }
}
