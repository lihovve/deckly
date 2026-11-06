package app.deckly.domain

import java.io.File

/** Вид фигуры из словаря `SHAPES` в `design/build.py`: ключ, подпись и разметка значка в сетке 100 × 100. */
data class DesignShape(val key: String, val label: String, val svg: String)

/**
 * Макет как источник истины для тестов фигур: `design/build.py` и `design/editor-shapes.js`. Тесты `domain` идут из
 * папки модуля, поэтому макет лежит на уровень выше.
 */
object DesignSources {
    private val designDir = File("../design")

    val buildScript: String by lazy { read("build.py") }
    val shapesScript: String by lazy { read("editor-shapes.js") }

    /** Виды фигур в порядке словаря `SHAPES`. */
    fun listShapes(): List<DesignShape> {
        val block = buildScript.substringAfter("SHAPES = {").substringBefore("\n}\n")
        return Regex("'([\\w-]+)': \\('([^']+)', '(.*)'\\),?").findAll(block)
            .map { DesignShape(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            .toList()
    }

    private fun read(name: String): String {
        val file = File(designDir, name)
        check(file.isFile) { "Нет файла макета ${file.absolutePath}" }
        return file.readText()
    }
}
