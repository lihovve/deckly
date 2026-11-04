package app.deckly

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Единственный путь записи в хранилище. Блоки выполняются в [scope] приложения строго по очереди, в
 * порядке вызовов; вызывающий ждёт результата своего блока. Отмена вызывающего запись не отменяет:
 * экран закрылся, а сохранение, которое уже заказано, дойдёт до диска и не оставит половину действия.
 *
 * Ошибка записи не ловится: вызывающий получает её, и она же всплывает в [scope], где становится
 * падением приложения. Транзакции хранилища оставляют базу целой, поэтому падение безопасно, а молчаливо
 * потерянная запись нет, даже если вызывающий к этому моменту уже отменён.
 */
class SerialWriter(scope: CoroutineScope) {
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (task in queue) task()
        }
    }

    suspend fun <T> write(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        queue.send {
            try {
                result.complete(block())
            } catch (failure: Throwable) {
                result.completeExceptionally(failure)
                throw failure
            }
        }
        return result.await()
    }
}
