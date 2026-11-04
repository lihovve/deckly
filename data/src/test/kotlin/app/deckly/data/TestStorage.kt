package app.deckly.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.openDecklyDatabase
import app.deckly.data.storage.DecklyStorage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue

/** Временные папки тестов: удаляются при выходе из JVM, чтобы не копиться в системной папке. */
private val temporaryRoots = ConcurrentLinkedQueue<File>().also { roots ->
    Runtime.getRuntime().addShutdownHook(Thread { roots.forEach { it.deleteRecursively() } })
}

/** Хранилище во временной папке: тесты идут без планшета. Папка существует, `strokes/` ещё нет. */
fun createTestStorage(): DecklyStorage {
    val root = Files.createTempDirectory("deckly-test").toFile()
    temporaryRoots.add(root)
    return DecklyStorage(root)
}

/** Открывает базу хранилища так же, как приложение, но на контексте Robolectric. */
fun openTestDatabase(storage: DecklyStorage): DecklyDatabase =
    openDecklyDatabase(ApplicationProvider.getApplicationContext<Context>(), storage)
