package vn.aow.monika.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.diag.Diagnostics.Report
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow

/**
 * Giao diện báo lỗi game:
 *  - Hộp thoại tự bật khi game vừa sập ([Diagnostics.pending]): nguyên nhân + đề xuất (đổi lõi) + gửi báo lỗi.
 *  - "Nhật ký lỗi" (Cài đặt → mở): danh sách báo cáo đã lưu, xem / sao chép / gửi / xóa.
 */
@Composable
fun BoxScope.CrashUi() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by Diagnostics.pending.collectAsState()
    val logOpen by Diagnostics.logOpen.collectAsState()
    val tick by Diagnostics.tick.collectAsState()
    var detail by remember { mutableStateOf<Report?>(null) }
    val cfg by AppGraph.config.config.collectAsState()

    fun send(r: Report) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Diagnostics.send(context, AppGraph.http, r, cfg.crash.endpoint) }
            Toast.makeText(
                context,
                if (ok) "Đã gửi báo lỗi cho AowVN. Cảm ơn bạn!" else "Đã chép báo lỗi. Dán vào nhóm AowVN để được hỗ trợ.",
                Toast.LENGTH_LONG,
            ).show()
            if (!ok && cfg.community.facebookGroup.isNotBlank()) vn.aow.monika.browser.InAppBrowserActivity.start(context, cfg.community.facebookGroup)
        }
    }

    // ---- Game vừa sập ----
    val p = pending
    var last by remember { mutableStateOf<Report?>(null) }
    if (p != null) last = p
    val shown = last
    val sys = shown?.session?.system.orEmpty()
    val alt = remember(shown) {
        // Đề xuất đổi lõi nếu hệ máy có lõi khác.
        val s = cfg.systems.firstOrNull { it.name == sys || it.id == sys }
        val current = shown?.session?.core.orEmpty()
        s?.takeIf { it.altCores.size > 1 }?.let { it.altCores.firstOrNull { c -> c != current } }?.let { s to it }
    }
    MonikaMenuSheet(
        p != null, { p?.let { Diagnostics.markSeen(context, it) } },
        title = "Game vừa dừng đột ngột",
        subtitle = shown?.session?.let { "${it.game} · ${it.system} · lõi ${it.core}" },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetRow(
                    shown?.title.orEmpty(), subtitle = causeHint(shown),
                    icon = R.drawable.ic_fluent_info_24_regular,
                )
                Text(
                    if (shown?.sent == true) "Monika đã tự gửi báo lỗi này cho AowVN (tắt trong Cài đặt). Bạn có thể thử lõi khác bên dưới."
                    else "Monika đã lưu lại thông tin (lõi, giai đoạn, log) để AowVN sửa lỗi này. Bấm Gửi báo lỗi giúp mình nhé!",
                    style = Monika.type.caption, color = SheetColors.textSecondary,
                )
            }
        },
        actions = buildList {
            shown?.let { r ->
                add(SheetAction("Gửi báo lỗi", R.drawable.ic_fluent_share_24_regular, highlight = true) { send(r); Diagnostics.markSeen(context, r) })
                add(SheetAction("Xem chi tiết", R.drawable.ic_fluent_document_24_regular) { detail = r; Diagnostics.markSeen(context, r) })
                alt?.let { (s, core) ->
                    add(SheetAction("Thử lõi $core", R.drawable.ic_fluent_arrow_sync_24_regular) {
                        AppGraph.prefs.setCoreOverride(s.id, core)
                        Toast.makeText(context, "Đã đổi lõi ${s.name} sang $core. Mở lại game để thử.", Toast.LENGTH_LONG).show()
                        Diagnostics.markSeen(context, r)
                    })
                }
                add(SheetAction("Bỏ qua", R.drawable.ic_fluent_dismiss_24_regular) { Diagnostics.markSeen(context, r) })
            }
        },
    )

    // ---- Nhật ký lỗi ----
    val reports = remember(tick, logOpen) { if (logOpen) Diagnostics.list(context) else emptyList() }
    MonikaMenuSheet(
        logOpen && detail == null, { Diagnostics.logOpen.value = false },
        title = "Nhật ký lỗi", subtitle = if (reports.isEmpty()) "Chưa có lỗi nào — tốt quá!" else "${reports.size} báo cáo · chạm để xem, sao chép, gửi",
        header = if (reports.isEmpty()) null else ({
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reports.take(12).forEach { r ->
                    SheetRow(
                        r.title, maxTitleLines = 3, subtitle = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(r.time) +
                            (if (r.component.isNotBlank()) " · ${r.component}" else "") + (if (r.count > 1) " · ×${r.count}" else "") + if (r.sent) " · đã gửi" else "",
                        icon = R.drawable.ic_fluent_info_24_regular, onClick = { detail = r },
                    )
                }
            }
        }),
        actions = if (reports.isEmpty()) emptyList() else listOf(
            SheetAction("Xóa hết", R.drawable.ic_fluent_delete_24_regular, keepOpen = true) { Diagnostics.clear(context) },
        ),
    )

    // ---- Chi tiết 1 báo cáo ----
    val d = detail
    MonikaMenuSheet(
        d != null, { detail = null },
        title = d?.title, subtitle = d?.let { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US).format(it.time) },
        header = {
            Text(
                d?.toText()?.take(4000).orEmpty(), style = Monika.type.caption, color = SheetColors.textSecondary,
                maxLines = 40, overflow = TextOverflow.Ellipsis,
            )
        },
        actions = listOfNotNull(
            d?.let { r -> SheetAction("Gửi báo lỗi", R.drawable.ic_fluent_share_24_regular, highlight = true) { send(r) } },
            d?.let { r ->
                SheetAction("Sao chép", R.drawable.ic_fluent_copy_24_regular) {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("Báo lỗi Aow Monika", r.toText()))
                    Toast.makeText(context, "Đã sao chép", Toast.LENGTH_SHORT).show()
                }
            },
            d?.let { r -> SheetAction("Xóa", R.drawable.ic_fluent_delete_24_regular) { Diagnostics.delete(context, r); detail = null } },
        ),
    )
}

/** Gợi ý cho người chơi theo loại lỗi + giai đoạn. */
private fun causeHint(r: Report?): String {
    val s = r?.session ?: return ""
    val loading = s.stage in setOf("start", "core-ready", "loading-game", "view-created")
    return when (r.kind) {
        "native" -> if (loading) "Lõi sập ngay khi nạp game — thường do file game hỏng/không hợp lõi này. Thử lõi khác hoặc tải lại game."
        else "Lõi giả lập sập giữa chừng. Thử đổi lõi khác, hạ độ phân giải trong Tùy chọn giả lập."
        "lowmem" -> "Máy hết RAM nên Android tắt game. Đóng bớt app nền, hạ độ phân giải / tắt tăng tốc."
        "anr" -> "Game bị treo. Thử lõi khác hoặc hạ độ phân giải."
        "killed" -> "Hệ thống tắt game (thường do thiếu RAM hoặc bạn thoát bằng đa nhiệm)."
        "java" -> "Lỗi trong chương trình Monika."
        else -> "Không rõ nguyên nhân (máy Android cũ không cho biết). Báo lỗi vẫn giúp AowVN tìm ra."
    }
}
