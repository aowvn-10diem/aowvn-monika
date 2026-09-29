package vn.aow.monika.azahar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
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
import org.citra.citra_emu.applets.SoftwareKeyboard
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow

/**
 * Hộp thoại của engine 3DS (thông báo, bàn phím, chọn Mii) vẽ bằng đúng menu popup dưới đáy của Monika —
 * cùng ngôn ngữ thiết kế với mọi giả lập khác. Kết quả trả về luồng giả lập đang chờ.
 */
@Composable
fun BoxScope.AzaharDialogs(d: AzDialog?) {
    when (d) {
        is AzDialog.Message -> MonikaMenuSheet(
            true, { d.reply.complete(false) }, actions = emptyList(), title = d.title,
            header = {
                Text(d.text, style = Monika.type.body, color = SheetColors.text, modifier = Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.yesNo) {
                        SheetChip(if (d.canContinue) "Tiếp tục" else "Có", true) { d.reply.complete(true) }
                        SheetChip(if (d.canContinue) "Dừng game" else "Không", false) { d.reply.complete(false) }
                    } else SheetChip("OK", true) { d.reply.complete(true) }
                }
            },
        )
        is AzDialog.Keyboard -> {
            var text by remember(d) { mutableStateOf("") }
            var error by remember(d) { mutableStateOf<String?>(null) }
            val max = d.config.maxTextLength
            MonikaMenuSheet(
                true, { d.reply.complete(SoftwareKeyboard.KeyboardData(1, "")) }, actions = emptyList(), title = d.title,
                header = {
                    Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().clip(Radius.medium).background(SheetColors.row).padding(12.dp)) {
                            BasicTextField(
                                text, { text = if (max > 0) it.take(max) else it },
                                singleLine = !d.config.multilineMode,
                                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        error?.let { Text(it, style = Monika.type.caption, color = Color(0xFFFF8A80)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val labels = d.config.buttonText
                            if (d.config.buttonConfig != SoftwareKeyboard.ButtonConfig.SINGLE) {
                                SheetChip(labels.getOrNull(0)?.ifBlank { null } ?: "Hủy", false) { d.reply.complete(SoftwareKeyboard.KeyboardData(0, "")) }
                            }
                            val ok = when (d.config.buttonConfig) { SoftwareKeyboard.ButtonConfig.SINGLE -> 0; SoftwareKeyboard.ButtonConfig.DUAL -> 1; else -> 2 }
                            SheetChip(labels.getOrNull(ok)?.ifBlank { null } ?: "OK", true) {
                                // Native kiểm tra chữ nhập (độ dài, ký tự cấm…); hợp lệ mới trả về.
                                val err = runCatching { SoftwareKeyboard.ValidateInput(text) }.getOrDefault(SoftwareKeyboard.ValidationError.None)
                                if (err == SoftwareKeyboard.ValidationError.None) d.reply.complete(SoftwareKeyboard.KeyboardData(ok, text))
                                else error = when (err) {
                                    SoftwareKeyboard.ValidationError.BlankInputNotAllowed, SoftwareKeyboard.ValidationError.EmptyInputNotAllowed -> "Không được để trống"
                                    SoftwareKeyboard.ValidationError.MaxLengthExceeded, SoftwareKeyboard.ValidationError.MaxDigitsExceeded -> "Nhập quá dài"
                                    SoftwareKeyboard.ValidationError.FixedLengthRequired -> "Cần đúng độ dài yêu cầu"
                                    else -> "Chữ nhập chưa hợp lệ"
                                }
                            }
                        }
                    }
                },
            )
        }
        is AzDialog.Mii -> MonikaMenuSheet(
            true, { d.reply.complete(d.config.let { org.citra.citra_emu.applets.MiiSelector.MiiSelectorData(1, 0) }) }, actions = emptyList(), title = d.title,
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.config.miiNames.forEachIndexed { i, n ->
                        SheetRow(n, onClick = { d.reply.complete(org.citra.citra_emu.applets.MiiSelector.MiiSelectorData(0, i)) })
                    }
                    if (d.config.enableCancelButton) SheetChip("Hủy", false) { d.reply.complete(org.citra.citra_emu.applets.MiiSelector.MiiSelectorData(1, 0)) }
                }
            },
        )
        null -> Unit
    }
}
