package app.deckly.data.transfer

import app.deckly.domain.model.Card
import app.deckly.domain.model.CardContent
import app.deckly.domain.model.Deck
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import app.deckly.domain.port.ImportError
import app.deckly.domain.port.ImportException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val DECK_FILE_FORMAT = "deckly-deck"
internal const val DECK_FILE_VERSION = 1

private const val EPOCH_COUNT = 4
private const val POINT_VALUES = 3

/** Файл колоды: `format` и `version` идут первыми, чтобы обрезанный файл всё равно опознавался. */
@Serializable
internal class DeckFile(
    val format: String,
    val version: Int,
    val deck: DeckDto,
    val cards: List<CardDto>,
)

/** Поля `Deck` без id; значения по эпохам — списки из четырёх элементов, `tail` — списки id карточек файла. */
@Serializable
internal class DeckDto(
    val name: String,
    val activeSection: String,
    val currentEpoch: Int,
    val passStarted: Boolean,
    val passCount: List<Int>,
    val passCursor: Int,
    val browseCursor: Int,
    val lastPassDay: List<Int?>,
    val waitingEpoch: Int,
    val pausedEpoch: Int,
    val pausedCursor: Int,
    val portionRatedCount: Int,
    val visibleSide: String,
    val tail: List<List<Long>>,
)

@Serializable
internal class CardDto(
    val id: Long,
    val section: String,
    val position: Int,
    val successes: Int,
    val sectionDay: Int,
    val lastSuccessDay: Int?,
    val createdAt: Long,
    val postponed: Boolean,
    val answerRevealed: Boolean,
    val failed: Boolean,
    val checking: Boolean,
    val excludedFromPass: Boolean,
    val front: List<StrokeDto>,
    val back: List<StrokeDto>,
)

/** Штрих: толщина и точки `[x, y, нажим]`. */
@Serializable
internal class StrokeDto(val width: Float, val points: List<List<Float>>)

/** Только то, что нужно, чтобы опознать файл и версию, не разбирая остальное. */
@Serializable
internal class DeckFileHeader(val format: String? = null, val version: Int? = null)

/** Карточка файла с прежним id и её штрихи. */
internal class ParsedCard(val card: Card, val front: List<Stroke>, val back: List<Stroke>)

/** Проверенная колода из файла: id колоды 0, id карточек и `tail` ещё файловые. */
internal class ParsedDeck(val deck: Deck, val cards: List<ParsedCard>)

internal val STRICT_JSON = Json { ignoreUnknownKeys = false }
private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

/** Начало файла колоды: обрезанный файл с таким началом повреждён, а не чужой. */
private val FORMAT_MARKER = Regex("""^\s*\{\s*"format"\s*:\s*"deckly-deck"""")

/**
 * Читает и проверяет файл целиком. Не JSON или не наш формат — `NOT_A_DECK_FILE`; версия новее
 * приложения — `UNSUPPORTED_VERSION`; всё остальное, включая обрезанный файл с нашим началом, —
 * `DAMAGED`. Ничего не пишет: это первый шаг импорта.
 */
internal fun parseDeckFile(bytes: ByteArray): ParsedDeck {
    val text = bytes.decodeToString()
    val header = try {
        LENIENT_JSON.decodeFromString<DeckFileHeader>(text)
    } catch (unreadable: IllegalArgumentException) {
        val error = if (FORMAT_MARKER.containsMatchIn(text)) ImportError.DAMAGED else ImportError.NOT_A_DECK_FILE
        throw ImportException(error, unreadable)
    }
    if (header.format != DECK_FILE_FORMAT) throw ImportException(ImportError.NOT_A_DECK_FILE)
    val version = header.version ?: throw buildDamaged("В файле нет версии формата")
    if (version > DECK_FILE_VERSION) throw ImportException(ImportError.UNSUPPORTED_VERSION)
    requireValid(version == DECK_FILE_VERSION) { "Версия формата $version не существует" }
    val file = try {
        STRICT_JSON.decodeFromString<DeckFile>(text)
    } catch (damaged: IllegalArgumentException) {
        throw ImportException(ImportError.DAMAGED, damaged)
    }
    return validateDeckFile(file)
}

private fun buildDamaged(reason: String): ImportException =
    ImportException(ImportError.DAMAGED, IllegalArgumentException(reason))

private inline fun requireValid(condition: Boolean, reason: () -> String) {
    if (!condition) throw buildDamaged(reason())
}

private fun validateDeckFile(file: DeckFile): ParsedDeck {
    val dto = file.deck
    requireValid(dto.currentEpoch in 1..EPOCH_COUNT) { "currentEpoch вне 1..4: ${dto.currentEpoch}" }
    requireValid(dto.waitingEpoch == 0 || dto.waitingEpoch in 2..EPOCH_COUNT) { "waitingEpoch: ${dto.waitingEpoch}" }
    requireValid(dto.pausedEpoch == 0 || dto.pausedEpoch in 2..EPOCH_COUNT) { "pausedEpoch: ${dto.pausedEpoch}" }
    requireValid(dto.passCursor >= 0) { "passCursor отрицательный: ${dto.passCursor}" }
    requireValid(dto.browseCursor >= 0) { "browseCursor отрицательный: ${dto.browseCursor}" }
    requireValid(dto.pausedCursor >= 0) { "pausedCursor отрицательный: ${dto.pausedCursor}" }
    requireValid(dto.portionRatedCount >= 0) { "portionRatedCount отрицательный: ${dto.portionRatedCount}" }
    requireValid(dto.passCount.size == EPOCH_COUNT) { "passCount должен быть из четырёх чисел" }
    requireValid(dto.passCount.all { it >= 0 }) { "passCount содержит отрицательное число" }
    requireValid(dto.lastPassDay.size == EPOCH_COUNT) { "lastPassDay должен быть из четырёх значений" }
    requireValid(dto.tail.size == EPOCH_COUNT) { "tail должен быть из четырёх списков" }

    val cardIds = file.cards.map { it.id }
    requireValid(cardIds.toSet().size == cardIds.size) { "Повторяется id карточки" }
    val unknownTailId = dto.tail.flatten().firstOrNull { it !in cardIds }
    requireValid(unknownTailId == null) { "В tail есть id $unknownTailId, которого нет среди карточек" }

    val cards = file.cards.map { parseCard(it) }
    val deck = Deck(
        id = 0,
        name = dto.name,
        activeSection = parseEnum<Section>(dto.activeSection, "activeSection"),
        currentEpoch = dto.currentEpoch,
        passStarted = dto.passStarted,
        passCount = toEpochValues(dto.passCount),
        passCursor = dto.passCursor,
        browseCursor = dto.browseCursor,
        visibleSide = parseEnum<Side>(dto.visibleSide, "visibleSide"),
        lastPassDay = toEpochValues(dto.lastPassDay),
        waitingEpoch = dto.waitingEpoch,
        pausedEpoch = dto.pausedEpoch,
        pausedCursor = dto.pausedCursor,
        portionRatedCount = dto.portionRatedCount,
        tail = toEpochValues(dto.tail),
    )
    return ParsedDeck(deck, cards)
}

private fun parseCard(dto: CardDto): ParsedCard {
    requireValid(dto.position >= 0) { "Карточка ${dto.id}: position отрицательный" }
    requireValid(dto.successes >= 0) { "Карточка ${dto.id}: successes отрицательный" }
    val card = Card(
        id = dto.id,
        section = parseEnum<Section>(dto.section, "section карточки ${dto.id}"),
        position = dto.position,
        successes = dto.successes,
        sectionDay = dto.sectionDay,
        lastSuccessDay = dto.lastSuccessDay,
        createdAt = dto.createdAt,
        postponed = dto.postponed,
        answerRevealed = dto.answerRevealed,
        failed = dto.failed,
        checking = dto.checking,
        excludedFromPass = dto.excludedFromPass,
    )
    return ParsedCard(card, dto.front.map { parseStroke(it, dto.id) }, dto.back.map { parseStroke(it, dto.id) })
}

/** Допустимость значений (толщина, конечные координаты, нажим 0..1) решает модель: она не создаёт плохой штрих. */
private fun parseStroke(dto: StrokeDto, cardId: Long): Stroke =
    try {
        Stroke(
            dto.width,
            dto.points.map { values ->
                requireValid(values.size == POINT_VALUES) { "Карточка $cardId: в точке ${values.size} чисел вместо трёх" }
                StrokePoint(values[0], values[1], values[2])
            },
        )
    } catch (invalid: IllegalArgumentException) {
        throw ImportException(ImportError.DAMAGED, IllegalArgumentException("Карточка $cardId: ${invalid.message}", invalid))
    }

private inline fun <reified T : Enum<T>> parseEnum(name: String, what: String): T =
    enumValues<T>().firstOrNull { it.name == name } ?: throw buildDamaged("Неизвестное значение $what: $name")

private fun <T> toEpochValues(values: List<T>): EpochValues<T> =
    EpochValues(values[0], values[1], values[2], values[3])

/** Колода и карточки в виде файла; id карточек — как в базе, `tail` без id карточек, которых нет. */
internal fun buildDeckFile(deck: Deck, cards: List<Card>, contents: List<CardContent>): DeckFile {
    val cardIds = cards.map { it.id }.toSet()
    fun keepExisting(ids: List<Long>): List<Long> = ids.filter { it in cardIds }
    val deckDto = DeckDto(
        name = deck.name,
        activeSection = deck.activeSection.name,
        currentEpoch = deck.currentEpoch,
        passStarted = deck.passStarted,
        passCount = listOf(deck.passCount.epoch1, deck.passCount.epoch2, deck.passCount.epoch3, deck.passCount.epoch4),
        passCursor = deck.passCursor,
        browseCursor = deck.browseCursor,
        lastPassDay = listOf(
            deck.lastPassDay.epoch1,
            deck.lastPassDay.epoch2,
            deck.lastPassDay.epoch3,
            deck.lastPassDay.epoch4,
        ),
        waitingEpoch = deck.waitingEpoch,
        pausedEpoch = deck.pausedEpoch,
        pausedCursor = deck.pausedCursor,
        portionRatedCount = deck.portionRatedCount,
        visibleSide = deck.visibleSide.name,
        tail = listOf(
            keepExisting(deck.tail.epoch1),
            keepExisting(deck.tail.epoch2),
            keepExisting(deck.tail.epoch3),
            keepExisting(deck.tail.epoch4),
        ),
    )
    val cardDtos = cards.zip(contents) { card, content -> buildCardDto(card, content) }
    return DeckFile(DECK_FILE_FORMAT, DECK_FILE_VERSION, deckDto, cardDtos)
}

private fun buildCardDto(card: Card, content: CardContent): CardDto = CardDto(
    id = card.id,
    section = card.section.name,
    position = card.position,
    successes = card.successes,
    sectionDay = card.sectionDay,
    lastSuccessDay = card.lastSuccessDay,
    createdAt = card.createdAt,
    postponed = card.postponed,
    answerRevealed = card.answerRevealed,
    failed = card.failed,
    checking = card.checking,
    excludedFromPass = card.excludedFromPass,
    front = content.front.map(::buildStrokeDto),
    back = content.back.map(::buildStrokeDto),
)

private fun buildStrokeDto(stroke: Stroke): StrokeDto =
    StrokeDto(stroke.width, stroke.points.map { listOf(it.x, it.y, it.pressure) })
