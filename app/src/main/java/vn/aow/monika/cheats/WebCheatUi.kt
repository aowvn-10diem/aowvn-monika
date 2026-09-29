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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow

private val SPEEDS = listOf(0.5, 1.0, 2.0, 3.0, 4.0)
private val MULTS = listOf(1, 2, 5, 10)

/** Menu cheat + tốc độ cho game web: cùng menu popup dưới đáy của Monika. */
@Composable
fun BoxScope.WebCheatSheet(c: WebCheatController) {
    MonikaMenuSheet(
        c.open, { c.close() }, actions = emptyList(),
        title = "Cheat & tốc độ",
        subtitle = c.message ?: when (c.engine) {
            "mv" -> "RPG Maker MV/MZ · áp ngay trong game"
            "tyrano" -> "TyranoScript · sửa biến của game"
            "mv-loading" -> "Game đang nạp… mở lại menu sau vài giây"
            else -> "Chưa nhận diện engine game · chỉ chỉnh được tốc độ"
        },
        header = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Title("Tốc độ game")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SPEEDS.forEach { s -> SheetChip(if (s < 1) "0.5x" else "${s.toInt()}x", c.speed == s) { c.chooseSpeed(s) } }
                }
                Text("Tốc độ đổi nhịp thời gian của trang; âm thanh vẫn giữ nhịp cũ.", style = Monika.type.caption, color = SheetColors.textSecondary)
                when (c.engine) {
                    "mv" -> MvSection(c)
                    "tyrano" -> TyranoSection(c)
                }
            }
        },
    )
}

@Composable
private fun Title(t: String) = Text(t, style = Monika.type.bodyStrong, color = SheetColors.text, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun MvSection(c: WebCheatController) {
    fun on(k: String) = (c.flags[k] ?: 0.0) > 0
    Title("Bật/tắt")
    listOf("god" to "Bất tử (máu không giảm)", "one" to "Một đòn hạ gục", "enc" to "Không gặp quái ngẫu nhiên", "wall" to "Đi xuyên tường", "fast" to "Chạy nhanh").forEach { (k, label) ->
        SheetRow(label, trailing = { SheetChip(if (on(k)) "Bật" else "Tắt", on(k)) { c.setToggle(k, !on(k)) } })
    }
    Title("Hệ số nhận được")
    listOf("exp" to "EXP", "gold" to "Vàng", "drop" to "Tỉ lệ rớt đồ").forEach { (k, label) ->
        SheetRow(label, trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MULTS.forEach { n -> SheetChip("x$n", (c.flags[k] ?: 1.0).toInt() == n) { c.setMult(k, n) } }
            }
        })
    }
    Title("Chạy ngay")
    ActionRow("Thêm 999.999 vàng", "Thêm") { c.action("gold") }
    ActionRow("Hồi đầy HP/MP cả đội", "Hồi") { c.action("heal") }
    ActionRow("Xóa trạng thái xấu", "Xóa") { c.action("restore") }
    SheetRow("Tăng cấp", trailing = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { SheetChip("+1", false) { c.action("level", 1) }; SheetChip("+10", false) { c.action("level", 10) } } })
    ActionRow("Thêm 100.000 EXP", "Thêm") { c.action("exp", 100000) }
    SheetRow("Cộng mọi chỉ số", trailing = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { SheetChip("+50", false) { c.action("stats", 50) }; SheetChip("+500", false) { c.action("stats", 500) } } })
    ActionRow("Thêm 99 mỗi vật phẩm", "Thêm") { c.action("items") }
    ActionRow("Thêm 9 mỗi vũ khí / giáp", "Thêm") { c.action("weapons") }
    VarEditor(c)
}

@Composable
private fun ActionRow(label: String, chip: String, run: () -> Unit) = SheetRow(label, trailing = { SheetChip(chip, false, onClick = run) })

@Composable
private fun VarEditor(c: WebCheatController) {
    var id by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    Title("Sửa biến / công tắc của game")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Num(id, { id = it }, "Số thứ tự", Modifier.weight(1f))
        Num(value, { value = it }, "Giá trị", Modifier.weight(1f))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetChip("Đọc biến", false) { id.toIntOrNull()?.let { c.readVar(it) } }
        SheetChip("Đặt biến", true) { val i = id.toIntOrNull(); val v = value.toIntOrNull(); if (i != null && v != null) c.setVar(i, v) }
        SheetChip("Bật công tắc", false) { id.toIntOrNull()?.let { c.setSwitch(it, true) } }
        SheetChip("Tắt công tắc", false) { id.toIntOrNull()?.let { c.setSwitch(it, false) } }
    }
    c.varInfo?.let { Text(it, style = Monika.type.caption, color = SheetColors.textSecondary) }
}

@Composable
private fun TyranoSection(c: WebCheatController) {
    Title("Biến số của game (${c.tyrano.size})")
    if (c.tyrano.isEmpty()) Text("Chưa có biến dạng số. Chơi một đoạn rồi mở lại.", style = Monika.type.caption, color = SheetColors.textSecondary)
    c.tyrano.forEach { v ->
        SheetRow("${v.scope}.${v.key}", subtitle = "= ${if (v.value % 1.0 == 0.0) v.value.toLong().toString() else v.value.toString()}", trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SheetChip("−100", false) { c.tyranoAdd(v, -100.0) }
                SheetChip("+100", false) { c.tyranoAdd(v, 100.0) }
                SheetChip("x2", false) { c.tyranoMul(v, 2.0) }
            }
        })
    }
}

@Composable
private fun Num(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    Box(modifier.clip(Radius.medium).background(SheetColors.row).padding(12.dp)) {
        if (value.isEmpty()) Text(hint, style = Monika.type.caption, color = SheetColors.textSecondary)
        BasicTextField(
            value, { onChange(it.filter { ch -> ch.isDigit() || ch == '-' }.take(9)) }, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
    }
}
