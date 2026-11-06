package app.deckly.editor

import app.deckly.domain.editor.Draft
import app.deckly.domain.editor.EditorStep
import app.deckly.domain.editor.HeldShape
import app.deckly.domain.editor.ShapeHandle
import app.deckly.domain.editor.addStroke
import app.deckly.domain.editor.resumeDraft
import app.deckly.domain.editor.startNewCard
import app.deckly.domain.editor.switchSide
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.CardUnits
import app.deckly.domain.model.Section
import app.deckly.domain.model.Shape
import app.deckly.domain.model.Side
import app.deckly.fixtures.buildStroke
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.ShapeFrame
import app.deckly.ui.contract.ShapeHandleKind
import app.deckly.ui.contract.ShapeKind
import app.deckly.ui.contract.Thickness
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorScreenMapperTest {
    private val front = buildStroke(1f to 1f, 5f to 5f)
    private val back = buildStroke(2f to 2f, 6f to 6f)

    @Test
    fun newCardHasNoCardId() {
        val session = startNewCard(Section.NEW)

        assertEquals(
            EditorScreenState(true, CardSideShown.FRONT, Thickness.MEDIUM, canUndo = false, dialog = null),
            buildEditorScreenState(session, Thickness.MEDIUM, null),
        )
    }

    @Test
    fun editedCardIsNotNew() {
        val session = resumeDraft(Draft(7, CardSide(), CardSide(), Side.FRONT, Section.NEW))

        assertEquals(false, buildEditorScreenState(session, Thickness.MEDIUM, null).isNewCard)
    }

    @Test
    fun undoIsAvailableOnlyWithHistory() {
        val empty = startNewCard(Section.NEW)
        val drawn = empty.addStroke(front)

        assertEquals(false, buildEditorScreenState(empty, Thickness.MEDIUM, null).canUndo)
        assertEquals(true, buildEditorScreenState(drawn, Thickness.MEDIUM, null).canUndo)
        assertEquals(listOf<EditorStep>(EditorStep.StrokeAdded(Side.FRONT)), drawn.history)
    }

    @Test
    fun sideThicknessAndDialogArePassedThrough() {
        val session = startNewCard(Section.NEW).switchSide(Side.BACK)

        val state = buildEditorScreenState(session, Thickness.THICK, EditorDialog.SAVE_ON_BACK)

        assertEquals(CardSideShown.BACK, state.side)
        assertEquals(Thickness.THICK, state.thickness)
        assertEquals(EditorDialog.SAVE_ON_BACK, state.dialog)
    }

    @Test
    fun thicknessesAreThreeTenAndTwentyFivePixels() {
        assertEquals(3f, Thickness.THIN.toPenWidthPx(), 0f)
        assertEquals(10f, Thickness.MEDIUM.toPenWidthPx(), 0f)
        assertEquals(25f, Thickness.THICK.toPenWidthPx(), 0f)
    }

    @Test
    fun shapeKindsMapBothWaysInThePaletteOrder() {
        assertEquals(Shape.Kind.entries, ShapeKind.entries.map { it.toDomainKind() })
        assertEquals(ShapeKind.entries, Shape.Kind.entries.map { it.toContractKind() })
        assertEquals(15, ShapeKind.entries.size)
    }

    @Test
    fun everyPaletteKindMapsToItsDomainKindAndBackByName() {
        for (kind in ShapeKind.entries) {
            assertEquals(kind.name, kind.toDomainKind().name)
            assertEquals(kind, kind.toDomainKind().toContractKind())
        }
    }

    @Test
    fun shapeFrameKeepsCardUnitsAngleAndMarksLinearShapes() {
        val rectangle = Shape(Shape.Kind.RECTANGLE, 100f, 200f, 400f, 500f, 15f, 3f)
        val arrow = Shape(Shape.Kind.ARROW, 10f, 20f, 300f, 100f, -35f, 3f)

        assertEquals(ShapeFrame(100f, 200f, 400f, 500f, 15f, isLinear = false), rectangle.toFrame())
        assertEquals(ShapeFrame(10f, 20f, 300f, 100f, -35f, isLinear = true), arrow.toFrame())
        for (kind in Shape.Kind.entries) {
            assertEquals(kind.name, kind.isLinear, Shape(kind, 0f, 0f, 1f, 1f, 0f, 1f).toFrame().isLinear)
        }
    }

    @Test
    fun selectedShapeCarriesKindFrameAndOutlineInDesignUnits() {
        val shape = Shape(Shape.Kind.STAR, 100f, 200f, 400f, 500f, 30f, 7f * CardUnits.PER_DESIGN_UNIT)

        val selected = shape.toSelectedShape()

        assertEquals(ShapeKind.STAR, selected.kind)
        assertEquals(shape.toFrame(), selected.frame)
        assertEquals(7, selected.outlineWidth)
    }

    @Test
    fun outlineWidthFromOneToTwelveSurvivesTheRoundTripWithoutLoss() {
        for (width in 1..12) {
            val shape = Shape(Shape.Kind.OVAL, 0f, 0f, 100f, 100f, 0f, width.toShapeWidth())

            assertEquals("толщина $width", width, shape.toSelectedShape().outlineWidth)
            assertEquals("толщина $width в единицах карточки", width * CardUnits.PER_DESIGN_UNIT, width.toShapeWidth(), 1e-4f)
        }
    }

    @Test
    fun outlineWidthOutsideTheSliderIsShownWithinIt() {
        // Файл из другой версии или чужой правки: на панели всё равно число 1…12.
        assertEquals(12, Shape(Shape.Kind.OVAL, 0f, 0f, 1f, 1f, 0f, 40f).toSelectedShape().outlineWidth)
        assertEquals(1, Shape(Shape.Kind.OVAL, 0f, 0f, 1f, 1f, 0f, 0.1f).toSelectedShape().outlineWidth)
    }

    @Test
    fun everyHandleOfTheContractMapsToTheSameHandleOfTheModel() {
        for (handle in ShapeHandleKind.entries) {
            assertEquals(handle.name, ShapeHandle.valueOf(handle.name), handle.toDomainHandle())
        }
        assertEquals(ShapeHandle.entries.map { it.name }, ShapeHandleKind.entries.map { it.name })
    }

    @Test
    fun screenStateCarriesSelectionPaletteAndRememberedWidth() {
        val session = startNewCard(Section.NEW)
        val shape = Shape(Shape.Kind.HEART, 100f, 200f, 400f, 500f, 0f, 5f * CardUnits.PER_DESIGN_UNIT)

        val state = buildEditorScreenState(
            session, Thickness.THIN, null,
            held = HeldShape(shape, editedIndex = null, original = null), isShapePaletteOpen = true, paletteOutlineWidth = 8,
        )

        assertEquals(shape.toSelectedShape(), state.selectedShape)
        assertEquals(true, state.isShapePaletteOpen)
        assertEquals(8, state.paletteOutlineWidth)
    }

    @Test
    fun screenStateWithoutShapesHasNoSelectionAndNoPalette() {
        val state = buildEditorScreenState(startNewCard(Section.NEW), Thickness.MEDIUM, null)

        assertEquals(null, state.selectedShape)
        assertEquals(false, state.isShapePaletteOpen)
        assertEquals(DEFAULT_OUTLINE_WIDTH, state.paletteOutlineWidth)
    }
}
