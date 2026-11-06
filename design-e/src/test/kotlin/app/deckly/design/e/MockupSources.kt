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

    // Сценарий редактора фигур: подписи ручек собираются в нём, а не в build.py.
    val shapesScript: String by lazy { read("editor-shapes.js") }

    private fun read(name: String): String {
        val file = File(designDir, name)
        check(file.isFile) { "Нет файла макетов ${file.absolutePath}" }
        return file.readText()
    }

    /**
     * Значение свойства [property] в правилах с точным селектором [selector]: как у каскада, побеждает последнее правило,
     * где свойство есть (селектор в `styles.css` может встречаться несколько раз). Без правила или свойства — ошибка.
     */
    fun findCssValue(selector: String, property: String): String {
        val bodies = css.split('}')
            .map { it.substringBefore('{').trim() to it.substringAfter('{', "") }
            .filter { (name, _) -> name == selector }
            .map { it.second }
        check(bodies.isNotEmpty()) { "В styles.css нет правила $selector" }
        val declaration = bodies.asReversed()
            .firstNotNullOfOrNull { body ->
                body.split(';').map { it.trim() }.firstOrNull { it.substringBefore(':').trim() == property }
            }
            ?: error("В правиле $selector нет свойства $property")
        return declaration.substringAfter(':').trim()
    }

    /** Подписи видов фигур в порядке словаря `SHAPES` в `build.py`. */
    fun listShapeLabels(): List<String> {
        val block = buildScript.substringAfter("SHAPES = {").substringBefore("\n}\n")
        return Regex("'[\\w-]+': \\('([^']+)', '").findAll(block).map { it.groupValues[1] }.toList()
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
