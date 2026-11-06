package app.deckly.domain.editor

import app.deckly.domain.model.Shape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HeldShapeTest {
    private val shape = Shape(Shape.Kind.OVAL, 10f, 10f, 100f, 80f, 0f, 3f)

    @Test
    fun newShapeHasNeitherPlaceNorOriginal() {
        val held = HeldShape(shape, editedIndex = null, original = null)

        assertEquals(shape, held.shape)
    }

    @Test
    fun placedShapeKeepsItsPlaceAndItsOriginal() {
        val held = HeldShape(shape, editedIndex = 2, original = shape)

        assertEquals(2, held.editedIndex)
        assertEquals(shape, held.original)
    }

    @Test
    fun placeWithoutOriginalIsRejected() {
        assertThrows<IllegalArgumentException> { HeldShape(shape, editedIndex = 1, original = null) }
    }

    @Test
    fun originalWithoutPlaceIsRejected() {
        assertThrows<IllegalArgumentException> { HeldShape(shape, editedIndex = null, original = shape) }
    }
}
