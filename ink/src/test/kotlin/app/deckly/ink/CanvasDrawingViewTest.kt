package app.deckly.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasDrawingViewTest {
    private lateinit var view: CanvasDrawingView
    private val strokes = mutableListOf<Stroke>()
    private val erasures = mutableListOf<Pair<List<StrokePoint>, Float>>()
    private val taps = mutableListOf<Int>()

    // Рамка 200 × 300 единиц карточки: левый край контура x = 128 (234 px при масштабе 1,83), середина по высоте y = 350
    // (640 px). Контур толщиной 6 единиц, лента выбора шире на 8 единиц макета с каждой стороны.
    private val rectangle = Shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, 0f, 6f)

    @Before
    fun createView() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        view = CanvasDrawingView(context)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(SHEET_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SHEET_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, SHEET_WIDTH, SHEET_HEIGHT)
        bind()
    }

    @Test
    fun stylusStrokeIsReportedInCardUnits() {
        val isConsumed = performGesture(
            MotionEvent.TOOL_TYPE_STYLUS,
            listOf(Sample(183f, 366f, 0.2f), Sample(915f, 732f, 0.6f), Sample(1830f, 183f, 1f)),
        )

        assertTrue(isConsumed)
        assertEquals(1, strokes.size)
        assertTrue(erasures.isEmpty())
        val stroke = strokes.single()
        assertEquals(6f / SCALE, stroke.width, 1e-3f)
        assertPoint(100f, 200f, 0.2f, stroke.points[0])
        assertPoint(500f, 400f, 0.6f, stroke.points[1])
        assertPoint(1000f, 100f, 1f, stroke.points[2])
    }

    @Test
    fun strokeWidthFollowsPenWidth() {
        bind(penWidthPx = 10f)

        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(100f, 100f, 1f), Sample(200f, 100f, 1f)))

        assertEquals(10f / SCALE, strokes.single().width, 1e-3f)
    }

    @Test
    fun everyPointKeepsItsOwnPressure() {
        val downTime = SystemClock.uptimeMillis()
        dispatch(downTime, MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, Sample(100f, 100f, 0.1f))
        // Пачка точек одного события: у исторических свой нажим, у текущей — свой. addBatch делает новую
        // точку текущей, а прежнюю уносит в историю.
        val move = obtain(downTime, MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, Sample(200f, 100f, 0.5f))
        move.addBatch(downTime + 1, 300f, 100f, 0.9f, 1f, 0)
        view.dispatchTouchEvent(move)
        dispatch(downTime, MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, Sample(300f, 100f, 0.9f))

        val pressures = strokes.single().points.map { it.pressure }
        assertEquals(listOf(0.1f, 0.5f, 0.9f), pressures)
    }

    @Test
    fun tapWithStylusIsOnePointStroke() {
        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(500f, 500f, 0.7f)))

        assertEquals(1, strokes.single().points.size)
    }

    @Test
    fun fingerDoesNotDraw() {
        val isConsumed = performGesture(
            MotionEvent.TOOL_TYPE_FINGER,
            listOf(Sample(100f, 100f, 1f), Sample(400f, 400f, 1f), Sample(800f, 400f, 1f)),
        )

        // Ладонь по листу ничего не рисует и не стирает, но и не пролетает дальше.
        assertTrue(isConsumed)
        assertTrue(strokes.isEmpty())
        assertTrue(erasures.isEmpty())
    }

    @Test
    fun stylusWithoutButtonDraws() {
        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(183f, 366f, 0.5f), Sample(1830f, 183f, 0.5f)))

        assertEquals(1, strokes.size)
        assertTrue(erasures.isEmpty())
    }

    @Test
    fun stylusButtonErases() {
        performGesture(
            MotionEvent.TOOL_TYPE_STYLUS,
            listOf(Sample(183f, 366f, 0.5f), Sample(1830f, 183f, 0.5f)),
            buttons = MotionEvent.BUTTON_STYLUS_PRIMARY,
        )

        assertTrue(strokes.isEmpty())
        // Путь ластика и его радиус приходят в единицах карточки.
        val (path, radius) = erasures.single()
        assertEquals(2, path.size)
        assertPoint(100f, 200f, 0f, path[0])
        assertPoint(1000f, 100f, 0f, path[1])
        assertEquals(16f / SCALE, radius, 1e-3f)
    }

    @Test
    fun eraserToolTypeErases() {
        performGesture(MotionEvent.TOOL_TYPE_ERASER, listOf(Sample(100f, 100f, 0.5f), Sample(300f, 100f, 0.5f)))

        assertTrue(strokes.isEmpty())
        assertEquals(1, erasures.size)
    }

    @Test
    fun disabledPenIgnoresStylus() {
        bind(isPenEnabled = false)

        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(100f, 100f, 1f), Sample(400f, 400f, 1f)))
        performGesture(MotionEvent.TOOL_TYPE_ERASER, listOf(Sample(100f, 100f, 1f), Sample(400f, 400f, 1f)))

        assertTrue(strokes.isEmpty())
        assertTrue(erasures.isEmpty())
    }

    @Test
    fun cancelledGestureReportsNothing() {
        val downTime = SystemClock.uptimeMillis()
        dispatch(downTime, MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, Sample(100f, 100f, 1f))
        dispatch(downTime, MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, Sample(200f, 100f, 1f))
        dispatch(downTime, MotionEvent.ACTION_CANCEL, MotionEvent.TOOL_TYPE_STYLUS, Sample(200f, 100f, 1f))

        assertTrue(strokes.isEmpty())
    }

    @Test
    fun penDisabledMidGestureDropsStroke() {
        val downTime = SystemClock.uptimeMillis()
        dispatch(downTime, MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, Sample(100f, 100f, 1f))
        dispatch(downTime, MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, Sample(300f, 100f, 1f))

        // Открылся диалог: недорисованный штрих хозяину не отдаётся и с листа исчезает.
        bind(isPenEnabled = false)
        dispatch(downTime, MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, Sample(300f, 100f, 1f))

        assertTrue(strokes.isEmpty())
        assertFalse(drawView().isInk(200, 100))
    }

    @Test
    fun changeOfHostStrokesInvalidatesView() {
        shadowOf(view).clearWasInvalidated()

        bind(strokes = listOf(Stroke(6f, listOf(StrokePoint(100f, 100f, 1f)))))

        assertTrue(shadowOf(view).wasInvalidated())
    }

    @Test
    fun repeatedHostStrokesDoNotInvalidateView() {
        val list = listOf(Stroke(6f, listOf(StrokePoint(100f, 100f, 1f))))
        bind(strokes = list)
        shadowOf(view).clearWasInvalidated()

        bind(strokes = list)

        assertFalse(shadowOf(view).wasInvalidated())
    }

    @Test
    fun drawsStrokesFromHost() {
        bind(strokes = listOf(Stroke(6f / SCALE, listOf(StrokePoint(500f, 500f, 1f)))))

        val bitmap = drawView()

        assertTrue(bitmap.isInk((500 * SCALE).toInt(), (500 * SCALE).toInt()))
        assertFalse(bitmap.isInk(20, 20))
    }

    @Test
    fun backgroundIsRectOfOwnSizeNotWholeCanvas() {
        // Compose не отсекает AndroidView по границам: drawColor закрасил бы всё, что нарисовано до листа.
        val bitmap = Bitmap.createBitmap(SHEET_WIDTH + 200, SHEET_HEIGHT + 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.RED)

        view.draw(canvas)

        assertEquals(Color.WHITE, bitmap.getPixel(20, 20))
        assertEquals(Color.RED, bitmap.getPixel(SHEET_WIDTH + 100, SHEET_HEIGHT + 100))
    }

    @Test
    fun liveStrokeIsDrawnWhileGestureRuns() {
        val downTime = SystemClock.uptimeMillis()
        dispatch(downTime, MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, Sample(300f, 300f, 1f))
        dispatch(downTime, MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, Sample(600f, 300f, 1f))

        val bitmap = drawView()

        assertTrue(strokes.isEmpty())
        assertTrue(bitmap.isInk(450, 300))
    }

    @Test
    fun erasingGestureDrawsNoLiveStroke() {
        val downTime = SystemClock.uptimeMillis()
        dispatch(downTime, MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, Sample(300f, 300f, 1f), MotionEvent.BUTTON_STYLUS_PRIMARY)
        dispatch(downTime, MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, Sample(600f, 300f, 1f), MotionEvent.BUTTON_STYLUS_PRIMARY)

        assertFalse(drawView().isInk(450, 300))
    }

    @Test
    fun finishedStrokeStaysVisibleUntilHostSendsIt() {
        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(300f, 300f, 1f), Sample(600f, 300f, 1f)))
        val reported = strokes.single()

        assertTrue(drawView().isInk(450, 300))
        // Перекомпозиция по другой причине приносит прежний пустой список: штрих не пропадает.
        bind()
        assertTrue(drawView().isInk(450, 300))
        // Хост догнал: штрих по-прежнему на месте.
        bind(strokes = listOf(reported))
        assertTrue(drawView().isInk(450, 300))
    }

    @Test
    fun strokeRemovedByHostDisappears() {
        val stroke = Stroke(6f / SCALE, listOf(StrokePoint(100f, 100f, 1f), StrokePoint(400f, 100f, 1f)))
        bind(strokes = listOf(stroke))
        assertTrue(drawView().isInk((250 * SCALE).toInt(), (100 * SCALE).toInt()))

        bind(strokes = emptyList())

        assertFalse(drawView().isInk((250 * SCALE).toInt(), (100 * SCALE).toInt()))
    }

    private fun bind(
        strokes: List<Stroke> = emptyList(),
        penWidthPx: Float = 6f,
        isPenEnabled: Boolean = true,
        shapes: List<Shape> = emptyList(),
    ) {
        view.bind(
            DrawingState(
                side = CardSide(strokes, shapes),
                penWidthPx = penWidthPx,
                isPenEnabled = isPenEnabled,
                uiRevision = 0,
                onStroke = { this.strokes += it },
                onErase = { path, radius -> erasures += path to radius },
                onShapeTap = { taps += it },
            ),
        )
    }

    // --- фигуры --------------------------------------------------------------------------------

    @Test
    fun tapNearShapeOutlineSelectsItAndReportsNoStroke() {
        bind(shapes = listOf(rectangle))

        val isConsumed = performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(240f, 640f, 0.5f)))

        assertTrue(isConsumed)
        assertEquals(listOf(0), taps)
        assertTrue(strokes.isEmpty())
        assertTrue(erasures.isEmpty())
    }

    @Test
    fun tapAtTheSecondShapeReportsItsIndex() {
        val far = Shape(Shape.Kind.OVAL, 600f, 100f, 900f, 300f, 0f, 3f)
        bind(shapes = listOf(rectangle, far))

        // Левая точка овала: x = 600 + 12 % · 300 = 636 единиц, y = 200: 1164 и 366 px.
        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(1165f, 366f, 0.5f)))

        assertEquals(listOf(1), taps)
        assertTrue(strokes.isEmpty())
    }

    @Test
    fun shapeTapLeavesNoTraceOnTheSheet() {
        bind(shapes = listOf(rectangle))
        val downTime = SystemClock.uptimeMillis()
        // Точка в 15 px от оси контура: внутри ленты выбора, но вне самого контура толщиной 11 px.
        dispatch(downTime, MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, Sample(249f, 640f, 1f))
        dispatch(downTime, MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, Sample(253f, 641f, 1f))
        assertTrue("след пера виден, пока касание идёт", drawView().isInk(251, 640))
        shadowOf(view).clearWasInvalidated()

        dispatch(downTime, MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, Sample(253f, 641f, 1f))

        assertEquals(listOf(0), taps)
        assertFalse("следа не осталось ни в списке, ни на листе", drawView().isInk(251, 640))
        assertTrue("лист перерисован без следа", shadowOf(view).wasInvalidated())
        // Контур самой фигуры на месте.
        assertTrue(drawView().isInk(234, 640))
    }

    @Test
    fun dotInsideAnEmptyShapeIsAStroke() {
        bind(shapes = listOf(rectangle))

        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(366f, 640f, 0.7f)))

        assertTrue(taps.isEmpty())
        assertEquals(1, strokes.single().points.size)
    }

    @Test
    fun strokeInsideAShapeIsAStrokeAndStaysVisible() {
        bind(shapes = listOf(rectangle))

        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(330f, 600f, 1f), Sample(420f, 600f, 1f), Sample(480f, 640f, 1f)))

        assertEquals(1, strokes.size)
        assertTrue(taps.isEmpty())
        assertTrue(drawView().isInk(375, 600))
    }

    @Test
    fun longStrokeAcrossTheOutlineIsAStrokeNotATap() {
        bind(shapes = listOf(rectangle))

        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(150f, 640f, 1f), Sample(300f, 640f, 1f), Sample(450f, 640f, 1f)))

        assertTrue(taps.isEmpty())
        assertEquals(1, strokes.size)
    }

    @Test
    fun disabledPenDoesNotSelectShapes() {
        bind(shapes = listOf(rectangle), isPenEnabled = false)

        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(240f, 640f, 0.5f)))

        assertTrue(taps.isEmpty())
        assertTrue(strokes.isEmpty())
    }

    @Test
    fun fingerOnTheOutlineDoesNotSelectShape() {
        bind(shapes = listOf(rectangle))

        val isConsumed = performGesture(MotionEvent.TOOL_TYPE_FINGER, listOf(Sample(240f, 640f, 0.5f)))

        assertTrue(isConsumed)
        assertTrue(taps.isEmpty())
        assertTrue(strokes.isEmpty())
    }

    @Test
    fun stylusButtonOnTheOutlineErasesInsteadOfSelecting() {
        bind(shapes = listOf(rectangle))

        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(240f, 640f, 0.5f), Sample(300f, 640f, 0.5f)), buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)

        assertTrue(taps.isEmpty())
        assertEquals(1, erasures.size)
    }

    @Test
    fun penDisabledMidTapReportsNothing() {
        bind(shapes = listOf(rectangle))
        val downTime = SystemClock.uptimeMillis()
        dispatch(downTime, MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, Sample(240f, 640f, 1f))

        bind(shapes = listOf(rectangle), isPenEnabled = false)
        dispatch(downTime, MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, Sample(240f, 640f, 1f))

        assertTrue(taps.isEmpty())
        assertTrue(strokes.isEmpty())
    }

    @Test
    fun drawsShapesFromHost() {
        bind(shapes = listOf(rectangle))

        val bitmap = drawView()

        assertTrue(bitmap.isInk(234, 640))
        assertFalse(bitmap.isInk(366, 640))
    }

    @Test
    fun changeOfHostShapesInvalidatesView() {
        shadowOf(view).clearWasInvalidated()

        bind(shapes = listOf(rectangle))

        assertTrue(shadowOf(view).wasInvalidated())
    }

    @Test
    fun repeatedHostShapesDoNotInvalidateView() {
        bind(shapes = listOf(rectangle))
        shadowOf(view).clearWasInvalidated()

        bind(shapes = listOf(rectangle.copy()))

        assertFalse(shadowOf(view).wasInvalidated())
    }

    @Test
    fun shapeRemovedByHostDisappears() {
        bind(shapes = listOf(rectangle))
        assertTrue(drawView().isInk(234, 640))

        bind(shapes = emptyList())

        assertFalse(drawView().isInk(234, 640))
    }

    @Test
    fun shapeMovedByHostIsDrawnAtTheNewPlaceOnly() {
        bind(shapes = listOf(rectangle))

        bind(shapes = listOf(rectangle.copy(left = 500f, right = 700f)))

        val bitmap = drawView()
        assertFalse(bitmap.isInk(234, 640))
        // Левый край нового контура: x = 500 + 14 % · 200 = 528 единиц, 966 px.
        assertTrue(bitmap.isInk(966, 640))
    }

    @Test
    fun pendingStrokeSurvivesNewShapesFromHost() {
        // Штрих нарисован листом, хост его ещё не записал; в этот момент от хоста пришли новые фигуры со старым
        // (пустым) списком штрихов. Штрих не должен пропасть, а фигура должна появиться.
        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(300f, 300f, 1f), Sample(600f, 300f, 1f)))
        val reported = strokes.single()
        assertTrue(drawView().isInk(450, 300))
        shadowOf(view).clearWasInvalidated()

        bind(strokes = emptyList(), shapes = listOf(rectangle))

        val bitmap = drawView()
        assertTrue("штрих листа пропал вместе со сменой фигур", bitmap.isInk(450, 300))
        assertTrue("фигура не нарисована", bitmap.isInk(234, 640))
        assertTrue(shadowOf(view).wasInvalidated())

        // Хост догнал штрих: ничего не пропадает и ничего не перерисовывается.
        shadowOf(view).clearWasInvalidated()
        bind(strokes = listOf(reported), shapes = listOf(rectangle))
        assertTrue(drawView().isInk(450, 300))
        assertTrue(drawView().isInk(234, 640))
        assertFalse(shadowOf(view).wasInvalidated())
    }

    @Test
    fun pendingStrokeSurvivesShapesRemovedByHost() {
        bind(shapes = listOf(rectangle))
        performGesture(MotionEvent.TOOL_TYPE_STYLUS, listOf(Sample(600f, 900f, 1f), Sample(900f, 900f, 1f)))

        bind(strokes = emptyList(), shapes = emptyList())

        val bitmap = drawView()
        assertTrue(bitmap.isInk(750, 900))
        assertFalse(bitmap.isInk(234, 640))
    }

    private fun drawView(): Bitmap {
        val bitmap = Bitmap.createBitmap(SHEET_WIDTH, SHEET_HEIGHT, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun performGesture(toolType: Int, samples: List<Sample>, buttons: Int = 0): Boolean {
        val downTime = SystemClock.uptimeMillis()
        var isConsumed = dispatch(downTime, MotionEvent.ACTION_DOWN, toolType, samples.first(), buttons)
        for (sample in samples.drop(1)) {
            isConsumed = dispatch(downTime, MotionEvent.ACTION_MOVE, toolType, sample, buttons) && isConsumed
        }
        return dispatch(downTime, MotionEvent.ACTION_UP, toolType, samples.last(), buttons) && isConsumed
    }

    private fun dispatch(downTime: Long, action: Int, toolType: Int, sample: Sample, buttons: Int = 0): Boolean =
        view.dispatchTouchEvent(obtain(downTime, action, toolType, sample, buttons))

    private fun obtain(downTime: Long, action: Int, toolType: Int, sample: Sample, buttons: Int = 0): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            this.toolType = toolType
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            x = sample.x
            y = sample.y
            pressure = sample.pressure
            size = 1f
        }
        return MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            buttons,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
    }

    private fun assertPoint(x: Float, y: Float, pressure: Float, actual: StrokePoint) {
        assertEquals(x, actual.x, 1e-2f)
        assertEquals(y, actual.y, 1e-2f)
        assertEquals(pressure, actual.pressure, 1e-6f)
    }

    private fun Bitmap.isInk(x: Int, y: Int): Boolean = Color.red(getPixel(x, y)) < 128

    private data class Sample(val x: Float, val y: Float, val pressure: Float)

    private companion object {
        const val SHEET_WIDTH = 1830
        const val SHEET_HEIGHT = 1500
        const val SCALE = 1.83f
    }
}
