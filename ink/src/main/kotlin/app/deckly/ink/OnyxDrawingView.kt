package app.deckly.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList

/**
 * Лист на сыром пере Onyx (`TouchHelper.create(view, callback)`): штрих под пером рисует прошивка, лист
 * хранит поверхность в соответствии со штрихами хозяина, чтобы после выключения пера ничего не пропало.
 *
 * Здесь только привязка к SDK. Когда перо включено ([PenGate]), какие штрихи показаны и что изменилось
 * ([ShownStrokes]), перевод точек в единицы карточки ([buildStroke]) проверены тестами на JVM.
 *
 * ПРОВЕРЕНО ТОЛЬКО НА JVM, а сам класс не проверялся и на JVM (там нет прошивки). На планшете это проверит
 * задача 12 плана 4. Не подтверждено устройством именно здесь:
 * - инструмент [DrawingTool.ERASER] через `setRawDrawingRenderEnabled(false)`: приходят ли точки штриха в
 *   колбэк рисования и не рисует ли прошивка линию; прототип проверял только ластик кнопкой пера;
 * - цикл «перо выключено → кадр → 150 мс → перо включено» для `uiRevision` и замены штрихов: виден ли
 *   интерфейс на экране, не пропадают ли штрихи;
 * - окно без фокуса или невидимое вместо явных `pause`/`resume` активити прототипа;
 * - поток колбэков пера (по байткоду SDK 1.5.8 `SFTouchRender` отдаёт их в потоке интерфейса, но код на
 *   это не полагается и сам переводит вызовы в поток интерфейса).
 */
internal class OnyxDrawingView(context: Context) :
    SurfaceView(context), SurfaceHolder.Callback, DrawingSurface {

    private val book = ShownStrokes()
    private val surfaceLock = Any()
    private var touchHelper: TouchHelper? = null

    private var tool = DrawingTool.PEN
    private var penWidthPx = 0f
    private var uiRevision: Int? = null
    private var onStroke: (Stroke) -> Unit = {}
    private var onErase: (List<StrokePoint>, Float) -> Unit = { _, _ -> }

    private val gate = PenGate(
        pen = object : PenPort {
            override fun enable() = enableRawDrawing()

            override fun disable() = disableRawDrawing()
        },
        scheduler = object : PenScheduler {
            override fun afterNextFrame(action: () -> Unit) {
                Choreographer.getInstance().postFrameCallback { action() }
            }

            override fun afterDelay(delayMs: Long, action: () -> Unit) {
                postDelayed({ action() }, delayMs)
            }
        },
    )

    private val callback = object : RawInputCallback() {
        override fun onBeginRawDrawing(shortcutDrawing: Boolean, point: TouchPoint) = Unit

        override fun onEndRawDrawing(outLimitRegion: Boolean, point: TouchPoint) = Unit

        override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint) = Unit

        override fun onRawDrawingTouchPointListReceived(pointList: TouchPointList) {
            val points = collectPoints(pointList)
            runOnUi { if (tool == DrawingTool.ERASER) reportErase(points) else reportStroke(points) }
        }

        override fun onBeginRawErasing(shortcutErasing: Boolean, point: TouchPoint) = Unit

        override fun onEndRawErasing(outLimitRegion: Boolean, point: TouchPoint) = Unit

        override fun onRawErasingTouchPointMoveReceived(point: TouchPoint) = Unit

        // Кнопка пера стирает при любом инструменте.
        override fun onRawErasingTouchPointListReceived(pointList: TouchPointList) {
            val points = collectPoints(pointList)
            runOnUi { reportErase(points) }
        }
    }

    init {
        holder.addCallback(this)
    }

    // --- DrawingSurface ------------------------------------------------------------------------

    override fun bind(state: DrawingState) {
        val isPenConfigChanged = state.tool != tool || state.penWidthPx != penWidthPx
        tool = state.tool
        penWidthPx = state.penWidthPx
        onStroke = state.onStroke
        onErase = state.onErase

        gate.setHostEnabled(state.isPenEnabled)
        // Инструмент и толщина обычно приходят вместе с новым uiRevision (цикл ниже включит перо с новыми
        // настройками); если нет, настройки применяются сразу.
        if (isPenConfigChanged && gate.isOn) reconfigurePen()

        val change = book.updateFromHost(state.strokes)
        val previousRevision = uiRevision
        uiRevision = state.uiRevision
        val isUiChanged = previousRevision != null && previousRevision != state.uiRevision

        // Пока перо включено, прошивка замораживает экран: удаление, возврат и смена стороны, как и
        // изменение интерфейса, видны только после цикла «перо выключено → кадр → перо включено».
        if (isUiChanged || change is StrokeChange.Replaced) gate.requestCycle()
        when (change) {
            StrokeChange.None -> Unit
            is StrokeChange.Appended -> drawSurface(computeBoundsOf(change.added))
            is StrokeChange.Replaced -> commitReplacement(change)
        }
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
        gate.onSessionClosed()
        val helper = touchHelper ?: return
        try {
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()
        } catch (error: Throwable) {
            Log.w(TAG, "Сессия пера не закрылась", error)
        }
        touchHelper = null
    }

    // Перо работает, только пока окно в фокусе и видно: шторка уведомлений и системные окна ложатся поверх
    // сырого пера, а в фоне активити его нужно отпустить.
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateWindowActive()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateWindowActive()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateWindowActive()
    }

    private fun updateWindowActive() {
        gate.setWindowActive(hasWindowFocus() && windowVisibility == View.VISIBLE)
    }

    // --- сессия пера ---------------------------------------------------------------------------

    private fun createTouchHelper() {
        try {
            touchHelper = TouchHelper.create(this, callback)
        } catch (error: Throwable) {
            Log.w(TAG, "TouchHelper не создан", error)
        }
    }

    // Порядок шагов из прототипа (по Notable `setupSurface`); открытие сессии на Go 10.3 Gen II стоит
    // 70–140 мс, поэтому она не пересоздаётся на каждый штрих. open и close сбрасывают стиль, поэтому
    // стиль ставится после openRawDrawing() и после каждого setRawDrawingEnabled(true).
    private fun startSession() {
        val helper = touchHelper ?: return
        try {
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()
            val limit = Rect()
            getLocalVisibleRect(limit)
            helper.setLimitRect(listOf(limit))
            helper.openRawDrawing()
            helper.setRawDrawingEnabled(true)
            configurePen(helper)
        } catch (error: Throwable) {
            Log.w(TAG, "Сессия пера не открылась", error)
            return
        }
        gate.onSessionOpened()
    }

    private fun enableRawDrawing() {
        val helper = touchHelper ?: return
        try {
            helper.setRawDrawingEnabled(true)
            configurePen(helper)
        } catch (error: Throwable) {
            Log.w(TAG, "Перо не включилось", error)
        }
    }

    private fun disableRawDrawing() {
        try {
            touchHelper?.setRawDrawingEnabled(false)
        } catch (error: Throwable) {
            Log.w(TAG, "Перо не выключилось", error)
        }
    }

    private fun reconfigurePen() {
        val helper = touchHelper ?: return
        try {
            configurePen(helper)
        } catch (error: Throwable) {
            Log.w(TAG, "Настройки пера не применились", error)
        }
    }

    // Включение пера сбрасывает трек ластика, стиль и вид отрисовки, поэтому всё ставится заново.
    // Серый след прошивки за ластиком выключен: колбэки ластика приходят и без него. При инструменте
    // ластик прошивка линию не рисует, а точки штриха идут в стирание.
    private fun configurePen(helper: TouchHelper) {
        helper.setEraserRawDrawingEnabled(false, ERASER_STROKE_ID)
        helper.setStrokeStyle(TouchHelper.STROKE_STYLE_FOUNTAIN)
        helper.setStrokeWidth(penWidthPx)
        helper.setStrokeColor(Color.BLACK)
        helper.setRawDrawingRenderEnabled(tool == DrawingTool.PEN)
    }

    // --- события пера --------------------------------------------------------------------------

    private fun collectPoints(pointList: TouchPointList): List<RawPoint> {
        val maxPressure = OnyxSdk.maxTouchPressure
        return (pointList.points ?: emptyList()).map { point ->
            RawPoint(point.x, point.y, normalizePressure(point.pressure, maxPressure))
        }
    }

    private fun reportStroke(points: List<RawPoint>) {
        val scale = CardGeometry.computeScale(width.toFloat())
        val stroke = buildStroke(points, penWidthPx, scale) ?: return
        // Прошивка уже нарисовала штрих под пером; поверхность догоняет, чтобы он не пропал после выключения.
        book.appendLocal(stroke)
        drawSurface(computeStrokeBounds(stroke, scale))
        onStroke(stroke)
    }

    private fun reportErase(points: List<RawPoint>) {
        if (points.isEmpty()) return
        val scale = CardGeometry.computeScale(width.toFloat())
        onErase(buildErasePath(points, scale), computeEraserRadius(scale))
    }

    private fun runOnUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else post(block)
    }

    // --- поверхность и экран -------------------------------------------------------------------

    // Фиксация замены: перо уже выключено (requestCycle), лист перерисован целиком, изменившееся место
    // обновлено частично. Полного обновления нет: оно мигает всем экраном.
    private fun commitReplacement(change: StrokeChange.Replaced) {
        drawSurface(null)
        val scale = CardGeometry.computeScale(width.toFloat())
        val bounds = computeChangedBounds(change.before, change.after, scale) ?: return
        ScreenRefresh.refreshRegion(this, clipToSheet(bounds))
    }

    private fun computeBoundsOf(strokes: List<Stroke>): PixelBounds? {
        val scale = CardGeometry.computeScale(width.toFloat())
        return strokes.mapNotNull { computeStrokeBounds(it, scale) }.reduceOrNull { a, b -> a.union(b) }
    }

    private fun clipToSheet(bounds: PixelBounds): Rect =
        Rect(
            bounds.left.coerceAtLeast(0),
            bounds.top.coerceAtLeast(0),
            bounds.right.coerceAtMost(width),
            bounds.bottom.coerceAtMost(height),
        )

    // Рисует штрихи на поверхность ([dirty] == null — вся). Экран при этом не трогает.
    private fun drawSurface(dirty: PixelBounds?) {
        synchronized(surfaceLock) {
            if (!holder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = if (dirty == null) holder.lockCanvas() else holder.lockCanvas(clipToSheet(dirty))
                canvas?.let {
                    it.drawColor(Color.WHITE)
                    drawStrokes(it, book.shown, CardGeometry.computeScale(width.toFloat()))
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Поверхность не нарисована", error)
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }
    }

    private companion object {
        const val TAG = "OnyxDrawingView"
        const val ERASER_STROKE_ID = 8
    }
}
