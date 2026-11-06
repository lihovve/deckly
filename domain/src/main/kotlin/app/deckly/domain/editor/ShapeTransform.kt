package app.deckly.domain.editor

import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Shape
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Перенос чистых функций `design/editor-shapes.js` без своих решений: те же формулы, те же ограничения. Демо считает в
 * единицах макета на листе 928 × 963; здесь рамка переводится из единиц карточки в единицы макета, считается и
 * возвращается обратно. `ShapeTransformTest` сверяет результат с значениями, снятыми у самого демо.
 */

/** Ручка выделенной фигуры: четыре угла рамки, два конца линии или стрелки и ручка поворота над рамкой. */
enum class ShapeHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, START, END, ROTATE }

private const val SHEET_WIDTH = 928.0
private const val SHEET_HEIGHT = 963.0

/** Поле: ни один угол рамки и ни ручка поворота не заходят ближе 32 к краю листа. */
private const val FIELD = 32.0
private const val MIN_FRAME = 80.0
private const val MIN_AXIS = 64.0
private const val AXIS_SHARE = 0.76
private const val END_SHARE = 0.38
private const val LINE_HEIGHT = 80.0
private const val ROTATE_HANDLE = 48.0
private const val INSERTED_SIZE = 220.0
private const val LIMIT_STEPS = 20

/**
 * Допуск в единицах макета на потерю точности при переводе рамки в `Float` и обратно: фигура, которую упор оставил
 * ровно у поля, после записи не должна вдруг «не помещаться». Только для проверки готовой фигуры ([canFitShape]); сами
 * движения считают строго, как демо, иначе упор уходил бы за поле на величину допуска.
 */
private const val TOLERANCE = 1e-3

/** Рамка в единицах макета: `x`, `y` — левый верхний угол до поворота, [angle] в градусах вокруг центра. */
private data class Frame(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val angle: Double,
    val isLinear: Boolean,
)

private val UNIT = CardUnits.PER_DESIGN_UNIT.toDouble()

private fun Shape.toFrame() = Frame(
    left / UNIT,
    top / UNIT,
    (right.toDouble() - left) / UNIT,
    (bottom.toDouble() - top) / UNIT,
    angle.toDouble(),
    kind.isLinear,
)

private fun Frame.toShape(template: Shape): Shape = template.copy(
    left = (x * UNIT).toFloat(),
    top = (y * UNIT).toFloat(),
    right = ((x + w) * UNIT).toFloat(),
    bottom = ((y + h) * UNIT).toFloat(),
    angle = angle.toFloat(),
)

/** Точка рамки `(x, y)` от её центра в мире: `findShapePoint` демо. */
private fun Frame.findPoint(offsetX: Double, offsetY: Double): Pair<Double, Double> {
    val radians = Math.toRadians(angle)
    return Pair(
        x + w / 2 + offsetX * cos(radians) - offsetY * sin(radians),
        y + h / 2 + offsetX * sin(radians) + offsetY * cos(radians),
    )
}

/** `canFitShape` демо: четыре угла, а у обычных видов ещё и ручка поворота над рамкой, внутри поля листа. */
private fun Frame.canFit(tolerance: Double = 0.0): Boolean {
    val points = listOf(-1, 1).flatMap { sx -> listOf(-1, 1).map { sy -> findPoint(sx * w / 2, sy * h / 2) } }
    val all = if (isLinear) points else points + findPoint(0.0, -h / 2 - ROTATE_HANDLE)
    return all.all { (px, py) ->
        px >= FIELD - tolerance && px <= SHEET_WIDTH - FIELD + tolerance &&
            py >= FIELD - tolerance && py <= SHEET_HEIGHT - FIELD + tolerance
    }
}

private fun Frame.interpolate(target: Frame, ratio: Double) = copy(
    x = x + (target.x - x) * ratio,
    y = y + (target.y - y) * ratio,
    w = w + (target.w - w) * ratio,
    h = h + (target.h - h) * ratio,
    angle = angle + (target.angle - angle) * ratio,
)

/**
 * `limitShapeTransform` демо: если [candidate] не помещается, ищется самое большое подвижение от [start] к нему, при
 * котором фигура ещё на листе (двадцать делений пополам). Ничего не помещается — остаётся [start].
 */
private fun limitTransform(start: Frame, candidate: Frame): Frame {
    if (candidate.canFit()) return candidate
    var low = 0.0
    var high = 1.0
    var result = start
    repeat(LIMIT_STEPS) {
        val ratio = (low + high) / 2
        val trial = start.interpolate(candidate, ratio)
        if (trial.canFit()) {
            low = ratio
            result = trial
        } else {
            high = ratio
        }
    }
    return result
}

/** Новая фигура на листе: рамка 220 × 220 (у линии и стрелок 220 × 80) по центру, без поворота. */
fun insertShape(kind: Shape.Kind, width: Float): Shape {
    val height = if (kind.isLinear) LINE_HEIGHT else INSERTED_SIZE
    val frame = Frame((SHEET_WIDTH - INSERTED_SIZE) / 2, (SHEET_HEIGHT - height) / 2, INSERTED_SIZE, height, 0.0, kind.isLinear)
    return frame.toShape(Shape(kind, 0f, 0f, 1f, 1f, 0f, width))
}

/** Помещается ли фигура на лист: углы рамки и ручка поворота не ближе 32 к краю. */
fun canFitShape(shape: Shape): Boolean = shape.toFrame().canFit(TOLERANCE)

/** Перенос на [dx], [dy] единиц карточки; у поля упирается плавно. */
fun moveShape(shape: Shape, dx: Float, dy: Float): Shape {
    val frame = shape.toFrame()
    val moved = frame.copy(x = frame.x + dx / UNIT, y = frame.y + dy / UNIT)
    return limitTransform(frame, moved).toShape(shape)
}

/**
 * Растягивание за угол [handle] на [dx], [dy] единиц карточки (в мире, не в осях фигуры): противоположный угол стоит на
 * месте, рамка не меньше 80 по каждой стороне.
 */
fun resizeShape(shape: Shape, handle: ShapeHandle, dx: Float, dy: Float): Shape {
    require(handle in CORNERS) { "Растягивать можно только за угол, не за $handle" }
    val frame = shape.toFrame()
    val radians = Math.toRadians(frame.angle)
    val worldX = dx / UNIT
    val worldY = dy / UNIT
    val localX = worldX * cos(radians) + worldY * sin(radians)
    val localY = -worldX * sin(radians) + worldY * cos(radians)
    var left = -frame.w / 2
    var right = frame.w / 2
    var top = -frame.h / 2
    var bottom = frame.h / 2
    if (handle == ShapeHandle.TOP_LEFT || handle == ShapeHandle.BOTTOM_LEFT) left = min(left + localX, right - MIN_FRAME)
    if (handle == ShapeHandle.TOP_RIGHT || handle == ShapeHandle.BOTTOM_RIGHT) right = max(right + localX, left + MIN_FRAME)
    if (handle == ShapeHandle.TOP_LEFT || handle == ShapeHandle.TOP_RIGHT) top = min(top + localY, bottom - MIN_FRAME)
    if (handle == ShapeHandle.BOTTOM_LEFT || handle == ShapeHandle.BOTTOM_RIGHT) bottom = max(bottom + localY, top + MIN_FRAME)
    val (centerX, centerY) = frame.findPoint((left + right) / 2, (top + bottom) / 2)
    val candidate = frame.copy(
        x = centerX - (right - left) / 2,
        y = centerY - (bottom - top) / 2,
        w = right - left,
        h = bottom - top,
    )
    return limitTransform(frame, candidate).toShape(shape)
}

private val CORNERS = setOf(ShapeHandle.TOP_LEFT, ShapeHandle.TOP_RIGHT, ShapeHandle.BOTTOM_LEFT, ShapeHandle.BOTTOM_RIGHT)

/**
 * Поворот вокруг центра: указатель сошёл с точки ([startX], [startY]) на ([x], [y]). Угол между направлениями на них
 * приводится к −180…180, поэтому поворот идёт короткой дорогой через границу.
 */
fun rotateShape(shape: Shape, startX: Float, startY: Float, x: Float, y: Float): Shape {
    val frame = shape.toFrame()
    val centerX = frame.x + frame.w / 2
    val centerY = frame.y + frame.h / 2
    val delta = atan2(y / UNIT - centerY, x / UNIT - centerX) - atan2(startY / UNIT - centerY, startX / UNIT - centerX)
    val turn = Math.toDegrees(atan2(sin(delta), cos(delta)))
    return limitTransform(frame, frame.copy(angle = frame.angle + turn)).toShape(shape)
}

/**
 * Конец линии или стрелки ([handle] — `START` или `END`) уведён в точку ([x], [y]). Второй конец стоит на месте, ось
 * (0,76 ширины рамки) идёт от него к указателю и не короче 64, рамка 80 высотой. Не помещается — указатель ведётся к
 * концу по двадцати делениям, а не рамка: так конец не прыгает.
 */
fun moveShapeEndpoint(shape: Shape, handle: ShapeHandle, x: Float, y: Float): Shape {
    require(handle == ShapeHandle.START || handle == ShapeHandle.END) { "Двигать можно только конец, не $handle" }
    val frame = shape.toFrame()
    val isStart = handle == ShapeHandle.START
    val moving = frame.findPoint((if (isStart) -END_SHARE else END_SHARE) * frame.w, 0.0)
    val fixed = frame.findPoint((if (isStart) END_SHARE else -END_SHARE) * frame.w, 0.0)
    val target = Pair(x / UNIT, y / UNIT)
    val candidate = buildEndpointFrame(frame, fixed, moving, target, isStart)
    if (candidate.canFit()) return candidate.toShape(shape)
    var low = 0.0
    var high = 1.0
    var result = frame
    repeat(LIMIT_STEPS) {
        val ratio = (low + high) / 2
        val trial = buildEndpointFrame(
            frame,
            fixed,
            moving,
            Pair(moving.first + (target.first - moving.first) * ratio, moving.second + (target.second - moving.second) * ratio),
            isStart,
        )
        if (trial.canFit()) {
            low = ratio
            result = trial
        } else {
            high = ratio
        }
    }
    return result.toShape(shape)
}

private fun buildEndpointFrame(
    frame: Frame,
    fixed: Pair<Double, Double>,
    moving: Pair<Double, Double>,
    target: Pair<Double, Double>,
    isStart: Boolean,
): Frame {
    var dx = target.first - fixed.first
    var dy = target.second - fixed.second
    var length = hypot(dx, dy)
    if (length < MIN_AXIS) {
        // Короче 64 ось не бывает; нулевая длина оставляет направление от неподвижного конца к прежнему подвижному.
        val angle = if (length > TOLERANCE) atan2(dy, dx) else atan2(moving.second - fixed.second, moving.first - fixed.first)
        dx = cos(angle) * MIN_AXIS
        dy = sin(angle) * MIN_AXIS
        length = MIN_AXIS
    }
    val width = length / AXIS_SHARE
    return frame.copy(
        x = fixed.first + dx / 2 - width / 2,
        y = fixed.second + dy / 2 - LINE_HEIGHT / 2,
        w = width,
        h = LINE_HEIGHT,
        angle = Math.toDegrees(if (isStart) atan2(-dy, -dx) else atan2(dy, dx)),
    )
}

/**
 * Жест над выделенной фигурой: [handle] — ручка, за которую взялись (`null` — перенос за контур), ([startX], [startY]) —
 * где перо коснулось листа, [startShape] — фигура в этот момент. Каждое движение считается от начала жеста, а не от
 * прошлого движения, поэтому упор в поле не накапливает ошибку и возврат пера возвращает фигуру.
 */
data class ActiveShapeGesture(val handle: ShapeHandle?, val startX: Float, val startY: Float, val startShape: Shape) {
    /** Фигура, когда перо в точке ([x], [y]). */
    fun applyTo(x: Float, y: Float): Shape = when (handle) {
        null -> moveShape(startShape, x - startX, y - startY)
        ShapeHandle.ROTATE -> rotateShape(startShape, startX, startY, x, y)
        ShapeHandle.START, ShapeHandle.END -> moveShapeEndpoint(startShape, handle, x, y)
        else -> resizeShape(startShape, handle, x - startX, y - startY)
    }
}
