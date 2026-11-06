package app.deckly.domain.editor

import app.deckly.domain.model.Shape

/**
 * Фигура в руках: выделена и ещё не применена, в черновик не входит. Новая фигура не имеет места ([editedIndex] и
 * [original] равны `null`), а у старой, которую выбрали касанием, это её место на видимой стороне и вид до правки: по
 * ним отмена возвращает фигуру, а применение заменяет её.
 */
data class HeldShape(val shape: Shape, val editedIndex: Int?, val original: Shape?) {
    init {
        require((editedIndex == null) == (original == null)) { "Место и прежний вид у фигуры в руках задаются вместе" }
    }
}
