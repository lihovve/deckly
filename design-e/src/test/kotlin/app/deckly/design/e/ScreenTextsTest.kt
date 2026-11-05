package app.deckly.design.e

import app.deckly.design.e.MockupSources.buildScript
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тексты редактора, списка колод, формы и экрана запуска: берутся из `design/build.py` или названы здесь отдельно. */
class ScreenTextsTest {
    @Test
    fun everyFixedPhraseOfEditorAndDeckScreensIsInMockup() {
        val owners = mapOf(
            "EditorTexts" to listConstants(EditorTexts::class.java),
            "DeckListTexts" to listConstants(DeckListTexts::class.java),
            "DeckNameTexts" to listConstants(DeckNameTexts::class.java),
        )

        for ((owner, phrases) in owners) {
            assertTrue("в $owner должны быть постоянные фразы", phrases.size >= 5)
            for ((name, phrase) in phrases) {
                assertTrue("$owner.$name = «$phrase» нет в build.py", buildScript.contains(phrase))
            }
        }
    }

    @Test
    fun composedPhrasesFollowMockupFormats() {
        assertEquals("Тонкая линия", EditorTexts.describeThickness(app.deckly.ui.contract.Thickness.THIN))
        assertEquals("Средняя линия", EditorTexts.describeThickness(app.deckly.ui.contract.Thickness.MEDIUM))
        assertEquals("Толстая линия", EditorTexts.describeThickness(app.deckly.ui.contract.Thickness.THICK))
        assertTrue(buildScript.contains("aria-label=\"{name} линия\""))
        assertEquals(listOf("01", "02", "09", "10", "12"), listOf(1, 2, 9, 10, 12).map { DeckListTexts.formatDeckNumber(it) })
        assertTrue(buildScript.contains("{number:02}"))
    }

    @Test
    fun startupPhrasesOutsideMockupAreExactlyTheDocumentedOnes() {
        val phrases = listConstants(StartupTexts::class.java)

        val beyond = phrases.map { it.second }.filterNot { buildScript.contains(it) }

        // Таблица текстов в решении 7 плана 4. «Закрыть» есть в макете (окно ошибки импорта), остальных фраз там нет.
        assertEquals(
            listOf(
                "Нужен доступ к файлам",
                "Колоды хранятся в папке Deckly, без доступа их не открыть.",
                "Открыть настройки",
                "База повреждена",
                "Файл Deckly/deckly.db не открылся, ничего не удалено.",
                "База не найдена",
                "В папке Deckly нет файла deckly.db, рисунки карточек не тронуты.",
            ),
            beyond,
        )
        assertEquals("Закрыть", StartupTexts.CLOSE)
        assertTrue(buildScript.contains(StartupTexts.CLOSE))
    }

    @Test
    fun everyRussianLiteralOfScreensIsInMockupOrDocumented() {
        val documented = (listConstants(DeckTextsBeyondMockup::class.java) + listConstants(StartupTexts::class.java)).map { it.second }.toSet()
        val sources = File("src/main/kotlin/app/deckly/design/e").listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue("исходники экранов не найдены", sources.size >= 10)

        val stray = mutableListOf<String>()
        for (file in sources) {
            for (literal in listStringLiterals(file.readText())) {
                if (literal in documented) continue
                // Подстановка $x и ${x} делит строку на куски: каждый кусок с буквами обязан быть в build.py.
                for (piece in literal.split(Regex("\\$\\{[^}]*}|\\$\\w+"))) {
                    val phrase = piece.trim { it.isWhitespace() || it in ",.:·?!()«»—-" }
                    if (phrase.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' } && !buildScript.contains(phrase)) {
                        stray += "${file.name}: «$phrase»"
                    }
                }
            }
        }

        assertEquals("фразы вне макета без записи в DeckTextsBeyondMockup и StartupTexts", emptyList<String>(), stray)
    }

    // Строковые литералы Kotlin без комментариев; сырых строк в экранах нет.
    private fun listStringLiterals(source: String): List<String> {
        val code = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            // Сообщения require / check / error — для программиста, а не тексты экрана.
            .filterNot { Regex("\\b(require|check|error)\\(").containsMatchIn(it) }
            .joinToString("\n") { it.substringBefore("//") }
        return Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"").findAll(code).map { it.groupValues[1] }.toList()
    }

    private fun listConstants(owner: Class<*>): List<Pair<String, String>> =
        owner.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { field -> field.isAccessible = true; field.name to field.get(null) as String }
}
