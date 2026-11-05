package app.deckly.design.e

import java.io.File

/**
 * Макеты варианта E как источник истины для проверок: `design/styles.css` и `design/build.py`. Тесты `design-e`
 * идут из папки модуля, поэтому макеты лежат на уровень выше.
 */
object MockupSources {
    private val designDir = File("../design")

    // Комментарии вырезаны: иначе «/* ... */» перед правилом попадает в его селектор.
    val css: String by lazy { read("styles.css").replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "") }
    val buildScript: String by lazy { read("build.py") }

    private fun read(name: String): String {
        val file = File(designDir, name)
        check(file.isFile) { "Нет файла макетов ${file.absolutePath}" }
        return file.readText()
    }

    /** Значение свойства [property] в правиле с точным селектором [selector]; без правила — ошибка. */
    fun findCssValue(selector: String, property: String): String {
        val body = css.split('}')
            .map { it.substringBefore('{').trim() to it.substringAfter('{', "") }
            .firstOrNull { (name, _) -> name == selector }
            ?.second
            ?: error("В styles.css нет правила $selector")
        val declaration = body.split(';')
            .map { it.trim() }
            .firstOrNull { it.substringBefore(':').trim() == property }
            ?: error("В правиле $selector нет свойства $property")
        return declaration.substringAfter(':').trim()
    }

    /** Число пикселей из значения вида `64px`, `-.6px` или `1.8`. */
    fun findCssNumber(selector: String, property: String, index: Int = 0): Float =
        Regex("-?\\d*\\.?\\d+").findAll(findCssValue(selector, property)).map { it.value.toFloat() }.toList()[index]

    /** Элементы значков из словаря `PATHS` в `build.py`: имя → теги `<path>`, `<circle>`, `<rect>` как есть. */
    fun listIconElements(): Map<String, List<String>> {
        val block = buildScript.substringAfter("PATHS = {").substringBefore("\n}\n")
        return Regex("'(\\w+)':\\s*'(.*)',?").findAll(block).associate { match ->
            val elements = Regex("<(?:path|circle|rect)[^>]*/>").findAll(match.groupValues[2]).map { it.value }.toList()
            match.groupValues[1] to elements
        }
    }
}
