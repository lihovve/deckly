package app.deckly.design.e

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Страницы списка колод считаются от высоты области списка и навигации (`.list{flex:1}` и `.page-nav{height:64px}` в макете).
 * Строка 148, перед списком отступ 16 и линия 1, под списком промежуток 16 и навигация 64.
 */
class DeckPagesTest {
    // Рамка кадра 1323: поля 32, шапка 64, нижняя строка 112 и промежутки 16 оставляют списку с навигацией 1051.
    private val frameRegion = 1051.dp

    @Test
    fun deckThatFitInTheListNeedNoPages() {
        assertEquals(DeckPages(rowsPerPage = 6, pageCount = 1), planDeckPages(deckCount = 6, regionHeight = frameRegion))
        assertEquals(DeckPages(rowsPerPage = 6, pageCount = 1), planDeckPages(deckCount = 1, regionHeight = frameRegion))
    }

    @Test
    fun oneDeckMoreThanTheListHoldsMakesASecondPage() {
        assertEquals(DeckPages(rowsPerPage = 6, pageCount = 2), planDeckPages(deckCount = 7, regionHeight = frameRegion))
        assertEquals(DeckPages(rowsPerPage = 6, pageCount = 2), planDeckPages(deckCount = 12, regionHeight = frameRegion))
        assertEquals(DeckPages(rowsPerPage = 6, pageCount = 3), planDeckPages(deckCount = 13, regionHeight = frameRegion))
    }

    @Test
    fun rowsPerPageFollowTheHeightAndNotAConstant() {
        // 17 (отступ и линия) + 148 на каждую строку: 4 строки лежат в 609 и не лезут в 608.
        assertEquals(4, planDeckPages(deckCount = 4, regionHeight = (17 + 4 * 148).dp).rowsPerPage)
        assertEquals(DeckPages(rowsPerPage = 3, pageCount = 2), planDeckPages(deckCount = 4, regionHeight = (17 + 4 * 148 - 1).dp))
        // Выше кадра строк на странице больше.
        assertEquals(DeckPages(rowsPerPage = 8, pageCount = 1), planDeckPages(deckCount = 8, regionHeight = 1300.dp))
    }

    @Test
    fun navigationTakesItsOwnHeightFromTheListOnlyWhenThereAreSeveralPages() {
        // Без навигации в 1053 лежат 7 строк (17 + 7 · 148 = 1053), с навигацией (16 + 64) только 6.
        val region = 1053.dp
        assertEquals(DeckPages(rowsPerPage = 7, pageCount = 1), planDeckPages(deckCount = 7, regionHeight = region))
        assertEquals(DeckPages(rowsPerPage = 6, pageCount = 2), planDeckPages(deckCount = 8, regionHeight = region))
    }

    @Test
    fun aPageHasAtLeastOneRowEvenInATinyRegion() {
        assertEquals(DeckPages(rowsPerPage = 1, pageCount = 3), planDeckPages(deckCount = 3, regionHeight = 50.dp))
        assertEquals(DeckPages(rowsPerPage = 1, pageCount = 1), planDeckPages(deckCount = 0, regionHeight = 50.dp))
    }
}
