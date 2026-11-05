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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.deckly.ui.contract.DeckListAction
import app.deckly.ui.contract.DeckListState
import app.deckly.ui.contract.DeckRow

/**
 * Список колод по макетам `decks` и `decks-empty`. Без меню колоды и импорта (план 5). Пока нет страниц, список
 * прокручивается, чтобы ни одна колода не потерялась за нижней строкой.
 */
@Composable
fun DeckListScreen(state: DeckListState, onAction: (DeckListAction) -> Unit) {
    ScreenFrame {
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
            DeckList(state.decks, { onAction(DeckListAction.OpenDeck(it)) }, Modifier.weight(1f))
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
private fun DeckList(decks: List<DeckRow>, onOpen: (Long) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = Tokens.ListMarginTop)
            .drawBehind { drawRect(Tokens.Black, Offset.Zero, Size(size.width, Tokens.BorderThin.toPx())) }
            .padding(top = Tokens.BorderThin)
            .verticalScroll(rememberScrollState()),
    ) {
        decks.forEachIndexed { index, deck -> DeckListRow(index + 1, deck, onOpen) }
    }
}

@Composable
private fun DeckListRow(number: Int, deck: DeckRow, onOpen: (Long) -> Unit) {
    Row(
        Modifier
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
        // Колонка номера: у текущей колоды галочка вместо номера.
        Box(Modifier.width(Tokens.ItemNumberWidth)) {
            if (deck.isCurrent) {
                Image(DecklyIcons.Check, contentDescription = null, modifier = Modifier.size(Tokens.ItemNumberIcon), colorFilter = ColorFilter.tint(Tokens.Black))
            } else {
                BasicText(DeckListTexts.formatDeckNumber(number), style = buildTextStyle(Tokens.ItemNumberText))
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
    }
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
