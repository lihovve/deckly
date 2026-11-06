package app.deckly.data.repository

import app.deckly.data.TestRepositories
import app.deckly.data.assertSuspendThrows
import app.deckly.data.buildPlainCard
import app.deckly.data.buildSeedContent
import app.deckly.data.buildSeedContentFile
import app.deckly.data.buildTestStroke
import app.deckly.data.createTestStorage
import app.deckly.data.seedDeck
import app.deckly.data.strokes.StrokeFileException
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.DeckState
import app.deckly.domain.model.createDeck
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileCardContentRepositoryTest {

    private val repos = TestRepositories(createTestStorage())
    private val state = DeckState(createDeck(1, "Колода"), listOf(buildPlainCard(1), buildPlainCard(2, 1)))

    @After
    fun closeRepositories() {
        repos.close()
    }

    @Test
    fun loadContentReturnsStoredStrokes() = runTest {
        seedDeck(repos, state)

        assertEquals(buildSeedContent(1), repos.contents.loadContent(1))
        assertEquals(buildSeedContent(2), repos.contents.loadContent(2))
    }

    @Test
    fun missingStrokeFileIsReported() = runTest {
        seedDeck(repos, state)
        File(repos.storage.strokesDir, buildSeedContentFile(1)).delete()

        assertSuspendThrows<StrokeFileException> { repos.contents.loadContent(1) }
    }

    @Test
    fun loadContentFallsBackToNewestFileOfTheCard() = runTest {
        seedDeck(repos, state)
        File(repos.storage.strokesDir, buildSeedContentFile(1)).delete()
        val older = repos.files.writeCardFile(1, CardSide(listOf(buildTestStroke(30))), CardSide())
        val newer = repos.files.writeCardFile(1, CardSide(listOf(buildTestStroke(31))), CardSide())
        // Файл другой карточки (id 12, начало то же) самый новый, но чужой.
        val foreign = repos.files.writeCardFile(12, CardSide(listOf(buildTestStroke(32))), CardSide())
        File(repos.storage.strokesDir, older).setLastModified(1_000)
        File(repos.storage.strokesDir, newer).setLastModified(5_000)
        File(repos.storage.strokesDir, foreign).setLastModified(9_000)

        val content = repos.contents.loadContent(1)

        assertEquals(CardContent(1, CardSide(listOf(buildTestStroke(31))), CardSide()), content)
    }

    @Test
    fun damagedReferencedFileDoesNotFallBack() = runTest {
        seedDeck(repos, state)
        val damaged = File(repos.storage.strokesDir, buildSeedContentFile(1))
        damaged.writeBytes(damaged.readBytes().copyOf(10))
        repos.files.writeCardFile(1, CardSide(listOf(buildTestStroke(30))), CardSide())

        assertSuspendThrows<StrokeFileException> { repos.contents.loadContent(1) }
    }

    @Test
    fun otherCardsLoadWhenOneFileIsDamaged() = runTest {
        seedDeck(repos, state)
        val damaged = File(repos.storage.strokesDir, buildSeedContentFile(1))
        damaged.writeBytes(damaged.readBytes().copyOf(10))

        assertSuspendThrows<StrokeFileException> { repos.contents.loadContent(1) }
        assertEquals(buildSeedContent(2), repos.contents.loadContent(2))
        assertEquals(state, repos.decks.loadDeckState(1))
    }

    @Test
    fun unknownCardIsReported() = runTest {
        seedDeck(repos, state)

        assertSuspendThrows<IllegalStateException> { repos.contents.loadContent(99) }
    }
}
