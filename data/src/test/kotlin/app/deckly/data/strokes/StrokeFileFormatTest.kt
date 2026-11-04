package app.deckly.data.strokes

import app.deckly.data.buildTestStroke
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StrokeFileFormatTest {

    private fun writeToBytes(front: List<Stroke>, back: List<Stroke>): ByteArray =
        ByteArrayOutputStream().also { writeStrokeFile(it, front, back) }.toByteArray()

    private fun readFromBytes(bytes: ByteArray): Pair<List<Stroke>, List<Stroke>> =
        readStrokeFile(ByteArrayInputStream(bytes))

    private fun assertDamaged(bytes: ByteArray) {
        assertThrows(StrokeFileException::class.java) { readFromBytes(bytes) }
    }

    @Test
    fun strokeFileRoundTripsExactFloats() {
        val front = listOf(
            Stroke(6f, listOf(StrokePoint(0f, 0f, 0f), StrokePoint(0.1f, 0.2f, 0.5f), StrokePoint(-12.75f, 1f / 3f, 1f))),
            Stroke(2.5f, listOf(StrokePoint(-0.001f, 1e-7f, 0.25f))),
        )
        val back = listOf(buildTestStroke(3))

        val (readFront, readBack) = readFromBytes(writeToBytes(front, back))

        assertEquals(front, readFront)
        assertEquals(back, readBack)
    }

    @Test
    fun emptySidesAndEmptyStrokeRoundTrip() {
        val front = listOf(Stroke(3f, emptyList()))

        val (readFront, readBack) = readFromBytes(writeToBytes(front, emptyList()))

        assertEquals(front, readFront)
        assertEquals(emptyList<Stroke>(), readBack)
    }

    @Test
    fun layoutIsSignatureVersionAndLittleEndianSides() {
        val front = listOf(Stroke(6f, listOf(StrokePoint(1f, 2f, 0.5f), StrokePoint(3f, 4f, 1f))))

        val bytes = writeToBytes(front, emptyList())

        // DKS1, версия 1, число штрихов лица 1, штрих: толщина, 2 точки по 3 float, число штрихов оборота 0.
        val expected = ByteBuffer.allocate(8 + 4 + (4 + 4 + 2 * 12) + 4).order(ByteOrder.LITTLE_ENDIAN)
            .put("DKS1".toByteArray(Charsets.US_ASCII)).putInt(1)
            .putInt(1).putFloat(6f).putInt(2)
            .putFloat(1f).putFloat(2f).putFloat(0.5f).putFloat(3f).putFloat(4f).putFloat(1f)
            .putInt(0)
            .array()
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun damagedStrokeFileIsReported() {
        val valid = writeToBytes(listOf(buildTestStroke(1), buildTestStroke(2)), listOf(buildTestStroke(3)))

        // Обрезанный файл на любой длине.
        for (length in valid.indices) assertDamaged(valid.copyOf(length))

        // Чужая сигнатура.
        assertDamaged(valid.copyOf().also { it[0] = 'X'.code.toByte() })

        // Версия 2.
        assertDamaged(valid.copyOf().also { it[4] = 2 })
    }

    private fun buildFileWithOnePoint(width: Float, x: Float, y: Float, pressure: Float): ByteArray =
        ByteBuffer.allocate(8 + 4 + (4 + 4 + 12) + 4).order(ByteOrder.LITTLE_ENDIAN)
            .put("DKS1".toByteArray(Charsets.US_ASCII)).putInt(1)
            .putInt(1).putFloat(width).putInt(1)
            .putFloat(x).putFloat(y).putFloat(pressure)
            .putInt(0)
            .array()

    private fun describeReading(bytes: ByteArray): String =
        try {
            readFromBytes(bytes)
            "read"
        } catch (damaged: StrokeFileException) {
            "damaged"
        } catch (other: Exception) {
            "crash ${other::class.simpleName}"
        }

    @Test
    fun strokeFileWithInvalidValuesIsDamaged() {
        assertEquals("контрольный файл читается", "read", describeReading(buildFileWithOnePoint(2f, 1f, 1f, 0.5f)))
        val invalid = mapOf(
            "pressure 1.0001" to buildFileWithOnePoint(2f, 1f, 1f, 1.0001f),
            "pressure -0.1" to buildFileWithOnePoint(2f, 1f, 1f, -0.1f),
            "pressure NaN" to buildFileWithOnePoint(2f, 1f, 1f, Float.NaN),
            "pressure Infinity" to buildFileWithOnePoint(2f, 1f, 1f, Float.POSITIVE_INFINITY),
            "width 0" to buildFileWithOnePoint(0f, 1f, 1f, 0.5f),
            "width -1" to buildFileWithOnePoint(-1f, 1f, 1f, 0.5f),
            "width NaN" to buildFileWithOnePoint(Float.NaN, 1f, 1f, 0.5f),
            "width Infinity" to buildFileWithOnePoint(Float.POSITIVE_INFINITY, 1f, 1f, 0.5f),
            "x NaN" to buildFileWithOnePoint(2f, Float.NaN, 1f, 0.5f),
            "y -Infinity" to buildFileWithOnePoint(2f, 1f, Float.NEGATIVE_INFINITY, 0.5f),
        )

        val outcomes = invalid.mapValues { (_, bytes) -> describeReading(bytes) }

        assertEquals(invalid.mapValues { "damaged" }, outcomes)
    }

    @Test
    fun implausibleCountsAreReportedWithoutAllocating() {
        val valid = writeToBytes(listOf(buildTestStroke(1)), emptyList())

        // Смещение 8 — число штрихов лица, 16 — число точек первого штриха.
        assertDamaged(replaceInt(valid, 8, Int.MAX_VALUE))
        assertDamaged(replaceInt(valid, 8, -1))
        assertDamaged(replaceInt(valid, 16, Int.MAX_VALUE))
    }

    private fun replaceInt(bytes: ByteArray, offset: Int, value: Int): ByteArray =
        bytes.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value) }

    @Test
    fun trailingBytesAreReported() {
        val valid = writeToBytes(listOf(buildTestStroke(1)), emptyList())

        assertDamaged(valid + byteArrayOf(0))
    }
}
