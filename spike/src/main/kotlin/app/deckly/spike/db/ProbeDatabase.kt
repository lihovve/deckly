package app.deckly.spike.db

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.io.File

@Entity
data class Counter(
    @PrimaryKey val id: Int = 1,
    val value: Long,
)

@Entity
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterValue: Long,
    val payload: String,
)

@Dao
abstract class ProbeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCounterIfAbsent(counter: Counter)

    @Query("UPDATE Counter SET value = value + 1 WHERE id = 1")
    abstract suspend fun incrementCounter()

    @Query("SELECT value FROM Counter WHERE id = 1")
    abstract suspend fun readCounter(): Long?

    @Insert
    abstract suspend fun insertEntry(entry: Entry)

    @Query("SELECT COUNT(*) FROM Entry")
    abstract suspend fun readEntries(): Long

    /** Один шаг серии в одной транзакции: счётчик растёт на единицу и добавляется строка с его значением. */
    @Transaction
    open suspend fun recordStep(payload: String) {
        insertCounterIfAbsent(Counter(value = 0))
        incrementCounter()
        insertEntry(Entry(counterValue = readCounter() ?: 0, payload = payload))
    }
}

@Database(entities = [Counter::class, Entry::class], version = 1, exportSchema = false)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao

    /** `PRAGMA journal_mode`: должен вернуть truncate. */
    suspend fun readJournalMode(): String = readPragma("PRAGMA journal_mode")

    /** `PRAGMA integrity_check`: ok, если база цела. */
    suspend fun checkIntegrity(): String = readPragma("PRAGMA integrity_check")

    // PRAGMA нельзя выразить через @Query (Room проверяет запросы при сборке), поэтому идём через соединение.
    private suspend fun readPragma(sql: String): String =
        useWriterConnection { connection ->
            connection.usePrepared(sql) { statement ->
                statement.step()
                statement.getText(0)
            }
        }

    companion object {
        /** Открывает базу по абсолютному пути [file]: Room принимает путь вместо имени. */
        fun open(context: Context, file: File): ProbeDatabase =
            Room.databaseBuilder(context.applicationContext, ProbeDatabase::class.java, file.absolutePath)
                .setDriver(AndroidSQLiteDriver())
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
    }
}
