package app.deckly.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import app.deckly.data.storage.DecklyStorage

/** База Deckly. Версия схемы 1; схема экспортируется в `data/schemas/`. */
@Database(
    entities = [DeckEntity::class, CardEntity::class, DraftEntity::class, AppStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DecklyDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun appStateDao(): AppStateDao
}

/**
 * Открывает базу в папке [storage]: Room принимает абсолютный путь вместо имени. Режим журнала —
 * `TRUNCATE`, не WAL: между транзакциями файл базы всегда целый, и копия одного `deckly.db` ничего
 * не теряет. Папки создаются, если их нет.
 */
fun openDecklyDatabase(context: Context, storage: DecklyStorage): DecklyDatabase {
    storage.ensureDirectories()
    return Room.databaseBuilder(
        context.applicationContext,
        DecklyDatabase::class.java,
        storage.databaseFile.absolutePath,
    )
        .setDriver(AndroidSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .addCallback(InitialRowsCallback)
        .build()
}

/** Первое открытие: единственная строка `app_state`, счётчик id карточек начинается с 1. */
private object InitialRowsCallback : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        connection.execSQL("INSERT INTO app_state (id, current_deck_id, next_card_id) VALUES (1, NULL, 1)")
    }
}
