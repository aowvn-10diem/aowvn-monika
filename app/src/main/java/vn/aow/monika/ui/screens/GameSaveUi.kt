package vn.aow.monika.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.library.*
import vn.aow.monika.ui.theme.*
import java.io.File

/** Hai thao tác của game, cùng bộ thành phần Monika; không xin quyền bộ nhớ. */
@Composable
internal fun GameSaveButtons(enabled: Boolean, busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    if (!enabled) return
    Column {
        GradientButton("Xuất save", onExport, Modifier.fillMaxWidth(), enabled = !busy)
        DarkButton("Nhập save", onImport, Modifier.fillMaxWidth(), enabled = !busy)
        Text(if (busy) "Đang chuyển save…" else "Thoát màn chơi trước khi chuyển save. Nhập luôn sao lưu bản cũ.",
            style = Monika.type.caption, color = Monika.colors.textSecondary)
    }
}

internal data class SaveUiActions(val busy: Boolean, val onExport: () -> Unit, val onImport: () -> Unit)

/** Launcher/plan nằm ngoài sheet nên đóng menu hoặc hủy SAF không thể nhập nhầm sang game khác. */
@Composable
internal fun rememberGameSaveActions(game: Game?): SaveUiActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Game?>(null) }
    var plan by remember { mutableStateOf<SaveImportPlan?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var backup by remember { mutableStateOf<File?>(null) }
    var backupToExport by remember { mutableStateOf<File?>(null) }

    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                // Nội dung save/URI/đường dẫn không đưa vào báo lỗi; lỗi nhập liệu hiện nguyên nhân cố định.
                if (error !is SaveTransferException) Diagnostics.recordHandled(context, "app:save-transfer",
                    "Không chuyển được save", java.io.IOException(error.javaClass.simpleName))
                result = (error as? SaveTransferException)?.message ?: "Không chuyển được save. Hãy kiểm tra file và dung lượng; nếu vừa nhập, bản sao cũ được giữ trong app."
            } finally { busy = false }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val selected = pending; pending = null
        if (uri != null && selected != null) perform {
            val count = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { GameSaveTransfer.export(context, selected, it) }
                    ?: throw SaveTransferException("Không mở được nơi ghi save.")
            }
            result = "Đã xuất $count file save/state của ${selected.name}."
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val selected = pending; pending = null
        if (uri != null && selected != null) perform {
            plan = withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.take(240) else null
                } ?: throw SaveTransferException("Không đọc được tên file save.")
                context.contentResolver.openInputStream(uri)?.use { GameSaveTransfer.prepare(context, selected, name, it) }
                    ?: throw SaveTransferException("Không đọc được file save.")
            }
        }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val selected = backupToExport; backupToExport = null
        if (uri != null && selected != null) perform {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out -> selected.inputStream().use { it.copyTo(out) } }
                    ?: throw SaveTransferException("Không ghi được bản sao lưu cũ.")
            }
            result = "Đã xuất bản sao save trước khi nhập."
        }
    }
    plan?.let { proposed ->
        AlertDialog(onDismissRequest = { if (!busy) plan = null },
            title = { Text("Nhập save cho ${proposed.target.game.name}?", style = Monika.type.cardTitle) },
            text = { Text("${proposed.target.game.system?.name} · ${proposed.writes.size} file\n${proposed.warning}\nSave cũ được sao lưu trước khi thay thế. Game không bị xóa.", style = Monika.type.body, color = Monika.colors.text) },
            confirmButton = { DarkButton("Sao lưu và nhập", { perform {
                val imported = withContext(Dispatchers.IO) { GameSaveTransfer.commit(context, proposed) }
                plan = null; backup = imported.backup
                result = "Đã nhập ${imported.count} file. Bản sao save cũ được giữ trong app; bạn có thể xuất ra file ZIP ngay."
            } }, enabled = !busy) },
            dismissButton = { SoftPillButton("Hủy", { if (!busy) plan = null }) },
            containerColor = Monika.colors.surface,
        )
    }
    result?.let { message ->
        AlertDialog(onDismissRequest = { result = null },
            title = { Text("Chuyển save", style = Monika.type.cardTitle) },
            text = { Text(message, style = Monika.type.body, color = Monika.colors.text) },
            confirmButton = { SoftPillButton("Đóng", { result = null }) },
            dismissButton = { backup?.let { previous -> SoftPillButton("Lưu bản sao cũ", {
                backupToExport = previous; result = null
                exportBackup.launch("save-truoc-khi-nhap.zip")
            }) } },
            containerColor = Monika.colors.surface,
        )
    }
    return SaveUiActions(busy || pending != null || plan != null, {
        if (!busy && pending == null && plan == null && game != null) { pending = game; backup = null; exportPicker.launch(GameSaveTransfer.filename(game)) }
    }, {
        if (!busy && pending == null && plan == null && game != null) { pending = game; backup = null; importPicker.launch(arrayOf("*/*")) }
    })
}
