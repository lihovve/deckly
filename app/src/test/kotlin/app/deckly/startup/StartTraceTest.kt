package app.deckly.startup

import app.deckly.domain.model.Section
import app.deckly.navigation.AppScreen
import app.deckly.navigation.EditorStart
import app.deckly.ui.contract.StartupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StartTraceTest {
    @Test
    fun lineNamesOnlyTheKindOfTheScreenAndTheTimeSinceProcessStart() {
        assertEquals("start screen=deck sinceProcessStart=1234.5ms", StartTrace.describeFirstScreen(AppScreen.Deck(42), 1234.5))
        assertEquals("start screen=list sinceProcessStart=900.0ms", StartTrace.describeFirstScreen(AppScreen.DeckList, 900.04))
        assertEquals(
            "start screen=editor sinceProcessStart=0.3ms",
            StartTrace.describeFirstScreen(AppScreen.Editor(7, EditorStart.ResumeDraft), 0.3),
        )
        assertEquals(
            "start screen=startup sinceProcessStart=10.0ms",
            StartTrace.describeFirstScreen(AppScreen.Startup(StartupState.DATABASE_DAMAGED), 10.0),
        )
    }

    @Test
    fun lineHasNoIdentifiersOrStartStates() {
        val deck = StartTrace.describeFirstScreen(AppScreen.Deck(4242, Section.EPOCH_2, 99_001), 1.0)
        val startup = StartTrace.describeFirstScreen(AppScreen.Startup(StartupState.NEEDS_FILE_ACCESS), 1.0)

        assertFalse(deck, deck.contains("4242") || deck.contains("99001") || deck.contains("EPOCH"))
        assertFalse(startup, startup.contains("NEEDS") || startup.contains("ACCESS"))
    }

    @Test
    fun screensThatAreNotFirstResultsStillHaveAKind() {
        assertEquals("start screen=loading sinceProcessStart=1.0ms", StartTrace.describeFirstScreen(AppScreen.Loading, 1.0))
        assertEquals("start screen=new-deck sinceProcessStart=1.0ms", StartTrace.describeFirstScreen(AppScreen.DeckName(), 1.0))
    }
}
