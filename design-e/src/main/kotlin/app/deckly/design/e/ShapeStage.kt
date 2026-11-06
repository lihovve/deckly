package app.deckly.design.e

import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.SelectedShape
import app.deckly.ui.contract.ShapeHandleKind
import kotlin.math.roundToInt

/**
 * Слой жестов пера над листом и, пока фигура выделена, её рамка и ручки (`.shape-object.is-selected` с ручками в макете).
 * Сама фигура на листе: её контур рисует `ink`, здесь только то, что видно при выделении, и жесты. Рамка повёрнута вокруг
 * центра на угол фигуры. У обычного вида: пунктир в 1 dp, четыре квадрата в углах и ручка поворота над рамкой со значком
 * и чертой. У линии и стрелок: без пунктира, два круга на концах оси. [selected] == `null`: рамки нет, слой остаётся ради
 * жеста, который начался при выделении и стал линией (касание мимо): он не должен оборваться, пока перо не оторвано.
 *
 * Жесты только пером (`PointerType.Stylus`) и без нажатой кнопки: палец и ладонь ничего не двигают и не ставят, перо с
 * кнопкой стирает. Касание в цели ручки даёт [EditorAction.StartShapeGesture] с этой ручкой, в любом другом месте листа
 * с `null`, остальное решает модель. Начало и каждое движение несут нажим, начало ещё и масштаб листа. Дальше движение,
 * конец или отмена жеста. Система отдаёт отсчёты пера пачкой за кадр: в модель идут все, по одному движению на отсчёт
 * (линии нужны все, иначе быстрая выйдет ломаной хордами), нажим исторических отсчётов берётся из исходного события.
 * Перед концом положение подъёма уходит ещё одним движением, если оно не совпало с последним. Подъём не сразу конец
 * жеста: система сообщает отрыв посреди контакта (дребезг, пары «UP → DOWN» через 0–60 мс), поэтому слой ждёт касание пером
 * ещё [REBOUND_WINDOW_MS] и, если оно пришло, ведёт тот же жест дальше (без нового `Start` и без новой проверки ленты, новое
 * касание идёт обычным движением). Координаты — в единицах карточки.
 */
@Composable
internal fun ShapeStage(selected: SelectedShape?, onAction: (EditorAction) -> Unit, modifier: Modifier = Modifier) {
    val currentFrame by rememberUpdatedState(selected?.frame)
    val currentOnAction by rememberUpdatedState(onAction)
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Жест начинает только перо без нажатой кнопки; остальные касания не трогаем, их разбирает лист.
                if (down.type != PointerType.Stylus || isStylusButtonPressed(currentEvent.motionEvent)) return@awaitEachGesture
                val scale = size.width / CARD_WIDTH
                val handle = currentFrame?.let { findHandleAt(it, down.position.x, down.position.y, scale, density) }
                down.consume()
                val pressure = down.pressure.coerceIn(0f, 1f)
                currentOnAction.invoke(EditorAction.StartShapeGesture(handle, down.position.x / scale, down.position.y / scale, pressure, scale))
                val isLifted = followPen(down, scale) { currentOnAction.invoke(it) }
                currentOnAction.invoke(if (isLifted) EditorAction.EndShapeGesture else EditorAction.CancelShapeGesture)
            }
        },
    ) {
        if (selected != null) ShapeFrameMarks(selected, constraints.maxWidth / CARD_WIDTH)
    }
}

private const val CARD_WIDTH = 1000f

/**
 * Окно склейки дребезга пера, мс. В проверке владельца на планшете пары «UP → DOWN» посреди касания по паузе: 0–20 мс 45
 * пар, 20–40 мс 13, 40–60 мс 5, 60–80 мс 1, 80–100 мс 0, а настоящие отрывы начинаются от 100 мс (100–200 мс 5, от 300 мс
 * 67). 80 мс стоят на границе: дребезг внутри, настоящий отрыв снаружи. По нажиму их не разделить (у дребезга UP несёт
 * нажим последнего движения, как и настоящий), по смещению нельзя: ложный DOWN в 706 px тоже должен склеиваться.
 */
private const val REBOUND_WINDOW_MS = 80L

// Ведёт перо с [down] до настоящего подъёма (`true`) или отмены касания (`false`). Отсчёты идут движениями; подъём с
// положением, отличным от последнего, ещё одним движением. После подъёма ждёт касание пером [REBOUND_WINDOW_MS]: пришло
// (дребезг), тот же жест продолжается, его первая точка идёт движением; не пришло, это настоящий подъём.
private suspend fun AwaitPointerEventScope.followPen(down: PointerInputChange, scale: Float, send: (EditorAction) -> Unit): Boolean {
    var pointer = down.id
    var last = PenPoint(down.position, down.pressure.coerceIn(0f, 1f))
    while (true) {
        val isLifted = drag(pointer) { change ->
            for (point in collectPenPoints(change, currentEvent.motionEvent)) {
                last = point
                send(buildMove(point, scale))
            }
            change.consume()
        }
        if (!isLifted) return false
        // Подъём приходит без движения (`drag` не зовёт на него обработчик) и может оказаться не там, где последнее движение:
        // без этого шага фигура останавливалась бы там, где было последнее движение. Нажим у точки подъёма берётся от
        // последнего движения: у настоящего пера событие подъёма несёт нажим последнего движения (на планшете, например,
        // 0,36), а у синтетических событий (`input stylus swipe`) он нулевой, и точка подъёма вышла бы с нулевым нажимом.
        val lift = currentEvent.changes.firstOrNull { it.id == pointer }?.position
        if (lift != null && lift != last.position) send(buildMove(PenPoint(lift, last.pressure), scale))
        val rebound = awaitRebound() ?: return true
        rebound.consume()
        pointer = rebound.id
        last = PenPoint(rebound.position, rebound.pressure.coerceIn(0f, 1f))
        send(buildMove(last, scale))
    }
}

// Касание пером без боковой кнопки в течение окна после подъёма или `null`. Палец и ладонь жест не склеивают.
private suspend fun AwaitPointerEventScope.awaitRebound(): PointerInputChange? =
    withTimeoutOrNull(REBOUND_WINDOW_MS) {
        var next = awaitFirstDown(requireUnconsumed = false)
        while (next.type != PointerType.Stylus || isStylusButtonPressed(currentEvent.motionEvent)) next = awaitFirstDown(requireUnconsumed = false)
        next
    }

// Отсчёт пера: положение на листе в пикселях и нажим от 0 до 1.
private data class PenPoint(val position: Offset, val pressure: Float)

// Движение жеста в единицах карточки: нажим держим в пределах 0..1, как требует действие.
private fun buildMove(point: PenPoint, scale: Float) =
    EditorAction.MoveShapeGesture(point.position.x / scale, point.position.y / scale, point.pressure.coerceIn(0f, 1f))

// Все отсчёты пера из одного события по порядку: накопленные за кадр (`historical`) и текущий. Compose нажим исторических
// отсчётов не несёт, он лежит в истории исходного `MotionEvent`; если её нет или она другой длины, нажим берётся у текущего.
// Указатель в `MotionEvent` ищется по инструменту, а не по `change.id`: `PointerId` у Compose не равен номеру в
// `MotionEvent` (Compose считает касания с создания окна, и совпадает с нулём только первое), так что со второго жеста поиск
// по `change.id` ничего не находил. Слой работает только с пером, а ладонь может стоять другим указателем.
private fun collectPenPoints(change: PointerInputChange, event: MotionEvent?): List<PenPoint> {
    val index = event?.let { source -> (0 until source.pointerCount).firstOrNull { source.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS } } ?: -1
    val history = event?.takeIf { index >= 0 && it.historySize == change.historical.size }
    val past = change.historical.mapIndexed { step, sample ->
        PenPoint(sample.position, history?.getHistoricalPressure(index, step) ?: change.pressure)
    }
    return past + PenPoint(change.position, change.pressure)
}

// Боковая кнопка стилуса стирает: так решает и лист (`CanvasDrawingView`), поэтому с ней перо фигуру не двигает. Смотрим на
// исходное событие: в `PointerButtons` Compose кончик пера (BUTTON_PRIMARY) и боковая кнопка (BUTTON_STYLUS_PRIMARY) оба
// «основные», и по ним одно от другого не отличить.
private fun isStylusButtonPressed(event: MotionEvent?): Boolean =
    event != null && event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0

// Рамка в пикселях листа: левый верхний угол и размер до поворота; поворот вокруг центра слоем.
@Composable
private fun ShapeFrameMarks(selected: SelectedShape, scale: Float) {
    val frame = selected.frame
    val density = LocalDensity.current
    val width = with(density) { ((frame.right - frame.left) * scale).toDp() }
    val height = with(density) { ((frame.bottom - frame.top) * scale).toDp() }
    Box(
        Modifier
            .offset { IntOffset((frame.left * scale).roundToInt(), (frame.top * scale).roundToInt()) }
            .size(width, height)
            .graphicsLayer { rotationZ = frame.angle }
            .semantics { contentDescription = EditorTexts.describeShape(selected.kind) }
            .then(if (frame.isLinear) Modifier else Modifier.drawBehind { drawDashedFrame() }),
    ) {
        if (frame.isLinear) {
            HandleMark(width * Tokens.ShapeStartShare, height / 2, CircleShape, EditorTexts.describeEndpoint(ShapeHandleKind.START))
            HandleMark(width * Tokens.ShapeEndShare, height / 2, CircleShape, EditorTexts.describeEndpoint(ShapeHandleKind.END))
        } else {
            HandleMark(0.dp, 0.dp, RectangleShape, EditorTexts.describeCorner(ShapeHandleKind.TOP_LEFT))
            HandleMark(width, 0.dp, RectangleShape, EditorTexts.describeCorner(ShapeHandleKind.TOP_RIGHT))
            HandleMark(0.dp, height, RectangleShape, EditorTexts.describeCorner(ShapeHandleKind.BOTTOM_LEFT))
            HandleMark(width, height, RectangleShape, EditorTexts.describeCorner(ShapeHandleKind.BOTTOM_RIGHT))
            RotateMark(width)
        }
    }
}

// Пунктир в 1 dp снаружи рамки (`outline:1px dashed #000` не входит в размер): внешний контур на 1 шире рамки с каждой
// стороны. Как у Chrome, каждая сторона начинается со штриха в своём начале (слева направо и сверху вниз) и им же
// кончается: штрих 3, промежуток около 2 подгоняется под длину стороны.
private fun DrawScope.drawDashedFrame() {
    val line = Tokens.ShapeFrameDash.toPx()
    val width = size.width
    val height = size.height
    drawDashedSide(Offset(-line, -line / 2f), Offset(width + line, -line / 2f), line)
    drawDashedSide(Offset(-line, height + line / 2f), Offset(width + line, height + line / 2f), line)
    drawDashedSide(Offset(-line / 2f, -line), Offset(-line / 2f, height + line), line)
    drawDashedSide(Offset(width + line / 2f, -line), Offset(width + line / 2f, height + line), line)
}

private fun DrawScope.drawDashedSide(from: Offset, to: Offset, line: Float) {
    val length = (to - from).getDistance()
    val dash = 3f * line
    val count = ((length + 2f * line) / (5f * line)).roundToInt().coerceAtLeast(2)
    val gap = (length - dash * count) / (count - 1)
    drawLine(Tokens.Black, from, to, strokeWidth = line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap)))
}

// Цель касания 64 dp с центром в ([centerX], [centerY]) рамки; знак `::after` стоит в ней на (24, 24). У псевдоэлемента нет
// `box-sizing: border-box` из `*{…}`, поэтому Chrome рисует знак 16 + 2 · 2 = 20 dp: рамка снаружи содержимого, и знак
// сдвинут на 2 dp вправо и вниз от середины цели. Квадрат или круг.
@Composable
private fun HandleMark(centerX: Dp, centerY: Dp, shape: Shape, description: String) {
    val inset = (Tokens.ShapeHandleTarget - Tokens.ShapeHandleMark) / 2
    Box(
        Modifier
            .offset(centerX - Tokens.ShapeHandleOffset, centerY - Tokens.ShapeHandleOffset)
            .size(Tokens.ShapeHandleTarget)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .offset(inset, inset)
                .size(Tokens.ShapeHandleMark + Tokens.ShapeHandleMarkBorder * 2)
                .background(Tokens.White, shape)
                .border(Tokens.ShapeHandleMarkBorder, Tokens.Black, shape),
        )
    }
}

// Ручка поворота: цель 64 dp с верхом на 80 dp выше рамки, значок 24 dp на белом и чёрная черта 1 × 16 dp под ней.
@Composable
private fun RotateMark(frameWidth: Dp) {
    Box(
        Modifier
            .offset(frameWidth / 2 - Tokens.ShapeHandleOffset, -Tokens.ShapeRotateTop)
            .size(Tokens.ShapeHandleTarget)
            .semantics { contentDescription = EditorTexts.ROTATE_SHAPE },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(Tokens.ShapeHandleTarget / 2 - Tokens.ShapeRotateLineWidth, Tokens.ShapeHandleTarget)
                .size(Tokens.ShapeRotateLineWidth, Tokens.ShapeRotateLineHeight)
                .background(Tokens.Black),
        )
        Image(
            DecklyIcons.Undo,
            contentDescription = null,
            modifier = Modifier.size(Tokens.ShapeRotateIcon).background(Tokens.White),
            colorFilter = ColorFilter.tint(Tokens.Black),
        )
    }
}
