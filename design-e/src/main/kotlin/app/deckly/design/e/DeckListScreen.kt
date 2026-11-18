package app.deckly.design.e

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckRow
import app.deckly.ui.contract.DeleteDeckDialog

/**
 * Список колод по макетам `decks`, `decks-empty`, `deck-menu*` и `dlg-delete-deck*`. У строки справа кнопка «⋯» с меню
 * «Rename» и «Delete deck»; открыто ли меню и у какой строки, знает только дизайн, окно удаления приходит состоянием.
 * Пока нет страниц, список прокручивается, чтобы ни одна колода не потерялась за нижней строкой. Импорта здесь ещё нет.
 */
@Composable
fun DeckListScreen(state: DeckListState, onAction: (DeckListAction) -> Unit) {
    // Колода с открытым меню и верх её строки на экране в момент нажатия «⋯»: список прокручивается. Меню закрывается
    // само, когда колоды уже нет в списке.
    var menu by remember { mutableStateOf<DeckMenu?>(null) }
    ScreenFrame(
        overlay = {
            val open = menu
            val deck = open?.let { opened -> state.decks.firstOrNull { it.id == opened.deckId } }
            if (open != null && deck != null) DeckMenuPopup(deck, open.rowTop, { menu = null }, onAction)
            state.deleteDialog?.let { DeleteDialogHost(it, onAction) }
        },
    ) {
        TitleBar(
            DeckListTexts.TITLE,
            left = if (state.canGoBack) {
                {
                    DecklyButton(
                        null, DecklyIcons.Left, ButtonKind.QUIET, DeckListTexts.BACK_TO_CARDS,
                        { onAction(DeckListAction.Back) }, Modifier.size(Tokens.ControlMin), isSquare = true,
                    )
                }
            } else {
                null
            },
        )
        if (state.decks.isEmpty()) {
            // Лист без подписи: в макете она — «Рукописная карточка» по умолчанию, а заголовок «Нет колод» читается сам.
            Sheet(Modifier.weight(1f).fillMaxWidth()) {
                SheetMessage(DecklyIcons.get(IconId.CARDS, Tokens.IconEmptyStroke)) { SheetTitle(DeckListTexts.NO_DECKS) }
            }
        } else {
            DeckList(state.decks, { onAction(DeckListAction.OpenDeck(it)) }, { deckId, rowTop -> menu = DeckMenu(deckId, rowTop) }, Modifier.weight(1f))
        }
        val newDeck: @Composable (Modifier) -> Unit = { cell ->
            DecklyButton(
                DeckListTexts.NEW_DECK, DecklyIcons.Plus, ButtonKind.PRIMARY, null,
                { onAction(DeckListAction.CreateDeck) }, cell, Tokens.FooterButtonText,
            )
        }
        Footer(FooterLayout.ONE, listOf(newDeck))
    }
}

// Линия над списком и под каждой строкой; список прокручивается внутри своей области, нижняя строка остаётся на месте.
@Composable
private fun DeckList(decks: List<DeckRow>, onOpen: (Long) -> Unit, onMenu: (Long, Dp) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = Tokens.ListMarginTop)
            .drawBehind { drawRect(Tokens.Black, Offset.Zero, Size(size.width, Tokens.BorderThin.toPx())) }
            .padding(top = Tokens.BorderThin)
            .verticalScroll(rememberScrollState()),
    ) {
        for (deck in decks) DeckListRow(deck, onOpen, onMenu)
    }
}

// Меню строки: колода и верх её строки в кадре.
private data class DeckMenu(val deckId: Long, val rowTop: Dp)

@Composable
private fun DeckListRow(deck: DeckRow, onOpen: (Long) -> Unit, onMenu: (Long, Dp) -> Unit) {
    // Положение строки читается в момент нажатия, а не хранится в состоянии: при прокрутке оно меняется каждый кадр.
    val density = LocalDensity.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Row(
        Modifier
            .onGloballyPositioned { coordinates[0] = it }
            .fillMaxWidth()
            .heightIn(min = Tokens.ItemMinHeight)
            .drawBehind {
                val line = Tokens.BorderThin.toPx()
                drawRect(Tokens.Black, Offset(0f, size.height - line), Size(size.width, line))
            }
            .padding(bottom = Tokens.BorderThin),
        horizontalArrangement = Arrangement.spacedBy(Tokens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Колонка отметки: галочка у текущей колоды, у остальных пусто (номеров строк нет).
        Box(Modifier.width(Tokens.ItemNumberWidth)) {
            if (deck.isCurrent) {
                Image(DecklyIcons.Check, contentDescription = null, modifier = Modifier.size(Tokens.ItemNumberIcon), colorFilter = ColorFilter.tint(Tokens.Black))
            }
        }
        // Нажимается название с подписью, а не вся строка (`a.fill` в макете).
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = Tokens.ItemFillMinHeight)
                .clickSilently(true, Role.Button) { onOpen(deck.id) }
                .padding(vertical = Tokens.ItemFillPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(Tokens.ItemFillGap, Alignment.CenterVertically),
        ) {
            BasicText(
                deck.name,
                style = buildTextStyle(Tokens.ItemTitleText, if (deck.isCurrent) FontWeight.Bold else FontWeight.Medium, lineHeightFactor = 1.2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DeckCaption(deck)
        }
        DecklyButton(
            null, DecklyIcons.More, ButtonKind.QUIET, DeckListTexts.formatMenuLabel(deck.name),
            {
                val row = coordinates[0]?.takeIf { it.isAttached }
                if (row != null) onMenu(deck.id, with(density) { row.positionInRoot().y.toDp() })
            },
            Modifier.size(Tokens.ControlMin), isSquare = true,
        )
    }
}

// Меню строки по макету `deck-menu`: «Rename» и «Delete deck». Пункта «Export to file» нет: экспорта и импорта на экране ещё
// нет, это отдельная работа, как и «Import from file» в нижней строке. Верх меню на 77 ниже верха строки, а если оно не
// помещается до нижнего поля кадра, сдвигается вверх ровно настолько, чтобы поместиться.
@Composable
private fun DeckMenuPopup(deck: DeckRow, rowTop: Dp, onClose: () -> Unit, onAction: (DeckListAction) -> Unit) {
    Popup(
        title = deck.name,
        items = listOf(
            PopupItem(DeckListTexts.RENAME, DecklyIcons.Pen, isDanger = false) {
                onClose()
                onAction(DeckListAction.RenameDeck(deck.id))
            },
            PopupItem(DeckListTexts.DELETE_DECK, DecklyIcons.Trash, isDanger = true) {
                onClose()
                onAction(DeckListAction.AskDeleteDeck(deck.id))
            },
        ),
        onDismiss = onClose,
        top = rowTop + Tokens.DeckMenuOffset,
        bottomMargin = Tokens.FrameMargin,
    )
}

// Окно по макету `dlg-delete-deck`: «Keep» главная, «Delete» обычная.
@Composable
private fun DeleteDialogHost(dialog: DeleteDeckDialog, onAction: (DeckListAction) -> Unit) {
    Dialog(
        DeckListTexts.formatDeleteTitle(dialog.name), DeckListTexts.formatDeleteText(dialog.cardCount),
        listOf(
            DialogAction(DeckTexts.KEEP, isPrimary = true) { onAction(DeckListAction.DismissDeleteDialog) },
            DialogAction(DeckTexts.DELETE, isPrimary = false) { onAction(DeckListAction.ConfirmDeleteDeck) },
        ),
    )
}

// «245 карточек · ▶ I», «18 карточек · выучены» или просто «N карточек».
@Composable
private fun DeckCaption(deck: DeckRow) {
    val style = buildTextStyle(Tokens.ItemInfoText)
    val epoch = deck.activeEpoch
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.ItemInfoGap), verticalAlignment = Alignment.CenterVertically) {
        when {
            epoch != null -> {
                BasicText(DeckListTexts.formatCaptionBeforeEpoch(deck.cardCount), style = style)
                Image(DecklyIcons.Active, contentDescription = null, modifier = Modifier.size(Tokens.IconSmall), colorFilter = ColorFilter.tint(Tokens.Black))
                BasicText(DeckTexts.formatEpoch(epoch), style = style)
            }
            deck.isAllLearned -> BasicText(DeckListTexts.formatLearnedCaption(deck.cardCount), style = style)
            else -> BasicText(formatCardCount(deck.cardCount), style = style)
        }
    }
}
