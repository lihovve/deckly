package app.deckly.data.db

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import app.deckly.data.storage.DecklyStorage
import app.deckly.data.strokes.countCardFiles
import java.io.File
import java.io.IOException

/** База не открылась или не прошла проверку целостности. Файл [file] не тронут; причина в [cause]. */
class DatabaseDamagedException(val file: File, message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Базы нет (или файл пуст), а рядом лежат файлы карточек: это обрыв копии или ещё не скопированная база.
 * Открыть Room значило бы создать пустую базу, и уборка стёрла бы все файлы штрихов. Ни база, ни файлы
 * не тронуты. [dir] — папка хранения, [cardFileCount] — сколько в `strokes/` файлов `card-*.dks`.
 */
class DatabaseMissingException(val dir: File, val cardFileCount: Int) :
    IOException("В папке ${dir.path} нет базы deckly.db, а в strokes/ лежит файлов карточек: $cardFileCount")

/**
 * Проверяет, что у файлов карточек есть база: нет `deckly.db` (или он пуст), а в `strokes/` есть хотя бы один
 * `card-*.dks` — [DatabaseMissingException]. Нет ни базы, ни файлов — свежая установка, всё в порядке.
 */
fun checkDatabaseIsNotMissing(storage: DecklyStorage) {
    if (storage.databaseFile.length() != 0L) return
    val cardFileCount = countCardFiles(storage.strokesDir)
    if (cardFileCount > 0) throw DatabaseMissingException(storage.rootDir, cardFileCount)
}

/**
 * Обработчик, который ничего не делает. Обработчик по умолчанию (`DefaultDatabaseErrorHandler`)
 * при повреждении УДАЛЯЕТ файл базы, а `AndroidSQLiteDriver` открывает базу именно с ним: испорченная
 * копия молча заменялась бы пустой базой.
 */
private val KeepFileErrorHandler = DatabaseErrorHandler { }

/**
 * Проверяет целостность [file] до того, как её откроет Room. Нет файла или он пустой — база новая,
 * проверять нечего. Иначе файл открывается с обработчиком, который ничего не удаляет, и `PRAGMA
 * quick_check` должен вернуть только `ok`; любая ошибка открытия или другой ответ —
 * [DatabaseDamagedException], файл остаётся как был.
 *
 * Открытие именно на запись (без создания): после убийства посреди транзакции рядом лежит горячий
 * `-journal`, его надо откатить, а соединение только на чтение откатить его не может и дало бы
 * ложное «повреждена». Откат горячего журнала — единственное, что проверка может записать.
 */
fun checkDatabaseIntegrity(file: File) {
    if (file.length() == 0L) return // файла нет или он пуст: база новая
    val database = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE, KeepFileErrorHandler)
    } catch (failure: SQLiteException) {
        throw DatabaseDamagedException(file, "Базу ${file.name} не удалось открыть: ${failure.message}", failure)
    }
    try {
        val answers = database.rawQuery("PRAGMA quick_check", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        if (answers != listOf("ok")) {
            throw DatabaseDamagedException(file, "Проверка базы ${file.name} нашла ошибки: ${answers.take(3)}")
        }
    } catch (failure: SQLiteException) {
        throw DatabaseDamagedException(file, "Базу ${file.name} не удалось проверить: ${failure.message}", failure)
    } finally {
        database.close()
    }
}
