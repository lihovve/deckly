package app.deckly.design.e

import app.deckly.ui.contract.SectionTab
import org.junit.Assert.assertEquals
import org.junit.Test

class TextsTest {
    @Test
    fun cardWordAgreesWithCount() {
        val expected = mapOf(
            0 to "0 карточек", 1 to "1 карточка", 2 to "2 карточки", 4 to "4 карточки", 5 to "5 карточек",
            11 to "11 карточек", 12 to "12 карточек", 14 to "14 карточек", 20 to "20 карточек",
            21 to "21 карточка", 22 to "22 карточки", 23 to "23 карточки", 25 to "25 карточек",
            40 to "40 карточек", 101 to "101 карточка", 111 to "111 карточек", 112 to "112 карточек",
        )
        for ((count, text) in expected) assertEquals(text, formatCardCount(count))
    }

    @Test
    fun tabsAreLabelledWithRomanEpochs() {
        assertEquals(
            listOf("Новые", "I", "II", "III", "IV", "Выучено"),
            SectionTab.entries.map { it.toLabel() },
        )
    }
}
