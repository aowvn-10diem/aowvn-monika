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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
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
fun gameReportAction(gameTitle: String? = null, component: String? = null,
                     extraDetail: String = "", closeMenu: () -> Unit): SheetAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<Diagnostics.ReportImage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    if (open) GameReportDialog(image, busy, { if (!busy) open = false }, sendError) { type, description, included ->
        busy = true; sendError = null
        scope.launch {
            try {
                val sent = withContext(Dispatchers.IO) {
                    safelySendUserReport {
                        val report = Diagnostics.recordUser(context, type, description, included,
                            gameTitle = gameTitle, component = component, extraDetail = extraDetail)
                        Diagnostics.sendResult(context, AppGraph.http, report, AppGraph.config.current.crash.endpoint)
                    }
                }
                if (sent.ok) {
                    open = false
                    Toast.makeText(context, context.getString(R.string.game_report_sent), Toast.LENGTH_LONG).show()
                } else {
                    // Giữ bản nháp để thử lại; lỗi tạo/lưu báo cáo cũng có lý do thay vì mắc ở "Đang gửi".
                    sendError = context.getString(R.string.game_report_send_failed, sent.error ?: "lỗi không rõ")
                }
            } finally { busy = false }
        }
    }
    return SheetAction(stringResource(R.string.game_report_title), R.drawable.ic_fluent_document_24_regular) {
        closeMenu()
        scope.launch {
            delay(220) // Menu đóng trước fallback PixelCopy Window.
            image = try { context.activity()?.let { UserGameReport.capture(it) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null } // Vẫn mở form chữ nếu không chụp được màn chuẩn bị.
            sendError = null; open = true
        }
    }
}

private class ReportDraft(initialType: String) {
    var type by mutableStateOf(initialType)
    var description by mutableStateOf("")
    var includeImage by mutableStateOf(true)
}

@Composable
internal fun GameReportDialog(image: Diagnostics.ReportImage?, busy: Boolean, onClose: () -> Unit,
                              error: String? = null, onSend: (String, String, Diagnostics.ReportImage?) -> Unit) {
    val initialType = stringResource(R.string.game_report_type_other)
    val draft = remember { ReportDraft(initialType) }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(stringResource(R.string.game_report_title)) },
        text = { GameReportContent(draft, image, busy, error) },
        confirmButton = { GameReportSubmit(draft, image, busy, onSend) },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(stringResource(R.string.game_report_cancel)) } })
}

/** Test cùng trường + nút gửi, không phụ thuộc cửa sổ Dialog của Robolectric. */
@Composable
internal fun GameReportForm(image: Diagnostics.ReportImage?, busy: Boolean, onClose: () -> Unit,
                            error: String? = null, onSend: (String, String, Diagnostics.ReportImage?) -> Unit) {
    val initialType = stringResource(R.string.game_report_type_other)
    val draft = remember { ReportDraft(initialType) }
    Column {
        GameReportContent(draft, image, busy, error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onClose) { Text(stringResource(R.string.game_report_cancel)) }
            GameReportSubmit(draft, image, busy, onSend)
        }
    }
}

@Composable
private fun GameReportContent(draft: ReportDraft, image: Diagnostics.ReportImage?, busy: Boolean, error: String?) {
    Column {
        GameReportFields(draft, image, busy)
        error?.let { Text(it, style = vn.aow.monika.ui.theme.Monika.type.caption,
            color = vn.aow.monika.ui.theme.Monika.colors.danger, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun GameReportFields(draft: ReportDraft, image: Diagnostics.ReportImage?, busy: Boolean) {
    val types = listOf(
        stringResource(R.string.game_report_type_video), stringResource(R.string.game_report_type_audio),
        stringResource(R.string.game_report_type_input), stringResource(R.string.game_report_type_frozen),
        stringResource(R.string.game_report_type_other))
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.game_report_question))
        types.forEach { choice -> FilterChip(selected = draft.type == choice, enabled = !busy, onClick = { draft.type = choice }, label = { Text(choice) }) }
        OutlinedTextField(value = draft.description, onValueChange = { draft.description = it.take(2000) }, enabled = !busy,
            label = { Text(stringResource(R.string.game_report_description)) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
        Row { Checkbox(checked = draft.includeImage && image != null, onCheckedChange = { draft.includeImage = it }, enabled = image != null && !busy); Text(stringResource(R.string.game_report_include_image)) }
        if (image == null) Text(stringResource(R.string.game_report_no_image))
        Text(stringResource(R.string.game_report_include_diagnostics))
    }
}

@Composable
private fun GameReportSubmit(draft: ReportDraft, image: Diagnostics.ReportImage?, busy: Boolean,
                             onSend: (String, String, Diagnostics.ReportImage?) -> Unit) {
    TextButton(enabled = !busy, onClick = { onSend(draft.type, draft.description, image.takeIf { draft.includeImage }) }) {
        Text(stringResource(if (busy) R.string.game_report_sending else R.string.game_report_send))
    }
}

/** Bắt cả khâu tạo/lưu báo cáo, không chỉ lỗi HTTP; hủy màn hình vẫn hủy coroutine. */
internal fun safelySendUserReport(send: () -> Diagnostics.SendResult): Diagnostics.SendResult = try { send() }
catch (e: CancellationException) { throw e }
catch (e: Exception) { Diagnostics.SendResult(false, e.javaClass.simpleName.ifBlank { "lỗi tạo báo cáo" }) }
