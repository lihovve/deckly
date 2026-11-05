package app.deckly.ui

import app.deckly.ui.contract.CardSideShown

/** Каждые 30 смен полное обновление экрана; первая смена после запуска тоже должная (решение владельца 5 ноября). */
private const val FULL_REFRESH_PERIOD = 30

/**
 * Что считается сменой для полного обновления. [screenToken] — номер перехода ([app.deckly.navigation.ScreenEntry.token]):
 * он растёт при каждом `navigate`, в том числе при возврате на экран, равный прежнему (из редактора в ту же колоду), и
 * заменяет отдельный счётчик выходов из редактора. [cardId] — карточка на листе колоды, [editorSide] — сторона в
 * редакторе; вне своих экранов оба `null`.
 *
 * Смены экрана, карточки и стороны считаются вместе. Переворот карточки при обучении и просмотре в ключ не входит:
 * его показывает прошивка и без полного обновления.
 */
data class RefreshKey(val screenToken: Long, val cardId: Long?, val editorSide: CardSideShown?)

/**
 * Решает, когда звать полное обновление: на первом экране после запуска и затем на каждой 30-й смене ключа; остальные
 * смены обновляет прошивка. Чаще полное обновление мигает всем экраном, а совсем без него копятся следы. Правило здесь
 * одно, чтобы поменять период после проверки на планшете. Счётчик живёт в памяти.
 *
 * Смена ключа считается один раз, сколько бы раз за неё ни спросили: эффект перезапускается, когда экран уходит из
 * композиции и возвращается. Счётчик обнуляет только [markRefreshed], то есть обновление, которое дошло до конца:
 * отменённое на полпути остаётся должным и на том же ключе, и на следующем.
 */
class ScreenRefresher {
    private var countedKey: RefreshKey? = null

    // С запасом в одну смену до порога: первая же смена после запуска делает обновление должным.
    private var changesSinceRefresh = FULL_REFRESH_PERIOD - 1

    fun isRefreshDue(key: RefreshKey): Boolean {
        if (key != countedKey) {
            countedKey = key
            changesSinceRefresh++
        }
        return changesSinceRefresh >= FULL_REFRESH_PERIOD
    }

    fun markRefreshed() {
        changesSinceRefresh = 0
    }
}

/**
 * Выполняет полное обновление, если для [key] оно должное, и обнуляет счётчик только после его завершения. Без должного
 * обновления ничего не делает: кадров не ждёт и перо не выключает.
 */
suspend fun refreshScreenIfDue(
    refresher: ScreenRefresher,
    key: RefreshKey,
    onPenPause: ((Boolean) -> Unit)?,
    awaitFrame: suspend () -> Unit,
    refresh: () -> Unit,
) {
    if (!refresher.isRefreshDue(key)) return
    performFullRefresh(onPenPause, awaitFrame, refresh)
    refresher.markRefreshed()
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
