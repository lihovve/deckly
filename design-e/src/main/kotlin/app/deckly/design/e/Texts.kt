package app.deckly.design.e

import app.deckly.ui.contract.SectionTab

/** Подпись вкладки секции: «New · I · II · III · IV · Learned» (`LABELS` в `design/build.py`). */
internal fun SectionTab.toLabel(): String = when (this) {
    SectionTab.NEW -> "New"
    SectionTab.EPOCH_1 -> "I"
    SectionTab.EPOCH_2 -> "II"
    SectionTab.EPOCH_3 -> "III"
    SectionTab.EPOCH_4 -> "IV"
    SectionTab.LEARNED -> "Learned"
}

/** «1 card», «0 cards», «23 cards»: единственное число только у единицы (в макете везде «cards»). */
internal fun formatCardCount(count: Int): String {
    val word = if (count == 1) "card" else "cards"
    return "$count $word"
}
