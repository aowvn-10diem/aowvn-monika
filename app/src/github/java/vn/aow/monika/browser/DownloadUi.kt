package vn.aow.monika.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vn.aow.monika.download.BrowserDownloads
import vn.aow.monika.download.DlJob
import vn.aow.monika.download.DlState
import vn.aow.monika.download.SaveDest
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.primaryGradient

private fun mb(b: Long) = String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)

private fun statusText(j: DlJob): String = when (j.state) {
    DlState.RUNNING -> if (j.total > 0) "${mb(j.done)} / ${mb(j.total)}" + (if (j.speed > 0) " · ${mb(j.speed)}/s" else "") else "Đã tải ${mb(j.done)}"
    DlState.READY -> "Đã tải xong ${mb(j.done)} · chọn nơi lưu"
    DlState.SAVING -> "Đang lưu…"
    DlState.FAILED -> "Lỗi: ${j.error ?: "không tải được"}"
    else -> ""
}

/**
 * Menu popup khi bắt đầu tải: file ĐÃ đang tải về ngay, người dùng chỉ đặt tên + chọn nơi lưu rồi bấm Lưu → file chuyển sang đúng chỗ.
 * Cùng thiết kế menu popup dưới đáy của Monika.
 */
@Composable
fun BoxScope.DownloadSheet(mgr: BrowserDownloads, pickFolder: () -> Unit) {
    val job = mgr.sheetFor
    // Nhớ nội dung đang hiện để hiệu ứng đóng không bị trống.
    var shown by remember { mutableStateOf<DlJob?>(null) }
    if (job != null) shown = job
    val j = shown
    MonikaMenuSheet(
        job != null && j != null && j.state != DlState.CANCELED, { mgr.sheetFor = null }, actions = emptyList(),
        title = "Tải xuống", subtitle = j?.let(::statusText),
        header = {
            if (j == null) return@MonikaMenuSheet
            var edited by remember(j.id) { mutableStateOf<String?>(null) }
            var choice by remember(j.id) { mutableStateOf(mgr.defaultDest()) }
            // Đã chọn thư mục mới từ trình chọn → cập nhật lựa chọn.
            val picked = mgr.pickedFolder
            LaunchedEffect(picked) { if (picked != null) { choice = picked; mgr.pickedFolder = null } }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (j.state == DlState.RUNNING || j.state == DlState.READY) {
                    val p = j.progress
                    if (p >= 0) LinearProgressIndicator({ p }, Modifier.fillMaxWidth().clip(Radius.pill), color = Monika.colors.accentCoral, trackColor = Color(0x22FFFFFF))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().clip(Radius.pill), color = Monika.colors.accentCoral, trackColor = Color(0x22FFFFFF))
                }
                Text("Tên file", style = Monika.type.caption, color = SheetColors.textSecondary)
                Box(Modifier.fillMaxWidth().clip(Radius.medium).background(SheetColors.row).padding(12.dp)) {
                    BasicTextField(
                        edited ?: j.name, { edited = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Lưu vào", style = Monika.type.caption, color = SheetColors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetChip("Thư viện game", choice == SaveDest.Library) { choice = SaveDest.Library }
                    SheetChip("Tải xuống", choice == SaveDest.Downloads) { choice = SaveDest.Downloads }
                }
                val folder = choice as? SaveDest.Folder
                SheetChip(folder?.label?.let { "Thư mục: $it" } ?: "Chọn thư mục khác…", folder != null) { pickFolder() }
                Text(
                    when (choice) {
                        SaveDest.Library -> "Tự đưa vào Thư viện (giải nén nếu là file nén)."
                        SaveDest.Downloads -> "Lưu nguyên file trong thư mục Tải xuống của máy."
                        is SaveDest.Folder -> "Lưu nguyên file vào thư mục bạn chọn."
                    },
                    style = Monika.type.caption, color = SheetColors.textSecondary,
                )
                if (j.state == DlState.FAILED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SheetChip("Đóng", false) { mgr.dismiss(j); mgr.sheetFor = null } }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SheetChip("Lưu", true) { mgr.confirm(j, edited ?: j.name, choice) }
                        SheetChip("Hủy tải", false) { mgr.cancel(j) }
                    }
                }
            }
        },
    )
}

/** Tiến trình tải ở góc trên phải: vòng tiến độ + % + tên file; chạm để mở lại menu (nếu chưa chọn nơi lưu). */
@Composable
fun BoxScope.DownloadCorner(mgr: BrowserDownloads) {
    val active = mgr.jobs.filter { it.state != DlState.CANCELED && it.state != DlState.SAVED }
    val saved = mgr.justSaved
    LaunchedEffect(saved) { if (saved != null) { delay(3200); if (mgr.justSaved == saved) mgr.justSaved = null } }
    if (active.isEmpty() && saved == null) return
    val first = active.firstOrNull()
    Row(
        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 10.dp, end = 10.dp)
            .clip(Radius.pill).background(Brush.verticalGradient(listOf(Color(0xEE34323A), Color(0xEE232227))))
            .clickable(enabled = first != null && !first.confirmed) { mgr.sheetFor = first }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (first != null) {
            val p = first.progress
            if (first.state == DlState.SAVING || p < 0) CircularProgressIndicator(Modifier.size(20.dp), color = Monika.colors.accentCoral, strokeWidth = 2.5.dp)
            else CircularProgressIndicator({ p }, Modifier.size(20.dp), color = Monika.colors.accentCoral, trackColor = Color(0x33FFFFFF), strokeWidth = 2.5.dp)
            val pct = if (first.state == DlState.SAVING) "Đang lưu…" else if (p >= 0) "${(p * 100).toInt()}%" else mb(first.done)
            Text(
                (if (active.size > 1) "${active.size} file · " else "") + pct + (if (active.size == 1) " · " + first.name else ""),
                style = Monika.type.caption, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 2.dp),
            )
        } else if (saved != null) {
            Text("✓ $saved", style = Monika.type.caption, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
