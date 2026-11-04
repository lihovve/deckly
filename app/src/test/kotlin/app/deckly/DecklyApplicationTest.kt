package app.deckly

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.deckly.ink.OnyxSdk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DecklyApplicationTest {
    // Манифест называет класс приложения: Robolectric собирает именно его.
    @Test
    fun applicationFromManifestBuildsContainerOnCreate() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        val decklyApplication = application as DecklyApplication

        assertNotNull(decklyApplication.container)
        // Планшет не Onyx: SDK не поднялся, приложение пойдёт на запасном холсте.
        assertFalse(OnyxSdk.isPenAvailable)
    }
}
