package app.deckly.decks

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestExportFileNameTest {
    @Test
    fun nameOfTheDeckGetsTheJsonExtension() {
        assertEquals("English A2.json", suggestExportFileName("English A2"))
        assertEquals("Немецкий A2.json", suggestExportFileName("Немецкий A2"))
    }

    @Test
    fun everyCharacterThatFileNamesForbidBecomesAnUnderscore() {
        assertEquals("a_b_c_d_e_f_g_h_i_j.json", suggestExportFileName("a/b:c*d?e\"f<g>h|i\\j"))
        // Один запрещённый символ даёт один `_`, без слияния.
        assertEquals("__.json", suggestExportFileName("//"))
    }

    @Test
    fun controlCharactersAreReplacedToo() {
        assertEquals("a_b_c.json", suggestExportFileName("a\nb\tc"))
    }

    @Test
    fun spacesAroundTheNameAreCutButInnerOnesStay() {
        assertEquals("Formulas 1.json", suggestExportFileName("  Formulas 1 "))
    }

    @Test
    fun nameThatLeavesNothingFallsBackToDeck() {
        assertEquals("deck.json", suggestExportFileName("   "))
        assertEquals("deck.json", suggestExportFileName(""))
    }

    @Test
    fun allowedPunctuationStays() {
        assertEquals("A.B-C_(1) 'x'; #5 & 100%.json", suggestExportFileName("A.B-C_(1) 'x'; #5 & 100%"))
    }
}
