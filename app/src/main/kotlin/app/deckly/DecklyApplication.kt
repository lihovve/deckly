package app.deckly

import android.app.Application
import app.deckly.ink.OnyxSdk

class DecklyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Без поднятого SDK перо работает на запасном холсте: ошибка инициализации приложение не останавливает.
        OnyxSdk.initialize(this)
        container = AppContainer(this)
    }
}
