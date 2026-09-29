package vn.aow.monika.azahar

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.features.cheats.model.Cheat
import org.citra.citra_emu.features.cheats.model.CheatEngine
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow
import java.io.File

/**
 * Mã cheat (Gateway) của game 3DS đang chạy. Dữ liệu do engine giữ; tệp cheats/<TitleID>.txt nằm trong thư mục Monika quản lý.
 * Mọi hàm gọi ở luồng chính của [AzaharActivity] sau khi engine đã nạp.
 */
class AzaharCheats(private val ctx: Context) {
    class Item(val index: Int, val cheat: Cheat, val name: String, val notes: String, val enabled: Boolean)

    var open by mutableStateOf(false)
    var items by mutableStateOf<List<Item>>(emptyList())
    var adding by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    private var titleId = 0L
    private var dirty = false

    fun show() {
        titleId = runCatching { NativeLibrary.getRunningTitleId() }.getOrDefault(0L)
        if (titleId == 0L) { message = "Game chưa chạy xong, thử lại sau vài giây"; open = true; items = emptyList(); return }
        runCatching { CheatEngine.loadCheatFile(titleId) }
        reload()
        message = null
        open = true
    }

    private fun reload() {
        items = runCatching { CheatEngine.getCheats().mapIndexed { i, c -> Item(i, c, c.getName(), c.getNotes(), c.getEnabled()) } }.getOrDefault(emptyList())
    }

    fun close() { save(); open = false; adding = false }

    private fun save() {
        if (dirty && titleId != 0L) { runCatching { CheatEngine.saveCheatFile(titleId) }; dirty = false }
    }

    fun toggle(i: Item) { i.cheat.setEnabled(!i.enabled); dirty = true; save(); reload() }

    fun remove(i: Item) { runCatching { CheatEngine.removeCheat(i.index) }; dirty = true; save(); reload() }

    /** Trả về null nếu thêm được, hoặc thông báo lỗi. */
    fun add(name: String, notes: String, code: String): String? {
        if (name.isBlank()) return "Nhập tên cho mã"
        val bad = runCatching { Cheat.isValidGatewayCode(code) }.getOrDefault(-1)
        if (bad != 0) return if (bad > 0) "Mã sai ở dòng $bad" else "Mã không hợp lệ"
        runCatching { CheatEngine.addCheat(Cheat.createGatewayCode(name.trim(), notes.trim(), code.trim())) }
            .onFailure { return "Không thêm được mã" }
        dirty = true; save(); reload()
        return null
    }

    /** Nhập tệp cheat .txt (định dạng Citra/Azahar) thay cho tệp của game này. */
    fun importFile(uri: Uri) {
        if (titleId == 0L) return
        val out = File(AzaharConfig.userDir(ctx), "cheats/%016X.txt".format(titleId))
        runCatching {
            out.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
            CheatEngine.loadCheatFile(titleId)
            reload()
            message = "Đã nhập ${items.size} mã"
        }.onFailure { message = "Không đọc được tệp cheat" }
    }
}

@Composable
fun BoxScope.AzaharCheatsSheet(c: AzaharCheats, pickFile: () -> Unit) {
    MonikaMenuSheet(
        c.open, { c.close() }, actions = emptyList(),
        title = "Mã cheat", subtitle = c.message ?: "Bật/tắt áp ngay trong game · lưu theo từng game",
        header = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.items.isEmpty() && !c.adding) Text("Game này chưa có mã. Thêm mã hoặc nhập tệp .txt.", style = Monika.type.caption, color = SheetColors.textSecondary)
                c.items.forEach { i ->
                    SheetRow(
                        i.name, subtitle = i.notes.ifBlank { null },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SheetChip(if (i.enabled) "Bật" else "Tắt", i.enabled) { c.toggle(i) }
                                SheetChip("Xóa", false) { c.remove(i) }
                            }
                        },
                    )
                }
                if (c.adding) AddForm(c) else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetChip("Thêm mã", true) { c.adding = true }
                    SheetChip("Nhập tệp .txt", false, onClick = pickFile)
                }
            }
        },
    )
}

@Composable
private fun AddForm(c: AzaharCheats) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val style = TextStyle(color = Color.White, fontSize = 15.sp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(name, { name = it }, "Tên mã", style, singleLine = true)
        Field(code, { code = it }, "Mã Gateway (mỗi dòng 2 nhóm 8 ký tự hex)", style, singleLine = false)
        err?.let { Text(it, style = Monika.type.caption, color = Color(0xFFFF8A80)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetChip("Lưu", true) { err = c.add(name, "", code); if (err == null) c.adding = false }
            SheetChip("Hủy", false) { c.adding = false }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, style: TextStyle, singleLine: Boolean) {
    Box(Modifier.fillMaxWidth().clip(Radius.medium).background(SheetColors.row).padding(12.dp)) {
        if (value.isEmpty()) Text(hint, style = Monika.type.caption, color = SheetColors.textSecondary)
        BasicTextField(value, onChange, singleLine = singleLine, textStyle = style, cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
    }
}
