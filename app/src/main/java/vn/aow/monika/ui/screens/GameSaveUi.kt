package vn.aow.monika.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** Trạng thái mục Save ở trang game. */
internal sealed interface SaveListState {
    data object Loading : SaveListState
    /** Hệ này chưa có đường chuyển save trong Monika (save vẫn nằm trong kết quả chơi của game). */
    data object Unsupported : SaveListState
    data class Ready(val saves: List<GameSaveEntry>) : SaveListState
    data class Failed(val message: String) : SaveListState
}

/** Câu báo lỗi chung — không chứa tên tệp/đường dẫn của người dùng. */
internal const val SAVE_LIST_ERROR = "Không đọc được save của game. Hãy kiểm tra dung lượng và thử lại."

/**
 * Đọc danh sách save của [game] trên luồng IO; đọc lại khi [reloadKey] đổi (sau khi nhập save).
 * Không ném lỗi ra UI: mọi lỗi thành [SaveListState.Failed] có câu tiếng Việt.
 */
@Composable
internal fun rememberSaveList(game: Game, reloadKey: Int): State<SaveListState> {
    val context = LocalContext.current
    return produceState<SaveListState>(SaveListState.Loading, game.key, reloadKey) {
        value = withContext(Dispatchers.IO) {
            try {
                if (!GameSaveCatalog.supported(context, game)) SaveListState.Unsupported
                else SaveListState.Ready(GameSaveCatalog.list(context, game))
            } catch (error: CancellationException) {
                throw error
            } catch (error: SaveTransferException) {
                SaveListState.Failed(error.message ?: SAVE_LIST_ERROR)
            } catch (error: Exception) {
                // Chỉ mã lỗi + loại ngoại lệ; không ghi tên tệp/đường dẫn của người dùng.
                Diagnostics.recordHandled(
                    context, "app:save-transfer", "doc-save-loi", java.io.IOException(error.javaClass.simpleName),
                )
                SaveListState.Failed(SAVE_LIST_ERROR)
            }
        }
    }
}

/**
 * Mục Save của trang game: liệt kê save hiện có (tên, thời điểm, dung lượng), nút nhập save từ tệp
 * và chia sẻ/xuất save. Chỉ hiển thị; mọi thao tác đi qua [onImport]/[onExport] của màn gọi.
 */
@Composable
internal fun GameSaveSection(
    state: SaveListState,
    busy: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Monika.colors
    val actionsEnabled = !busy && state !is SaveListState.Unsupported
    MonikaCard(modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Save", style = Monika.type.cardTitle, color = c.text, modifier = Modifier.weight(1f))
            if (state is SaveListState.Ready && state.saves.isNotEmpty()) Tag("${state.saves.size} mục")
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            SaveListState.Loading -> Text("Đang đọc save…", style = Monika.type.body, color = c.textSecondary)
            SaveListState.Unsupported -> Text(
                "Hệ này chưa hỗ trợ chuyển save trên Monika. Bạn vẫn chơi và lưu trong game bình thường.",
                style = Monika.type.body, color = c.textSecondary,
            )
            is SaveListState.Failed -> Text(state.message, style = Monika.type.body, color = c.textSecondary)
            is SaveListState.Ready -> if (state.saves.isEmpty()) {
                Text("Chưa có save nào.", style = Monika.type.body, color = c.text)
                Text(
                    "Chơi game rồi lưu trong game, hoặc nhập tệp save có sẵn bên dưới.",
                    style = Monika.type.caption, color = c.textSecondary,
                )
            } else {
                state.saves.forEach { SaveRow(it) }
            }
        }
        Spacer(Modifier.height(12.dp))
        GradientButton("Chia sẻ save", onExport, Modifier.fillMaxWidth(), enabled = actionsEnabled)
        Spacer(Modifier.height(8.dp))
        DarkButton("Nhập save từ tệp", onImport, Modifier.fillMaxWidth(), enabled = actionsEnabled)
        Spacer(Modifier.height(8.dp))
        Text(
            if (busy) "Đang chuyển save…" else "Save cũ luôn được sao lưu trong app trước khi ghi đè. Game không bị xóa.",
            style = Monika.type.caption, color = c.textSecondary,
        )
    }
}

/** Một dòng save: tên + thời điểm bên trái, dung lượng bên phải. */
@Composable
private fun SaveRow(save: GameSaveEntry) {
    val c = Monika.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(save.name, style = Monika.type.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(saveTime(save.savedAt), style = Monika.type.caption, color = c.textSecondary)
        }
        Text(formatGameSize(save.size), style = Monika.type.caption, color = c.textSecondary)
    }
}

/** "12/03/2026 21:05"; không rõ thời điểm thì nói thẳng thay vì hiện 01/01/1970. */
internal fun saveTime(epochMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    if (epochMillis <= 0) "Không rõ thời điểm"
    else java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .format(java.time.Instant.ofEpochMilli(epochMillis).atZone(zone))

/** Launcher/plan nằm ngoài sheet nên đóng menu hoặc hủy SAF không thể nhập nhầm sang game khác. */
@Composable
internal fun rememberGameSaveActions(game: Game?, onImported: () -> Unit = {}): SaveUiActions {
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
                onImported()
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
