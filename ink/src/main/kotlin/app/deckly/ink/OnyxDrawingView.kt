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
 * хранит поверхность в соответствии со стороной хозяина (штрихи и фигуры), чтобы после выключения пера ничего не
 * пропало.
 *
 * Здесь только привязка к SDK. Когда перо включено ([PenGate]), какие штрихи и фигуры показаны и что изменилось
 * ([ShownSide]), касание у контура фигуры ([findTappedShape]), границы перерисовки ([computeChangedBounds],
 * [computeTraceBounds]), перевод точек в единицы карточки ([buildStroke]) и сами рисунки ([drawCardSide]) проверены
 * тестами на JVM.
 *
 * ПРОВЕРЕНО ТОЛЬКО НА JVM, а сам класс не проверялся и на JVM (там нет прошивки). На планшете это проверит
 * задача 8 плана 4.3. Не подтверждено устройством именно здесь:
 * - цикл «перо выключено → кадр → 150 мс → перо включено» для `uiRevision`, замены штрихов и смены фигур: виден ли
 *   интерфейс на экране, не пропадают ли штрихи и фигуры;
 * - след касания у контура: исчезает ли серый след прошивки после цикла пера, `drawSurface` и частичного обновления
 *   вокруг касания, и не остаётся ли он после того, как хозяин выключил перо выбором фигуры;
 * - скорость перетаскивания фигуры: каждое движение перерисовывает весь лист (`drawSurface(null)`); если на планшете
 *   будет тормозить, первое средство — перерисовка только изменившейся области;
 * - как скоро после выбора фигуры хозяин выключает перо и не успевает ли прошивка принять следующее касание за штрих;
 * - толщина и вид следа прошивки у касания: запас вокруг него ([computeTraceBounds]) взят с запасом, а не измерен;
 * - окно без фокуса или невидимое вместо явных `pause`/`resume` активити прототипа;
 * - поток колбэков пера (по байткоду SDK 1.5.8 `SFTouchRender` отдаёт их в потоке интерфейса, но код на
 *   это не полагается и сам переводит вызовы в поток интерфейса).
 */
internal class OnyxDrawingView(context: Context) :
    SurfaceView(context), SurfaceHolder.Callback, DrawingSurface {

    private val book = ShownSide()
    private val surfaceLock = Any()
    private var touchHelper: TouchHelper? = null

    private var penWidthPx = 0f
    private var uiRevision: Int? = null
    private var onStroke: (Stroke) -> Unit = {}
    private var onErase: (List<StrokePoint>, Float) -> Unit = { _, _ -> }
    private var onShapeTap: (Int) -> Unit = {}

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
            runOnUi { reportStroke(points) }
        }

        override fun onBeginRawErasing(shortcutErasing: Boolean, point: TouchPoint) = Unit

        override fun onEndRawErasing(outLimitRegion: Boolean, point: TouchPoint) = Unit

        override fun onRawErasingTouchPointMoveReceived(point: TouchPoint) = Unit

        // Стирает боковая кнопка стилуса.
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
        val isPenWidthChanged = state.penWidthPx != penWidthPx
        penWidthPx = state.penWidthPx
        onStroke = state.onStroke
        onErase = state.onErase
        onShapeTap = state.onShapeTap

        gate.setHostEnabled(state.isPenEnabled)
        // Толщина обычно приходит вместе с новым uiRevision (цикл ниже включит перо с новой толщиной); если нет,
        // она применяется сразу.
        if (isPenWidthChanged && gate.isOn) reconfigurePen()

        val change = book.updateFromHost(state.side)
        val previousRevision = uiRevision
        uiRevision = state.uiRevision
        val isUiChanged = previousRevision != null && previousRevision != state.uiRevision

        // Пока перо включено, прошивка замораживает экран: удаление, возврат, смена стороны и фигур, как и
        // изменение интерфейса, видны только после цикла «перо выключено → кадр → перо включено».
        if (isUiChanged || change is SideChange.Replaced) gate.requestCycle()
        when (change) {
            SideChange.None -> Unit
            is SideChange.Appended -> drawSurface(computeBoundsOf(change.added))
            is SideChange.Replaced -> commitReplacement(change)
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
    // Серый след прошивки за ластиком выключен: колбэки ластика приходят и без него. Рендер сырого пера
    // включён всегда: кончик пера рисует.
    private fun configurePen(helper: TouchHelper) {
        helper.setEraserRawDrawingEnabled(false, ERASER_STROKE_ID)
        helper.setStrokeStyle(TouchHelper.STROKE_STYLE_FOUNTAIN)
        helper.setStrokeWidth(penWidthPx)
        helper.setStrokeColor(Color.BLACK)
        helper.setRawDrawingRenderEnabled(true)
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
        val tapped = findTappedShape(points, book.shapes, scale)
        if (tapped != null) {
            // Короткое касание у контура выбирает фигуру: штриха нет. Прошивка уже нарисовала след под пером, а пока перо
            // включено, она замораживает обновление всего экрана, и частичное обновление вокруг касания ничего не даст.
            // Выбор фигуры сторону не меняет (фигура в руках равна исходной), так что `Replaced` не придёт и перерисовки
            // не будет. Поэтому, как при стирании, сначала цикл пера (перо выключается сразу, хозяин за 150 мс успевает
            // запретить его сам: выбор ничего не пишет на диск), потом поверхность и область обновления, потом выбор.
            gate.requestCycle()
            clearTrace(points)
            onShapeTap(tapped)
            return
        }
        val stroke = buildStroke(points, penWidthPx, scale) ?: return
        // Прошивка уже нарисовала штрих под пером; поверхность догоняет, чтобы он не пропал после выключения.
        book.appendLocal(stroke)
        drawSurface(computeStrokeBounds(stroke, scale))
        onStroke(stroke)
    }

    private fun clearTrace(points: List<RawPoint>) {
        val trace = computeTraceBounds(points, penWidthPx) ?: return
        drawSurface(trace)
        ScreenRefresh.refreshRegion(this, clipToSheet(trace))
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
    private fun commitReplacement(change: SideChange.Replaced) {
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
                    drawCardSide(it, book.shown, CardGeometry.computeScale(width.toFloat()))
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
