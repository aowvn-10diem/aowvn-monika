package vn.aow.monika.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetColors

/** Kết quả dịch 1 khung hình: các đoạn (gốc → Việt) hoặc thông báo lỗi. */
data class TranslateResult(val lines: List<TLine>, val message: String?)

/** Hộp nổi hiện bản dịch tiếng Việt (bản gốc nhỏ bên dưới). Game vẫn tạm dừng phía sau khi mở menu. */
@Composable
fun BoxScope.TranslateResultSheet(result: TranslateResult?, busy: Boolean, onDismiss: () -> Unit) {
    var last by remember { mutableStateOf<TranslateResult?>(null) }
    if (result != null) last = result
    val r = last
    MonikaMenuSheet(
        result != null || busy, onDismiss,
        title = if (busy) "Đang dịch…" else "Bản dịch tiếng Việt",
        subtitle = if (busy) "Đọc chữ trên khung hình hiện tại" else null,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!busy) {
                    r?.message?.let { Text(it, style = Monika.type.body, color = SheetColors.textSecondary) }
                    r?.lines?.forEach { l ->
                        Column(Modifier.fillMaxWidth().clip(Radius.medium).background(SheetColors.row).padding(12.dp)) {
                            Text(l.vi, style = Monika.type.body, color = SheetColors.text)
                            if (l.original.isNotBlank()) Text(l.original, style = Monika.type.caption, color = SheetColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        actions = emptyList<SheetAction>(),
    )
}
