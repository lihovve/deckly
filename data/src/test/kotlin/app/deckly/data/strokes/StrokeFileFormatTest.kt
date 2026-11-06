package app.deckly.data.strokes

import app.deckly.data.buildTestStroke
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Shape
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

    private fun writeToBytes(front: CardSide, back: CardSide): ByteArray =
        ByteArrayOutputStream().also { writeStrokeFile(it, front, back) }.toByteArray()

    private fun readFromBytes(bytes: ByteArray): Pair<CardSide, CardSide> =
        readStrokeFile(ByteArrayInputStream(bytes))

    private fun assertDamaged(bytes: ByteArray) {
        assertThrows(StrokeFileException::class.java) { readFromBytes(bytes) }
    }

    private fun sideOf(vararg strokes: Stroke) = CardSide(strokes.toList())

    private val star = Shape(Shape.Kind.STAR, 100f, 200.5f, 400f, 500.25f, 30f, 6.5f)

    // --- версия 1: файлы, которые уже лежат на устройстве ---------------------------------------------

    /**
     * Файл версии 1 собран из байтов вручную, а не записан `writeStrokeFile`: на устройстве владельца лежат настоящие
     * карточки, и это единственная защита от их потери, если формат читают как-то не так, как его когда-то писали.
     */
    private fun buildVersionOneBytes(): ByteArray =
        ByteBuffer.allocate(8 + (4 + (4 + 4 + 2 * 12)) + (4 + (4 + 4 + 12) + (4 + 4)))
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("DKS1".toByteArray(Charsets.US_ASCII)).putInt(1)
            // Лицо: один штрих толщиной 6, две точки.
            .putInt(1).putFloat(6f).putInt(2)
            .putFloat(1f).putFloat(2f).putFloat(0.5f).putFloat(3f).putFloat(4f).putFloat(1f)
            // Оборот: один штрих толщиной 2,5 из одной точки и штрих без точек.
            .putInt(2).putFloat(2.5f).putInt(1).putFloat(-5f).putFloat(7f).putFloat(0.25f)
            .putFloat(3f).putInt(0)
            .array()

    @Test
    fun readsVersionOneFileWithoutShapes() {
        val (front, back) = readFromBytes(buildVersionOneBytes())

        assertEquals(
            CardSide(listOf(Stroke(6f, listOf(StrokePoint(1f, 2f, 0.5f), StrokePoint(3f, 4f, 1f))))),
            front,
        )
        assertEquals(
            CardSide(listOf(Stroke(2.5f, listOf(StrokePoint(-5f, 7f, 0.25f))), Stroke(3f, emptyList()))),
            back,
        )
        assertEquals(emptyList<Shape>(), front.shapes)
        assertEquals(emptyList<Shape>(), back.shapes)
    }

    @Test
    fun versionOneEmptyFileReadsAsTwoEmptySides() {
        val bytes = ByteBuffer.allocate(8 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
            .put("DKS1".toByteArray(Charsets.US_ASCII)).putInt(1).putInt(0).putInt(0).array()

        assertEquals(CardSide() to CardSide(), readFromBytes(bytes))
    }

    @Test
    fun damagedVersionOneFileIsReportedAtAnyLength() {
        val valid = buildVersionOneBytes()

        for (length in valid.indices) assertDamaged(valid.copyOf(length))
        assertDamaged(valid + byteArrayOf(0))
        // Тело версии 1 под заголовком версии 2: после штрихов не хватает чисел фигур.
        assertDamaged(valid.copyOf().also { it[4] = 2 })
    }

    // --- версия 2 ----------------------------------------------------------------------------------------

    @Test
    fun strokeFileRoundTripsExactFloats() {
        val front = sideOf(
            Stroke(6f, listOf(StrokePoint(0f, 0f, 0f), StrokePoint(0.1f, 0.2f, 0.5f), StrokePoint(-12.75f, 1f / 3f, 1f))),
            Stroke(2.5f, listOf(StrokePoint(-0.001f, 1e-7f, 0.25f))),
        )
        val back = sideOf(buildTestStroke(3))

        val (readFront, readBack) = readFromBytes(writeToBytes(front, back))

        assertEquals(front, readFront)
        assertEquals(back, readBack)
    }

    @Test
    fun emptySidesAndEmptyStrokeRoundTrip() {
        val front = sideOf(Stroke(3f, emptyList()))

        val (readFront, readBack) = readFromBytes(writeToBytes(front, CardSide()))

        assertEquals(front, readFront)
        assertEquals(CardSide(), readBack)
    }

    @Test
    fun shapesOnBothSidesRoundTripWithTheirAngles() {
        val front = CardSide(
            listOf(buildTestStroke(1)),
            listOf(star, Shape(Shape.Kind.ARROW, 10f, 20f, 300f, 100f, -179.5f, 1f)),
        )
        val back = CardSide(emptyList(), listOf(Shape(Shape.Kind.DROP, -50f, -60f, 70f, 80f, 720f, Float.MIN_VALUE)))

        val (readFront, readBack) = readFromBytes(writeToBytes(front, back))

        assertEquals(front, readFront)
        assertEquals(back, readBack)
    }

    @Test
    fun everyKindRoundTripsByItsCode() {
        val shapes = Shape.Kind.entries.map { Shape(it, 1f, 2f, 3f, 4f, 5f, 6f) }

        val (front, _) = readFromBytes(writeToBytes(CardSide(shapes = shapes), CardSide()))

        assertEquals(shapes, front.shapes)
    }

    @Test
    fun layoutIsSignatureVersionTwoStrokesThenShapesPerSide() {
        val front = CardSide(
            listOf(Stroke(6f, listOf(StrokePoint(1f, 2f, 0.5f), StrokePoint(3f, 4f, 1f)))),
            listOf(star),
        )

        val bytes = writeToBytes(front, CardSide())

        // DKS1, версия 2; лицо: 1 штрих (толщина, 2 точки), 1 фигура (код 9 и шесть float); оборот: 0 штрихов, 0 фигур.
        val expected = ByteBuffer.allocate(8 + (4 + (4 + 4 + 2 * 12)) + (4 + 28) + 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
            .put("DKS1".toByteArray(Charsets.US_ASCII)).putInt(2)
            .putInt(1).putFloat(6f).putInt(2)
            .putFloat(1f).putFloat(2f).putFloat(0.5f).putFloat(3f).putFloat(4f).putFloat(1f)
            .putInt(1).putInt(9).putFloat(100f).putFloat(200.5f).putFloat(400f).putFloat(500.25f).putFloat(30f).putFloat(6.5f)
            .putInt(0).putInt(0)
            .array()
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun damagedVersionTwoFileIsReportedAtAnyLength() {
        val valid = writeToBytes(
            CardSide(listOf(buildTestStroke(1), buildTestStroke(2)), listOf(star)),
            CardSide(listOf(buildTestStroke(3)), listOf(star.copy(kind = Shape.Kind.LINE))),
        )

        for (length in valid.indices) assertDamaged(valid.copyOf(length))
        assertDamaged(valid + byteArrayOf(0))
        assertDamaged(valid.copyOf().also { it[0] = 'X'.code.toByte() })
    }

    @Test
    fun versionsOtherThanOneAndTwoAreRejected() {
        val valid = writeToBytes(CardSide(listOf(buildTestStroke(1))), CardSide())

        for (version in listOf(0, 3, 4, -1, Int.MAX_VALUE)) {
            assertDamaged(replaceInt(valid, 4, version))
        }
    }

    // --- недопустимые значения -------------------------------------------------------------------------

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

    /** Файл версии 2 из одной фигуры на лице; значения подставляются как есть, в обход модели. */
    private fun buildFileWithOneShape(
        code: Int = 9,
        left: Float = 100f,
        top: Float = 200f,
        right: Float = 400f,
        bottom: Float = 500f,
        angle: Float = 30f,
        width: Float = 6f,
    ): ByteArray =
        ByteBuffer.allocate(8 + 4 + (4 + 28) + 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
            .put("DKS1".toByteArray(Charsets.US_ASCII)).putInt(2)
            .putInt(0).putInt(1)
            .putInt(code).putFloat(left).putFloat(top).putFloat(right).putFloat(bottom).putFloat(angle).putFloat(width)
            .putInt(0).putInt(0)
            .array()

    @Test
    fun shapeWithInvalidValuesIsDamaged() {
        assertEquals("контрольный файл читается", "read", describeReading(buildFileWithOneShape()))
        val invalid = mapOf(
            "code 0" to buildFileWithOneShape(code = 0),
            "code 16" to buildFileWithOneShape(code = 16),
            "code -1" to buildFileWithOneShape(code = -1),
            "code max" to buildFileWithOneShape(code = Int.MAX_VALUE),
            "left = right" to buildFileWithOneShape(left = 400f, right = 400f),
            "left > right" to buildFileWithOneShape(left = 500f, right = 400f),
            "top = bottom" to buildFileWithOneShape(top = 500f, bottom = 500f),
            "top > bottom" to buildFileWithOneShape(top = 600f, bottom = 500f),
            "left NaN" to buildFileWithOneShape(left = Float.NaN),
            "bottom Infinity" to buildFileWithOneShape(bottom = Float.POSITIVE_INFINITY),
            "angle NaN" to buildFileWithOneShape(angle = Float.NaN),
            "angle Infinity" to buildFileWithOneShape(angle = Float.POSITIVE_INFINITY),
            "width 0" to buildFileWithOneShape(width = 0f),
            "width -1" to buildFileWithOneShape(width = -1f),
            "width NaN" to buildFileWithOneShape(width = Float.NaN),
        )

        val outcomes = invalid.mapValues { (_, bytes) -> describeReading(bytes) }

        assertEquals(invalid.mapValues { "damaged" }, outcomes)
    }

    @Test
    fun shapeCutInTheMiddleIsDamaged() {
        val bytes = buildFileWithOneShape()

        // Фигура занимает байты от 8 + 4 + 4 до 8 + 4 + 4 + 28: обрыв на любом из них.
        for (length in (8 + 4 + 4) until (8 + 4 + 4 + 28)) assertDamaged(bytes.copyOf(length))
    }

    @Test
    fun implausibleCountsAreReportedWithoutAllocating() {
        val valid = writeToBytes(CardSide(listOf(buildTestStroke(1))), CardSide())

        // Смещение 8 — число штрихов лица, 16 — число точек первого штриха.
        assertDamaged(replaceInt(valid, 8, Int.MAX_VALUE))
        assertDamaged(replaceInt(valid, 8, -1))
        assertDamaged(replaceInt(valid, 16, Int.MAX_VALUE))
    }

    @Test
    fun shapeCountLargerThanTheRestOfTheFileIsDamaged() {
        val valid = buildFileWithOneShape()

        // Смещение 12 — число фигур лица (после числа штрихов): больше, чем помещается, и отрицательное.
        assertDamaged(replaceInt(valid, 12, 2))
        assertDamaged(replaceInt(valid, 12, Int.MAX_VALUE))
        assertDamaged(replaceInt(valid, 12, -1))
    }

    private fun replaceInt(bytes: ByteArray, offset: Int, value: Int): ByteArray =
        bytes.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value) }

    @Test
    fun trailingBytesAreReported() {
        val valid = writeToBytes(CardSide(listOf(buildTestStroke(1))), CardSide())

        assertDamaged(valid + byteArrayOf(0))
    }
}
