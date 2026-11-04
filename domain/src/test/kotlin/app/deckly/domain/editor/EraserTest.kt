package app.deckly.domain.editor

import app.deckly.domain.buildPath
import app.deckly.domain.buildStroke
import app.deckly.domain.model.Stroke
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EraserTest {

    @Test
    fun fastStrokeWithSparsePointsIsErased() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertTrue(isStrokeTouched(stroke, buildPath(50f to 3f), radius = 5f))
    }

    @Test
    fun tapErasesWithinRadius() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertTrue(isStrokeTouched(stroke, buildPath(50f to 4f), radius = 5f))
        assertFalse(isStrokeTouched(stroke, buildPath(50f to 6f), radius = 5f))
    }

    @Test
    fun touchAtExactRadiusCounts() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertTrue(isStrokeTouched(stroke, buildPath(50f to 5f), radius = 5f))
    }

    @Test
    fun strokeEndsAreNotExtendedIntoLines() {
        val stroke = buildStroke(0f to 0f, 10f to 0f)

        assertFalse(isStrokeTouched(stroke, buildPath(20f to 3f), radius = 5f))
        assertTrue(isStrokeTouched(stroke, buildPath(13f to 3f), radius = 5f))
    }

    @Test
    fun crossingPathTouchesStrokeEvenWhenNoPointIsNear() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)
        val crossing = buildPath(50f to -10f, 50f to 10f)

        assertTrue(isStrokeTouched(stroke, crossing, radius = 0.5f))
    }

    @Test
    fun parallelPathBeyondRadiusDoesNotTouch() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertFalse(isStrokeTouched(stroke, buildPath(0f to 10f, 100f to 10f), radius = 5f))
        assertTrue(isStrokeTouched(stroke, buildPath(0f to 4f, 100f to 4f), radius = 5f))
    }

    @Test
    fun anySegmentOfTheStrokeCounts() {
        val stroke = buildStroke(0f to 0f, 10f to 0f, 10f to 100f)

        assertTrue(isStrokeTouched(stroke, buildPath(14f to 50f), radius = 5f))
        assertFalse(isStrokeTouched(stroke, buildPath(14f to 50f), radius = 3f))
    }

    @Test
    fun singlePointStrokeIsTouchedByNearbyTap() {
        val dot = buildStroke(10f to 10f)

        assertTrue(isStrokeTouched(dot, buildPath(12f to 10f), radius = 3f))
        assertFalse(isStrokeTouched(dot, buildPath(12f to 10f), radius = 1f))
    }

    @Test
    fun emptyStrokeOrEmptyPathTouchesNothing() {
        val stroke = buildStroke(0f to 0f, 100f to 0f)

        assertFalse(isStrokeTouched(Stroke(2f, emptyList()), buildPath(0f to 0f), radius = 50f))
        assertFalse(isStrokeTouched(stroke, emptyList(), radius = 50f))
    }
}
