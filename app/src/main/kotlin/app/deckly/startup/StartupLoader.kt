package app.deckly.startup

import app.deckly.data.DecklyData
import app.deckly.data.db.DatabaseDamagedException
import app.deckly.data.db.DatabaseMissingException
import app.deckly.navigation.AppScreen
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.StartupState

/** Куда идти при запуске и открытое хранение; [data] равно `null`, пока открыть его не удалось. */
data class StartResult(val screen: AppScreen, val data: DecklyData?)

/**
 * Порядок запуска. Доступ ко всем файлам проверяется до открытия хранения: без него SQLite не откроет
 * файл, и проверка базы сообщила бы «повреждена» вместо «нет доступа».
 */
class StartupLoader(
    private val hasFileAccess: () -> Boolean,
    private val openData: suspend () -> DecklyData,
) {
    suspend fun loadStart(): StartResult {
        if (!hasFileAccess()) return StartResult(AppScreen.Startup(StartupState.NEEDS_FILE_ACCESS), null)
        val data = try {
            openData()
        } catch (failure: DatabaseDamagedException) {
            return StartResult(AppScreen.Startup(StartupState.DATABASE_DAMAGED), null)
        } catch (failure: DatabaseMissingException) {
            return StartResult(AppScreen.Startup(StartupState.DATABASE_MISSING), null)
        }
        return StartResult(findStartScreen(data), data)
    }

    // Черновик важнее текущей колоды: недорисованная карточка открывается там, где её бросили.
    // Нет ни черновика, ни текущей колоды (колод нет или ни одна не открывалась) — список колод.
    private suspend fun findStartScreen(data: DecklyData): AppScreen {
        val draft = data.drafts.loadDraft()
        if (draft != null) return AppScreen.Editor(draft.deckId, EditorStart.ResumeDraft)
        val currentDeckId = data.appState.loadCurrentDeckId()
        if (currentDeckId != null) return AppScreen.Deck(currentDeckId)
        return AppScreen.DeckList
    }
}
