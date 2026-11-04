package app.deckly.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Колода: все поля `Deck`. Значения по эпохам лежат в колонках `_1.._4`, `tail_N` — id карточек
 * через запятую (пустая строка — пустой список). Перечисления хранятся именами.
 */
@Entity(tableName = "deck")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    @ColumnInfo(name = "active_section") val activeSection: String,
    @ColumnInfo(name = "current_epoch") val currentEpoch: Int,
    @ColumnInfo(name = "pass_started") val passStarted: Boolean,
    @ColumnInfo(name = "pass_count_1") val passCount1: Int,
    @ColumnInfo(name = "pass_count_2") val passCount2: Int,
    @ColumnInfo(name = "pass_count_3") val passCount3: Int,
    @ColumnInfo(name = "pass_count_4") val passCount4: Int,
    @ColumnInfo(name = "pass_cursor") val passCursor: Int,
    @ColumnInfo(name = "browse_cursor") val browseCursor: Int,
    @ColumnInfo(name = "visible_side") val visibleSide: String,
    @ColumnInfo(name = "last_pass_day_1") val lastPassDay1: Int?,
    @ColumnInfo(name = "last_pass_day_2") val lastPassDay2: Int?,
    @ColumnInfo(name = "last_pass_day_3") val lastPassDay3: Int?,
    @ColumnInfo(name = "last_pass_day_4") val lastPassDay4: Int?,
    @ColumnInfo(name = "waiting_epoch") val waitingEpoch: Int,
    @ColumnInfo(name = "paused_epoch") val pausedEpoch: Int,
    @ColumnInfo(name = "paused_cursor") val pausedCursor: Int,
    @ColumnInfo(name = "portion_rated_count") val portionRatedCount: Int,
    @ColumnInfo(name = "tail_1") val tail1: String,
    @ColumnInfo(name = "tail_2") val tail2: String,
    @ColumnInfo(name = "tail_3") val tail3: String,
    @ColumnInfo(name = "tail_4") val tail4: String,
)

/**
 * Карточка: все поля `Card`, колода и имя файла со штрихами. `id` выдаёт счётчик
 * `app_state.next_card_id`, а не база: id удалённых карточек не переиспользуются.
 */
@Entity(
    tableName = "card",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deck_id")],
)
data class CardEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "deck_id") val deckId: Long,
    val section: String,
    val position: Int,
    val successes: Int,
    @ColumnInfo(name = "section_day") val sectionDay: Int,
    @ColumnInfo(name = "last_success_day") val lastSuccessDay: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val postponed: Boolean,
    @ColumnInfo(name = "answer_revealed") val answerRevealed: Boolean,
    val failed: Boolean,
    val checking: Boolean,
    @ColumnInfo(name = "excluded_from_pass") val excludedFromPass: Boolean,
    @ColumnInfo(name = "content_file") val contentFile: String,
)

/**
 * Состояние алгоритма карточки без колоды и файла: все столбцы `card`, кроме `deck_id` и
 * `content_file`. Для `@Update(entity = CardEntity::class)`: шаг движка не трогает ссылку на файл.
 */
data class CardStateUpdate(
    val id: Long,
    val section: String,
    val position: Int,
    val successes: Int,
    @ColumnInfo(name = "section_day") val sectionDay: Int,
    @ColumnInfo(name = "last_success_day") val lastSuccessDay: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val postponed: Boolean,
    @ColumnInfo(name = "answer_revealed") val answerRevealed: Boolean,
    val failed: Boolean,
    val checking: Boolean,
    @ColumnInfo(name = "excluded_from_pass") val excludedFromPass: Boolean,
)

/** Черновик редактора. Одна строка с `id = 1`; `card_id` пуст у новой карточки. */
@Entity(tableName = "draft")
data class DraftEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "deck_id") val deckId: Long,
    @ColumnInfo(name = "card_id") val cardId: Long?,
    @ColumnInfo(name = "visible_side") val visibleSide: String,
    @ColumnInfo(name = "return_section") val returnSection: String,
    @ColumnInfo(name = "content_file") val contentFile: String,
)

/** Состояние приложения. Одна строка с `id = 1`, её создаёт база при первом открытии. */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "current_deck_id") val currentDeckId: Long?,
    @ColumnInfo(name = "next_card_id") val nextCardId: Long,
)

/** `id` единственной строки в `draft` и `app_state`. */
const val SINGLE_ROW_ID = 1
