package app.deckly.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.deckly.domain.model.CardSide
import app.deckly.domain.model.Stroke
import app.deckly.domain.model.StrokePoint
import app.deckly.editor.CanvasState
import app.deckly.ink.CardSideImage
import app.deckly.ink.DrawingSheet

/**
 * Листы, которые приложение вставляет в слоты дизайна. Отдельный интерфейс нужен, чтобы тесты сборки экранов
 * видели, что листу передали, не поднимая перо и холст: настоящие листы проверяет `ink`.
 */
interface SheetSlots {
    /** Сторона карточки в обучении и просмотре. */
    @Composable
    fun DeckSheet(strokes: List<Stroke>, modifier: Modifier)

    /** Холст редактора; [canvas] уже учитывает паузу пера на время обновления экрана. */
    @Composable
    fun EditorSheet(
        canvas: CanvasState,
        onStroke: (Stroke) -> Unit,
        onErase: (List<StrokePoint>, Float) -> Unit,
        modifier: Modifier,
    )
}

/** Настоящие листы из `ink`. Описание листа ставит дизайн через модификатор слота, поэтому здесь его нет. */
object InkSheetSlots : SheetSlots {
    @Composable
    override fun DeckSheet(strokes: List<Stroke>, modifier: Modifier) {
        CardSideImage(CardSide(strokes), modifier, description = null)
    }

    @Composable
    override fun EditorSheet(
        canvas: CanvasState,
        onStroke: (Stroke) -> Unit,
        onErase: (List<StrokePoint>, Float) -> Unit,
        modifier: Modifier,
    ) {
        DrawingSheet(
            side = CardSide(canvas.strokes),
            penWidthPx = canvas.penWidthPx,
            isPenEnabled = canvas.isPenEnabled,
            uiRevision = canvas.uiRevision,
            onStroke = onStroke,
            onErase = onErase,
            // Выбор фигуры придёт в задаче 6: пока в приложении фигур нет, касание у контура случиться не может.
            onShapeTap = {},
            modifier = modifier,
        )
    }
}
