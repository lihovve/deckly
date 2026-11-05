package app.deckly.design.e

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import app.deckly.ui.contract.CardMenuItem
import app.deckly.ui.contract.CardSideShown
import app.deckly.ui.contract.DeckAction
import app.deckly.ui.contract.DeckBody
import app.deckly.ui.contract.DeckDialog
import app.deckly.ui.contract.DeckFooter
import app.deckly.ui.contract.DeckHeader
import app.deckly.ui.contract.DeckScreenState
import app.deckly.ui.contract.TransitionKind

/**
 * Экран колоды по макетам `learn-*`, `browse-*`, `pool*`, `learned*`, `empty-*`, `transition*`, `menu-*`, `dlg-*`.
 * Порядок элементов и подписи взяты из `design/build.py`; решает всё состояние, дизайн лишь рисует его. Лист
 * карточки отдаёт приложение через [sheet]; меню открыто или нет — состояние дизайна.
 */
@Composable
fun DeckScreen(state: DeckScreenState, onAction: (DeckAction) -> Unit, sheet: @Composable (Modifier) -> Unit) {
    // Меню закрывается само, когда пункты или окно поменялись: открытое меню под окном было бы ловушкой.
    var isMenuOpen by remember(state.header.cardMenu, state.dialog) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Tokens.White)) {
        Column(Modifier.fillMaxSize().padding(Tokens.FrameMargin), verticalArrangement = Arrangement.spacedBy(Tokens.FrameGap)) {
            DeckTopBar(state.header, onAction, onMenu = { isMenuOpen = true })
            SectionTabs(
                state.tabs,
                { onAction(DeckAction.OpenSection(it)) },
                Modifier.semantics { contentDescription = DeckTexts.SECTIONS },
            )
            DeckSheet(state, sheet, Modifier.weight(1f).fillMaxWidth())
            DeckFooterRow(state, onAction)
        }
        if (isMenuOpen) {
            Popup(
                title = DeckTexts.CARD_ACTIONS,
                items = state.header.cardMenu.map { item ->
                    buildPopupItem(item) {
                        isMenuOpen = false
                        onAction(DeckAction.ChooseMenuItem(item))
                    }
                },
                onDismiss = { isMenuOpen = false },
            )
        }
        state.dialog?.let { DeckDialogHost(it, onAction) }
    }
}

// --- шапка -----------------------------------------------------------------------------------------

// Название колоды слева, затем счётчик позиции, успехи, плюс и «⋯» (функция head в build.py).
@Composable
private fun DeckTopBar(header: DeckHeader, onAction: (DeckAction) -> Unit, onMenu: () -> Unit) {
    TopBar {
        DeckHeading(header.deckName, { onAction(DeckAction.OpenDeckList) }, Modifier.weight(1f))
        header.position?.let { position ->
            Metric(
                DeckTexts.findPositionLabel(position.kind),
                DeckTexts.formatPositionValue(position.position, position.total),
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = DeckTexts.describePosition(position.kind, position.position, position.total)
                },
            )
        }
        header.successes?.let { Metric(DeckTexts.SUCCESSES, DeckTexts.formatSuccessesValue(it.successes, it.threshold)) }
        if (header.canCreateCard) {
            DecklyButton(null, DecklyIcons.Plus, ButtonKind.QUIET, DeckTexts.CREATE_CARD, { onAction(DeckAction.CreateCard) }, Modifier.size(Tokens.ControlMin), isSquare = true)
        }
        if (header.cardMenu.isNotEmpty()) {
            DecklyButton(null, DecklyIcons.More, ButtonKind.QUIET, DeckTexts.CARD_ACTIONS, onMenu, Modifier.size(Tokens.ControlMin), isSquare = true)
        }
    }
}

@Composable
private fun DeckHeading(name: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .height(Tokens.BarHeight)
            .clickSilently(true, Role.Button, onClick)
            .semantics { contentDescription = DeckTexts.describeDeckHeading(name) },
        horizontalArrangement = Arrangement.spacedBy(Tokens.DeckHeadingGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            name,
            modifier = Modifier.weight(1f, fill = false),
            style = buildTextStyle(Tokens.TitleText, FontWeight.Bold, letterSpacing = Tokens.DeckHeadingSpacing),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Image(DecklyIcons.Down, contentDescription = null, modifier = Modifier.size(Tokens.DeckArrow), colorFilter = ColorFilter.tint(Tokens.Black))
    }
}

// --- лист ------------------------------------------------------------------------------------------

@Composable
private fun DeckSheet(state: DeckScreenState, sheet: @Composable (Modifier) -> Unit, modifier: Modifier) {
    when (val body = state.body) {
        is DeckBody.Card -> {
            val label = describeCardSheet(body, isLearning = state.footer is DeckFooter.Learning)
            Sheet(modifier) { sheet(Modifier.fillMaxSize().semantics { contentDescription = label }) }
        }
        is DeckBody.EmptySection -> Sheet(modifier.semantics { contentDescription = DeckTexts.EMPTY_SECTION }) {
            SheetMessage(DecklyIcons.get(IconId.CARDS, Tokens.IconEmptyStroke)) {
                SheetTitle(if (body.isPool) DeckTexts.NO_NEW_CARDS else DeckTexts.NO_CARDS)
            }
        }
        is DeckBody.Transition -> Sheet(modifier.semantics { contentDescription = describeTransitionSheet(body) }) {
            SheetMessage(DecklyIcons.get(findTransitionIcon(body.kind), Tokens.IconEmptyStroke)) {
                SheetTitle(DeckTexts.formatEpoch(body.epoch), Tokens.BigNumeralText, Tokens.BigNumeralSpacing)
                BasicText(
                    describeTransitionHint(body),
                    modifier = Modifier.widthIn(max = Tokens.HintMaxWidth),
                    style = buildTextStyle(Tokens.HintText, lineHeightFactor = 1.5f, align = TextAlign.Center),
                )
            }
        }
    }
}

// Учебная карточка называется «Вопрос» или «Ответ», просматриваемая — «Рукописная карточка» (функции learn и browse).
private fun describeCardSheet(body: DeckBody.Card, isLearning: Boolean): String = when {
    body.isContentDamaged -> DeckTexts.DRAWING_UNAVAILABLE
    !isLearning -> DeckTexts.HANDWRITTEN_CARD
    body.side == CardSideShown.FRONT -> DeckTexts.QUESTION
    else -> DeckTexts.ANSWER
}

private fun describeTransitionSheet(body: DeckBody.Transition): String = when (body.kind) {
    TransitionKind.REGULAR -> DeckTexts.describeNextPass(body.epoch)
    TransitionKind.INSERTED -> DeckTexts.INSERTED_PASS
    TransitionKind.RESUMED -> DeckTexts.RESUMED_PASS
}

// «23 карточки», «40 карточек» или «Осталось 60 из 100».
private fun describeTransitionHint(body: DeckBody.Transition): String = when (body.kind) {
    TransitionKind.RESUMED -> DeckTexts.formatRemaining(body.remaining, body.total)
    else -> formatCardCount(body.remaining)
}

private fun findTransitionIcon(kind: TransitionKind): IconId = when (kind) {
    TransitionKind.REGULAR -> IconId.CHECK
    TransitionKind.INSERTED -> IconId.RIGHT
    TransitionKind.RESUMED -> IconId.UNDO
}

// Значок 64, под ним заголовок и пояснение: столбец по центру листа с полями 48 и промежутком 28 (.card в styles.css).
@Composable
internal fun SheetMessage(icon: ImageVector, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(Tokens.SheetPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.SheetGap, Alignment.CenterVertically),
    ) {
        Image(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(bottom = Tokens.EmptyIconGap).size(Tokens.IconEmpty),
            colorFilter = ColorFilter.tint(Tokens.Black),
        )
        content()
    }
}

@Composable
internal fun SheetTitle(
    text: String,
    size: TextUnit = Tokens.EmptyTitleText,
    letterSpacing: TextUnit = Tokens.EmptyTitleSpacing,
) {
    BasicText(
        text,
        modifier = Modifier.widthIn(max = Tokens.EmptyTitleMaxWidth),
        style = buildTextStyle(size, FontWeight.Bold, lineHeightFactor = 1.15f, letterSpacing = letterSpacing, align = TextAlign.Center),
    )
}

// --- нижняя строка ---------------------------------------------------------------------------------

@Composable
private fun DeckFooterRow(state: DeckScreenState, onAction: (DeckAction) -> Unit) {
    val side = (state.body as? DeckBody.Card)?.side ?: CardSideShown.FRONT
    val flipDescription = if (side == CardSideShown.FRONT) DeckTexts.SHOW_ANSWER else DeckTexts.SHOW_QUESTION
    when (val footer = state.footer) {
        is DeckFooter.Learning -> {
            val postpone: @Composable (Modifier) -> Unit = { cell ->
                DecklyButton(
                    null, DecklyIcons.Defer, if (footer.postpone.isAvailable) ButtonKind.QUIET else ButtonKind.OFF,
                    DeckTexts.describePostpone(footer.postpone.block), { onAction(DeckAction.Postpone) }, cell,
                )
            }
            // Переворот выделен на вопросе, на ответе обычный: на ответе главное действие — оценка.
            val flipKind = when {
                !footer.flip.isAvailable -> ButtonKind.OFF
                side == CardSideShown.FRONT -> ButtonKind.PRIMARY
                else -> ButtonKind.NORMAL
            }
            val flip: @Composable (Modifier) -> Unit = { cell ->
                DecklyButton(null, DecklyIcons.Flip, flipKind, flipDescription, { onAction(DeckAction.Flip) }, cell)
            }
            val notKnew: @Composable (Modifier) -> Unit = { cell ->
                DecklyButton(DeckTexts.NOT_KNEW, DecklyIcons.Cross, ButtonKind.NORMAL, null, { onAction(DeckAction.DidNotKnow) }, cell, Tokens.FooterButtonText)
            }
            val knew: @Composable (Modifier) -> Unit = { cell ->
                DecklyButton(DeckTexts.KNEW, DecklyIcons.Check, ButtonKind.NORMAL, null, { onAction(DeckAction.Knew) }, cell, Tokens.FooterButtonText)
            }
            Footer(FooterLayout.LEARNING, if (footer.areRatingsShown) listOf(postpone, flip, notKnew, knew) else listOf(postpone, flip))
        }
        is DeckFooter.Browsing -> {
            val arrowKind = if (footer.canBrowse) ButtonKind.QUIET else ButtonKind.OFF
            val previous: @Composable (Modifier) -> Unit = { cell ->
                DecklyButton(null, DecklyIcons.Left, arrowKind, DeckTexts.PREVIOUS_CARD, { onAction(DeckAction.ShowPrevious) }, cell)
            }
            val flip: @Composable (Modifier) -> Unit = { cell ->
                DecklyButton(null, DecklyIcons.Flip, ButtonKind.NORMAL, flipDescription, { onAction(DeckAction.Flip) }, cell)
            }
            val next: @Composable (Modifier) -> Unit = { cell ->
                DecklyButton(null, DecklyIcons.Right, arrowKind, DeckTexts.NEXT_CARD, { onAction(DeckAction.ShowNext) }, cell)
            }
            Footer(FooterLayout.NAVIGATION, listOf(previous, flip, null, next))
        }
        is DeckFooter.StartPass -> {
            val start: @Composable (Modifier) -> Unit = { cell ->
                val label = if (footer.kind == TransitionKind.RESUMED) DeckTexts.CONTINUE_PASS else DeckTexts.START_PASS
                DecklyButton(label, DecklyIcons.Right, ButtonKind.PRIMARY, null, { onAction(DeckAction.StartPass) }, cell, Tokens.FooterButtonText)
            }
            Footer(FooterLayout.ONE, listOf(start))
        }
        // Пустая нижняя строка занимает место, чтобы лист был одного размера на всех экранах.
        DeckFooter.None -> Footer(FooterLayout.ONE, emptyList())
    }
}

// --- меню и окна -----------------------------------------------------------------------------------

private fun buildPopupItem(item: CardMenuItem, onClick: () -> Unit): PopupItem = when (item) {
    CardMenuItem.EDIT -> PopupItem(DeckTexts.EDIT, DecklyIcons.Pen, isDanger = false, onClick)
    CardMenuItem.RETURN_TO_NEW -> PopupItem(DeckTexts.RETURN_TO_NEW, DecklyIcons.Undo, isDanger = false, onClick)
    CardMenuItem.DELETE -> PopupItem(DeckTexts.DELETE_CARD, DecklyIcons.Trash, isDanger = true, onClick)
}

@Composable
private fun DeckDialogHost(dialog: DeckDialog, onAction: (DeckAction) -> Unit) {
    val keep = DialogAction(DeckTexts.KEEP, isPrimary = true) { onAction(DeckAction.DismissDialog) }
    when (dialog) {
        DeckDialog.CONFIRM_DELETE_CARD -> Dialog(
            DeckTexts.DELETE_TITLE, DeckTexts.DELETE_TEXT,
            listOf(keep, DialogAction(DeckTexts.DELETE, isPrimary = false) { onAction(DeckAction.ConfirmDialog) }),
        )
        DeckDialog.CONFIRM_RETURN_TO_NEW -> Dialog(
            DeckTexts.RETURN_TITLE, DeckTexts.RETURN_TEXT,
            listOf(keep, DialogAction(DeckTexts.RETURN, isPrimary = false) { onAction(DeckAction.ConfirmDialog) }),
        )
        DeckDialog.NEED_NEW_CARD -> Dialog(
            DeckTexts.NEED_CARD_TITLE, DeckTexts.NEED_CARD_TEXT,
            listOf(DialogAction(DeckTexts.OPEN_NEW, isPrimary = true) { onAction(DeckAction.ConfirmDialog) }),
        )
        DeckDialog.DONE_FOR_TODAY -> Dialog(
            DeckTexts.DONE_TITLE, null,
            listOf(DialogAction(DeckTexts.OPEN_NEW, isPrimary = true, icon = DecklyIcons.Right) { onAction(DeckAction.ConfirmDialog) }),
        )
    }
}
