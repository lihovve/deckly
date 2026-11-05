package app.deckly.design.e

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

/**
 * Индикация-ловушка: закрашивает элемент красным и считает, сколько раз её создали. Compose создаёт индикацию лениво, при
 * первом нажатии, поэтому проверять нужно с удержанным касанием. Компонент, который берёт индикацию из окружения
 * (`LocalIndication`), выдаст себя красным цветом и счётчиком; наши компоненты не должны делать ни того ни другого (ТЗ: без
 * ripple и подсветки).
 */
object IndicationProbe : IndicationNodeFactory {
    var createdCount = 0
        private set

    fun reset() {
        createdCount = 0
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        createdCount++
        return object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()
                drawRect(Color.Red)
            }
        }
    }

    override fun equals(other: Any?): Boolean = other === IndicationProbe

    override fun hashCode(): Int = -1
}
