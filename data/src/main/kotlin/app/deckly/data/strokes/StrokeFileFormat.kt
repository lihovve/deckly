package app.deckly.data.strokes

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
private const val VERSION = 1
private const val HEADER_BYTES = 8
private const val COUNT_BYTES = 4
private const val STROKE_HEADER_BYTES = 8
private const val POINT_BYTES = 12

/**
 * Формат файла штрихов (little-endian): `DKS1`, `int32` версия, затем две стороны, лицо и оборот.
 * У стороны `int32` число штрихов; у штриха `float32` толщина, `int32` число точек и точки по
 * `float32 x, y, pressure`. Один файл — обе стороны одной карточки.
 */
fun writeStrokeFile(output: OutputStream, front: List<Stroke>, back: List<Stroke>) {
    val size = HEADER_BYTES + computeSideBytes(front) + computeSideBytes(back)
    val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(SIGNATURE).putInt(VERSION)
    putSide(buffer, front)
    putSide(buffer, back)
    output.write(buffer.array())
}

/** Читает файл целиком; любой изъян формата — [StrokeFileException]. */
fun readStrokeFile(input: InputStream): Pair<List<Stroke>, List<Stroke>> {
    val buffer = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    if (buffer.remaining() < HEADER_BYTES) throw StrokeFileException("Файл короче заголовка")
    val signature = ByteArray(SIGNATURE.size).also { buffer.get(it) }
    if (!signature.contentEquals(SIGNATURE)) throw StrokeFileException("Это не файл штрихов Deckly")
    val version = buffer.getInt()
    if (version != VERSION) throw StrokeFileException("Версия формата $version не поддерживается")
    val front = readSide(buffer)
    val back = readSide(buffer)
    if (buffer.hasRemaining()) throw StrokeFileException("В конце файла лишние байты: ${buffer.remaining()}")
    return front to back
}

private fun computeSideBytes(strokes: List<Stroke>): Int =
    COUNT_BYTES + strokes.sumOf { STROKE_HEADER_BYTES + it.points.size * POINT_BYTES }

private fun putSide(buffer: ByteBuffer, strokes: List<Stroke>) {
    buffer.putInt(strokes.size)
    for (stroke in strokes) {
        buffer.putFloat(stroke.width).putInt(stroke.points.size)
        for (point in stroke.points) buffer.putFloat(point.x).putFloat(point.y).putFloat(point.pressure)
    }
}

private fun readSide(buffer: ByteBuffer): List<Stroke> {
    val count = readCount(buffer, STROKE_HEADER_BYTES)
    return List(count) { readStroke(buffer) }
}

private fun readStroke(buffer: ByteBuffer): Stroke {
    if (buffer.remaining() < STROKE_HEADER_BYTES) throw StrokeFileException("Файл оборван посреди штриха")
    val width = buffer.getFloat()
    val count = readPointCount(buffer)
    return Stroke(width, List(count) { StrokePoint(buffer.getFloat(), buffer.getFloat(), buffer.getFloat()) })
}

private fun readPointCount(buffer: ByteBuffer): Int {
    val count = buffer.getInt()
    if (count < 0 || count.toLong() * POINT_BYTES > buffer.remaining()) {
        throw StrokeFileException("Недопустимое число точек: $count")
    }
    return count
}

/** Число элементов, которое ещё помещается в остаток файла: защита от мусорного счётчика. */
private fun readCount(buffer: ByteBuffer, minItemBytes: Int): Int {
    if (buffer.remaining() < COUNT_BYTES) throw StrokeFileException("Файл оборван перед счётчиком")
    val count = buffer.getInt()
    if (count < 0 || count.toLong() * minItemBytes > buffer.remaining()) {
        throw StrokeFileException("Недопустимое число штрихов: $count")
    }
    return count
}
