package vn.aow.monika.apkinstall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.ui.theme.GradientProgress
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow

/** Menu popup của trình cài game (cùng thiết kế MonikaMenuSheet với mọi giả lập). */
@Composable
fun BoxScope.ApkInstallSheet(
    st: UiState,
    onInstall: () -> Unit,
    onChoose: (java.io.File) -> Unit,
    onRetry: () -> Unit,
    onUninstallOld: (String) -> Unit,
    onPlay: (String) -> Unit,
    onHealthStart: () -> Unit,
    onHealthSkip: () -> Unit,
    onHealthAnswer: (Boolean) -> Unit,
    onMethod: (Method) -> Unit,
    onOpenGame: () -> Unit,
    onPickFolder: () -> Unit,
    onClose: () -> Unit,
) {
    val r = st.result
    val name = r?.label ?: r?.packageName
    val close = SheetAction("Đóng", R.drawable.ic_fluent_dismiss_24_regular, onClick = onClose)
    val (title, subtitle) = when (st.phase) {
        UiState.Phase.IDLE, UiState.Phase.INSPECTING -> "Đang đọc gói game…" to null
        UiState.Phase.NEED_CHOICE -> "Chọn game cần cài" to "${st.candidates.size} file cài đặt trong thư mục"
        UiState.Phase.READY -> (name ?: "Cài game Android") to r?.let { "v${it.versionName ?: it.versionCode} · ${size(it.totalBytes)}" }
        UiState.Phase.INSTALLING -> "Đang cài game" to name
        UiState.Phase.COPYING_OBB -> "Đang chép dữ liệu game" to name
        UiState.Phase.REPACKING -> "Đang chuẩn bị game" to name
        UiState.Phase.PUSHING_DATA -> "Đang chép dữ liệu (Data)" to name
        UiState.Phase.HEALTH_READY -> "Đã cài — kiểm tra thử" to name
        UiState.Phase.HEALTH_RUNNING -> "Đang kiểm tra game…" to name
        UiState.Phase.HEALTH_ASK -> "Game có chạy được không?" to name
        UiState.Phase.CHOOSE_METHOD -> "Thử cách khác" to name
        UiState.Phase.SAF_PREPARE -> "Cách 2 — cấp quyền thư mục" to name
        UiState.Phase.DONE -> "Đã cài xong" to name
        UiState.Phase.FAILED -> "Chưa cài được" to name
    }
    val actions = when (st.phase) {
        UiState.Phase.READY -> if (r != null && r.fatal == null)
            listOf(SheetAction("Cài đặt", R.drawable.ic_fluent_arrow_download_24_regular, highlight = true, onClick = onInstall), close)
        else listOf(close)
        UiState.Phase.INSTALLING, UiState.Phase.COPYING_OBB, UiState.Phase.REPACKING, UiState.Phase.PUSHING_DATA ->
            listOf(SheetAction("Ẩn", R.drawable.ic_fluent_dismiss_24_regular, onClick = onClose))
        UiState.Phase.HEALTH_READY -> listOf(
            SheetAction("Mở thử", R.drawable.ic_fluent_play_24_filled, highlight = true, onClick = onHealthStart),
            SheetAction("Bỏ qua", R.drawable.ic_fluent_dismiss_24_regular, onClick = onHealthSkip),
        )
        UiState.Phase.HEALTH_ASK -> listOf(
            SheetAction("Được", R.drawable.ic_fluent_checkmark_circle_24_filled, highlight = true) { onHealthAnswer(true) },
            SheetAction("Không được", R.drawable.ic_fluent_dismiss_24_regular) { onHealthAnswer(false) },
        )
        UiState.Phase.CHOOSE_METHOD -> {
            val applicable = r?.let { ApkInstallFlow.applicable(it) }.orEmpty()
            Method.values().filter { it in applicable }.map { m ->
                val a = st.checklist.firstOrNull { it.method == m }
                SheetAction(if (m == Method.REPACK) "Thử Cách 1 lại" else "Cách ${m.ordinal + 1}", R.drawable.ic_fluent_arrow_clockwise_24_regular,
                    enabled = a?.state != State.NOT_AVAILABLE && m != Method.ADB) { onMethod(m) }
            } + close
        }
        UiState.Phase.SAF_PREPARE -> listOf(
            SheetAction("Chọn thư mục", R.drawable.ic_fluent_folder_open_24_regular, highlight = true, onClick = onPickFolder),
            SheetAction("Mở game", R.drawable.ic_fluent_play_24_filled, onClick = onOpenGame),
            close,
        )
        UiState.Phase.HEALTH_RUNNING -> listOf(close)
        UiState.Phase.DONE -> listOfNotNull(r?.let { SheetAction("Chơi", R.drawable.ic_fluent_play_24_filled, highlight = true) { onPlay(it.packageName) } }, close)
        UiState.Phase.FAILED -> {
            val kind = st.failure?.kind
            val pkg = r?.packageName
            listOfNotNull(
                if ((kind == InstallOutcome.Kind.SIGNATURE_CONFLICT || kind == InstallOutcome.Kind.DOWNGRADE) && pkg != null)
                    SheetAction("Gỡ bản cũ", R.drawable.ic_fluent_delete_24_regular, highlight = true) { onUninstallOld(pkg) }
                else SheetAction("Thử lại", R.drawable.ic_fluent_arrow_clockwise_24_regular, highlight = true, onClick = onRetry),
                close,
            )
        }
        else -> listOf(close)
    }
    MonikaMenuSheet(
        visible = true, onDismiss = onClose, actions = actions, title = title, subtitle = subtitle,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (st.phase) {
                    UiState.Phase.NEED_CHOICE -> st.candidates.take(8).forEach { f ->
                        SheetRow(f.name, subtitle = size(f.length()), icon = R.drawable.ic_fluent_arrow_download_24_regular, onClick = { onChoose(f) })
                    }
                    UiState.Phase.READY -> {
                        st.message?.let { SheetRow(it, icon = R.drawable.ic_fluent_info_24_regular) }
                        r?.problems?.forEach { p -> SheetRow(p.text, maxTitleLines = 6, icon = R.drawable.ic_fluent_alert_24_regular) }
                        r?.let { info(it) }
                    }
                    UiState.Phase.INSTALLING, UiState.Phase.COPYING_OBB, UiState.Phase.REPACKING, UiState.Phase.PUSHING_DATA -> {
                        Text(
                            when (st.phase) {
                                UiState.Phase.INSTALLING -> "Đang cài đặt"
                                UiState.Phase.REPACKING -> "Đang chuẩn bị game"
                                UiState.Phase.PUSHING_DATA -> "Game đang tự chép dữ liệu"
                                else -> "Đang chép dữ liệu"
                            } + (st.percent?.let { " $it%" } ?: "…") +
                                "\nBạn có thể bấm Ẩn — Monika vẫn tiếp tục và báo khi xong.",
                            style = Monika.type.caption, color = SheetColors.textSecondary,
                        )
                        GradientProgress((st.percent ?: 0) / 100f)
                    }
                    UiState.Phase.DONE -> {
                        SheetRow("Game đã sẵn sàng. Bấm Chơi để mở.", icon = R.drawable.ic_fluent_checkmark_circle_24_filled)
                        if (st.skippedData) SheetRow("Game này còn dữ liệu (Data) cần chép thêm — sẽ hỗ trợ ở bản sau.", maxTitleLines = 4, icon = R.drawable.ic_fluent_alert_24_regular)
                    }
                    UiState.Phase.FAILED -> {
                        SheetRow(st.failure?.text.orEmpty(), maxTitleLines = 6, icon = R.drawable.ic_fluent_alert_24_regular)
                        if (st.checklist.isNotEmpty()) Checklist(st.checklist)
                    }
                    UiState.Phase.HEALTH_READY -> SheetRow("Monika sẽ mở game khoảng 15 giây để xem có chạy được không, rồi quay lại đây để bạn xác nhận.", maxTitleLines = 5, icon = R.drawable.ic_fluent_info_24_regular)
                    UiState.Phase.HEALTH_RUNNING -> SheetRow("Đang theo dõi game… Bạn cứ chơi thử; đợi khoảng 20 giây rồi quay lại Monika.", maxTitleLines = 4, icon = R.drawable.ic_fluent_info_24_regular)
                    UiState.Phase.HEALTH_ASK -> {
                        SheetRow("Monika thấy game hiện màn hình và chạy ổn. Game có vào được màn hình chơi không?", maxTitleLines = 4, icon = R.drawable.ic_fluent_info_24_regular)
                        Checklist(st.checklist)
                    }
                    UiState.Phase.SAF_PREPARE -> {
                        SheetRow("① Bấm Mở game, đợi vào tới màn hình chính rồi thoát (để Android tạo thư mục dữ liệu).", maxTitleLines = 4, icon = R.drawable.ic_fluent_play_24_regular)
                        SheetRow("② Bấm Chọn thư mục → \"Dùng thư mục này\" → \"Cho phép\". Nếu nút bị mờ, máy của bạn chặn Cách 2 — hãy thử Cách 3.", maxTitleLines = 5, icon = R.drawable.ic_fluent_folder_open_24_regular)
                    }
                    UiState.Phase.CHOOSE_METHOD -> {
                        st.message?.let { SheetRow(it, maxTitleLines = 5, icon = R.drawable.ic_fluent_alert_24_regular) }
                        Checklist(st.checklist)
                    }
                    else -> Box(Modifier.padding(4.dp)) { vn.aow.monika.ui.theme.Spinner() }
                }
            }
        },
    )
}

/** Checklist Cách 1/2/3 của game: đã thử cách nào, kết quả ra sao. */
@Composable
private fun Checklist(list: List<Attempt>) {
    list.forEach { a ->
        val icon = when (a.state) { State.OK -> "✅"; State.FAILED -> "❌"; State.RUNNING -> "⏳"; State.NOT_TRIED -> "⚪"; State.NOT_AVAILABLE -> "🚫" }
        SheetRow("$icon ${a.method.title}", maxTitleLines = 3, subtitle = a.reason ?: if (a.method != Method.REPACK && a.state == State.NOT_TRIED) "Sắp có ở bản sau." else a.method.note)
    }
}

@Composable
private fun info(r: InspectResult) {
    if (r.obbFiles.isNotEmpty()) SheetRow("Có dữ liệu OBB: ${r.obbFiles.size} file (${size(r.obbFiles.sumOf { it.payload.size })}) — Monika tự chép", icon = R.drawable.ic_fluent_storage_24_regular)
    if (r.dataFiles.isNotEmpty()) SheetRow("Có dữ liệu Data: ${r.dataFiles.size} file (${size(r.dataFiles.sumOf { it.payload.size })})", icon = R.drawable.ic_fluent_folder_open_24_regular)
    if (r.parts.size > 1) SheetRow("Gói gồm ${r.parts.size} phần (APK chia nhỏ)", icon = R.drawable.ic_fluent_layer_24_regular)
}

internal fun size(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0).replace('.', ',')
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "%.0f KB".format(bytes / 1024.0)
}
