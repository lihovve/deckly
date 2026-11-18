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
 * Перенос чистых функций `design/editor-shapes.js`: те же формулы движений. Демо считает в единицах макета на листе
 * 928 × 963; здесь рамка переводится из единиц карточки в единицы макета, считается и возвращается обратно.
 * `ShapeTransformTest` сверяет движения без упора с значениями, снятыми у самого демо. Отличия от демо — по замечаниям
 * владельца после проверки 18 ноября (фигура должна доходить до края листа): что считается «помещается на лист» (см.
 * [canFitShape]) и как вставляется фигура (см. [insertShape]); упор у края и вставку проверяют свои тесты.
 */

/** Ручка выделенной фигуры: четыре угла рамки, два конца линии или стрелки и ручка поворота. */
enum class ShapeHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, START, END, ROTATE }

private const val SHEET_WIDTH = 928.0
private const val SHEET_HEIGHT = 963.0

private const val MIN_FRAME = 80.0
private const val MIN_AXIS = 64.0
private const val AXIS_SHARE = 0.76
private const val END_SHARE = 0.38
private const val LINE_HEIGHT = 80.0
private const val LIMIT_STEPS = 20

/**
 * Допуск в единицах макета на потерю точности при переводе рамки в `Float` и обратно: фигура, которую упор оставил
 * ровно у края листа, после записи не должна вдруг «не помещаться». Только для проверки готовой фигуры ([canFitShape]) и как
 * запас, с которым возвращается на лист стартовая рамка жеста ([pullIntoSheet]); сами движения считают строго, иначе упор
 * уходил бы за край на величину допуска.
 */
private const val TOLERANCE = 1e-3

/**
 * Рамка в единицах макета: `x`, `y` — левый верхний угол до поворота, [angle] в градусах вокруг центра, [outline] — толщина
 * контура фигуры: у края листа она выступает за точки правила «помещается» на половину.
 */
private data class Frame(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val angle: Double,
    val kind: Shape.Kind,
    val outline: Double,
) {
    val isLinear: Boolean get() = kind.isLinear
}

private val UNIT = CardUnits.PER_DESIGN_UNIT.toDouble()

private fun Shape.toFrame() = Frame(
    left / UNIT,
    top / UNIT,
    (right.toDouble() - left) / UNIT,
    (bottom.toDouble() - top) / UNIT,
    angle.toDouble(),
    kind,
    width / UNIT,
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

/**
 * Помещается ли фигура на лист. Обычные виды: четыре угла рамки на листе, ручка поворота в правило не входит. Линия и
 * стрелки: на листе все точки контура (ось и острия, из того же кода, что и рисует фигуру), а рамка высотой 80 у них
 * невидима и не считается. Лист для этих точек уже на половину толщины контура с каждой стороны: концы и стыки контура
 * круглые, поэтому ровно полтолщины выступает за точку, и без этого край холста срезал бы половину линии.
 */
private fun Frame.canFit(tolerance: Double = 0.0): Boolean {
    val margin = outline / 2 - tolerance
    return listRulePoints().all { (px, py) ->
        px >= margin && px <= SHEET_WIDTH - margin && py >= margin && py <= SHEET_HEIGHT - margin
    }
}

// Точки правила «помещается»: углы рамки обычного вида или точки контура линии и стрелок.
private fun Frame.listRulePoints(): List<Pair<Double, Double>> = if (isLinear) listContourPoints() else listCorners()

/**
 * Рамка, с которой начинается жест. Фигура из файла прежней версии могла стоять у края или за ним по прежнему правилу (без
 * толщины контура, с рамкой по сетке значка): ни один кандидат от такой рамки не поместился бы, и у стрелки, прижатой концом
 * к краю, второй конец не двигался бы совсем. Поэтому не помещающаяся рамка сдвигается внутрь на величину выступа по каждой
 * оси (перенос, размер и угол прежние); помещающаяся возвращается как есть, без единого бита изменений.
 */
private fun Frame.pullIntoSheet(): Frame {
    if (canFit()) return this
    // Запас [TOLERANCE] сверх границы: пересчёт точек от сдвинутой рамки даёт погрешность округления, и рамка, поставленная
    // ровно на границу, могла бы из-за неё не поместиться; результат жеста к тому же записывается в `Float`.
    val margin = outline / 2 + TOLERANCE
    val points = listRulePoints()
    val shiftX = computeShift(points.minOf { it.first }, points.maxOf { it.first }, SHEET_WIDTH, margin)
    val shiftY = computeShift(points.minOf { it.second }, points.maxOf { it.second }, SHEET_HEIGHT, margin)
    return copy(x = x + shiftX, y = y + shiftY)
}

// Сдвиг вдоль оси длиной [size], чтобы точки от [lowest] до [highest] встали на лист с отступом [margin]: вправо за левый выступ, влево за правый.
private fun computeShift(lowest: Double, highest: Double, size: Double, margin: Double): Double =
    max(0.0, margin - lowest) - max(0.0, highest - (size - margin))

private fun Frame.listCorners(): List<Pair<Double, Double>> =
    listOf(-1, 1).flatMap { sx -> listOf(-1, 1).map { sy -> findPoint(sx * w / 2, sy * h / 2) } }

// Точки контура линии или стрелки в единицах макета; толщина контура на их положение не влияет (её учитывает [canFit]). Та же
// формула, что рисует фигуру (`listContourOutlines`), но от рамки напрямую в `Double`: через `Shape` рамка округлялась бы до
// `Float`, шум порядка 5e-5 сравним с зазором у фигуры, стоящей у границы, и кандидат помещался бы случайно.
private fun Frame.listContourPoints(): List<Pair<Double, Double>> =
    listContourOutlines(kind, x * UNIT, y * UNIT, (x + w) * UNIT, (y + h) * UNIT, angle).flatten().map { (px, py) -> px / UNIT to py / UNIT }

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

/**
 * Новая фигура на листе по центру, без поворота. У обычного вида большая сторона рамки равна [size] единиц макета, вторая
 * идёт по пропорциям контура вида, но не меньше [MIN_FRAME]: прямоугольник при 220 получается 220 × 183,3, капля при 100 —
 * 80 × 100. У линии и стрелок рамка [size] × 80. В демо макета рамка квадратная, а контур стоит в ней с полями значка;
 * приложение ставит рамку по контуру (замечание владельца 18 ноября: фигура должна доходить до края листа). [size]
 * держится в границах ползунка палитры, их следит вызывающий.
 */
fun insertShape(kind: Shape.Kind, width: Float, size: Float): Shape {
    val (frameWidth, frameHeight) = if (kind.isLinear) size.toDouble() to LINE_HEIGHT else sizeContourFrame(kind, size.toDouble())
    val frame = Frame(
        (SHEET_WIDTH - frameWidth) / 2, (SHEET_HEIGHT - frameHeight) / 2, frameWidth, frameHeight, 0.0, kind, width / UNIT,
    )
    return frame.toShape(Shape(kind, 0f, 0f, 1f, 1f, 0f, width))
}

// Ширина и высота рамки вида по его контуру: большая сторона [size], вторая по отношению сторон контура, но не меньше 80.
private fun sizeContourFrame(kind: Shape.Kind, size: Double): Pair<Double, Double> {
    val bounds = kind.findGridBounds()
    val aspect = (bounds.maxX.toDouble() - bounds.minX) / (bounds.maxY - bounds.minY)
    return if (aspect >= 1) size to max(size / aspect, MIN_FRAME) else max(size * aspect, MIN_FRAME) to size
}

/**
 * Помещается ли фигура на лист: контур целиком на листе 928 × 963 единиц макета вместе со своей толщиной. Точки правила:
 * у обычных видов четыре угла рамки (ручка поворота не считается), у линии и стрелок все точки контура; лист для них уже
 * на половину толщины контура с каждой стороны, поля сверх этого нет. Так сделано по замечанию владельца после проверки
 * 18 ноября (фигура должна доходить до края листа); `canFitShape` демо макета держит угол и ручку не ближе 32 к краю.
 */
fun canFitShape(shape: Shape): Boolean = shape.toFrame().canFit(TOLERANCE)

/** Перенос на [dx], [dy] единиц карточки; у края листа упирается плавно. */
fun moveShape(shape: Shape, dx: Float, dy: Float): Shape {
    val frame = shape.toFrame().pullIntoSheet()
    val moved = frame.copy(x = frame.x + dx / UNIT, y = frame.y + dy / UNIT)
    return limitTransform(frame, moved).toShape(shape)
}

/**
 * Растягивание за угол [handle] на [dx], [dy] единиц карточки (в мире, не в осях фигуры): противоположный угол стоит на
 * месте, рамка не меньше 80 по каждой стороне.
 */
fun resizeShape(shape: Shape, handle: ShapeHandle, dx: Float, dy: Float): Shape {
    require(handle in CORNERS) { "Растягивать можно только за угол, не за $handle" }
    val frame = shape.toFrame().pullIntoSheet()
    val radians = Math.toRadians(frame.angle)
    val worldX = dx / UNIT
    val worldY = dy / UNIT
    val localX = worldX * cos(radians) + worldY * sin(radians)
    val localY = -worldX * sin(radians) + worldY * cos(radians)
    // Меньше 80 сторона не становится, но фигура из файла прежней версии бывает уже меньше: ей нижняя граница — её размер на
    // начало жеста, иначе первое же касание угла рывком растило бы её до 80. Расти она может.
    val minWidth = min(MIN_FRAME, frame.w)
    val minHeight = min(MIN_FRAME, frame.h)
    var left = -frame.w / 2
    var right = frame.w / 2
    var top = -frame.h / 2
    var bottom = frame.h / 2
    if (handle == ShapeHandle.TOP_LEFT || handle == ShapeHandle.BOTTOM_LEFT) left = min(left + localX, right - minWidth)
    if (handle == ShapeHandle.TOP_RIGHT || handle == ShapeHandle.BOTTOM_RIGHT) right = max(right + localX, left + minWidth)
    if (handle == ShapeHandle.TOP_LEFT || handle == ShapeHandle.TOP_RIGHT) top = min(top + localY, bottom - minHeight)
    if (handle == ShapeHandle.BOTTOM_LEFT || handle == ShapeHandle.BOTTOM_RIGHT) bottom = max(bottom + localY, top + minHeight)
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
    val frame = shape.toFrame().pullIntoSheet()
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
    val frame = shape.toFrame().pullIntoSheet()
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
