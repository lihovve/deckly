package app.deckly.data.strokes

import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Файл штрихов повреждён, неполон, чужой или новее приложения; а также пропал. */
class StrokeFileException(message: String, cause: Throwable? = null) : IOException(message, cause)

private val SIGNATURE = "DKS1".toByteArray(Charsets.US_ASCII)

/** Пишется всегда версия 2, читаются обе: версия 1 — файлы, которые уже лежат на устройствах, без фигур. */
private const val VERSION = 2
private const val FIRST_VERSION = 1
private const val HEADER_BYTES = 8
private const val COUNT_BYTES = 4
private const val STROKE_HEADER_BYTES = 8
private const val POINT_BYTES = 12

/** Фигура: `int32` код вида и шесть `float32`: `left, top, right, bottom, angle, width`. */
private const val SHAPE_BYTES = 28

/**
 * Формат файла штрихов (little-endian): `DKS1`, `int32` версия, затем две стороны, лицо и оборот. У стороны `int32`
 * число штрихов; у штриха `float32` толщина, `int32` число точек и точки по `float32 x, y, pressure`. В версии 2 после
 * штрихов каждой стороны идут `int32` число фигур и сами фигуры ([SHAPE_BYTES] на каждую, код вида — `Shape.Kind.code`);
 * в версии 1 фигур нет. Один файл — обе стороны одной карточки.
 */
fun writeStrokeFile(output: OutputStream, front: CardSide, back: CardSide) {
    val size = HEADER_BYTES + computeSideBytes(front) + computeSideBytes(back)
    val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(SIGNATURE).putInt(VERSION)
    putSide(buffer, front)
    putSide(buffer, back)
    output.write(buffer.array())
}

/** Читает файл целиком; любой изъян формата — [StrokeFileException]. */
fun readStrokeFile(input: InputStream): Pair<CardSide, CardSide> {
    val buffer = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    if (buffer.remaining() < HEADER_BYTES) throw StrokeFileException("Файл короче заголовка")
    val signature = ByteArray(SIGNATURE.size).also { buffer.get(it) }
    if (!signature.contentEquals(SIGNATURE)) throw StrokeFileException("Это не файл штрихов Deckly")
    val version = buffer.getInt()
    if (version != FIRST_VERSION && version != VERSION) throw StrokeFileException("Версия формата $version не поддерживается")
    val hasShapes = version >= VERSION
    val front = readSide(buffer, hasShapes)
    val back = readSide(buffer, hasShapes)
    if (buffer.hasRemaining()) throw StrokeFileException("В конце файла лишние байты: ${buffer.remaining()}")
    return front to back
}

private fun computeSideBytes(side: CardSide): Int =
    COUNT_BYTES + side.strokes.sumOf { STROKE_HEADER_BYTES + it.points.size * POINT_BYTES } +
        COUNT_BYTES + side.shapes.size * SHAPE_BYTES

private fun putSide(buffer: ByteBuffer, side: CardSide) {
    buffer.putInt(side.strokes.size)
    for (stroke in side.strokes) {
        buffer.putFloat(stroke.width).putInt(stroke.points.size)
        for (point in stroke.points) buffer.putFloat(point.x).putFloat(point.y).putFloat(point.pressure)
    }
    buffer.putInt(side.shapes.size)
    for (shape in side.shapes) {
        buffer.putInt(shape.kind.code)
            .putFloat(shape.left).putFloat(shape.top).putFloat(shape.right).putFloat(shape.bottom)
            .putFloat(shape.angle).putFloat(shape.width)
    }
}

private fun readSide(buffer: ByteBuffer, hasShapes: Boolean): CardSide {
    val strokes = List(readCount(buffer, STROKE_HEADER_BYTES, "штрихов")) { readStroke(buffer) }
    val shapes = if (hasShapes) List(readCount(buffer, SHAPE_BYTES, "фигур")) { readShape(buffer) } else emptyList()
    return CardSide(strokes, shapes)
}

private fun readStroke(buffer: ByteBuffer): Stroke {
    if (buffer.remaining() < STROKE_HEADER_BYTES) throw StrokeFileException("Файл оборван посреди штриха")
    val width = buffer.getFloat()
    val count = readPointCount(buffer)
    return try {
        Stroke(width, List(count) { StrokePoint(buffer.getFloat(), buffer.getFloat(), buffer.getFloat()) })
    } catch (invalid: IllegalArgumentException) {
        // Нажим вне 0..1, не число или нулевая толщина: модель такой штрих создать не даёт.
        throw StrokeFileException("Недопустимое значение в штрихе: ${invalid.message}", invalid)
    }
}

private fun readShape(buffer: ByteBuffer): Shape {
    if (buffer.remaining() < SHAPE_BYTES) throw StrokeFileException("Файл оборван посреди фигуры")
    val code = buffer.getInt()
    val kind = Shape.Kind.findByCode(code) ?: throw StrokeFileException("Неизвестный вид фигуры: $code")
    return try {
        Shape(kind, buffer.getFloat(), buffer.getFloat(), buffer.getFloat(), buffer.getFloat(), buffer.getFloat(), buffer.getFloat())
    } catch (invalid: IllegalArgumentException) {
        // Пустая рамка, не число или нулевая толщина контура: модель такую фигуру создать не даёт.
        throw StrokeFileException("Недопустимое значение в фигуре: ${invalid.message}", invalid)
    }
}

private fun readPointCount(buffer: ByteBuffer): Int {
    val count = buffer.getInt()
    if (count < 0 || count.toLong() * POINT_BYTES > buffer.remaining()) {
        throw StrokeFileException("Недопустимое число точек: $count")
    }
    return count
}

/** Число элементов [what], которое ещё помещается в остаток файла: защита от мусорного счётчика. */
private fun readCount(buffer: ByteBuffer, minItemBytes: Int, what: String): Int {
    if (buffer.remaining() < COUNT_BYTES) throw StrokeFileException("Файл оборван перед счётчиком")
    val count = buffer.getInt()
    if (count < 0 || count.toLong() * minItemBytes > buffer.remaining()) {
        throw StrokeFileException("Недопустимое число $what: $count")
    }
    return count
}
