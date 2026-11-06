package app.deckly

import android.app.Application
import android.content.Context
import android.os.Environment
import app.deckly.data.DecklyData
import app.deckly.data.openDecklyData
import app.deckly.editor.OutlineWidthStore
import app.deckly.editor.PreferencesOutlineWidthStore
import app.deckly.startup.StartupLoader
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Всё, что живёт столько же, сколько процесс: хранение, единый писатель, часы дней, источник
 * случайности. ViewModel получают это отсюда. [hasFileAccess] и [openStorage] подменяют в тестах.
 *
 * Проверка доступа по умолчанию (`Environment.isExternalStorageManager()`) на JVM не вызывается: в Robolectric
 * она падает внутри фреймворка. Проверит её задача 12 плана 4 на планшете.
 */
class AppContainer(
    application: Application,
    private val hasFileAccess: () -> Boolean = { Environment.isExternalStorageManager() },
    private val openStorage: suspend (Context, File) -> DecklyData = ::openDecklyData,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val outlineWidthStore: OutlineWidthStore =
        PreferencesOutlineWidthStore(application.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)),
) {
    private val context: Context = application.applicationContext
    private val openLock = Mutex()

    @Volatile
    private var data: DecklyData? = null

    val writer = SerialWriter(scope)
    val clock = DayClock()
    val random: Random = Random.Default
    val startupLoader = StartupLoader(hasFileAccess, ::openDataOnce)

    /** Хранение, открытое при запуске. До этого — `IllegalStateException`: экраны открываются только после запуска. */
    fun requireData(): DecklyData = checkNotNull(data) { "Хранение ещё не открыто" }

    // Процесс переживает activity: новая activity запускает загрузку заново, а писатель в базу один, поэтому
    // хранение открывается один раз. Неудачное открытие не запоминается: повтор откроет заново.
    private suspend fun openDataOnce(): DecklyData = openLock.withLock {
        data ?: openStorage(context, File(Environment.getExternalStorageDirectory(), STORAGE_DIR_NAME))
            .also { data = it }
    }

    private companion object {
        const val STORAGE_DIR_NAME = "Deckly"
        const val SETTINGS_NAME = "deckly-settings"
    }
}
