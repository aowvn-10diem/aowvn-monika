package vn.aow.monika.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import vn.aow.monika.R
import vn.aow.monika.library.GameStorage
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.GradientProgress
import vn.aow.monika.ui.theme.Illustration
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.SoftPillButton
import java.io.File

private data class DownloadRow(
    val id: Long,
    val title: String,
    val status: Int,
    val done: Long,
    val total: Long,
    val speed: Long,
)

private data class StorageInfo(val total: Long, val free: Long, val games: Long)

/** Màn "Tải xuống": dung lượng máy + các lượt tải của app (đọc từ DownloadManager, cập nhật mỗi giây). */
@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val c = Monika.colors
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var rows by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var storage by remember { mutableStateOf<StorageInfo?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            storage = withContext(Dispatchers.IO) { readStorage(context) }
            val last = mutableMapOf<Long, Long>()
            while (true) {
                val now = withContext(Dispatchers.IO) { queryDownloads(context, last) }
                rows = now
                now.forEach { last[it.id] = it.done }
                delay(1000)
            }
        }
    }

    val active = rows.filter { it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PENDING || it.status == DownloadManager.STATUS_PAUSED }
    val done = rows.filter { it.status == DownloadManager.STATUS_SUCCESSFUL }
    val failed = rows.filter { it.status == DownloadManager.STATUS_FAILED }
    val dm = context.getSystemService(DownloadManager::class.java)

    Screen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DockClearance), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Box(Modifier.padding(horizontal = (0).dp)) { MonikaHeader("Tải xuống") } }
            storage?.let { s -> item { StorageCard(s) } }
            item { SectionRow("Đang tải xuống", active.size) }
            if (active.isEmpty()) item {
                Text("Không có lượt tải nào. Bấm \"Tải game\" trong bài viết để bắt đầu.", style = Monika.type.body, color = c.textSecondary)
            }
            items(active, key = { it.id }) { r -> ActiveRow(r) { dm.remove(r.id); tick++ } }
            if (failed.isNotEmpty()) {
                item { SectionRow("Lỗi", failed.size) }
                items(failed, key = { it.id }) { r -> DoneRow(r, ok = false) { dm.remove(r.id); tick++ } }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionRow("Đã hoàn thành", done.size) }
                    if (done.isNotEmpty()) SoftPillButton("Xóa tất cả", { done.forEach { dm.remove(it.id) }; tick++ }, R.drawable.ic_fluent_delete_24_regular)
                }
            }
            if (done.isEmpty() && active.isEmpty()) item {
                EmptyState(R.drawable.fluent3d_package, "Chưa tải gì", "Game tải xong sẽ tự giải nén vào tab Giả lập.")
            }
            items(done, key = { it.id }) { r -> DoneRow(r, ok = true) { dm.remove(r.id); tick++ } }
        }
    }
}

@Composable
private fun SectionRow(title: String, count: Int) {
    val c = Monika.colors
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Monika.type.sectionTitle, color = c.text)
        Text("  ($count)", style = Monika.type.body, color = c.textSecondary)
    }
}

@Composable
private fun StorageCard(s: StorageInfo) {
    val c = Monika.colors
    val used = s.total - s.free
    val usedRatio = if (s.total > 0) used.toFloat() / s.total else 0f
    val gameRatio = if (s.total > 0) s.games.toFloat() / s.total else 0f
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                val track = c.track
                Canvas(Modifier.size(104.dp)) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(listOf(Color(0xFFFFB052), Color(0xFFFF7F78), Color(0xFFE95CC8), Color(0xFFFFB052))),
                        -90f, 360f * usedRatio, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(gb(s.total), style = Monika.type.cardTitle, color = c.text)
                    Text("Tổng", style = Monika.type.caption, color = c.textSecondary)
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dung lượng lưu trữ", style = Monika.type.cardTitle, color = c.text)
                Text("Đã dùng ${gb(used)} / ${gb(s.total)}", style = Monika.type.caption, color = c.textSecondary)
                GradientProgress(usedRatio)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(Color(0xFFFFA44F), "Game", gb(s.games))
                    Legend(c.textTertiary, "Còn trống", gb(s.free))
                }
                if (gameRatio > 0f) Spacer(Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    val c = Monika.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(Radius.pill).background(color))
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, style = Monika.type.caption, color = c.textSecondary)
            Text(value, style = Monika.type.caption, color = c.text)
        }
    }
}

@Composable
private fun ActiveRow(r: DownloadRow, onCancel: () -> Unit) {
    val c = Monika.colors
    val p = if (r.total > 0) r.done.toFloat() / r.total else 0f
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.medium, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(Radius.thumb).background(Brush.linearGradient(listOf(Color(0xFFFFB052), Color(0xFFE95CC8)))), contentAlignment = Alignment.Center) {
                Illustration(R.drawable.fluent3d_package, Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.title, style = Monika.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${mb(r.done)} / ${if (r.total > 0) mb(r.total) else "?"}", style = Monika.type.caption, color = c.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { GradientProgress(p) }
                    Text("  ${(p * 100).toInt()}%", style = Monika.type.caption, color = c.text)
                }
                val eta = if (r.speed > 0 && r.total > 0) (r.total - r.done) / r.speed else -1
                Text(
                    when (r.status) {
                        DownloadManager.STATUS_PENDING -> "Đang chờ…"
                        DownloadManager.STATUS_PAUSED -> "Tạm dừng (chờ mạng)"
                        else -> "Đang tải… ${mb(r.speed)}/s" + if (eta >= 0) " • Còn ${formatEta(eta)}" else ""
                    },
                    style = Monika.type.caption, color = c.textTertiary,
                )
            }
            CircleButton(R.drawable.ic_fluent_dismiss_24_regular, "Hủy tải", onCancel, size = 44.dp)
        }
    }
}

@Composable
private fun DoneRow(r: DownloadRow, ok: Boolean, onRemove: () -> Unit) {
    val c = Monika.colors
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.medium, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(Radius.thumb).background(c.surfaceSoft), contentAlignment = Alignment.Center) {
                Illustration(R.drawable.fluent3d_package, Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.title, style = Monika.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (ok) "${mb(r.total)} • Đã tải xong" else "Tải thất bại", style = Monika.type.caption, color = if (ok) c.textSecondary else c.danger)
            }
            if (ok) Icon(painterResource(R.drawable.ic_fluent_checkmark_circle_24_filled), "Xong", Modifier.size(26.dp), tint = c.success)
            CircleButton(R.drawable.ic_fluent_dismiss_24_regular, "Xóa khỏi danh sách", onRemove, size = 40.dp)
        }
    }
}

private fun queryDownloads(context: Context, last: Map<Long, Long>): List<DownloadRow> {
    val dm = context.getSystemService(DownloadManager::class.java)
    return dm.query(DownloadManager.Query())?.use { cur ->
        buildList {
            while (cur.moveToNext()) {
                val id = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                add(
                    DownloadRow(
                        id = id,
                        title = cur.getString(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(),
                        status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        done = done,
                        total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                        speed = (done - (last[id] ?: done)).coerceAtLeast(0),
                    )
                )
            }
        }.sortedByDescending { it.id }
    }.orEmpty()
}

private fun readStorage(context: Context): StorageInfo {
    val root = GameStorage.root(context)
    val stat = StatFs(root.path)
    val games = GameStorage.games(context).walkTopDown().filter(File::isFile).sumOf { it.length() }
    return StorageInfo(stat.totalBytes, stat.availableBytes, games)
}

private fun gb(bytes: Long) = "%.1f GB".format(bytes / 1_073_741_824.0).replace('.', ',')
private fun mb(bytes: Long) = if (bytes >= 1_073_741_824) gb(bytes) else "%.1f MB".format(bytes / 1_048_576.0).replace('.', ',')
private fun formatEta(sec: Long) = if (sec >= 3600) "${sec / 3600} giờ" else if (sec >= 60) "${sec / 60} phút" else "$sec giây"
