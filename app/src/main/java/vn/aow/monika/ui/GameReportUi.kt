package vn.aow.monika.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.diag.UserGameReport
import vn.aow.monika.ui.theme.SheetAction

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Dùng trong menu game; không chụp trước khi người chơi bấm action này. */
@Composable
fun gameReportAction(closeMenu: () -> Unit): SheetAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<Diagnostics.ReportImage?>(null) }
    var busy by remember { mutableStateOf(false) }
    if (open) GameReportDialog(image, busy, { if (!busy) open = false }) { type, description, included ->
        busy = true
        scope.launch {
            val sent = withContext(Dispatchers.IO) {
                val report = Diagnostics.recordUser(context, type, description, included)
                Diagnostics.send(context, AppGraph.http, report, AppGraph.config.current.crash.endpoint)
            }
            busy = false; open = false
            Toast.makeText(context, if (sent) "Đã gửi báo lỗi. Cảm ơn bạn!" else "Đã lưu báo lỗi và chép phần chữ để bạn gửi cho AowVN.", Toast.LENGTH_LONG).show()
        }
    }
    return SheetAction("Báo lỗi game này", R.drawable.ic_fluent_document_24_regular) {
        closeMenu()
        scope.launch {
            delay(220) // Menu đóng trước fallback PixelCopy Window.
            image = context.activity()?.let { UserGameReport.capture(it) }
            open = true
        }
    }
}

private class ReportDraft {
    var type by mutableStateOf("Lỗi khác")
    var description by mutableStateOf("")
    var includeImage by mutableStateOf(true)
}

@Composable
internal fun GameReportDialog(image: Diagnostics.ReportImage?, busy: Boolean, onClose: () -> Unit,
                              onSend: (String, String, Diagnostics.ReportImage?) -> Unit) {
    val draft = remember { ReportDraft() }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text("Báo lỗi game này") },
        text = { GameReportFields(draft, image, busy) },
        confirmButton = { GameReportSubmit(draft, image, busy, onSend) },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Hủy") } })
}

/** Test cùng trường + nút gửi, không phụ thuộc cửa sổ Dialog của Robolectric. */
@Composable
internal fun GameReportForm(image: Diagnostics.ReportImage?, busy: Boolean, onClose: () -> Unit,
                            onSend: (String, String, Diagnostics.ReportImage?) -> Unit) {
    val draft = remember { ReportDraft() }
    Column {
        GameReportFields(draft, image, busy)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onClose) { Text("Hủy") }
            GameReportSubmit(draft, image, busy, onSend)
        }
    }
}

@Composable
private fun GameReportFields(draft: ReportDraft, image: Diagnostics.ReportImage?, busy: Boolean) {
    val types = listOf("Không lên hình", "Không có tiếng", "Phím không hoạt động", "Game bị treo", "Lỗi khác")
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bạn gặp lỗi gì?")
        types.forEach { choice -> FilterChip(selected = draft.type == choice, enabled = !busy, onClick = { draft.type = choice }, label = { Text(choice) }) }
        OutlinedTextField(value = draft.description, onValueChange = { draft.description = it.take(2000) }, enabled = !busy,
            label = { Text("Mô tả thêm (tùy chọn)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
        Row { Checkbox(checked = draft.includeImage && image != null, onCheckedChange = { draft.includeImage = it }, enabled = image != null && !busy); Text("Kèm ảnh game") }
        if (image == null) Text("Chưa chụp được ảnh; báo lỗi vẫn gửi được.")
        Text("Kèm thông tin máy và nhật ký lỗi gần đây.")
    }
}

@Composable
private fun GameReportSubmit(draft: ReportDraft, image: Diagnostics.ReportImage?, busy: Boolean,
                             onSend: (String, String, Diagnostics.ReportImage?) -> Unit) {
    TextButton(enabled = !busy, onClick = { onSend(draft.type, draft.description, image.takeIf { draft.includeImage }) }) {
        Text(if (busy) "Đang gửi…" else "Gửi báo lỗi")
    }
}
