package app.deckly.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.deckly.data.db.DecklyDatabase
import app.deckly.data.db.DraftEntity
import app.deckly.data.db.mapCardEntity
import app.deckly.data.db.mapDeckEntity
import app.deckly.data.db.openDecklyDatabase
import app.deckly.data.storage.DecklyStorage
import app.deckly.domain.model.DeckState
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

/** Имя файла штрихов, под которым [seedDeck] кладёт карточку. Самих файлов нет: задача 2 их не читает. */
fun buildSeedContentFile(cardId: Long): String = "card-$cardId-seed.dks"

/**
 * Кладёт колоду и её карточки прямо в базу с id из [state]. Пока нет `commitDraft`, это единственный
 * способ получить карточки в базе.
 */
suspend fun seedDeck(database: DecklyDatabase, state: DeckState) {
    database.deckDao().insertDeck(mapDeckEntity(state.deck))
    for (card in state.cards) {
        database.cardDao().insertCard(mapCardEntity(card, state.deck.id, buildSeedContentFile(card.id)))
    }
}

/** Кладёт в базу черновик колоды [deckId] с файлом [contentFile]. */
suspend fun seedDraft(database: DecklyDatabase, deckId: Long, contentFile: String) {
    database.draftDao().insertDraft(
        DraftEntity(
            deckId = deckId,
            cardId = null,
            visibleSide = "FRONT",
            returnSection = "NEW",
            contentFile = contentFile,
        ),
    )
}
