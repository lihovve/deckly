package app.deckly.design.e

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

/** Кнопка окна: [isPrimary] — основная (рамка 3), иначе обычная; [icon] — значок перед подписью. */
data class DialogAction(val label: String, val isPrimary: Boolean, val icon: ImageVector? = null, val onClick: () -> Unit)

/** Пункт меню: [isDanger] отделён линией сверху (удаление). */
data class PopupItem(val label: String, val icon: ImageVector, val isDanger: Boolean, val onClick: () -> Unit)

// Поглощает все касания в своей области: то, что лежит под окном, их не получает. Ребёнок обрабатывает событие раньше
// (основной проход идёт от потомков к родителю), поэтому кнопки внутри работают.
internal fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

/**
 * Окно подтверждения поверх экрана. Фон не затемняется, но и касаний не получает: окно кладут последним в `Box`
 * на весь кадр, и оно перекрывает его целиком. Белое кольцо в 12 dp отделяет окно от рисунка под ним. Кнопок одна
 * или две.
 */
@Composable
fun Dialog(title: String, text: String?, actions: List<DialogAction>, modifier: Modifier = Modifier) {
    require(actions.size in 1..2) { "В окне одна или две кнопки: ${actions.size}" }
    val shape = RoundedCornerShape(Tokens.RadiusLarge)
    Box(modifier.fillMaxSize().blockTouches(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(Tokens.DialogWidth)
                .semantics {
                    paneTitle = title
                    isTraversalGroup = true
                }
                .drawBehind {
                    val ring = Tokens.DialogRing.toPx()
                    drawRoundRect(
                        Tokens.White,
                        Offset(-ring, -ring),
                        Size(size.width + 2 * ring, size.height + 2 * ring),
                        CornerRadius(Tokens.RadiusLarge.toPx() + ring),
                    )
                }
                .background(Tokens.White, shape)
                .border(Tokens.BorderThick, Tokens.Black, shape)
                .padding(Tokens.BorderThick + Tokens.DialogPadding),
            verticalArrangement = Arrangement.spacedBy(Tokens.DialogGap),
        ) {
            BasicText(title, style = buildTextStyle(Tokens.DialogTitleText, FontWeight.Bold, lineHeightFactor = 1.15f, letterSpacing = Tokens.DialogTitleSpacing))
            if (text != null) BasicText(text, style = buildTextStyle(Tokens.BodyText, lineHeightFactor = 1.5f))
            val cells = actions.map { action ->
                val cell: @Composable (Modifier) -> Unit = { cellModifier ->
                    DecklyButton(action.label, action.icon, if (action.isPrimary) ButtonKind.PRIMARY else ButtonKind.NORMAL, null, action.onClick, cellModifier)
                }
                cell
            }
            Footer(
                if (actions.size == 1) FooterLayout.ONE else FooterLayout.TWO,
                cells,
                Modifier.padding(top = Tokens.DialogFooterGap),
                Tokens.DialogFooterHeight,
            )
        }
    }
}

/**
 * Меню поверх экрана: справа сверху, [top] — отступ от верха кадра. Фон не затемняется; касание мимо закрывает меню
 * ([onDismiss]), касание внутри, но мимо пунктов, ничего не делает.
 */
@Composable
fun Popup(title: String, items: List<PopupItem>, onDismiss: () -> Unit, modifier: Modifier = Modifier, top: Dp = Tokens.PopupTop) {
    val shape = RoundedCornerShape(Tokens.RadiusSmall)
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickSilently(true, Role.Button, onDismiss)
                .semantics { contentDescription = "Закрыть меню" },
        )
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = top, end = Tokens.PopupEnd)
                .width(Tokens.PopupWidth)
                .semantics {
                    contentDescription = title
                    isTraversalGroup = true
                }
                .background(Tokens.White, shape)
                .border(Tokens.BorderMedium, Tokens.Black, shape)
                .padding(Tokens.BorderMedium)
                .padding(horizontal = Tokens.PopupPaddingHorizontal, vertical = Tokens.PopupPaddingVertical)
                .blockTouches(),
        ) {
            BasicText(
                title,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        val line = Tokens.BorderThin.toPx()
                        drawRect(Tokens.Black, Offset(0f, size.height - line), Size(size.width, line))
                    }
                    .padding(top = Tokens.PopupTitlePaddingTop, bottom = Tokens.PopupTitlePaddingBottom),
                style = buildTextStyle(Tokens.LabelText),
            )
            for (item in items) PopupRow(item)
        }
    }
}

@Composable
private fun PopupRow(item: PopupItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.PopupItemHeight)
            .clickSilently(true, Role.Button, item.onClick)
            .drawWithContent {
                drawContent()
                if (item.isDanger) drawRect(Tokens.Black, Offset.Zero, Size(size.width, Tokens.BorderThin.toPx()))
            },
        horizontalArrangement = Arrangement.spacedBy(Tokens.PopupItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(item.icon, contentDescription = null, modifier = Modifier.size(Tokens.IconDefault), colorFilter = ColorFilter.tint(Tokens.Black))
        BasicText(item.label, style = buildTextStyle(Tokens.BodyText))
    }
}
