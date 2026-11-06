package app.deckly.data.timing

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchTraceTest {
    @Test
    fun downOfStylusNamesToolActionRoundedPositionAndDeliveryDelay() {
        val line = TouchTrace.describeTouch(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 123.6f, 456.4f, 1_000L, 1_007L)

        assertEquals("touch tool=STYLUS action=DOWN x=124 y=456 delay=7ms", line)
    }

    @Test
    fun upAndCancelAreTraced() {
        assertEquals(
            "touch tool=FINGER action=UP x=10 y=20 delay=0ms",
            TouchTrace.describeTouch(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_UP, 10f, 20f, 500L, 500L),
        )
        assertEquals(
            "touch tool=FINGER action=CANCEL x=10 y=20 delay=3ms",
            TouchTrace.describeTouch(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_CANCEL, 10f, 20f, 500L, 503L),
        )
    }

    @Test
    fun movesAndSecondaryPointersAreNotTraced() {
        for (action in listOf(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_HOVER_MOVE)) {
            assertNull(TouchTrace.describeTouch(MotionEvent.TOOL_TYPE_STYLUS, action, 1f, 2f, 0L, 1L))
        }
    }

    @Test
    fun everyToolHasItsOwnName() {
        val names = mapOf(
            MotionEvent.TOOL_TYPE_FINGER to "FINGER",
            MotionEvent.TOOL_TYPE_STYLUS to "STYLUS",
            MotionEvent.TOOL_TYPE_ERASER to "ERASER",
            MotionEvent.TOOL_TYPE_MOUSE to "MOUSE",
            MotionEvent.TOOL_TYPE_UNKNOWN to "UNKNOWN",
            99 to "UNKNOWN",
        )

        for ((tool, name) in names) {
            val line = TouchTrace.describeTouch(tool, MotionEvent.ACTION_DOWN, 0f, 0f, 0L, 0L)
            assertEquals("touch tool=$name action=DOWN x=0 y=0 delay=0ms", line)
        }
    }
}
