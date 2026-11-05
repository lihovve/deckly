package app.deckly.design.e

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import app.deckly.ui.contract.DeckNameAction
import app.deckly.ui.contract.DeckNameState

/**
 * Форма новой колоды по макету `deck-new`: шапка «Отмена» / «Создать», подпись и поле. «Создать» серое, пока имя пустое.
 * Фокус в поле при открытии, чтобы сразу вышла клавиатура; место под клавиатуру оставляет окно приложения.
 */
@Composable
fun DeckNameScreen(state: DeckNameState, onAction: (DeckNameAction) -> Unit) {
    ScreenFrame {
        TitleBar(
            DeckNameTexts.TITLE,
            left = { DecklyButton(DeckNameTexts.CANCEL, null, ButtonKind.QUIET, null, { onAction(DeckNameAction.Cancel) }) },
            right = {
                DecklyButton(
                    DeckNameTexts.CREATE, null, if (state.canSave) ButtonKind.PRIMARY else ButtonKind.OFF, null,
                    { onAction(DeckNameAction.Save) },
                )
            },
        )
        NameField(state, onAction)
    }
}

@Composable
private fun NameField(state: DeckNameState, onAction: (DeckNameAction) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.padding(top = Tokens.FieldMarginTop), verticalArrangement = Arrangement.spacedBy(Tokens.FieldGap)) {
        BasicText(DeckNameTexts.FIELD_LABEL, style = buildTextStyle(Tokens.FieldLabelText))
        BasicTextField(
            value = state.name,
            onValueChange = { onAction(DeckNameAction.ChangeName(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.InputHeight)
                .focusRequester(focus)
                .border(Tokens.BorderMedium, Tokens.Black, RoundedCornerShape(Tokens.RadiusSmall))
                // Рамка в Compose места не занимает, а в макете (border-box) занимает: текст стоит в 2 + 16 от края.
                .padding(Tokens.BorderMedium + Tokens.InputPadding),
            textStyle = buildTextStyle(Tokens.InputText),
            singleLine = true,
            cursorBrush = SolidColor(Tokens.Black),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // «Готово» на клавиатуре создаёт колоду, только если имя непустое.
            keyboardActions = KeyboardActions(onDone = { if (state.canSave) onAction(DeckNameAction.Save) }),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (state.name.isEmpty()) {
                        BasicText(DeckNameTexts.PLACEHOLDER, style = buildTextStyle(Tokens.InputText, color = Tokens.Unavailable))
                    }
                    innerTextField()
                }
            },
        )
    }
}
