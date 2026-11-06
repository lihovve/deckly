package app.deckly.data

import android.database.sqlite.SQLiteDatabase
import app.deckly.data.strokes.StrokeFiles
import app.deckly.domain.model.CardSide
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

/**
 * Первая запись черновика замирает после того, как файл записан и до транзакции, пока тест не вызовет
 * [release]; об остановке сообщает [firstWritten]. Остальные записи идут без задержки.
 */
class GatedDraftFiles(dir: File) : StrokeFiles(dir) {
    val firstWritten = CountDownLatch(1)
    val release = CountDownLatch(1)
    private val calls = AtomicInteger()

    override fun writeDraftFile(front: CardSide, back: CardSide): String {
        val name = super.writeDraftFile(front, back)
        if (calls.getAndIncrement() == 0) {
            firstWritten.countDown()
            release.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        return name
    }

    private companion object {
        const val RELEASE_TIMEOUT_SECONDS = 10L
    }
}

/**
 * Диспетчер вызывающего. При первой отправке продолжения, когда [isTarget] верно для результата запроса
 * [sql] (одно число), отменяет корутину. Так воспроизводится «отмена сразу после коммита»: продолжение
 * после транзакции отправляется на этот диспетчер, когда в базе уже лежит её результат.
 */
class CancelWhenDatabaseDispatcher(
    private val databaseFile: File,
    private val sql: String,
    private val isTarget: (Int) -> Boolean,
) : CoroutineDispatcher() {
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    var cancelled = false
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!cancelled && isTarget(readNumber())) {
            cancelled = true
            context[Job]?.cancel()
        }
        executor.execute(block)
    }

    private fun readNumber(): Int =
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery(sql, null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    fun close() {
        executor.shutdown()
    }
}

/** После каждой записанной карточки или черновика отменяет [job]: отмена приходит после записи файла, до транзакции. */
class CancelAfterWriteStrokeFiles(dir: File) : StrokeFiles(dir) {
    @Volatile
    var job: Job? = null

    override fun writeCardFile(cardId: Long, front: CardSide, back: CardSide): String =
        super.writeCardFile(cardId, front, back).also { job?.cancel() }

    override fun writeDraftFile(front: CardSide, back: CardSide): String =
        super.writeDraftFile(front, back).also { job?.cancel() }
}
