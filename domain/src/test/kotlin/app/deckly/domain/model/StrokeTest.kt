package app.deckly.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StrokeTest {

    private fun describeOutcome(create: () -> Any): String =
        try {
            create()
            "created"
        } catch (rejected: IllegalArgumentException) {
            "rejected"
        }

    @Test
    fun invalidPointsAndStrokesAreRejected() {
        val cases = mapOf<String, () -> Any>(
            "pressure -0.1" to { StrokePoint(1f, 1f, -0.1f) },
            "pressure 1.0001" to { StrokePoint(1f, 1f, 1.0001f) },
            "pressure NaN" to { StrokePoint(1f, 1f, Float.NaN) },
            "pressure Infinity" to { StrokePoint(1f, 1f, Float.POSITIVE_INFINITY) },
            "x NaN" to { StrokePoint(Float.NaN, 1f, 0.5f) },
            "x Infinity" to { StrokePoint(Float.POSITIVE_INFINITY, 1f, 0.5f) },
            "y NaN" to { StrokePoint(1f, Float.NaN, 0.5f) },
            "y -Infinity" to { StrokePoint(1f, Float.NEGATIVE_INFINITY, 0.5f) },
            "width 0" to { Stroke(0f, emptyList()) },
            "width -1" to { Stroke(-1f, emptyList()) },
            "width NaN" to { Stroke(Float.NaN, emptyList()) },
            "width Infinity" to { Stroke(Float.POSITIVE_INFINITY, emptyList()) },
        )

        val outcomes = cases.mapValues { (_, create) -> describeOutcome(create) }

        assertEquals(cases.mapValues { "rejected" }, outcomes)
    }

    @Test
    fun validEdgeValuesAreAccepted() {
        val cases = mapOf<String, () -> Any>(
            "pressure 0" to { StrokePoint(0f, 0f, 0f) },
            "pressure 1" to { StrokePoint(-5.5f, 7f, 1f) },
            "pressure just inside" to { StrokePoint(1f, 1f, Math.nextUp(0f)) },
            "pressure just below one" to { StrokePoint(1f, 1f, Math.nextDown(1f)) },
            "largest coordinates" to { StrokePoint(Float.MAX_VALUE, -Float.MAX_VALUE, 0.5f) },
            "smallest width" to { Stroke(Float.MIN_VALUE, emptyList()) },
            "largest width" to { Stroke(Float.MAX_VALUE, listOf(StrokePoint(1f, 1f, 1f))) },
            "stroke without points" to { Stroke(2f, emptyList()) },
        )

        val outcomes = cases.mapValues { (_, create) -> describeOutcome(create) }

        assertEquals(cases.mapValues { "created" }, outcomes)
    }
}
