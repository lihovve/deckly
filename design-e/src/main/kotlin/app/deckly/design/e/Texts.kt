package app.deckly.design.e

import app.deckly.ui.contract.SectionTab

/** Подпись вкладки секции: «Новые · I · II · III · IV · Выучено» (README макетов). */
internal fun SectionTab.toLabel(): String = when (this) {
    SectionTab.NEW -> "Новые"
    SectionTab.EPOCH_1 -> "I"
    SectionTab.EPOCH_2 -> "II"
    SectionTab.EPOCH_3 -> "III"
    SectionTab.EPOCH_4 -> "IV"
    SectionTab.LEARNED -> "Выучено"
}

/** «1 карточка», «23 карточки», «40 карточек»: слово согласуется с числом. */
internal fun formatCardCount(count: Int): String = "$count ${findCardWord(count)}"

private fun findCardWord(count: Int): String {
    val lastTwo = count % 100
    val last = count % 10
    return when {
        lastTwo in 11..14 -> "карточек"
        last == 1 -> "карточка"
        last in 2..4 -> "карточки"
        else -> "карточек"
    }
}
