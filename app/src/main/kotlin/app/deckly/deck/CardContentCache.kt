package app.deckly.deck

import app.deckly.domain.model.CardContent
import app.deckly.domain.port.CardContentRepository
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Штрихи показанных карточек: последние [CAPACITY], самая давно не нужная вытесняется. Следующая карточка
 * прохода подгружается заранее (`DeckViewModel`), чтобы открыться быстрее 200 мс (ТЗ).
 *
 * Файл, который не прочитался (пропал, повреждён, не открывается), даёт [Result.failure], и ошибка не
 * запоминается: следующая попытка читает файл заново. Любая другая ошибка, например «карточки нет в базе»,
 * — ошибка программы и не прячется.
 */
class CardContentCache(private val contents: CardContentRepository) {
    // Замок держится и на чтении файла: подгрузка и показ одной карточки не читают файл дважды.
    private val lock = Mutex()
    private val entries = object : LinkedHashMap<Long, CardContent>(CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CardContent>): Boolean = size > CAPACITY
    }

    suspend fun loadContent(cardId: Long): Result<CardContent> = lock.withLock {
        entries[cardId]?.let { return@withLock Result.success(it) }
        try {
            Result.success(contents.loadContent(cardId).also { entries[cardId] = it })
        } catch (failure: IOException) {
            Result.failure(failure)
        }
    }

    private companion object {
        const val CAPACITY = 4
        const val LOAD_FACTOR = 0.75f
    }
}
