package app.deckly.spike.db

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import app.deckly.spike.SpikeLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale

/**
 * Проверка базы в `/sdcard/Deckly/spike/`: серия записей, проверка целостности после убийства
 * приложения и открытие базы, созданной не этим приложением. Итоги идут в [SpikeLog] строками
 * `PROBE_*`, которые разбирает `tools/spike-db-killtest.sh`.
 */
class DbProbe(context: Context) {
    private val appContext = context.applicationContext

    // Операции идут по очереди: две базы на одном файле одновременно здесь не нужны.
    private val mutex = Mutex()

    @Suppress("DEPRECATION")
    private val directory = File(Environment.getExternalStorageDirectory(), "Deckly/spike")
    private val probeFile = File(directory, "probe.db")

    /**
     * [count] шагов серии, время каждого; итог `PROBE_SERIES`, затем уведомление MediaStore о файле
     * (`PROBE_SCAN`), чтобы компьютер по USB увидел свежую базу.
     */
    suspend fun runSeries(count: Int = SERIES_SIZE) {
        mutex.withLock {
            if (!hasAllFilesAccess()) return
            directory.mkdirs()
            val database = ProbeDatabase.open(appContext, probeFile)
            try {
                val dao = database.probeDao()
                var maxNanos = 0L
                val startedAt = System.nanoTime()
                for (index in 0 until count) {
                    val stepStartedAt = System.nanoTime()
                    dao.recordStep(buildPayload(index))
                    maxNanos = maxOf(maxNanos, System.nanoTime() - stepStartedAt)
                }
                val totalNanos = System.nanoTime() - startedAt
                SpikeLog.write(
                    "PROBE_SERIES",
                    "n" to count,
                    "avgMs" to formatMillis(totalNanos / count),
                    "maxMs" to formatMillis(maxNanos),
                    "totalMs" to formatMillis(totalNanos),
                )
            } finally {
                database.close()
            }
            scanProbeFile()
        }
    }

    /**
     * Открывает базу [file], читает журнал, целостность, счётчик и число строк; база цела, если
     * `integrity_check` вернул ok, а счётчик равен числу строк. База `probe.db` создаётся, если её
     * нет; чужая отсутствующая база не создаётся.
     */
    suspend fun check(file: File = probeFile) {
        mutex.withLock {
            if (!hasAllFilesAccess()) return
            if (!file.exists() && file != probeFile) {
                SpikeLog.write(
                    "PROBE_CHECK",
                    "file" to file.absolutePath,
                    "journal" to "-",
                    "integrity" to "missing",
                    "counter" to "-",
                    "entries" to "-",
                    "consistent" to false,
                )
                return
            }
            file.parentFile?.mkdirs()
            val database = ProbeDatabase.open(appContext, file)
            try {
                val counter = database.probeDao().readCounter() ?: 0L
                val entries = database.probeDao().readEntries()
                SpikeLog.write(
                    "PROBE_CHECK",
                    "file" to file.absolutePath,
                    "journal" to database.readJournalMode(),
                    "integrity" to database.checkIntegrity(),
                    "counter" to counter,
                    "entries" to entries,
                    "consistent" to (counter == entries),
                )
            } finally {
                database.close()
            }
        }
    }

    /** База, созданная не этим приложением: её по adb кладёт в папку задача 5. */
    suspend fun checkForeign() {
        check(File(directory, "foreign.db"))
    }

    private fun hasAllFilesAccess(): Boolean {
        val isGranted = Environment.isExternalStorageManager()
        if (!isGranted) {
            SpikeLog.write("PROBE_DENIED")
        }
        return isGranted
    }

    private fun scanProbeFile() {
        MediaScannerConnection.scanFile(appContext, arrayOf(probeFile.absolutePath), null) { path, uri ->
            SpikeLog.write("PROBE_SCAN", "path" to path, "uri" to uri)
        }
    }

    private fun buildPayload(index: Int): String = index.toString().padEnd(PAYLOAD_LENGTH, 'x')

    private fun formatMillis(nanos: Long): String = String.format(Locale.ROOT, "%.2f", nanos / NANOS_PER_MILLI)

    private companion object {
        const val SERIES_SIZE = 2000
        const val PAYLOAD_LENGTH = 200
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
