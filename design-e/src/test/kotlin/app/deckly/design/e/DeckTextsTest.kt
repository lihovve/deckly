package app.deckly.design.e

import app.deckly.design.e.MockupSources.buildScript
import app.deckly.ui.contract.ActionBlock
import app.deckly.ui.contract.PositionKind
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тексты экрана колоды берутся из `design/build.py`: каждая постоянная фраза обязана там быть. */
class DeckTextsTest {
    @Test
    fun everyFixedPhraseIsInMockup() {
        val phrases = DeckTexts::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { field -> field.isAccessible = true; field.name to field.get(null) as String }

        assertTrue("в DeckTexts должны быть постоянные фразы", phrases.size > 30)
        for ((name, phrase) in phrases) {
            assertTrue("$name = «$phrase» нет в build.py", buildScript.contains(phrase))
        }
    }

    @Test
    fun composedPhrasesFollowMockupFormats() {
        assertEquals("Learning, card 14 of 40", DeckTexts.describePosition(PositionKind.LEARNING, 14, 40))
        assertEquals("Browse, card 2 of 112", DeckTexts.describePosition(PositionKind.BROWSING, 2, 112))
        assertEquals("English A2. Choose deck", DeckTexts.describeDeckHeading("English A2"))
        assertEquals("60 of 100 remaining", DeckTexts.formatRemaining(60, 100))
        assertEquals("Next pass: epoch II", DeckTexts.describeNextPass(2))
        assertEquals(listOf("I", "II", "III", "IV"), (1..4).map { DeckTexts.formatEpoch(it) })
        // Форматы из build.py: подпись счётчика позиции и «card 14 of 40».
        assertTrue(buildScript.contains("{label}, card {position}"))
        assertTrue(buildScript.contains("position.replace(' of ', ' / ')"))
    }

    @Test
    fun postponeDescriptionNamesTheReasonInLowerCase() {
        assertEquals("Move to end of pass", DeckTexts.describePostpone(null))
        assertEquals("Move to end of pass: answer already revealed", DeckTexts.describePostpone(ActionBlock.ANSWER_REVEALED))
        assertEquals("Move to end of pass: already postponed", DeckTexts.describePostpone(ActionBlock.ALREADY_POSTPONED))
        assertEquals("Move to end of pass: last card", DeckTexts.describePostpone(ActionBlock.LAST_IN_PASS))
        // Блокировка «виден вопрос» принадлежит оценкам, у переноса причины для неё нет.
        assertEquals("Move to end of pass", DeckTexts.describePostpone(ActionBlock.FRONT_VISIBLE))
        // В build.py причина дописывается как «Move to end of pass: » + причина.lower().
        assertTrue(buildScript.contains("f': {blocked_reason.lower()}'"))
    }

    @Test
    fun tabDescriptionNamesCountAndActiveEpoch() {
        assertEquals("New, 112 cards", DeckTexts.describeTab("New", 112, false))
        assertEquals("II, 23 cards, currently learning", DeckTexts.describeTab("II", 23, true))
        assertEquals("I, 1 card", DeckTexts.describeTab("I", 1, false))
        // В build.py описание вкладки собирается из подписи, числа «cards» и хвоста «currently learning».
        assertTrue(buildScript.contains("{label}, {counts[i]} cards"))
    }

    @Test
    fun sharedPhrasesOfComponentsAreInMockupToo() {
        for (phrase in listOf("Close menu", ", currently learning", "Deck sections", "New", "Learned")) {
            assertTrue("«$phrase» нет в build.py", buildScript.contains(phrase))
        }
        assertEquals("Close menu", DeckTexts.CLOSE_MENU)
        assertEquals("currently learning", DeckTexts.CURRENTLY_LEARNING)
        assertEquals("Drawing unavailable", DeckTexts.DRAWING_UNAVAILABLE)
    }
}
