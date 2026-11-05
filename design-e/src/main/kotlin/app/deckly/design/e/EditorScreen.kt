package app.deckly.design.e

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.EditorAction
import app.deckly.ui.contract.EditorDialog
import app.deckly.ui.contract.EditorScreenState
import app.deckly.ui.contract.EditorTool
import app.deckly.ui.contract.Thickness

/**
 * Редактор карточки по макетам `editor-*`, `dlg-discard`, `dlg-delete-edit`, `dlg-editor-back`: шапка, вкладки сторон,
 * лист, панель инструментов. Лист отдаёт приложение слотом [sheet]; холст на нём рисует `ink`.
 */
@Composable
fun EditorScreen(state: EditorScreenState, onAction: (EditorAction) -> Unit, sheet: @Composable (Modifier) -> Unit) {
    ScreenFrame(overlay = { state.dialog?.let { EditorDialogHost(it, onAction) } }) {
        EditorHeader(state.isNewCard, onAction)
        SideTabs(state.side) { onAction(EditorAction.SelectSide(it)) }
        Sheet(Modifier.weight(1f).fillMaxWidth()) {
            sheet(Modifier.fillMaxSize().semantics { contentDescription = EditorTexts.CANVAS })
        }
        EditorTools(state, onAction)
    }
}

// --- шапка -----------------------------------------------------------------------------------------

// У новой карточки слева «Отменить» без значка, у правки — «Удалить» со значком корзины; справа главное «Сохранить».
@Composable
private fun EditorHeader(isNewCard: Boolean, onAction: (EditorAction) -> Unit) {
    val left: @Composable () -> Unit = if (isNewCard) {
        {
            DecklyButton(
                EditorTexts.CANCEL, null, ButtonKind.QUIET, EditorTexts.CANCEL_DESCRIPTION,
                { onAction(EditorAction.Cancel) },
            )
        }
    } else {
        {
            DecklyButton(
                EditorTexts.DELETE, DecklyIcons.Trash, ButtonKind.QUIET, EditorTexts.DELETE_DESCRIPTION,
                { onAction(EditorAction.Delete) },
            )
        }
    }
    TitleBar(
        title = if (isNewCard) EditorTexts.TITLE_NEW else EditorTexts.TITLE_EDIT,
        left = left,
        right = { DecklyButton(EditorTexts.SAVE, DecklyIcons.Check, ButtonKind.PRIMARY, null, { onAction(EditorAction.Save) }) },
    )
}

// --- вкладки сторон --------------------------------------------------------------------------------

@Composable
private fun SideTabs(side: CardSideShown, onSelect: (CardSideShown) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Tokens.SideTabsHeight)
            .semantics { contentDescription = EditorTexts.SIDES }
            .drawWithContent {
                drawContent()
                val line = Tokens.BorderThin.toPx()
                drawRect(Tokens.Black, Offset(0f, size.height - line), Size(size.width, line))
            }
            .padding(bottom = Tokens.BorderThin),
        horizontalArrangement = Arrangement.spacedBy(Tokens.SideTabsGap),
    ) {
        SideTab(EditorTexts.FRONT, side == CardSideShown.FRONT, Modifier.weight(1f)) { onSelect(CardSideShown.FRONT) }
        SideTab(EditorTexts.BACK, side == CardSideShown.BACK, Modifier.weight(1f)) { onSelect(CardSideShown.BACK) }
    }
}

@Composable
private fun SideTab(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clickSilently(true, Role.Tab, onClick)
            .semantics { selected = isSelected },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BasicText(label, style = buildTextStyle(Tokens.BodyText, if (isSelected) FontWeight.Bold else FontWeight.Normal))
        }
        // Подчёркивание есть у всех вкладок и занимает место, прозрачное у невыбранной.
        Box(Modifier.fillMaxWidth().height(Tokens.TabUnderline).background(if (isSelected) Tokens.Black else Color.Transparent))
    }
}

// --- инструменты -----------------------------------------------------------------------------------

// Перо, ластик, группа толщин; отмена штриха прижата вправо (`.tools>.undo{margin-left:auto}`).
@Composable
private fun EditorTools(state: EditorScreenState, onAction: (EditorAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(Tokens.ToolsHeight).semantics { contentDescription = EditorTexts.TOOLS },
        horizontalArrangement = Arrangement.spacedBy(Tokens.ToolsGap),
    ) {
        ToolButton(DecklyIcons.Pen, EditorTexts.PEN, state.tool == EditorTool.PEN) { onAction(EditorAction.SelectTool(EditorTool.PEN)) }
        ToolButton(DecklyIcons.Eraser, EditorTexts.ERASER, state.tool == EditorTool.ERASER) {
            onAction(EditorAction.SelectTool(EditorTool.ERASER))
        }
        ThicknessGroup(state.thickness) { onAction(EditorAction.SelectThickness(it)) }
        Spacer(Modifier.weight(1f))
        DecklyButton(
            null, DecklyIcons.Undo, if (state.canUndo) ButtonKind.QUIET else ButtonKind.OFF, EditorTexts.UNDO_STROKE,
            { onAction(EditorAction.Undo) }, Modifier.size(Tokens.ToolButton),
        )
    }
}

// Выбранный инструмент в рамке 3 dp, как главная кнопка; остальные тихие.
@Composable
private fun ToolButton(icon: ImageVector, description: String, isSelected: Boolean, onClick: () -> Unit) {
    DecklyButton(
        null, icon, if (isSelected) ButtonKind.PRIMARY else ButtonKind.QUIET, description, onClick,
        Modifier.size(Tokens.ToolButton).semantics { selected = isSelected },
    )
}

// Группа «Толщина пера»: линии 1 dp слева и справа, три кнопки с образцом линии высотой 2, 5 и 9.
@Composable
private fun ThicknessGroup(selected: Thickness, onSelect: (Thickness) -> Unit) {
    Row(
        Modifier
            .fillMaxHeight()
            .padding(vertical = Tokens.ThicknessMarginVertical)
            .semantics { contentDescription = EditorTexts.THICKNESS }
            .drawWithContent {
                drawContent()
                val line = Tokens.BorderThin.toPx()
                drawRect(Tokens.Black, Offset.Zero, Size(line, size.height))
                drawRect(Tokens.Black, Offset(size.width - line, 0f), Size(line, size.height))
            }
            .padding(horizontal = Tokens.BorderThin + Tokens.ThicknessPaddingHorizontal),
        horizontalArrangement = Arrangement.spacedBy(Tokens.ThicknessGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThicknessButton(Thickness.THIN, Tokens.LineThin, selected == Thickness.THIN, onSelect)
        ThicknessButton(Thickness.MEDIUM, Tokens.LineMedium, selected == Thickness.MEDIUM, onSelect)
        ThicknessButton(Thickness.THICK, Tokens.LineThick, selected == Thickness.THICK, onSelect)
    }
}

@Composable
private fun ThicknessButton(thickness: Thickness, lineHeight: Dp, isSelected: Boolean, onSelect: (Thickness) -> Unit) {
    val shape = RoundedCornerShape(Tokens.RadiusSmall)
    Box(
        Modifier
            .size(Tokens.ThicknessButtonWidth, Tokens.ThicknessButtonHeight)
            .clickSilently(true, Role.Button) { onSelect(thickness) }
            .semantics {
                contentDescription = EditorTexts.describeThickness(thickness)
                selected = isSelected
            }
            .background(Tokens.White, shape)
            .border(
                if (isSelected) Tokens.BorderThick else Tokens.BorderMedium,
                if (isSelected) Tokens.Black else Color.Transparent,
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(Tokens.LineSampleWidth).height(lineHeight).background(Tokens.Black, RoundedCornerShape(Tokens.LineSampleRadius)))
    }
}

// --- окна ------------------------------------------------------------------------------------------

// Первая кнопка всегда «Продолжить» (главная), вторая — само действие.
@Composable
private fun EditorDialogHost(dialog: EditorDialog, onAction: (EditorAction) -> Unit) {
    val keep = DialogAction(EditorTexts.KEEP_EDITING, isPrimary = true) { onAction(EditorAction.DismissDialog) }
    fun confirm(label: String) = DialogAction(label, isPrimary = false) { onAction(EditorAction.ConfirmDialog) }
    when (dialog) {
        EditorDialog.DISCARD_DRAFT -> Dialog(EditorTexts.DISCARD_TITLE, EditorTexts.DISCARD_TEXT, listOf(keep, confirm(EditorTexts.DISCARD)))
        EditorDialog.DELETE_CARD -> Dialog(EditorTexts.DELETE_CARD_TITLE, EditorTexts.DELETE_CARD_TEXT, listOf(keep, confirm(EditorTexts.DELETE)))
        EditorDialog.SAVE_ON_BACK -> Dialog(EditorTexts.SAVE_EXIT_TITLE, null, listOf(keep, confirm(EditorTexts.SAVE_EXIT)))
    }
}
