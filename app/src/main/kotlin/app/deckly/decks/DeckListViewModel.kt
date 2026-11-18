package app.deckly.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deckly.SerialWriter
import app.deckly.domain.port.AppStateRepository
import app.deckly.domain.port.DeckRepository
import app.deckly.domain.port.DeckSummary
import app.deckly.domain.port.DeckTransfer
import app.deckly.domain.port.ImportException
import app.deckly.navigation.AppScreen
import app.deckly.navigation.DeckToRename
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckRow
import app.deckly.ui.contract.DeleteDeckDialog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Строка списка из сводки колоды. Эпоха с ▶ — текущая эпоха колоды; у пустой колоды и у полностью выученной её нет:
 * проходить нечего. Пустая колода не считается выученной.
 */
fun buildDeckRow(summary: DeckSummary, isCurrent: Boolean): DeckRow {
    val isAllLearned = summary.cardCount > 0 && summary.learnedCount == summary.cardCount
    return DeckRow(
        id = summary.id,
        name = summary.name,
        cardCount = summary.cardCount,
        activeEpoch = if (summary.cardCount == 0 || isAllLearned) null else summary.currentEpoch,
        isAllLearned = isAllLearned,
        isCurrent = isCurrent,
    )
}

/**
 * Список колод: показывает колоды, открывает их, ведёт к форме новой колоды и к форме переименования, спрашивает про
 * удаление и удаляет, экспортирует колоду в файл и импортирует из файла. Меню строки открыто или нет, знает только
 * дизайн; окна удаления и ошибки импорта приходят состоянием. Системное окно выбора файла открывает хост по просьбе
 * [fileRequests] и сообщает выбор: так модель не держит ссылку на экран.
 *
 * Экспорт только читает и идёт мимо писателя: медленный приёмник файла не должен держать запись базы. Импорт читает файл
 * до писателя по той же причине, а `ImportException` ловит внутри блока записи: иначе писатель, который пробрасывает
 * ошибку блока ещё и в область приложения, уронил бы приложение чужим файлом. Пока идёт один перенос, второй не стартует.
 */
class DeckListViewModel(
    private val decks: DeckRepository,
    private val appState: AppStateRepository,
    private val writer: SerialWriter,
    private val transfer: DeckTransfer,
    private val files: DeckFiles,
    private val report: (String, Throwable) -> Unit,
    private val navigate: (AppScreen) -> Unit,
) : ViewModel() {
    private sealed interface Input {
        data class FromScreen(val action: DeckListAction) : Input
        data class ExportFile(val file: String?) : Input
        data class ImportFile(val file: String?) : Input
    }

    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val fileChannel = Channel<FileRequest>(Channel.BUFFERED)
    private val mutableState = MutableStateFlow<DeckListState?>(null)

    /** `null`, пока колоды загружаются. */
    val state: StateFlow<DeckListState?> = mutableState.asStateFlow()

    /** Просьбы к хосту открыть системное окно выбора файла; выбор хост сообщает [onExportFileChosen] и [onImportFileChosen]. */
    val fileRequests: Flow<FileRequest> = fileChannel.receiveAsFlow()

    // Текущая колода из хранилища, если она есть в списке: к ней ведёт «Назад».
    private var currentDeckId: Long? = null
    private var rows: List<DeckRow> = emptyList()
    private var deleteDialog: DeleteDeckDialog? = null
    private var isImportErrorShown = false

    // Колода, для которой открыто окно создания файла экспорта, и признак идущего переноса (экспорта или импорта).
    private var pendingExportId: Long? = null
    private var isTransferring = false

    init {
        viewModelScope.launch {
            reload()
            for (input in inputs) handle(input)
        }
    }

    fun onAction(action: DeckListAction) {
        inputs.trySend(Input.FromScreen(action))
    }

    /** Системное окно создания файла закрыто: [file] — созданный файл или `null`, если пользователь отказался. */
    fun onExportFileChosen(file: String?) {
        inputs.trySend(Input.ExportFile(file))
    }

    /** Системное окно выбора файла закрыто: [file] — выбранный файл или `null`, если пользователь отказался. */
    fun onImportFileChosen(file: String?) {
        inputs.trySend(Input.ImportFile(file))
    }

    private suspend fun handle(input: Input) {
        when (input) {
            is Input.FromScreen -> handle(input.action)
            is Input.ExportFile -> startExport(input.file)
            is Input.ImportFile -> startImport(input.file)
        }
    }

    private suspend fun handle(action: DeckListAction) {
        when (action) {
            is DeckListAction.OpenDeck -> openDeck(action.id)
            DeckListAction.CreateDeck -> navigate(AppScreen.DeckName())
            DeckListAction.Back -> currentDeckId?.let { navigate(AppScreen.Deck(it)) }
            // Устаревшую строку (колоды уже нет) молча теряем, как и при открытии.
            is DeckListAction.RenameDeck -> findRow(action.id)?.let { navigate(AppScreen.DeckName(DeckToRename(it.id, it.name))) }
            is DeckListAction.AskDeleteDeck -> findRow(action.id)?.let { row ->
                deleteDialog = DeleteDeckDialog(row.id, row.name, row.cardCount)
                publish()
            }
            DeckListAction.ConfirmDeleteDeck -> deleteAskedDeck()
            DeckListAction.DismissDeleteDialog -> {
                deleteDialog = null
                publish()
            }
            is DeckListAction.ExportDeck -> askForExportFile(action.id)
            DeckListAction.ImportDeck -> if (!isTransferring) fileChannel.trySend(FileRequest.OpenDeckFile)
            DeckListAction.DismissImportError -> {
                isImportErrorShown = false
                publish()
            }
        }
    }

    // Список читается из хранилища целиком: после удаления «текущей» может не быть, и «Назад» пропадает.
    private suspend fun reload() {
        val summaries = decks.listDecks()
        currentDeckId = appState.loadCurrentDeckId()?.takeIf { id -> summaries.any { it.id == id } }
        rows = summaries.map { buildDeckRow(it, isCurrent = it.id == currentDeckId) }
        publish()
    }

    private fun publish() {
        mutableState.value = DeckListState(rows, canGoBack = currentDeckId != null, deleteDialog = deleteDialog, isImportErrorShown = isImportErrorShown)
    }

    private fun findRow(id: Long): DeckRow? = rows.firstOrNull { it.id == id }

    // Окно закрывается сразу, а удаление идёт через писателя: повторное подтверждение, пока оно идёт, окна уже не находит.
    private suspend fun deleteAskedDeck() {
        val asked = deleteDialog ?: return
        deleteDialog = null
        publish()
        writer.write { decks.deleteDeck(asked.id) }
        reload()
    }

    // Колоды, которой нет в списке, не открываем: строка могла устареть.
    private suspend fun openDeck(id: Long) {
        if (findRow(id) == null) return
        writer.write { appState.saveCurrentDeckId(id) }
        navigate(AppScreen.Deck(id))
    }

    private fun askForExportFile(id: Long) {
        if (isTransferring) return
        val row = findRow(id) ?: return
        pendingExportId = id
        fileChannel.trySend(FileRequest.CreateDeckFile(suggestExportFileName(row.name)))
    }

    // Отказ от выбора ничего не делает. Выбор без просьбы (после перезапуска модели) или во время другого переноса не пишется,
    // а файл, который успело создать системное окно, удаляется: оно всегда создаёт новый пустой файл, чужой не затрагивается.
    private suspend fun startExport(file: String?) {
        val id = pendingExportId
        pendingExportId = null
        if (file == null) return
        if (id == null || isTransferring) return deleteQuietly(file)
        isTransferring = true
        viewModelScope.launch {
            try {
                exportTo(id, file)
            } finally {
                isTransferring = false
            }
        }
    }

    // Окна об ошибке экспорта в макете нет: сбой уходит в журнал, а пустой файл, который создал системный выбор, удаляется.
    private suspend fun exportTo(id: Long, file: String) {
        try {
            files.write(file) { transfer.exportDeck(id, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report("Экспорт колоды $id не удался", failure)
            deleteQuietly(file)
        }
    }

    private suspend fun deleteQuietly(file: String) {
        try {
            files.delete(file)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report("Пустой файл экспорта не удалён", failure)
        }
    }

    private fun startImport(file: String?) {
        if (file == null || isTransferring) return
        isTransferring = true
        viewModelScope.launch {
            try {
                importFrom(file)
            } finally {
                isTransferring = false
            }
        }
    }

    // Нечитаемый файл и любой отказ импорта дают окно ошибки; в базе и папке штрихов при этом ничего нового нет.
    private suspend fun importFrom(file: String) {
        val bytes = try {
            files.readAll(file)
        } catch (failure: IOException) {
            return showImportError("Файл колоды не прочитан", failure)
        } catch (failure: SecurityException) {
            return showImportError("Файл колоды не прочитан", failure)
        }
        val failure = writer.write {
            try {
                transfer.importDeck(ByteArrayInputStream(bytes))
                null
            } catch (rejected: ImportException) {
                rejected
            }
        }
        if (failure == null) reload() else showImportError("Файл колоды не принят: ${failure.error}", failure)
    }

    private fun showImportError(message: String, failure: Throwable) {
        report(message, failure)
        isImportErrorShown = true
        publish()
    }
}
