package app.deckly.domain.model

import app.deckly.domain.DesignSources
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShapeTest {
    private fun describeOutcome(create: () -> Any): String =
        try {
            create()
            "created"
        } catch (rejected: IllegalArgumentException) {
            "rejected"
        }

    @Test
    fun kindsFollowTheMockupDictionaryInOrder() {
        val design = DesignSources.listShapes()

        assertEquals(15, design.size)
        assertEquals(design.map { it.key }, Shape.Kind.entries.map { it.key })
        // Разметка значка дословно из `SHAPES`: по ней строится сетка 100 × 100.
        assertEquals(design.map { it.svg }, Shape.Kind.entries.map { it.svg })
    }

    @Test
    fun kindCodesAreFixedBecauseTheyAreInTheFileFormat() {
        assertEquals((1..15).toList(), Shape.Kind.entries.map { it.code })
        val expected = listOf(
            "line", "arrow", "double-arrow", "rectangle", "oval", "triangle", "diamond", "hexagon",
            "star", "moon", "sun", "heart", "cloud", "bolt", "drop",
        )
        assertEquals(expected, Shape.Kind.entries.map { it.key })
    }

    @Test
    fun onlyLineAndArrowsAreLinear() {
        val linear = Shape.Kind.entries.filter { it.isLinear }

        assertEquals(listOf(Shape.Kind.LINE, Shape.Kind.ARROW, Shape.Kind.DOUBLE_ARROW), linear)
        // Тот же набор, что `isLinearShape` в демо макета.
        assertTrue(DesignSources.shapesScript.contains("['line', 'arrow', 'double-arrow'].includes(kind)"))
    }

    @Test
    fun kindIsFoundByCodeAndByKey() {
        for (kind in Shape.Kind.entries) {
            assertEquals(kind, Shape.Kind.findByCode(kind.code))
            assertEquals(kind, Shape.Kind.findByKey(kind.key))
        }
        assertNull(Shape.Kind.findByCode(0))
        assertNull(Shape.Kind.findByCode(16))
        assertNull(Shape.Kind.findByKey("pentagon"))
    }

    @Test
    fun invalidShapesAreRejected() {
        val kind = Shape.Kind.RECTANGLE
        val cases = mapOf<String, () -> Any>(
            "valid" to { Shape(kind, 10f, 20f, 110f, 220f, 0f, 3f) },
            "left = right" to { Shape(kind, 10f, 20f, 10f, 220f, 0f, 3f) },
            "left > right" to { Shape(kind, 110f, 20f, 10f, 220f, 0f, 3f) },
            "top = bottom" to { Shape(kind, 10f, 20f, 110f, 20f, 0f, 3f) },
            "top > bottom" to { Shape(kind, 10f, 220f, 110f, 20f, 0f, 3f) },
            "left NaN" to { Shape(kind, Float.NaN, 20f, 110f, 220f, 0f, 3f) },
            "top NaN" to { Shape(kind, 10f, Float.NaN, 110f, 220f, 0f, 3f) },
            "right Infinity" to { Shape(kind, 10f, 20f, Float.POSITIVE_INFINITY, 220f, 0f, 3f) },
            "bottom Infinity" to { Shape(kind, 10f, 20f, 110f, Float.POSITIVE_INFINITY, 0f, 3f) },
            "angle NaN" to { Shape(kind, 10f, 20f, 110f, 220f, Float.NaN, 3f) },
            "angle Infinity" to { Shape(kind, 10f, 20f, 110f, 220f, Float.NEGATIVE_INFINITY, 3f) },
            "width 0" to { Shape(kind, 10f, 20f, 110f, 220f, 0f, 0f) },
            "width -1" to { Shape(kind, 10f, 20f, 110f, 220f, 0f, -1f) },
            "width NaN" to { Shape(kind, 10f, 20f, 110f, 220f, 0f, Float.NaN) },
            "width Infinity" to { Shape(kind, 10f, 20f, 110f, 220f, 0f, Float.POSITIVE_INFINITY) },
        )

        val outcomes = cases.mapValues { describeOutcome(it.value) }

        assertEquals("created", outcomes.getValue("valid"))
        assertEquals(emptyList<String>(), outcomes.filter { it.key != "valid" && it.value == "created" }.keys.toList())
    }

    @Test
    fun anyAngleIsAllowed() {
        for (angle in listOf(-720f, -180f, 0f, 30f, 180f, 359f, 1080f)) {
            assertEquals(angle, Shape(Shape.Kind.STAR, 0f, 0f, 10f, 10f, angle, 1f).angle)
        }
    }

    @Test
    fun cardSideIsEmptyByDefaultAndHoldsStrokesAndShapes() {
        val side = CardSide()

        assertTrue(side.strokes.isEmpty())
        assertTrue(side.shapes.isEmpty())
        val shape = Shape(Shape.Kind.OVAL, 0f, 0f, 10f, 10f, 0f, 2f)
        val stroke = Stroke(2f, listOf(StrokePoint(1f, 1f, 0.5f)))
        val filled = CardSide(listOf(stroke), listOf(shape))
        assertEquals(listOf(stroke), filled.strokes)
        assertEquals(listOf(shape), filled.shapes)
        assertFalse(filled == side)
    }

    @Test
    fun cardContentKeepsBothSides() {
        val front = CardSide(listOf(Stroke(2f, emptyList())))
        val content = CardContent(7L, front, CardSide())

        assertEquals(front, content.front)
        assertEquals(CardSide(), content.back)
    }
}
