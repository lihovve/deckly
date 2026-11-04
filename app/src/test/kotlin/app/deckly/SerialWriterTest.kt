package app.deckly

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SerialWriterTest {
    // Область писателя на планировщике теста. В backgroundScope задачи считаются фоновыми, и
    // advanceUntilIdle() до них не доходит.
    private fun TestScope.createWriterScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @Test
    fun writesRunInCallOrderAndSurviveCallerCancellation() = runTest {
        val writer = SerialWriter(createWriterScope())
        val log = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val first = launch { writer.write { log += "first start"; gate.await(); log += "first end" } }
        val second = launch { writer.write { log += "second" } }
        val third = launch { writer.write { log += "third" } }
        runCurrent()
        assertEquals(listOf("first start"), log)

        // Отмена вызывающего не отменяет запись: ни ту, что идёт, ни ту, что ждёт очереди.
        second.cancel()
        first.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("first start", "first end", "second", "third"), log)
        assertTrue(third.isCompleted)
    }

    @Test
    fun writeReturnsResultOfBlock() = runTest {
        val writer = SerialWriter(createWriterScope())

        assertEquals(42, writer.write { 42 })
        assertEquals("next", writer.write { "next" })
    }

    @Test
    fun writesNeverOverlap() = runTest {
        val writer = SerialWriter(createWriterScope())
        var running = 0
        var maxRunning = 0
        repeat(5) {
            launch {
                writer.write {
                    running++
                    maxRunning = maxOf(maxRunning, running)
                    delay(10)
                    running--
                }
            }
        }
        advanceUntilIdle()

        assertEquals(1, maxRunning)
        assertEquals(0, running)
    }

    @Test
    fun failedWriteReachesCallerAndCrashesScope() {
        // Ошибка записи не ловится: вызывающий получает её, и она же уходит в область приложения,
        // где без обработчика становится падением приложения.
        val dispatcher = StandardTestDispatcher()
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error -> uncaught += error })
        val writer = SerialWriter(scope)
        val failure = IOException("диск")
        var received: Throwable? = null

        TestScope(dispatcher).launch {
            try {
                writer.write<Unit> { throw failure }
            } catch (error: IOException) {
                received = error
            }
        }
        dispatcher.scheduler.advanceUntilIdle()

        // Библиотека корутин может вернуть копию исключения с восстановленным стеком, поэтому по типу и тексту.
        assertTrue(received is IOException)
        assertEquals("диск", received?.message)
        assertEquals(listOf("диск"), uncaught.map { it.message })
    }

    @Test
    fun failedWriteAfterCallerCancellationIsNotLost() {
        val dispatcher = StandardTestDispatcher()
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error -> uncaught += error })
        val writer = SerialWriter(scope)
        val gate = CompletableDeferred<Unit>()
        val failure = IOException("диск")

        val caller = TestScope(dispatcher).launch {
            writer.write<Unit> { gate.await(); throw failure }
        }
        dispatcher.scheduler.runCurrent()
        caller.cancel()
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        if (uncaught.isEmpty()) fail("Ошибка записи потерялась вместе с отменённым вызывающим")
        assertEquals(listOf("диск"), uncaught.map { it.message })
    }
}
