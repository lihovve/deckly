package app.deckly.deck

import app.deckly.data.strokes.StrokeFileException
import app.deckly.fakes.FakeCardContentRepository
import app.deckly.domain.port.CardContentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardContentCacheTest {
    private val repository = FakeCardContentRepository()
    private val cache = CardContentCache(repository)

    @Test
    fun loadedContentIsReturned() = runTest {
        val result = cache.loadContent(7)

        assertEquals(FakeCardContentRepository.buildContent(7), result.getOrThrow())
    }

    @Test
    fun secondLoadComesFromCache() = runTest {
        cache.loadContent(7)
        cache.loadContent(7)

        assertEquals(1, repository.loadCounts[7])
    }

    @Test
    fun keepsLastFourCards() = runTest {
        for (id in 1L..4L) cache.loadContent(id)
        // Обращение к 1 делает её свежей, поэтому пятая карточка вытесняет самую давнюю, 2.
        cache.loadContent(1)
        cache.loadContent(5)

        val kept = listOf(1L, 3L, 4L, 5L)
        for (id in kept) cache.loadContent(id)
        assertEquals(listOf(1, 1, 1, 1), kept.map { repository.loadCounts[it] })
        cache.loadContent(2)
        assertEquals(2, repository.loadCounts[2])
    }

    @Test
    fun damagedFileIsAFailureAndIsNotRemembered() = runTest {
        repository.damagedCardIds += 7

        val failed = cache.loadContent(7)
        repository.damagedCardIds -= 7
        val repaired = cache.loadContent(7)

        assertTrue(failed.exceptionOrNull() is StrokeFileException)
        assertTrue(repaired.isSuccess)
        assertEquals(2, repository.loadCounts[7])
    }

    @Test
    fun otherErrorsAreNotHidden() = runTest {
        val broken = CardContentCache(object : CardContentRepository {
            override suspend fun loadContent(cardId: Long) = throw IllegalStateException("Карточки $cardId нет в базе")
        })

        val error = runCatching { broken.loadContent(7) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun simultaneousLoadsOfOneCardReadItOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.loadGate = gate
        val first = launch { cache.loadContent(7) }
        val second = launch { cache.loadContent(7) }
        runCurrent()

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.loadCounts[7])
        assertTrue(first.isCompleted && second.isCompleted)
    }
}
