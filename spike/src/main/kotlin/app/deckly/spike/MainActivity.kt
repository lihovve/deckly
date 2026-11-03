package app.deckly.spike

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PenMode(val label: String) {
    ONYX("Onyx"),
    ONYX_SF("Onyx SF"),
    CANVAS("Canvas"),
}

class MainActivity : ComponentActivity() {
    private var currentSheet: PenSheet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Проверка отказа SDK: `--ez noSdk true` до создания листа; Application при этом не трогаем.
        if (intent.getBooleanExtra(EXTRA_NO_SDK, false)) {
            SdkStatus.initError = IllegalStateException("noSdk")
            SdkStatus.logSummary()
        }
        setContent {
            SpikeScreen(
                getSheet = { currentSheet },
                onSheetCreated = { currentSheet = it },
                onSheetReleased = { released -> if (currentSheet === released) currentSheet = null },
            )
        }
    }

    // Перо включается, только пока окно в фокусе: шторка уведомлений и системные окна ложатся поверх
    // сырого пера (Notable делает так же). Без фокуса в onResume перо включит onWindowFocusChanged.
    override fun onResume() {
        super.onResume()
        if (hasWindowFocus()) {
            currentSheet?.resume()
        }
    }

    override fun onPause() {
        currentSheet?.pause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            currentSheet?.resume()
        } else {
            currentSheet?.pause()
        }
    }

    private companion object {
        const val EXTRA_NO_SDK = "noSdk"
    }
}

private const val STATUS_LINES = 12
private const val REFRESH_SETTLE_MS = 300L

@Composable
private fun SpikeScreen(
    getSheet: () -> PenSheet?,
    onSheetCreated: (PenSheet) -> Unit,
    onSheetReleased: (PenSheet) -> Unit,
) {
    val isPenAvailable = SdkStatus.isPenAvailable
    var mode by rememberSaveable { mutableStateOf(if (isPenAvailable) PenMode.ONYX else PenMode.CANVAS) }
    var isGcAfterErase by remember { mutableStateOf(PenSettings.isGcAfterErase) }
    val lines by SpikeLog.lines.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusBlock(mode = mode, lines = lines.takeLast(STATUS_LINES))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(2.dp, Color.Black),
        ) {
            key(mode) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context -> createSheet(context, mode).also { onSheetCreated(it as PenSheet) } },
                    onRelease = { view -> onSheetReleased(view as PenSheet) },
                )
            }
        }

        ButtonRow {
            PenButton("Onyx", enabled = isPenAvailable, selected = mode == PenMode.ONYX) { mode = PenMode.ONYX }
            PenButton("Onyx SF", enabled = isPenAvailable, selected = mode == PenMode.ONYX_SF) { mode = PenMode.ONYX_SF }
            PenButton("Canvas", selected = mode == PenMode.CANVAS) { mode = PenMode.CANVAS }
            PenButton("Очистить") {
                StrokeStore.clear()
                scope.refreshSheet(getSheet)
            }
        }
        ButtonRow {
            PenButton("GC") { scope.repaintAll(getSheet, UpdateMode.GC) }
            PenButton("REGAL+") { scope.repaintAll(getSheet, UpdateMode.REGAL_PLUS) }
            PenButton("Статус") { scope.refreshSheet(getSheet) }
            PenButton("GC после ластика: ${if (isGcAfterErase) "вкл" else "выкл"}", selected = isGcAfterErase) {
                isGcAfterErase = !isGcAfterErase
                PenSettings.isGcAfterErase = isGcAfterErase
                SpikeLog.line("SETTING", "gcAfterErase" to isGcAfterErase)
            }
        }
        // Ряд кнопок базы (задача 4) добавляется здесь.
    }
}

private fun createSheet(context: Context, mode: PenMode): View = when (mode) {
    PenMode.ONYX -> OnyxPenView(context, useSfRender = false)
    PenMode.ONYX_SF -> OnyxPenView(context, useSfRender = true)
    PenMode.CANVAS -> CanvasPenView(context)
}

// Пока перо Onyx включено, прошивка замораживает экран: отпускаем его, даём обновиться и возвращаем перо.
private fun CoroutineScope.refreshSheet(getSheet: () -> PenSheet?) {
    launch {
        val sheet = getSheet() ?: return@launch
        sheet.pause()
        sheet.redrawAll()
        delay(REFRESH_SETTLE_MS)
        sheet.resume()
    }
}

// Полное обновление экрана выбранным режимом: pause() -> перерисовать лист -> repaintEveryThing -> resume().
private fun CoroutineScope.repaintAll(getSheet: () -> PenSheet?, updateMode: UpdateMode) {
    launch {
        val sheet = getSheet()
        sheet?.pause()
        sheet?.redrawAll()
        try {
            EpdController.repaintEveryThing(updateMode)
            SpikeLog.line("REPAINT", "mode" to updateMode)
        } catch (error: Throwable) {
            SpikeLog.line("REPAINT", "mode" to updateMode, "error" to error)
        }
        delay(REFRESH_SETTLE_MS)
        sheet?.resume()
    }
}

@Composable
private fun StatusBlock(mode: PenMode, lines: List<String>) {
    val sdk = if (SdkStatus.isPenAvailable) "Onyx SDK: ok" else "Onyx SDK: нет (${SdkStatus.initError ?: "не Onyx"})"
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        BasicText(
            text = "Режим: ${mode.label} · $sdk · ${SdkStatus.deviceClass ?: "-"}",
            style = TextStyle(fontSize = 14.sp, color = Color.Black),
        )
        for (line in lines) {
            BasicText(
                text = line,
                maxLines = 1,
                style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black),
            )
        }
    }
}

@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

// Кнопка не меньше 64 dp, без ripple и анимаций (правила e-ink из ТЗ).
@Composable
private fun RowScope.PenButton(
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val ink = if (enabled) Color.Black else Color.Gray
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .border(if (selected) 4.dp else 2.dp, ink)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontSize = 18.sp, color = ink, textAlign = TextAlign.Center),
        )
    }
}
