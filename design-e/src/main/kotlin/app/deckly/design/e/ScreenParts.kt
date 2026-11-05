package app.deckly.design.e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Кадр экрана (`.screen` в макете): белый фон, поля 32 dp, промежуток между частями 16 dp. [overlay] — окна поверх
 * кадра.
 */
@Composable
internal fun ScreenFrame(overlay: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Tokens.White)) {
        Column(
            Modifier.fillMaxSize().padding(Tokens.FrameMargin),
            verticalArrangement = Arrangement.spacedBy(Tokens.FrameGap),
            content = content,
        )
        overlay()
    }
}

/**
 * Шапка с названием экрана (`head(title=…)`): слева [left], посередине жирное название, справа [right]. Название
 * занимает всё свободное место.
 */
@Composable
internal fun TitleBar(title: String, left: (@Composable () -> Unit)? = null, right: (@Composable () -> Unit)? = null) {
    TopBar {
        left?.invoke()
        BasicText(
            title,
            modifier = Modifier.weight(1f),
            style = buildTextStyle(Tokens.TitleText, FontWeight.Bold, lineHeightFactor = 1.2f, letterSpacing = Tokens.HeadingSpacing),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        right?.invoke()
    }
}
