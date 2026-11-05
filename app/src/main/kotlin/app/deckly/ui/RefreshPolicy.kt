package app.deckly.ui

import app.deckly.ui.contract.CardSideShown

/**
 * Всё, от чего зависит полное обновление экрана (решение 4 плана 4). [screenToken] — номер перехода
 * ([app.deckly.navigation.ScreenEntry.token]): он растёт при каждом `navigate`, в том числе при возврате на
 * экран, равный прежнему (из редактора в ту же колоду), и заменяет отдельный счётчик выходов из редактора.
 * [cardId] — карточка на листе колоды, [editorSide] — сторона в редакторе; вне своих экранов оба `null`.
 *
 * Переворот карточки при обучении и просмотре в ключ не входит: он обновляется частично, как делает прошивка.
 */
data class RefreshKey(val screenToken: Long, val cardId: Long?, val editorSide: CardSideShown?)

/**
 * Нужно ли полное обновление экрана. Правило здесь одно, чтобы поменять его после проверки на планшете: полное
 * при смене экрана, при смене показанной карточки и в редакторе при смене стороны.
 */
fun isFullRefreshNeeded(previous: RefreshKey?, next: RefreshKey): Boolean {
    if (previous == null) return true
    return previous.screenToken != next.screenToken ||
        previous.cardId != next.cardId ||
        previous.editorSide != next.editorSide
}

/**
 * Помнит, для какого ключа полное обновление уже выполнено. Ключ записывается только после обновления: эффект,
 * отменённый на полпути (экран ушёл из композиции и вернулся), обновление не сделал и должен сделать его снова.
 */
class ScreenRefresher {
    private var refreshed: RefreshKey? = null

    fun isNeeded(key: RefreshKey): Boolean = isFullRefreshNeeded(refreshed, key)

    fun markRefreshed(key: RefreshKey) {
        refreshed = key
    }
}

/** Выполняет полное обновление для [key], если оно нужно, и запоминает ключ только после его завершения. */
suspend fun refreshScreenIfNeeded(
    refresher: ScreenRefresher,
    key: RefreshKey,
    onPenPause: ((Boolean) -> Unit)?,
    awaitFrame: suspend () -> Unit,
    refresh: () -> Unit,
) {
    if (!refresher.isNeeded(key)) return
    performFullRefresh(onPenPause, awaitFrame, refresh)
    refresher.markRefreshed(key)
}

/**
 * Полное обновление: два кадра, чтобы Compose дорисовал экран, затем [refresh]. В редакторе перо на это время
 * выключено ([onPenPause]): пока оно включено, прошивка перерисовку не показывает. Если эффект отменили на
 * полпути (пришёл новый ключ), перо всё равно возвращается.
 */
suspend fun performFullRefresh(
    onPenPause: ((Boolean) -> Unit)?,
    awaitFrame: suspend () -> Unit,
    refresh: () -> Unit,
) {
    onPenPause?.invoke(true)
    try {
        awaitFrame()
        awaitFrame()
        refresh()
    } finally {
        onPenPause?.invoke(false)
    }
}
