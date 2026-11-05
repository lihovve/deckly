package app.deckly.design.e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * Экран, пока открывается хранилище (`startup-loading` в макете): «Deckly» по центру белого листа, без индикатора,
 * кнопок и перехода. Полное обновление экрана на нём не вызывается: первым остаётся первый настоящий экран.
 */
@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(Tokens.White), contentAlignment = Alignment.Center) {
        BasicText(
            StartupTexts.APP_NAME,
            style = buildTextStyle(
                Tokens.LoadingBrandText,
                FontWeight.Bold,
                lineHeightFactor = Tokens.LoadingBrandLineHeightFactor,
                letterSpacing = Tokens.LoadingBrandSpacing,
                align = TextAlign.Center,
            ),
        )
    }
}
