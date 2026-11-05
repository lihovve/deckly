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
    fun phrasesBeyondMockupAreOnlyTheDocumentedOnes() {
        val beyond = DeckTextsBeyondMockup::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { field -> field.isAccessible = true; field.get(null) as String }

        // Рисунок не открылся: решение 6 плана 4, в build.py такого нет.
        assertEquals(listOf("Рисунок не открылся"), beyond)
        assertTrue(!buildScript.contains("Рисунок не открылся"))
    }

    @Test
    fun composedPhrasesFollowMockupFormats() {
        assertEquals("14 / 40", DeckTexts.formatPositionValue(14, 40))
        assertEquals("Обучение, карточка 14 из 40", DeckTexts.describePosition(PositionKind.LEARNING, 14, 40))
        assertEquals("Просмотр, карточка 2 из 112", DeckTexts.describePosition(PositionKind.BROWSING, 2, 112))
        assertEquals("3 / 4", DeckTexts.formatSuccessesValue(3, 4))
        assertEquals("Немецкий A2. Выбрать колоду", DeckTexts.describeDeckHeading("Немецкий A2"))
        assertEquals("Осталось 60 из 100", DeckTexts.formatRemaining(60, 100))
        assertEquals("Следующий проход: эпоха II", DeckTexts.describeNextPass(2))
        assertEquals(listOf("I", "II", "III", "IV"), (1..4).map { DeckTexts.formatEpoch(it) })
        // Форматы из build.py: подпись счётчика позиции и «карточка 14 из 40».
        assertTrue(buildScript.contains("{label}, карточка {position}"))
        assertTrue(buildScript.contains("{position.replace(\" из \", \" / \")}"))
    }

    @Test
    fun postponeDescriptionNamesTheReasonInLowerCase() {
        assertEquals("В конец прохода", DeckTexts.describePostpone(null))
        assertEquals("В конец прохода: ответ уже открыт", DeckTexts.describePostpone(ActionBlock.ANSWER_REVEALED))
        assertEquals("В конец прохода: уже отложена", DeckTexts.describePostpone(ActionBlock.ALREADY_POSTPONED))
        assertEquals("В конец прохода: последняя", DeckTexts.describePostpone(ActionBlock.LAST_IN_PASS))
        // Блокировка «виден вопрос» принадлежит оценкам, у переноса причины для неё нет.
        assertEquals("В конец прохода", DeckTexts.describePostpone(ActionBlock.FRONT_VISIBLE))
        // В build.py причина дописывается как «В конец прохода: » + причина.lower().
        assertTrue(buildScript.contains("f': {blocked_reason.lower()}'"))
    }

    @Test
    fun sharedPhrasesOfComponentsAreInMockupToo() {
        for (phrase in listOf("Закрыть меню", ", сейчас обучение", "Секции колоды", "Новые", "Выучено")) {
            assertTrue("«$phrase» нет в build.py", buildScript.contains(phrase))
        }
    }
}
