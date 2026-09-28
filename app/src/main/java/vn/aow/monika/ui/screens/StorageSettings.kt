package vn.aow.monika.ui.screens

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.library.StorageCleaner
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.GradientProgress
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Spinner

private const val GB = 1024L * 1024 * 1024

/** Mốc giới hạn bộ nhớ đệm (giống thanh trượt của Telegram). -1 = vô hạn. */
private val LIMITS = listOf(2 * GB, 5 * GB, 16 * GB, 32 * GB, -1L)
private val LIMIT_LABELS = listOf("2 GB", "5 GB", "16 GB", "32 GB", "Vô hạn")

private val DAY_OPTIONS = listOf(0 to "Không bao giờ", 2 to "2 ngày", 7 to "1 tuần", 30 to "1 tháng", 90 to "3 tháng")

/**
 * Cài đặt dung lượng kiểu Telegram:
 * - Tự động xóa khi không dùng: theo từng nhóm (game / lõi / ảnh & bài viết).
 * - Giới hạn bộ nhớ đệm (mặc định 2 GB): vượt thì xóa thứ lâu không dùng nhất.
 * Save game và game tự thêm từ máy không bao giờ bị xóa.
 */
@Composable
fun StorageSettingsContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Monika.colors
    val prefs = AppGraph.prefs
    var usage by remember { mutableStateOf<StorageCleaner.Usage?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var gameDays by remember { mutableIntStateOf(prefs.autoDeleteGameDays) }
    var coreDays by remember { mutableIntStateOf(prefs.autoDeleteCoreDays) }
    var mediaDays by remember { mutableIntStateOf(prefs.autoDeleteMediaDays) }
    var limitIndex by remember { mutableFloatStateOf(LIMITS.indexOf(prefs.cacheLimitBytes).coerceAtLeast(0).toFloat()) }

    LaunchedEffect(tick) { usage = withContext(Dispatchers.IO) { StorageCleaner.usage(context) } }

    fun runClean(mediaOnly: Boolean) {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { StorageCleaner.clean(context, mediaOnly = mediaOnly) }
            busy = false
            tick++
            val games = if (r.removedGames.isNotEmpty()) " · dọn ${r.removedGames.size} game" else ""
            Toast.makeText(context, "Đã giải phóng ${size(r.freed)}$games", Toast.LENGTH_SHORT).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tổng quan
        val u = usage
        if (u == null) Spinner() else {
            val limit = LIMITS[limitIndex.toInt()]
            Text(
                "Bộ nhớ đệm: ${size(u.total)}" + if (limit > 0) " / ${size(limit)}" else "",
                style = Monika.type.cardTitle, color = c.text,
            )
            if (limit > 0) GradientProgress(u.total.toFloat() / limit)
            UsageLine(c.accentOrange, "Game tải từ aow.vn", u.games)
            UsageLine(c.accentPurple, "Lõi giả lập", u.cores)
            UsageLine(c.accentCyan, "Ảnh & bài viết", u.media)
        }
        Text(
            "Game tải từ aow.vn, lõi giả lập và ảnh đều tải lại được bất cứ lúc nào. Save game và game bạn tự thêm từ máy KHÔNG bao giờ bị xóa.",
            style = Monika.type.caption, color = c.textSecondary,
        )

        // Tự động xóa
        Text("Tự động xóa khi không dùng", style = Monika.type.bodyStrong, color = c.accentCoral, modifier = Modifier.padding(top = 4.dp))
        AutoDeleteRow(R.drawable.ic_fluent_games_24_regular, Brush.linearGradient(listOf(Color(0xFFFFB052), Color(0xFFE95CC8))), "Game tải từ aow.vn", gameDays) {
            gameDays = it; prefs.autoDeleteGameDays = it
        }
        AutoDeleteRow(R.drawable.ic_fluent_layer_24_regular, Brush.linearGradient(listOf(Color(0xFF8076FF), Color(0xFF628DF4))), "Lõi giả lập", coreDays) {
            coreDays = it; prefs.autoDeleteCoreDays = it
        }
        AutoDeleteRow(R.drawable.ic_fluent_globe_24_regular, Brush.linearGradient(listOf(Color(0xFF63D68A), Color(0xFF66CFF3))), "Ảnh & bài viết", mediaDays) {
            mediaDays = it; prefs.autoDeleteMediaDays = it
        }
        Text(
            "Thứ không được mở trong khoảng thời gian này sẽ bị xóa khỏi máy để tiết kiệm dung lượng. Game bị dọn vẫn hiện trong Thư viện với nút \"Tải lại\".",
            style = Monika.type.caption, color = c.textSecondary,
        )

        // Giới hạn bộ nhớ đệm
        Text("Giới hạn bộ nhớ đệm", style = Monika.type.bodyStrong, color = c.accentCoral, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth()) {
            LIMIT_LABELS.forEachIndexed { i, label ->
                Text(
                    label, style = Monika.type.caption, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    color = if (i == limitIndex.toInt()) c.accentCoral else c.textSecondary,
                )
            }
        }
        Slider(
            value = limitIndex,
            onValueChange = { limitIndex = it },
            onValueChangeFinished = {
                prefs.cacheLimitBytes = LIMITS[limitIndex.toInt()]
                runClean(mediaOnly = false)
            },
            valueRange = 0f..(LIMITS.size - 1).toFloat(),
            steps = LIMITS.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = c.accentCoral, activeTrackColor = c.accentCoral, inactiveTrackColor = c.track,
                activeTickColor = Color.White, inactiveTickColor = c.textTertiary,
            ),
        )
        Text(
            "Nếu bộ nhớ đệm vượt giới hạn, thứ lâu không dùng nhất sẽ bị xóa trước: ảnh & bài viết → lõi giả lập → game lâu không chơi.",
            style = Monika.type.caption, color = c.textSecondary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton("Dọn ngay", { runClean(false) }, Modifier.weight(1f), icon = R.drawable.ic_fluent_delete_24_regular, enabled = !busy, height = 48.dp)
            DarkButton("Xóa ảnh", { runClean(true) }, enabled = !busy)
        }
    }
}

@Composable
private fun UsageLine(color: Color, label: String, bytes: Long) {
    val c = Monika.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(Radius.pill).background(color))
        Text(label, style = Monika.type.body, color = c.text, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text(size(bytes), style = Monika.type.body, color = c.textSecondary)
    }
}

/** Hàng kiểu Telegram: icon màu · tên · giá trị (bấm để chọn). */
@Composable
private fun AutoDeleteRow(@DrawableRes icon: Int, brush: Brush, label: String, days: Int, onChange: (Int) -> Unit) {
    val c = Monika.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(Radius.small).clickable { open = true }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(Radius.thumb).background(brush), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, Modifier.size(22.dp), tint = Color.White)
            }
            Text(label, style = Monika.type.body, color = c.text, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Text(DAY_OPTIONS.firstOrNull { it.first == days }?.second ?: "$days ngày", style = Monika.type.bodyStrong, color = c.accentCoral)
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.background(c.surface)) {
            DAY_OPTIONS.forEach { (d, text) ->
                DropdownMenuItem(
                    text = { Text(text, style = Monika.type.body, color = if (d == days) c.accentCoral else c.text) },
                    onClick = { open = false; onChange(d) },
                )
            }
        }
    }
}

/** Nhãn giới hạn hiện tại cho dòng mô tả trong Cài đặt. */
fun cacheLimitLabel(): String = LIMIT_LABELS.getOrNull(LIMITS.indexOf(AppGraph.prefs.cacheLimitBytes)) ?: "2 GB"

private fun size(bytes: Long): String = when {
    bytes >= GB -> "%.1f GB".format(bytes.toDouble() / GB).replace('.', ',')
    bytes >= 1024 * 1024 -> "%.0f MB".format(bytes / 1048576.0)
    else -> "%.0f KB".format(bytes / 1024.0)
}
