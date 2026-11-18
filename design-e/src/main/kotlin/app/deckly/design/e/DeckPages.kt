package app.deckly.design.e

import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import kotlin.math.floor

/** Разбиение списка колод на страницы: [rowsPerPage] строк на странице, [pageCount] страниц. */
internal data class DeckPages(val rowsPerPage: Int, val pageCount: Int)

/**
 * Страницы списка из [deckCount] колод в области высотой [regionHeight] (список и, если страниц несколько, навигация под
 * ним). На странице столько строк, сколько целиком помещается: перед списком отступ и линия, строка `ItemMinHeight`.
 * Навигация места занимает только тогда, когда страниц несколько: если все колоды помещаются без неё, её нет вовсе.
 * Страница держит хотя бы одну строку, как бы мала ни была область.
 */
internal fun planDeckPages(deckCount: Int, regionHeight: Dp): DeckPages {
    val overhead = Tokens.ListMarginTop + Tokens.BorderThin
    val capacityWithoutNav = countRows(regionHeight - overhead)
    if (deckCount <= capacityWithoutNav) return DeckPages(capacityWithoutNav.coerceAtLeast(1), pageCount = 1)
    val rows = countRows(regionHeight - overhead - Tokens.FrameGap - Tokens.PageNavHeight).coerceAtLeast(1)
    return DeckPages(rows, pageCount = ceil(deckCount.toFloat() / rows).toInt())
}

// Сколько строк целиком лежит в высоте [height].
private fun countRows(height: Dp): Int = floor(height / Tokens.ItemMinHeight).toInt()
