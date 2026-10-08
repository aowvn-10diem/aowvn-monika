package vn.aow.monika.ui.controls

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import vn.aow.monika.Prefs
import vn.aow.monika.ui.theme.*

@Composable
fun ControllerSettings(prefs: Prefs) {
    var options by remember(prefs) { mutableStateOf(prefs.controllerOptions) }
    val c = Monika.colors
    fun update(next: ControllerOptions) { options = next.bounded(); prefs.controllerOptions = options }
    Text("Rung khi nhấn", style = Monika.type.bodyStrong, color = c.text)
    val labels = mapOf(HapticLevel.OFF to "Tắt", HapticLevel.LIGHT to "Nhẹ", HapticLevel.MEDIUM to "Vừa", HapticLevel.STRONG to "Mạnh")
    ChipBar(HapticLevel.entries.toList(), options.haptic, { labels.getValue(it) },
        { update(options.copy(haptic = it)) }, accent = true, contentPadding = PaddingValues(0.dp))
    Text("Tôn trọng cài đặt rung khi chạm của Android. Áp dụng khi mở game tiếp theo.",
        style = Monika.type.caption, color = c.textSecondary)
    @Composable fun toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = Monika.type.body, color = c.text)
            Switch(value, change, modifier = Modifier.semantics { contentDescription = label }, colors = SwitchDefaults.colors(checkedThumbColor = c.surfaceDark,
                checkedTrackColor = c.accentOrange, uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.track))
        }
    }
    toggle("Hiệu ứng lún khi bấm", options.pressAnimation) { update(options.copy(pressAnimation = it)) }
    toggle("Hiện nhãn phím", options.labels) { update(options.copy(labels = it)) }
    @Composable fun slider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
        Text("$label ${(value * 100).toInt()}%", style = Monika.type.bodyStrong, color = c.text)
        Slider(value, change, modifier = Modifier.semantics { contentDescription = label }, valueRange = range, colors = SliderDefaults.colors(thumbColor = c.surfaceDark,
            activeTrackColor = c.accentOrange, inactiveTrackColor = c.track))
    }
    slider("Cỡ nút chung", options.size, .7f..1.4f) { update(options.copy(size = it)) }
    slider("Độ mờ chung", options.opacity, .2f..1f) { update(options.copy(opacity = it)) }
    Text("Cỡ nút được giới hạn theo màn hình; vùng chạm của nút tròn luôn ít nhất 72 dp.",
        style = Monika.type.caption, color = c.textSecondary)
    SoftPillButton("Về mặc định", { update(ControllerOptions()) }, vn.aow.monika.R.drawable.ic_fluent_arrow_counterclockwise_24_regular)
}
