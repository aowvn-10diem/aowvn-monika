package vn.aow.monika.ui.controls

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.config.CoreScreen
import vn.aow.monika.runner.CoreOptions
import vn.aow.monika.runner.DsScreen
import vn.aow.monika.ui.theme.*
import kotlin.math.roundToInt

/**
 * V78c: mục "Màn hình" trong Cài đặt Monika (NDS: bố cục · tỉ lệ · khoảng cách).
 * [defaults] = tùy chọn mặc định của lõi trong config (`cores.<id>.options`); lựa chọn của người chơi ghi vào [CoreOptions]
 * (cùng nơi với bảng "Tùy chọn giả lập" trong game) và có hiệu lực khi mở game tiếp theo.
 */
@Composable
fun ScreenSettings(coreId: String, screen: CoreScreen, defaults: Map<String, String>) {
    val context = LocalContext.current
    val c = Monika.colors
    var options by remember(coreId) { mutableStateOf(defaults + CoreOptions.saved(context, coreId)) }
    val state = DsScreen.current(screen, options)
    fun write(pair: Pair<String, String>) {
        CoreOptions.save(context, coreId, pair.first, pair.second)
        options = options + pair
    }

    if (screen.title.isNotBlank()) Text(screen.title, style = Monika.type.bodyStrong, color = c.text)
    if (screen.layoutKey.isNotBlank() && screen.layouts.isNotEmpty()) {
        Text("Bố cục hai màn hình", style = Monika.type.bodyStrong, color = c.text)
        ChipBar(screen.layouts, state.layout, { it.label }, { write(DsScreen.valueOf(screen.layoutKey, it)) },
            accent = true, contentPadding = PaddingValues(0.dp))
    }
    if (DsScreen.showsRatio(screen, state)) {
        Text("Tỉ lệ màn lớn / màn nhỏ", style = Monika.type.bodyStrong, color = c.text)
        ChipBar(screen.ratios, state.ratio ?: screen.ratios.first(), { it.label }, { write(DsScreen.valueOf(screen.ratioKey, it)) },
            accent = true, contentPadding = PaddingValues(0.dp))
    }
    if (DsScreen.showsGap(screen)) {
        var drag by remember(state.gap) { mutableFloatStateOf(state.gap.toFloat()) }
        val label = "Khoảng cách giữa hai màn"
        Text("$label: ${drag.roundToInt()} px", style = Monika.type.bodyStrong, color = c.text)
        Slider(
            drag, { drag = it }, modifier = Modifier.semantics { contentDescription = label },
            valueRange = 0f..screen.gapMax.toFloat(), steps = (screen.gapMax - 1).coerceAtLeast(0),
            onValueChangeFinished = { write(DsScreen.gapValue(screen, drag.roundToInt())) },
            colors = SliderDefaults.colors(thumbColor = c.surfaceDark, activeTrackColor = c.accentOrange, inactiveTrackColor = c.track),
        )
    }
    Text(
        "Áp dụng khi mở game tiếp theo. Chỉ dùng cho lõi melonDS DS (máy 64-bit); lõi dự phòng chỉnh trong game: Menu → Tùy chọn giả lập.",
        style = Monika.type.caption, color = c.textSecondary,
    )
    SoftPillButton("Về mặc định", {
        CoreOptions.remove(context, coreId, DsScreen.keys(screen))
        options = defaults + CoreOptions.saved(context, coreId)
    }, R.drawable.ic_fluent_arrow_counterclockwise_24_regular)
}
