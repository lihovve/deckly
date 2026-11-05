package app.deckly.design.e

import app.deckly.ui.contract.SectionTab
import org.junit.Assert.assertEquals
import org.junit.Test

class TextsTest {
    @Test
    fun cardWordIsSingularOnlyForOne() {
        val expected = mapOf(
            0 to "0 cards", 1 to "1 card", 2 to "2 cards", 4 to "4 cards", 5 to "5 cards",
            11 to "11 cards", 12 to "12 cards", 21 to "21 cards", 22 to "22 cards", 40 to "40 cards",
            101 to "101 cards", 111 to "111 cards", 112 to "112 cards",
        )
        for ((count, text) in expected) assertEquals(text, formatCardCount(count))
    }

    @Test
    fun tabsAreLabelledWithRomanEpochs() {
        assertEquals(
            listOf("New", "I", "II", "III", "IV", "Learned"),
            SectionTab.entries.map { it.toLabel() },
        )
    }
}
