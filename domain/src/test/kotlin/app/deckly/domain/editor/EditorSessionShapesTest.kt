package app.deckly.domain.editor

import app.deckly.domain.buildPath
import app.deckly.domain.buildStroke
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Section
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class EditorSessionShapesTest {
    private val rectangle = Shape(Shape.Kind.RECTANGLE, 100f, 200f, 300f, 500f, 0f, 6f)
    private val oval = Shape(Shape.Kind.OVAL, 400f, 200f, 700f, 400f, 0f, 3f)
    private val stroke = buildStroke(0f to 0f, 10f to 0f)
    private val farStroke = buildStroke(900f to 900f, 950f to 900f)

    private fun session() = startNewCard(Section.NEW)

    // --- добавление ------------------------------------------------------------------------------

    @Test
    fun shapeIsAddedToTheVisibleSideAndUndone() {
        val session = session().addShape(rectangle)

        assertEquals(listOf(rectangle), session.draft.front.shapes)
        assertEquals(listOf<EditorStep>(EditorStep.ShapeAdded(Side.FRONT)), session.history)

        val undone = session.undoLastStep()

        assertEquals(emptyList<Shape>(), undone.draft.front.shapes)
        assertEquals(emptyList<EditorStep>(), undone.history)
    }

    @Test
    fun shapesGoToTheVisibleSideAndKeepOrder() {
        val session = session().addShape(rectangle).switchSide(Side.BACK).addShape(oval).addShape(rectangle)

        assertEquals(listOf(rectangle), session.draft.front.shapes)
        assertEquals(listOf(oval, rectangle), session.draft.back.shapes)
    }

    @Test
    fun undoOfShapeOnTheOtherSideShowsThatSide() {
        val session = session().addShape(rectangle).switchSide(Side.BACK)

        val undone = session.undoLastStep()

        assertEquals(Side.FRONT, undone.draft.visibleSide)
        assertEquals(emptyList<Shape>(), undone.draft.front.shapes)
    }

    @Test
    fun strokesAndShapesAreUndoneInReverseOrder() {
        val session = session().addStroke(stroke).addShape(rectangle).addStroke(farStroke)

        val steps = generateSequence(session) { it.undoLastStep().takeIf { next -> next.history.isNotEmpty() } }.toList()

        assertEquals(CardSide(listOf(stroke, farStroke), listOf(rectangle)), steps[0].draft.front)
        assertEquals(CardSide(listOf(stroke), listOf(rectangle)), steps[1].draft.front)
        assertEquals(CardSide(listOf(stroke)), steps[2].draft.front)
        assertEquals(CardSide(), steps[2].undoLastStep().draft.front)
    }

    // --- правка ----------------------------------------------------------------------------------

    @Test
    fun changingShapeReplacesItAndUndoRestoresTheOldOne() {
        val moved = rectangle.copy(left = 150f, right = 350f)
        val session = session().addShape(rectangle).addShape(oval).changeShape(0, moved)

        assertEquals(listOf(moved, oval), session.draft.front.shapes)
        assertEquals(EditorStep.ShapeChanged(Side.FRONT, 0, rectangle), session.history.last())

        val undone = session.undoLastStep()

        assertEquals(listOf(rectangle, oval), undone.draft.front.shapes)
    }

    @Test
    fun changingToTheSameShapeIsNotAStep() {
        val session = session().addShape(rectangle)

        val same = session.changeShape(0, rectangle.copy())

        assertSame(session, same)
        assertEquals(1, same.history.size)
    }

    @Test
    fun changeOfAShapeOnTheOtherSideIsUndoneWithThatSideShown() {
        val moved = rectangle.copy(angle = 30f)
        val session = session().addShape(rectangle).changeShape(0, moved).switchSide(Side.BACK)

        val undone = session.undoLastStep()

        assertEquals(Side.FRONT, undone.draft.visibleSide)
        assertEquals(listOf(rectangle), undone.draft.front.shapes)
    }

    @Test
    fun heldNewShapeIsAddedOnApply() {
        val session = session().addStroke(stroke)

        val applied = session.applyHeldShape(HeldShape(rectangle, editedIndex = null, original = null))

        assertEquals(listOf(rectangle), applied.draft.front.shapes)
        assertEquals(EditorStep.ShapeAdded(Side.FRONT), applied.history.last())
    }

    @Test
    fun heldOldShapeChangesTheOneItCameFrom() {
        val moved = rectangle.copy(top = 250f, bottom = 550f)
        val session = session().addShape(rectangle).addShape(oval)

        val applied = session.applyHeldShape(HeldShape(moved, editedIndex = 0, original = rectangle))

        assertEquals(listOf(moved, oval), applied.draft.front.shapes)
        assertEquals(EditorStep.ShapeChanged(Side.FRONT, 0, rectangle), applied.history.last())
    }

    @Test
    fun heldOldShapeWithoutChangesLeavesNoStep() {
        val session = session().addShape(rectangle)

        val applied = session.applyHeldShape(HeldShape(rectangle, editedIndex = 0, original = rectangle))

        assertSame(session, applied)
    }

    // --- то, что видно на листе ---------------------------------------------------------------------

    @Test
    fun visibleContentIsTheVisibleSideWithoutHeldShape() {
        val session = session().addStroke(stroke).addShape(rectangle).switchSide(Side.BACK).addShape(oval)

        assertEquals(CardSide(emptyList(), listOf(oval)), session.listVisibleContent(null))
        assertEquals(CardSide(listOf(stroke), listOf(rectangle)), session.switchSide(Side.FRONT).listVisibleContent(null))
    }

    @Test
    fun heldNewShapeIsShownLastAmongTheShapes() {
        val session = session().addShape(rectangle)

        val visible = session.listVisibleContent(HeldShape(oval, editedIndex = null, original = null))

        assertEquals(listOf(rectangle, oval), visible.shapes)
        // В черновик фигура в руках не входит.
        assertEquals(listOf(rectangle), session.draft.front.shapes)
    }

    @Test
    fun heldOldShapeReplacesItsPlaceInTheVisibleContent() {
        val moved = oval.copy(left = 450f, right = 750f)
        val session = session().addShape(rectangle).addShape(oval)

        val visible = session.listVisibleContent(HeldShape(moved, editedIndex = 1, original = oval))

        assertEquals(listOf(rectangle, moved), visible.shapes)
        assertEquals(listOf(rectangle, oval), session.draft.front.shapes)
    }

    // --- ластик ----------------------------------------------------------------------------------

    @Test
    fun eraserTakesStrokesAndShapesInOneStepAndUndoReturnsThemToTheirPlaces() {
        val session = session().addStroke(farStroke).addShape(rectangle).addStroke(stroke).addShape(oval)

        // Ластик проходит по левому краю прямоугольника (x = 128), по штриху у начала координат и мимо овала.
        val erased = session.eraseAlong(buildPath(0f to 0f, 5f to 0f), 5f).eraseAlong(buildPath(128f to 300f, 128f to 400f), 3f)

        assertEquals(CardSide(listOf(farStroke), listOf(oval)), erased.draft.front)
        assertEquals(2, erased.history.size - session.history.size)
        val last = erased.history.last() as EditorStep.ContentErased
        assertEquals(listOf(ErasedShape(0, rectangle)), last.shapes)
        assertEquals(emptyList<ErasedStroke>(), last.strokes)

        val restored = erased.undoLastStep().undoLastStep()

        assertEquals(session.draft.front, restored.draft.front)
    }

    @Test
    fun oneEraserPathRemovesStrokeAndShapeAsASingleStep() {
        val crossing = buildStroke(100f to 300f, 140f to 300f)
        val session = session().addShape(rectangle).addStroke(crossing)

        val erased = session.eraseAlong(buildPath(110f to 250f, 140f to 350f), 4f)

        assertEquals(CardSide(), erased.draft.front)
        assertEquals(1, erased.history.size - session.history.size)
        val last = erased.history.last() as EditorStep.ContentErased
        assertEquals(listOf(ErasedStroke(0, crossing)), last.strokes)
        assertEquals(listOf(ErasedShape(0, rectangle)), last.shapes)
        assertEquals(session.draft.front, erased.undoLastStep().draft.front)
    }

    @Test
    fun eraserThatTouchesNothingGivesNoStep() {
        val session = session().addShape(rectangle).addStroke(stroke)

        val untouched = session.eraseAlong(buildPath(800f to 800f, 850f to 800f), 3f)

        assertSame(session, untouched)
    }

    @Test
    fun eraserDoesNotTouchTheInsideOfAnEmptyShape() {
        val session = session().addShape(rectangle)

        val untouched = session.eraseAlong(buildPath(200f to 350f), 3f)

        assertSame(session, untouched)
    }

    @Test
    fun erasedShapesReturnByAscendingIndexes() {
        val first = Shape(Shape.Kind.LINE, 100f, 600f, 400f, 700f, 0f, 3f)
        val second = Shape(Shape.Kind.LINE, 100f, 620f, 400f, 720f, 0f, 3f)
        val third = Shape(Shape.Kind.OVAL, 600f, 600f, 900f, 900f, 0f, 3f)
        val session = session().addShape(first).addShape(second).addShape(third)

        val erased = session.eraseAlong(buildPath(150f to 600f, 150f to 800f), 20f)

        assertEquals(listOf(third), erased.draft.front.shapes)
        assertEquals(listOf(first, second, third), erased.undoLastStep().draft.front.shapes)
    }

    @Test
    fun historyDepthCountsShapeSteps() {
        var session = session()
        repeat(UNDO_DEPTH + 5) { session = session.addShape(rectangle) }

        assertEquals(UNDO_DEPTH, session.history.size)
        assertEquals(UNDO_DEPTH + 5, session.draft.front.shapes.size)
    }
}
