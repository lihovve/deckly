package app.deckly.design.e

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.viewinterop.AndroidView
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Нажатие, при котором кончик между касанием и подъёмом хоть чуть сдвинулся. Живое перо и палец всегда так нажимают
 * (приходит ACTION_MOVE), а тесты нажатий без движения этого не видят. Окно, меню и палитра не должны отменять нажатие
 * своих кнопок из-за движения и при этом не пускать касание к тому, что лежит под ними.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TouchJitterTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val answers = mutableListOf<String>()
    private val host by lazy { EditorHost(composeRule) }
    private var backgroundClicks = 0
    private var sheetView: TouchCountingView? = null

    // --- окно ----------------------------------------------------------------------------------

    @Test
    fun dialogButtonsReactWhenTheFingerMovesBetweenTouchAndLift() {
        showFrame { Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildDialogActions()) }

        pressWithFingerMovement(composeRule.onNodeWithText("Keep"))
        pressWithFingerMovement(composeRule.onNodeWithText("Delete"))

        assertEquals(listOf("Keep", "Delete"), answers)
    }

    @Test
    fun dialogButtonsReactWhenTheStylusMovesBetweenTouchAndLift() {
        showFrame { Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildDialogActions()) }

        pressWithStylusMovement(composeRule.onNodeWithText("Keep"))
        pressWithStylusMovement(composeRule.onNodeWithText("Delete"))

        assertEquals(listOf("Keep", "Delete"), answers)
    }

    @Test
    fun touchesBehindTheDialogReachNothingEvenWithMovement() {
        showFrame {
            Box(Modifier.fillMaxSize().clickable { backgroundClicks++ })
            Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildDialogActions())
        }

        for (position in listOf(Offset(20f, 20f), Offset(100f, 400f), Offset(496f, 1300f))) {
            composeRule.onNodeWithTag("frame").performTouchInput { down(position); moveBy(Offset(1f, 1f)); up() }
        }

        assertEquals(0, backgroundClicks)
        assertTrue(answers.isEmpty())
    }

    @Test
    fun theSheetViewBehindTheDialogGetsNoStylusTouches() {
        showFrame {
            SheetProbe()
            Dialog("Delete card?", "Both sides will be deleted. You can't undo this.", buildDialogActions())
        }

        // Мимо окна и поверх него, но мимо кнопок: перо рисует по листу, а под окном листу касания не нужны.
        for ((x, y) in listOf(100f to 400f, 496f to 660f)) {
            dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, listOf(x to y, (x + 30f) to (y + 30f), (x + 60f) to (y + 60f)))
        }

        assertEquals(0, checkNotNull(sheetView).touchEvents)
    }

    @Test
    fun theSheetViewGetsStylusTouchesWhenNothingCoversIt() {
        showFrame { SheetProbe() }

        dispatchGesture(composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS, listOf(100f to 400f, 130f to 430f))

        assertTrue(checkNotNull(sheetView).touchEvents > 0)
    }

    // --- меню ----------------------------------------------------------------------------------

    @Test
    fun popupItemsReactWhenTheFingerMovesBetweenTouchAndLift() {
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = {}) }

        pressWithFingerMovement(composeRule.onNodeWithText("Edit"))
        pressWithFingerMovement(composeRule.onNodeWithText("Delete card"))

        assertEquals(listOf("Edit", "Delete card"), answers)
    }

    @Test
    fun popupItemsReactWhenTheStylusMovesBetweenTouchAndLift() {
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = {}) }

        pressWithStylusMovement(composeRule.onNodeWithText("Edit"))
        pressWithStylusMovement(composeRule.onNodeWithText("Delete card"))

        assertEquals(listOf("Edit", "Delete card"), answers)
    }

    @Test
    fun touchOutsideThePopupStillClosesItWithMovement() {
        var dismissed = 0
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = { dismissed++ }) }

        composeRule.onNodeWithTag("frame").performTouchInput { down(Offset(100f, 800f)); moveBy(Offset(1f, 1f)); up() }

        assertEquals(1, dismissed)
    }

    @Test
    fun movingTouchInsideThePopupBesideItsItemsDoesNotCloseIt() {
        var dismissed = 0
        showFrame { Popup("Card actions", buildPopupItems(), onDismiss = { dismissed++ }) }

        pressWithFingerMovement(composeRule.onNodeWithText("Card actions"))
        pressWithStylusMovement(composeRule.onNodeWithText("Card actions"))

        assertEquals(0, dismissed)
        assertTrue(answers.isEmpty())
    }

    // --- палитра фигур -------------------------------------------------------------------------

    @Test
    fun paletteChoicesAndCrossReactWhenTheFingerMovesBetweenTouchAndLift() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        pressWithFingerMovement(composeRule.onNodeWithContentDescription(EditorTexts.describeShape(ShapeKind.STAR)))
        pressWithFingerMovement(findCross())

        assertEquals(listOf(EditorAction.ChooseShape(ShapeKind.STAR), EditorAction.CloseShapePalette), host.actions)
    }

    @Test
    fun paletteChoicesAndCrossReactWhenTheStylusMovesBetweenTouchAndLift() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        pressWithStylusMovement(composeRule.onNodeWithContentDescription(EditorTexts.describeShape(ShapeKind.STAR)))
        pressWithStylusMovement(findCross())

        assertEquals(listOf(EditorAction.ChooseShape(ShapeKind.STAR), EditorAction.CloseShapePalette), host.actions)
    }

    @Test
    fun touchBesideThePaletteWindowClosesItWithMovementAndReachesNothingBelow() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        // Справа от окна лежит «Undo», над ним в шапке «Save»: оба под палитрой.
        composeRule.onNodeWithTag("frame").performTouchInput { down(Offset(900f, 1323f - 32f - 56f)); moveBy(Offset(1f, 1f)); up() }
        composeRule.onNodeWithTag("frame").performTouchInput { down(Offset(900f, 64f)); moveBy(Offset(1f, 1f)); up() }

        assertEquals(listOf<EditorAction>(EditorAction.CloseShapePalette, EditorAction.CloseShapePalette), host.actions)
    }

    @Test
    fun touchInsideThePaletteWindowBesideControlsDoesNothingWithMovement() {
        host.show(buildEditorState(isShapePaletteOpen = true))

        val line = composeRule.onNodeWithContentDescription("Line").fetchSemanticsNode().boundsInWindow
        composeRule.onNodeWithTag("frame").performTouchInput { down(Offset(50f, line.top + 50f)); moveBy(Offset(1f, 1f)); up() }

        assertTrue(host.actions.isEmpty())
    }

    // --- вспомогательное -----------------------------------------------------------------------

    private fun showFrame(content: @Composable BoxScope.() -> Unit) {
        composeRule.setContent {
            Box(Modifier.size(Tokens.FrameWidth, Tokens.FrameHeight).background(Color.White).testTag("frame"), content = content)
        }
    }

    @Composable
    private fun SheetProbe() {
        AndroidView(factory = { context -> TouchCountingView(context).also { sheetView = it } }, modifier = Modifier.fillMaxSize())
    }

    // Палец: касание, сдвиг на пиксель и подъём; один ACTION_MOVE между DOWN и UP.
    private fun pressWithFingerMovement(node: SemanticsNodeInteraction) {
        node.performTouchInput { down(center); moveBy(Offset(1f, 1f)); up() }
    }

    // Перо настоящими `MotionEvent`: касание в середине узла, сдвиг на пиксель и подъём. Плотность mdpi: 1 dp = 1 пиксель.
    private fun pressWithStylusMovement(node: SemanticsNodeInteraction) {
        val center = node.fetchSemanticsNode().boundsInWindow.center
        dispatchGesture(
            composeRule.activity, composeRule, MotionEvent.TOOL_TYPE_STYLUS,
            listOf(center.x to center.y, (center.x + 1f) to (center.y + 1f)),
        )
    }

    private fun findCross(): SemanticsNodeInteraction = composeRule.onAllNodesWithContentDescription("Close shape menu")[1]

    private fun buildDialogActions() = listOf(
        DialogAction("Keep", isPrimary = true) { answers += "Keep" },
        DialogAction("Delete", isPrimary = false) { answers += "Delete" },
    )

    private fun buildPopupItems() = listOf(
        PopupItem("Edit", DecklyIcons.Pen, isDanger = false) { answers += "Edit" },
        PopupItem("Delete card", DecklyIcons.Trash, isDanger = true) { answers += "Delete card" },
    )

    // Лист под окном: настоящий `View`, как у листа с пером; считает события, которые ему дошли.
    private class TouchCountingView(context: Context) : View(context) {
        var touchEvents = 0

        override fun onTouchEvent(event: MotionEvent): Boolean {
            touchEvents++
            return true
        }
    }
}
