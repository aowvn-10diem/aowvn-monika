package vn.aow.monika.cheats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow

/** Menu cheat dùng chung cho mọi giả lập: cùng menu popup dưới đáy của Monika. */
@Composable
fun BoxScope.CheatSheet(c: CheatController, pickFile: () -> Unit) {
    val scope = rememberCoroutineScope()
    val auto = c.items.count { it.source == "auto" }
    MonikaMenuSheet(
        c.open, { c.close() }, actions = emptyList(),
        title = "Mã cheat",
        subtitle = c.message ?: if (auto > 0) "Monika đã nhận diện game và thêm $auto mã · bật/tắt áp ngay" else "Bật/tắt áp ngay trong game · lưu theo từng game",
        header = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.variants.size > 1) {
                    Text("Bộ mã", style = Monika.type.caption, color = SheetColors.textSecondary)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.variants.forEach { v -> SheetChip(v.label, v.file == c.currentSet) { scope.launch { c.switchSet(v) } } }
                    }
                }
                if (c.busy) Text("Đang tải bộ mã…", style = Monika.type.caption, color = SheetColors.textSecondary)
                if (c.items.isEmpty() && !c.adding && !c.busy) {
                    Text("Chưa có mã cho game này. Thêm mã hoặc nhập tệp .cht (Monika tự tìm mã khi nhận diện được tên game).", style = Monika.type.caption, color = SheetColors.textSecondary)
                }
                c.items.forEachIndexed { i, e ->
                    SheetRow(
                        e.name, subtitle = if (e.source == "auto") "Tự thêm" else "Của bạn",
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SheetChip(if (e.enabled) "Bật" else "Tắt", e.enabled) { c.toggle(i) }
                                SheetChip("Xóa", false) { c.remove(i) }
                            }
                        },
                    )
                }
                if (c.adding) AddForm(c) else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetChip("Thêm mã", true) { c.adding = true }
                    SheetChip("Nhập tệp .cht", false, onClick = pickFile)
                }
            }
        },
    )
}

@Composable
private fun AddForm(c: CheatController) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val style = TextStyle(color = Color.White, fontSize = 15.sp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(name, { name = it }, "Tên mã (vd. Vô hạn HP)", style, true)
        Field(code, { code = it }, "Mã (GameShark / Action Replay / Game Genie…), mỗi dòng 1 mã", style, false)
        err?.let { Text(it, style = Monika.type.caption, color = Color(0xFFFF8A80)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetChip("Lưu", true) { err = c.add(name, code); if (err == null) c.adding = false }
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
