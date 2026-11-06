package app.deckly.design.e

import app.deckly.design.e.MockupSources.buildScript
import app.deckly.design.e.MockupSources.shapesScript
import app.deckly.ui.contract.ShapeHandleKind
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тексты редактора, списка колод, формы и экрана запуска берутся из `design/build.py` дословно; в экранах нет кириллицы. */
class ScreenTextsTest {
    @Test
    fun everyFixedPhraseOfEditorDeckAndStartupScreensIsInMockup() {
        val owners = mapOf(
            "EditorTexts" to listConstants(EditorTexts::class.java),
            "DeckListTexts" to listConstants(DeckListTexts::class.java),
            "DeckNameTexts" to listConstants(DeckNameTexts::class.java),
            "StartupTexts" to listConstants(StartupTexts::class.java),
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
        assertEquals("Thin line", EditorTexts.describeThickness(app.deckly.ui.contract.Thickness.THIN))
        assertEquals("Medium line", EditorTexts.describeThickness(app.deckly.ui.contract.Thickness.MEDIUM))
        assertEquals("Thick line", EditorTexts.describeThickness(app.deckly.ui.contract.Thickness.THICK))
        assertTrue(buildScript.contains("aria-label=\"{name} line\""))
        assertEquals(listOf("01", "02", "09", "10", "12"), listOf(1, 2, 9, 10, 12).map { DeckListTexts.formatDeckNumber(it) })
        assertTrue(buildScript.contains("{number:02}"))
    }

    @Test
    fun shapeHandleLabelsFollowTheMockupScript() {
        // Подписи ручек собирает сценарий редактора фигур: углы, концы линии и ручка поворота.
        assertTrue(shapesScript.contains("{nw: 'top left', ne: 'top right', sw: 'bottom left', se: 'bottom right'}"))
        assertTrue(shapesScript.contains("'Resize: ' + label + ' corner'"))
        assertTrue(shapesScript.contains("{start: 'start point', end: 'end point'}"))
        assertTrue(shapesScript.contains("'Move ' + label"))
        assertTrue(shapesScript.contains("setAttribute('aria-label', 'Rotate shape')"))
        assertEquals("Resize: top left corner", EditorTexts.describeCorner(ShapeHandleKind.TOP_LEFT))
        assertEquals("Resize: top right corner", EditorTexts.describeCorner(ShapeHandleKind.TOP_RIGHT))
        assertEquals("Resize: bottom left corner", EditorTexts.describeCorner(ShapeHandleKind.BOTTOM_LEFT))
        assertEquals("Resize: bottom right corner", EditorTexts.describeCorner(ShapeHandleKind.BOTTOM_RIGHT))
        assertEquals("Move start point", EditorTexts.describeEndpoint(ShapeHandleKind.START))
        assertEquals("Move end point", EditorTexts.describeEndpoint(ShapeHandleKind.END))
        assertEquals("Rotate shape", EditorTexts.ROTATE_SHAPE)
    }

    @Test
    fun cornerLabelOfAHandleThatIsNotACornerIsAProgramError() {
        for (handle in listOf(ShapeHandleKind.ROTATE, ShapeHandleKind.START, ShapeHandleKind.END)) {
            assertThrows(IllegalStateException::class.java) { EditorTexts.describeCorner(handle) }
        }
    }

    @Test
    fun shapeNamesAreMockupLabelsOfTheFifteenShapes() {
        val labels = MockupSources.listShapeLabels()

        assertEquals(15, labels.size)
        assertEquals(labels, app.deckly.ui.contract.ShapeKind.entries.map { EditorTexts.describeShape(it) })
        for (label in labels) assertTrue("«$label» нет в build.py", buildScript.contains("('$label', '"))
    }

    @Test
    fun noCyrillicLiteralInScreens() {
        val stray = listScreenSources().flatMap { file -> findCyrillicLiterals(file.readText()).map { "${file.name}: «$it»" } }

        assertEquals("кириллица в экранах: интерфейс английский", emptyList<String>(), stray)
    }

    @Test
    fun everyLiteralWithLettersOfScreensIsInMockup() {
        val stray = listScreenSources().flatMap { file ->
            findLiteralsOutsideMockup(file.readText(), buildScript).map { "${file.name}: «$it»" }
        }

        assertEquals("фразы экранов, которых нет в build.py", emptyList<String>(), stray)
    }

    @Test
    fun cyrillicLiteralIsCaughtInScreenSource() {
        val source = """val a = "Open"; val b = "Закрыть"; val c = "Close ${'$'}x"; val d = "закрыть"; val e = "ёж"; val f = "Ёж""""

        assertEquals(listOf("Закрыть", "закрыть", "ёж", "Ёж"), findCyrillicLiterals(source))
        // Сообщения require / check / error и комментарии — для программиста, не тексты экрана.
        assertEquals(emptyList<String>(), findCyrillicLiterals("require(x) { \"Нет значка\" } // \"Закрыть\"\n/* \"Закрыть\" */"))
    }

    @Test
    fun englishLiteralOutsideMockupIsCaughtInScreenSource() {
        val mockup = "Close Open settings"
        val source = """val a = "Close"; val b = "Open settings"; val c = "Dismiss"; val d = "Close ${'$'}name now"; val e = "I, ${'$'}n""""

        // «Dismiss» и «now» в макете не нашлись; «I» тоже, потому что в этом макете его нет.
        assertEquals(listOf("Dismiss", "now", "I"), findLiteralsOutsideMockup(source, mockup))
    }

    private fun listScreenSources(): List<File> {
        // Контуры значков в Icons.kt — данные, а не тексты.
        val sources = File("src/main/kotlin/app/deckly/design/e").listFiles { file -> file.extension == "kt" && file.name != "Icons.kt" }.orEmpty()
        assertTrue("исходники экранов не найдены", sources.size >= 10)
        return sources.sortedBy { it.name }
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

    private fun findCyrillicLiterals(source: String): List<String> =
        listStringLiterals(source).filter { literal -> literal.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' } }

    // Подстановка $x и ${x} делит строку на куски: каждый кусок с буквами обязан быть в build.py.
    private fun findLiteralsOutsideMockup(source: String, mockup: String): List<String> =
        listStringLiterals(source)
            .flatMap { literal -> literal.split(Regex("\\$\\{[^}]*}|\\$\\w+")) }
            .map { piece -> piece.trim { it.isWhitespace() || it in ",.:·?!()«»“”—-" } }
            .filter { phrase -> phrase.any { it.isLetter() } && !mockup.contains(phrase) }

    private fun listConstants(owner: Class<*>): List<Pair<String, String>> =
        owner.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { field -> field.isAccessible = true; field.name to field.get(null) as String }
}
