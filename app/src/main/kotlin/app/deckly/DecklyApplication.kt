package app.deckly

import android.app.Application
import app.deckly.ink.OnyxSdk

open class DecklyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Без поднятого SDK перо работает на запасном холсте: ошибка инициализации приложение не останавливает.
        OnyxSdk.initialize(this)
        container = createContainer()
    }

    /** Тестовое приложение подменяет проверку доступа и открытие хранения: на JVM системной проверки нет. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
