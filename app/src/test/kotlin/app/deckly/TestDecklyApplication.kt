package app.deckly

import app.deckly.data.DecklyData
import kotlinx.coroutines.CompletableDeferred

/**
 * Приложение для тестов activity: проверка доступа и открытие хранения берутся из [TestWorld], а не из системы
 * (`Environment.isExternalStorageManager()` в Robolectric падает). Включается через `@Config(application = …)`.
 */
class TestDecklyApplication : DecklyApplication() {
    override fun createContainer(): AppContainer = AppContainer(
        application = this,
        hasFileAccess = { TestWorld.hasFileAccess },
        openStorage = { _, _ ->
            TestWorld.openGate?.await()
            TestWorld.openFailure?.let { throw it }
            checkNotNull(TestWorld.data) { "TestWorld.data не задан" }
        },
    )
}

/** Что отдаёт хранение тестовому приложению; сбрасывается перед каждым тестом. */
object TestWorld {
    @Volatile var hasFileAccess = true
    @Volatile var data: DecklyData? = null
    @Volatile var openFailure: Exception? = null
    @Volatile var openGate: CompletableDeferred<Unit>? = null

    fun reset() {
        hasFileAccess = true
        data = null
        openFailure = null
        openGate = null
    }
}
