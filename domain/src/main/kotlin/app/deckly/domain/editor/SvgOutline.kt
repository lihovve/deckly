package app.deckly.domain.editor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Точка контура в сетке 100 × 100: так задан значок фигуры в `design/build.py`. */
data class GridPoint(val x: Float, val y: Float)

/** Самый длинный отрезок ломаной, которой заменена дуга или кривая, в единицах сетки. */
private const val MAX_SEGMENT = 1.9

private val ELEMENT = Regex("<(\\w+)([^>]*?)/>")
private val ATTRIBUTE = Regex("([\\w-]+)=\"([^\"]*)\"")
private val TOKEN = Regex("[A-Za-z]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

/**
 * Контуры разметки значка: `<path d>`, `<rect>`, `<ellipse>` и `<circle>`. В `d` разрешены `M m L l H h V v C c A a Z z`
 * с неявным повтором команды (`M88 50 68 70`, `m50 10 12 27 29 3-22 20`). Каждый `M` начинает новую ломаную, `Z`
 * замыкает её. Дуги и кривые заменены ломаной с отрезками не длиннее 2 единиц сетки. Всё, чего нет в списке, — ошибка:
 * тихо потерянный отрезок дал бы искажённую фигуру.
 */
internal fun parseSvgOutlines(svg: String): List<List<GridPoint>> =
    ELEMENT.findAll(svg).flatMap { match ->
        readElement(match.groupValues[1], readAttributes(match.groupValues[2])).asSequence()
    }.toList()

private fun readAttributes(text: String): Map<String, String> =
    ATTRIBUTE.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

private fun readElement(tag: String, attributes: Map<String, String>): List<List<GridPoint>> {
    fun readNumber(name: String): Double =
        requireNotNull(attributes[name]?.toDoubleOrNull()) { "В <$tag> нет числа $name" }
    return when (tag) {
        "path" -> readPath(requireNotNull(attributes["d"]) { "В <path> нет d" })
        "rect" -> {
            val x = readNumber("x")
            val y = readNumber("y")
            val right = x + readNumber("width")
            val bottom = y + readNumber("height")
            val corners = listOf(buildPoint(x, y), buildPoint(right, y), buildPoint(right, bottom), buildPoint(x, bottom))
            listOf(corners + corners.first())
        }
        "ellipse" -> listOf(flattenEllipse(readNumber("cx"), readNumber("cy"), readNumber("rx"), readNumber("ry")))
        "circle" -> listOf(flattenEllipse(readNumber("cx"), readNumber("cy"), readNumber("r"), readNumber("r")))
        else -> throw IllegalArgumentException("Элемент <$tag> не поддерживается")
    }
}

private fun buildPoint(x: Double, y: Double) = GridPoint(x.toFloat(), y.toFloat())

/** Ломаная по эллипсу: число отрезков кратно четырём, поэтому крайние точки на осях попадают в неё. */
private fun flattenEllipse(cx: Double, cy: Double, rx: Double, ry: Double): List<GridPoint> {
    val steps = 4 * max(1, ceil(2 * PI * max(rx, ry) / (4 * MAX_SEGMENT)).toInt())
    val points = (0 until steps).map { index ->
        val angle = 2 * PI * index / steps
        buildPoint(cx + rx * cos(angle), cy + ry * sin(angle))
    }
    return points + points.first()
}

private val ARGUMENTS = mapOf('M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'A' to 7, 'Z' to 0)

private fun readPath(data: String): List<List<GridPoint>> {
    val tokens = TOKEN.findAll(data).map { it.value }.toList()
    val builder = PathBuilder()
    var index = 0
    var command = ' '
    while (index < tokens.size) {
        val token = tokens[index]
        if (token[0].isLetter()) {
            command = token[0]
            index++
            require(command.uppercaseChar() in ARGUMENTS) { "Команда $command не поддерживается" }
            require(builder.isStarted || command.uppercaseChar() == 'M') { "Путь должен начинаться с M: $data" }
            if (command.uppercaseChar() == 'Z') {
                builder.close()
                continue
            }
        } else {
            require(command != ' ') { "Данные пути начинаются с числа: $data" }
            require(command.uppercaseChar() != 'Z') { "После Z ждали команду: $token" }
        }
        val count = ARGUMENTS.getValue(command.uppercaseChar())
        require(index + count <= tokens.size) { "Не хватает чисел для $command: $data" }
        val numbers = tokens.subList(index, index + count).map {
            requireNotNull(it.toDoubleOrNull()) { "Ждали число, а встретили $it" }
        }
        index += count
        builder.apply(command, numbers)
        // Неявный повтор: после M следующие пары — это L, после m — l.
        if (command == 'M') command = 'L' else if (command == 'm') command = 'l'
    }
    return builder.finish()
}

/** Текущее состояние разбора пути: текущая точка, начало подконтура и ломаные. */
private class PathBuilder {
    private val outlines = mutableListOf<List<GridPoint>>()
    private var line: MutableList<GridPoint>? = null
    private var x = 0.0
    private var y = 0.0
    private var startX = 0.0
    private var startY = 0.0

    /** Был ли уже `M`: путь без начальной точки не имеет смысла. */
    var isStarted = false
        private set

    fun apply(command: Char, n: List<Double>) {
        val isRelative = command.isLowerCase()
        val dx = if (isRelative) x else 0.0
        val dy = if (isRelative) y else 0.0
        when (command.uppercaseChar()) {
            'M' -> {
                isStarted = true
                flush()
                x = n[0] + dx
                y = n[1] + dy
                startX = x
                startY = y
                line = mutableListOf(buildPoint(x, y))
            }
            'L' -> lineTo(n[0] + dx, n[1] + dy)
            'H' -> lineTo(n[0] + dx, y)
            'V' -> lineTo(x, n[0] + dy)
            'C' -> curveTo(x, y, n[0] + dx, n[1] + dy, n[2] + dx, n[3] + dy, n[4] + dx, n[5] + dy)
            'A' -> arcTo(n[0], n[1], n[2], n[3] != 0.0, n[4] != 0.0, n[5] + dx, n[6] + dy)
        }
    }

    fun close() {
        val current = ensureLine()
        current += buildPoint(startX, startY)
        x = startX
        y = startY
        flush()
    }

    fun finish(): List<List<GridPoint>> {
        flush()
        return outlines
    }

    private fun flush() {
        line?.takeIf { it.size >= 2 }?.let { outlines += it }
        line = null
    }

    // Рисующая команда без текущей ломаной (после Z) начинает новую в текущей точке.
    private fun ensureLine(): MutableList<GridPoint> =
        line ?: mutableListOf(buildPoint(x, y)).also { line = it }

    private fun lineTo(toX: Double, toY: Double) {
        ensureLine() += buildPoint(toX, toY)
        x = toX
        y = toY
    }

    private fun curveTo(x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) {
        val current = ensureLine()
        val longestLeg = maxOf(hypot(x1 - x0, y1 - y0), hypot(x2 - x1, y2 - y1), hypot(x3 - x2, y3 - y2))
        // Скорость кривой не больше трёх самых длинных звеньев: при таком числе шагов отрезок не длиннее 2 единиц.
        val steps = chooseStepCount(4, ceil(1.5 * longestLeg / (MAX_SEGMENT / 2)).toInt())
        for (step in 1..steps) {
            val t = step.toDouble() / steps
            val u = 1 - t
            current += buildPoint(
                u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3,
            )
        }
        x = x3
        y = y3
    }

    /** Дуга по преобразованию из конечных точек в центр, приложение F.6.5 спецификации SVG. */
    private fun arcTo(
        rxIn: Double,
        ryIn: Double,
        rotationDegrees: Double,
        isLarge: Boolean,
        isSweep: Boolean,
        toX: Double,
        toY: Double,
    ) {
        val current = ensureLine()
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        if ((toX == x && toY == y) || rx == 0.0 || ry == 0.0) {
            current += buildPoint(toX, toY)
            x = toX
            y = toY
            return
        }
        val phi = Math.toRadians(rotationDegrees)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val halfDx = (x - toX) / 2
        val halfDy = (y - toY) / 2
        val x1 = cosPhi * halfDx + sinPhi * halfDy
        val y1 = -sinPhi * halfDx + cosPhi * halfDy
        val lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
        if (lambda > 1) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }
        val numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        val denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
        val coefficient = (if (isLarge != isSweep) 1 else -1) * sqrt(max(0.0, numerator / denominator))
        val centerX1 = coefficient * rx * y1 / ry
        val centerY1 = -coefficient * ry * x1 / rx
        val centerX = cosPhi * centerX1 - sinPhi * centerY1 + (x + toX) / 2
        val centerY = sinPhi * centerX1 + cosPhi * centerY1 + (y + toY) / 2
        val startAngle = computeAngle(1.0, 0.0, (x1 - centerX1) / rx, (y1 - centerY1) / ry)
        var sweep = computeAngle((x1 - centerX1) / rx, (y1 - centerY1) / ry, (-x1 - centerX1) / rx, (-y1 - centerY1) / ry)
        if (!isSweep && sweep > 0) sweep -= 2 * PI
        if (isSweep && sweep < 0) sweep += 2 * PI
        val steps = chooseStepCount(2, ceil(max(rx, ry) * abs(sweep) / MAX_SEGMENT).toInt())
        for (step in 1 until steps) {
            val angle = startAngle + sweep * step / steps
            val px = rx * cos(angle)
            val py = ry * sin(angle)
            current += buildPoint(cosPhi * px - sinPhi * py + centerX, sinPhi * px + cosPhi * py + centerY)
        }
        current += buildPoint(toX, toY)
        x = toX
        y = toY
    }

    /** Угол от вектора (ux, uy) к (vx, vy) со знаком, от −π до π. */
    private fun computeAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val length = hypot(ux, uy) * hypot(vx, vy)
        val angle = acos((dot / length).coerceIn(-1.0, 1.0))
        return if (ux * vy - uy * vx < 0) -angle else angle
    }

    private fun chooseStepCount(minimum: Int, steps: Int): Int = max(minimum, steps + steps % 2)
}
